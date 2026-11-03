package com.lumestar.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Dados do TMDB para um filme ou série. Vazio (tid = 0) quando não foi encontrado. */
class Meta(
    val tid: Int, val tv: Boolean, val poster: String, val backdrop: String, val overview: String, val vote: Double, val year: Int,
    var genres: String = "", var cast: String = "", var runtime: Int = 0, var cert: String = "",
) {
    fun posterUrl(w: Int = 342) = if (poster.isBlank()) "" else "https://image.tmdb.org/t/p/w$w$poster"
    fun backdropUrl(w: Int = 1280) = if (backdrop.isBlank()) "" else "https://image.tmdb.org/t/p/w$w$backdrop"
}

/**
 * Capas, fundos, sinopse e nota do TMDB (themoviedb.org).
 * Busca só o que aparece na tela, um de cada vez em segundo plano, e guarda no aparelho.
 * Precisa da chave grátis do próprio usuário (Configurações › Capas e sinopses).
 */
object Tmdb {
    var rev by mutableIntStateOf(0)
    var status by mutableStateOf("")
    private lateinit var file: File
    private lateinit var sp: android.content.SharedPreferences
    private val cache = ConcurrentHashMap<String, Meta>()
    private val pending = ConcurrentHashMap.newKeySet<String>()
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var dirty = false
    @Volatile private var keyBad = false

    /** chave do usuário (Configurações) ou, se não houver, a chave padrão do app */
    var userKey: String = ""
        private set
    /** usa a chave reserva (TMDB_KEY_2) quando a principal é recusada */
    @Volatile private var backup = false
    val key: String get() = userKey.ifBlank { if (backup || BuildConfig.TMDB_KEY.isBlank()) BuildConfig.TMDB_KEY2 else BuildConfig.TMDB_KEY }
    private var testKey: String? = null
    private val useKey get() = testKey ?: key

    fun init(c: Context) {
        sp = c.getSharedPreferences("lumestar-tmdb", Context.MODE_PRIVATE)
        userKey = Secret.dec(sp.getString("chave", "") ?: "")
        file = File(c.filesDir, "tmdb.json")
        Thread {
            try {
                if (file.exists()) {
                    val o = JSONObject(file.readText())
                    o.keys().forEach { k -> val m = o.getJSONObject(k); cache[k] = Meta(m.optInt("i"), m.optBoolean("tv"), m.optString("p"), m.optString("b"), m.optString("o"), m.optDouble("v", 0.0), m.optInt("y"), m.optString("g"), m.optString("c"), m.optInt("r"), m.optString("k")) }
                    main.post { rev++ }
                }
            } catch (e: Exception) {}
        }.start()
    }

    val on get() = key.isNotBlank() && !keyBad
    val count get() = cache.values.count { it.tid > 0 }

    fun setKey(k: String) {
        userKey = k.trim(); keyBad = false
        sp.edit().putString("chave", Secret.enc(userKey)).apply()
        cache.entries.removeIf { it.value.tid == 0 } // tenta de novo o que não tinha achado
        rev++
    }

    fun clear() { cache.clear(); file.delete(); rev++ }

    /** chave de cada título, calculada uma vez só */
    private val keys = ConcurrentHashMap<String, String>()
    private fun cacheKey(it: Item) = keys.getOrPut(it.id) { (if (it.kind == SERIES) "s:" else "f:") + clean(it.title) + "|" + it.year }

    /** Só consulta o que já foi baixado (rápido; não agenda busca). */
    fun cachedPoster(it: Item): String {
        if (it.kind == LIVE || cache.isEmpty()) return ""
        return cache[cacheKey(it)]?.takeIf { m -> m.tid > 0 }?.posterUrl() ?: ""
    }

    /** Tira do nome o que atrapalha a busca: [4K], (Dublado), LEG, ano, etc. */
    private val RX_BR = Regex("\\[[^\\]]*\\]|\\([^)]*\\)|\\{[^}]*\\}")
    private val RX_TAGS = Regex("(?i)\\b(4k|uhd|fhd|full ?hd|hd|sd|1080p|720p|2160p|h265|hevc|dublado|dub|legendado|leg|nacional|dual ?audio|multi|cam|ts|hdr|10bit|s\\d{1,2}e\\d{1,3})\\b")
    private val RX_YEAR = Regex("\\b(19|20)\\d{2}\\b")
    private val RX_SEP = Regex("[|_.:\\-–]+")
    private val RX_SP = Regex("\\s+")

    fun clean(t: String): String {
        var s = RX_BR.replace(t, " ")
        s = RX_TAGS.replace(s, " ")
        s = RX_SEP.replace(RX_YEAR.replace(s, " "), " ")
        return RX_SP.replace(s, " ").trim()
    }

    /** O que já se sabe deste título. Se ainda não buscou, agenda a busca (e a tela atualiza sozinha). */
    fun meta(it: Item): Meta? {
        if (it.kind == LIVE) return null
        if (!on && cache.isEmpty()) return null // sem chave e nada guardado: não gasta tempo
        val k = cacheKey(it)
        cache[k]?.let { m -> return if (m.tid > 0) m else null }
        if (on && pending.add(k)) pool.execute { fetch(it, k) }
        return null
    }

    fun poster(it: Item): String = meta(it)?.posterUrl() ?: ""

    private fun get(path: String): JSONObject {
        val k = useKey
        val bearer = k.length > 40
        val url = "https://api.themoviedb.org/3/$path" + (if (path.contains('?')) "&" else "?") + "language=pt-BR" + (if (bearer) "" else "&api_key=$k")
        val c = Net.open(url, 15000, if (bearer) mapOf("Authorization" to "Bearer $k", "Accept" to "application/json") else emptyMap())
        return JSONObject(Net.read(c))
    }

    private fun fetch(it: Item, k: String) {
        try {
            val tv = it.kind == SERIES
            val q = clean(it.title)
            if (q.length < 2) { cache[k] = Meta(0, tv, "", "", "", 0.0, 0); return }
            fun search(withYear: Boolean): JSONObject? {
                val y = if (withYear && it.year > 0) (if (tv) "&first_air_date_year=${it.year}" else "&year=${it.year}") else ""
                val r = get((if (tv) "search/tv" else "search/movie") + "?query=" + URLEncoder.encode(q, "UTF-8") + y).optJSONArray("results")
                return if (r != null && r.length() > 0) r.getJSONObject(0) else null
            }
            val o = search(true) ?: if (it.year > 0) search(false) else null
            cache[k] = if (o == null) Meta(0, tv, "", "", "", 0.0, 0) else Meta(
                o.optInt("id"), tv, o.optString("poster_path").takeIf { p -> p != "null" } ?: "", o.optString("backdrop_path").takeIf { p -> p != "null" } ?: "",
                o.optString("overview"), o.optDouble("vote_average", 0.0), (o.optString(if (tv) "first_air_date" else "release_date").take(4).toIntOrNull() ?: 0)
            )
            dirty = true
            main.post { rev++ }
            scheduleSave()
        } catch (e: java.io.IOException) {
            if (e.message?.contains("401") == true && userKey.isBlank() && !backup && BuildConfig.TMDB_KEY2.isNotBlank() && BuildConfig.TMDB_KEY.isNotBlank()) { backup = true; pending.remove(k); return }
            if (e.message?.contains("401") == true) { keyBad = true; main.post { status = "A chave do TMDB foi recusada. Confira em Configurações › Capas e sinopses."; rev++ } }
        } catch (e: Exception) {
        } finally { pending.remove(k) }
    }

    /** Gêneros, duração e elenco: só quando abre o detalhe. */
    fun details(m: Meta, done: () -> Unit) {
        if (!on || m.tid == 0 || m.genres.isNotEmpty() || m.cast.isNotEmpty()) return
        pool.execute {
            try {
                val o = get((if (m.tv) "tv/" else "movie/") + m.tid + "?append_to_response=credits," + (if (m.tv) "content_ratings" else "release_dates"))
                val g = o.optJSONArray("genres"); val gl = ArrayList<String>()
                if (g != null) for (i in 0 until g.length()) gl.add(g.getJSONObject(i).optString("name"))
                val cr = o.optJSONObject("credits")?.optJSONArray("cast"); val cl = ArrayList<String>()
                if (cr != null) for (i in 0 until minOf(5, cr.length())) cl.add(cr.getJSONObject(i).optString("name"))
                m.genres = gl.joinToString(", ").ifBlank { "—" }; m.cast = cl.joinToString(", ")
                m.runtime = o.optInt("runtime", 0).takeIf { r -> r > 0 } ?: (o.optJSONArray("episode_run_time")?.optInt(0) ?: 0)
                m.cert = try {
                    if (m.tv) {
                        val a = o.optJSONObject("content_ratings")?.optJSONArray("results"); var c = ""
                        if (a != null) for (i in 0 until a.length()) if (a.getJSONObject(i).optString("iso_3166_1") == "BR") c = a.getJSONObject(i).optString("rating")
                        c
                    } else {
                        val a = o.optJSONObject("release_dates")?.optJSONArray("results"); var c = ""
                        if (a != null) for (i in 0 until a.length()) if (a.getJSONObject(i).optString("iso_3166_1") == "BR") {
                            val d = a.getJSONObject(i).optJSONArray("release_dates")
                            if (d != null) for (j in 0 until d.length()) { val x = d.getJSONObject(j).optString("certification"); if (x.isNotBlank()) { c = x; break } }
                        }
                        c
                    }
                } catch (e: Exception) { "" }
                dirty = true; scheduleSave()
                main.post { rev++; done() }
            } catch (e: Exception) {}
        }
    }

    /** Testa a chave com uma busca simples. */
    fun test(k: String, cb: (String, Boolean) -> Unit) {
        Thread {
            val r = try {
                testKey = k.trim()
                val o = get("search/movie?query=" + URLEncoder.encode("Matrix", "UTF-8"))
                "Chave aceita · " + (o.optJSONArray("results")?.length() ?: 0) + " resultados de teste" to true
            } catch (e: Throwable) {
                (if (e.message?.contains("401") == true) "Chave recusada pelo TMDB. Confira se copiou a \"Chave da API\" inteira." else "Falhou: " + Net.why(e)) to false
            } finally { testKey = null }
            main.post { cb(r.first, r.second) }
        }.start()
    }

    @Volatile private var saving = false
    private fun scheduleSave() {
        if (saving) return
        saving = true
        main.postDelayed({
            Thread {
                try {
                    dirty = false
                    val o = JSONObject()
                    cache.forEach { (k, m) -> o.put(k, JSONObject().put("i", m.tid).put("tv", m.tv).put("p", m.poster).put("b", m.backdrop).put("o", m.overview).put("v", m.vote).put("y", m.year).put("g", m.genres).put("c", m.cast).put("r", m.runtime).put("k", m.cert)) }
                    file.writeText(o.toString())
                } catch (e: Exception) {}
                saving = false
            }.start()
        }, 4000)
    }
}
