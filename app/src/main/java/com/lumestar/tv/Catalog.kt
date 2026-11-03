package com.lumestar.tv

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.Normalizer
import java.util.Calendar
import kotlin.concurrent.thread

const val LIVE = 0
const val MOVIE = 1
const val SERIES = 2

/** Um título ou canal do catálogo, venha de onde vier (conta Xtream ou lista M3U/JSON/TXT). */
class Item(
    val id: String,
    val title: String,
    val group: String,
    val kind: Int,
    val url: String,
    val logo: String,
    val year: Int,
    val plot: String,
    val rating: String,
    val seriesId: String,
    val src: String,
    val tabs: Set<String>,
    val adult: Boolean,
    val ext: String = "",
)

class Episode(val id: String, val season: Int, val num: Int, val title: String, val url: String, val plot: String)

fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** Organiza cada item nas abas certas pelo grupo (group-title) e pelo nome, como no simulador. */
object Classifier {
    private val rules = listOf(
        "series" to Regex("serie|temporada|\\bs\\d+\\s*e\\d+"),
        "novelas" to Regex("novela"),
        "infantil" to Regex("infantil|kids|desenho|crianc|anima[cç]|cartoon|disney ?junior|nick"),
        "documentarios" to Regex("document|docu|discovery|nat ?geo|history|animal planet"),
        "esportes" to Regex("esporte|sport|futebol|luta|combate|ufc|premiere|espn|nba|nfl|f1|formula"),
        "noticias" to Regex("noticia|news|jornal|cnn"),
        "musicas" to Regex("radio|musica|music|clipe|\\bfm\\b|mtv|bis\\b"),
        "filmes" to Regex("filme|movie|vod|cinema|lancamento"),
    )
    private val adultRx = Regex("adult|xxx|\\+18|18\\+|adulto|porn|hot ?club|erotic|sexy")
    val adultWords = adultRx

    fun isAdult(group: String, name: String) = adultRx.containsMatchIn(norm("$group $name"))

    /** Abas de um item: canais sempre em TV ao vivo e, se forem de esporte, notícia etc., também na aba do assunto. */
    fun tabs(kind: Int, group: String, name: String): Set<String> {
        val g = norm(group); val n = norm(name)
        var hit: String? = null
        for ((t, re) in rules) if (g.isNotEmpty() && re.containsMatchIn(g)) { hit = t; break }
        if (hit == null) for ((t, re) in rules) if (re.containsMatchIn(n)) { hit = t; break }
        val h: String = hit ?: ""
        return when (kind) {
            LIVE -> if (h == "musicas") setOf("musicas") else if (h.isNotEmpty() && h != "filmes" && h != "series" && h != "novelas") setOf("aovivo", h) else setOf("aovivo")
            SERIES -> setOf(if (h == "novelas" || h == "infantil" || h == "documentarios") h else "series")
            else -> setOf(when (h) { "infantil", "documentarios", "esportes", "novelas" -> h; "series" -> "series"; else -> "filmes" })
        }
    }

    fun yearOf(title: String, explicit: String = ""): Int {
        explicit.trim().take(4).toIntOrNull()?.let { if (it in 1900..2100) return it }
        return Regex("[(\\[](\\d{4})[)\\]]").find(title)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1900..2100 } ?: 0
    }
}

/** Catálogo em memória, já separado por aba e por categoria. */
object Catalog {
    var rev by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var status by mutableStateOf("")

    @Volatile var items: List<Item> = emptyList(); private set
    @Volatile var byId: Map<String, Item> = emptyMap(); private set
    /** aba -> (categoria -> itens em ordem alfabética), categorias também em ordem alfabética */
    @Volatile var byTab: Map<String, List<Pair<String, List<Item>>>> = emptyMap(); private set
    val episodes = java.util.concurrent.ConcurrentHashMap<String, List<Episode>>()
    val year = Calendar.getInstance().get(Calendar.YEAR)

    fun visible(i: Item) = Store.adultOpen || !i.adult

    fun publish(list: List<Item>) {
        val dedup = LinkedHashMap<String, Item>()
        list.forEach { dedup.putIfAbsent(it.id, it) }
        val all = dedup.values.toList()
        val tabs = HashMap<String, HashMap<String, ArrayList<Item>>>()
        for (it in all) for (t in it.tabs) tabs.getOrPut(t) { HashMap() }.getOrPut(it.group.ifBlank { "Sem categoria" }) { ArrayList() }.add(it)
        val cmp = Comparator<String> { a, b -> norm(a).compareTo(norm(b)) }
        val out = HashMap<String, List<Pair<String, List<Item>>>>()
        for ((t, groups) in tabs) out[t] = groups.entries.sortedWith { a, b -> cmp.compare(a.key, b.key) }.map { (g, l) -> g to l.sortedWith { a, b -> cmp.compare(a.title, b.title) } }
        items = all; byId = dedup; byTab = out
        rev++
    }

    fun rows(tab: String): List<Pair<String, List<Item>>> =
        (byTab[tab] ?: emptyList()).map { (g, l) -> g to l.filter { visible(it) } }.filter { it.second.isNotEmpty() }

    fun count(tab: String) = rows(tab).sumOf { it.second.size }

    fun releases(): List<Item> = items.filter { it.kind != LIVE && it.year == year && visible(it) }

    fun search(q: String, limit: Int = 60): List<Item> {
        val n = norm(q.trim())
        if (n.length < 1) return emptyList()
        return items.asSequence().filter { visible(it) && norm(it.title).contains(n) }
            .sortedBy { if (norm(it.title).startsWith(n)) 0 else 1 }.take(limit).toList()
    }

    // ---------- guardar no aparelho, para abrir rápido ----------
    private fun file(c: Context) = File(c.filesDir, "catalogo.json")

    fun loadCache(c: Context) {
        val f = file(c)
        if (!f.exists()) return
        try {
            val list = ArrayList<Item>()
            JsonReader(f.bufferedReader()).use { r ->
                r.beginArray()
                while (r.hasNext()) {
                    r.beginArray()
                    val id = r.nextString(); val title = r.nextString(); val group = r.nextString(); val kind = r.nextInt()
                    val url = r.nextString(); val logo = r.nextString(); val yr = r.nextInt(); val plot = r.nextString()
                    val rating = r.nextString(); val sid = r.nextString(); val src = r.nextString(); val tabs = r.nextString()
                    val adult = r.nextBoolean(); val ext = r.nextString()
                    r.endArray()
                    list.add(Item(id, title, group, kind, url, logo, yr, plot, rating, sid, src, tabs.split(",").filter { it.isNotEmpty() }.toSet(), adult, ext))
                }
                r.endArray()
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post { if (items.isEmpty()) publish(list) }
        } catch (e: Throwable) { f.delete() }
    }

    private fun saveCache(c: Context, list: List<Item>) {
        try {
            val tmp = File(c.filesDir, "catalogo.tmp")
            JsonWriter(tmp.bufferedWriter()).use { w ->
                w.beginArray()
                for (i in list) {
                    w.beginArray()
                    w.value(i.id).value(i.title).value(i.group).value(i.kind.toLong()).value(i.url).value(i.logo).value(i.year.toLong())
                        .value(i.plot).value(i.rating).value(i.seriesId).value(i.src).value(i.tabs.joinToString(",")).value(i.adult).value(i.ext)
                    w.endArray()
                }
                w.endArray()
            }
            tmp.renameTo(file(c))
        } catch (e: Throwable) {}
    }

    /** Baixa de novo a conta em uso e todas as listas, em segundo plano. */
    fun refresh(c: Context, done: ((String) -> Unit)? = null) {
        if (loading) return
        loading = true
        status = "Atualizando sua lista…"
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        thread(name = "catalogo") {
            val all = ArrayList<Item>()
            val notes = ArrayList<String>()
            val acc = Store.accounts.getOrNull(Store.activeAcc)
            if (acc != null && acc.filled) {
                try { val l = Xtream.load(acc); all.addAll(l); notes.add("${acc.name.ifBlank { "Conta" }}: ${l.size} itens") }
                catch (e: Throwable) { notes.add("${acc.name.ifBlank { "Conta" }}: ${Net.why(e)}") }
            }
            for (ls in Store.lists.toList()) {
                try {
                    val l = Lists.load(ls)
                    all.addAll(l); ls.info = "${l.size} itens"; notes.add("${ls.name}: ${l.size} itens")
                } catch (e: Throwable) { ls.info = Net.why(e); notes.add("${ls.name}: ${Net.why(e)}") }
            }
            val msg = if (notes.isEmpty()) "Nenhuma conta ou lista configurada." else notes.joinToString(" · ")
            val ok = all.isNotEmpty() || (acc == null || !acc.filled) && Store.lists.isEmpty()
            if (ok) saveCache(c, all)
            main.post {
                if (ok) publish(all)
                Store.saveLists()
                loading = false
                status = msg
                done?.invoke(msg)
            }
        }
    }

    /** Lista de episódios de uma série (Xtream busca no servidor; listas usam o que já veio). */
    fun loadEpisodes(it: Item, cb: (List<Episode>) -> Unit) {
        episodes[it.id]?.let { e -> cb(e); return }
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        thread {
            val eps = try { if (it.src.startsWith("xt:")) Xtream.episodes(it) else emptyList() } catch (e: Throwable) { emptyList() }
            main.post { if (eps.isNotEmpty()) episodes[it.id] = eps; cb(eps) }
        }
    }
}
