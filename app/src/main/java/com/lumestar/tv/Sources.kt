package com.lumestar.tv

import android.util.JsonReader
import android.util.JsonToken
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Acesso à internet com tempo limite, redirecionamentos e mensagens de erro em português. */
object Net {
    const val UA = "LumeStarTV/0.2 (Android TV)"

    fun open(url: String, timeoutRead: Int = 60000, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        var u = url
        repeat(6) {
            val c = (URL(u).openConnection(PP.proxy()) as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = timeoutRead
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept-Encoding", "gzip")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw java.io.IOException("redirecionamento sem destino")
                u = URL(URL(u), loc).toString(); c.disconnect(); return@repeat
            }
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return c
        }
        throw java.io.IOException("redirecionamentos demais")
    }

    fun stream(c: HttpURLConnection): InputStream {
        val s = c.inputStream
        return if (c.contentEncoding.equals("gzip", true)) java.util.zip.GZIPInputStream(s) else s
    }

    fun text(url: String, max: Int = 40_000_000): String = read(open(url), max)

    fun read(c: HttpURLConnection, max: Int = 40_000_000): String {
        stream(c).use { ins ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(65536)
            while (true) { val n = ins.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > max) throw java.io.IOException("lista grande demais") }
            return out.toString("UTF-8")
        }
    }

    fun why(e: Throwable): String = when {
        e is java.net.UnknownHostException -> "servidor não encontrado (confira o endereço e a internet)"
        e is java.net.SocketTimeoutException -> "o servidor demorou demais para responder"
        e is java.net.ConnectException -> "o servidor recusou a conexão"
        e.message?.startsWith("HTTP 401") == true || e.message?.startsWith("HTTP 403") == true -> "acesso negado (usuário ou senha?)"
        e.message?.startsWith("HTTP 404") == true -> "endereço não encontrado (404)"
        e.message?.startsWith("HTTP") == true -> "o servidor respondeu ${e.message}"
        e is SecurityException -> "sem permissão de internet"
        else -> e.message ?: "erro desconhecido"
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/** Contas no padrão Xtream Codes (URL do servidor + usuário + senha). */
object Xtream {
    fun base(url: String): String {
        var u = url.trim().trimEnd('/')
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "http://$u"
        return u.replace(Regex("/(player_api\\.php|get\\.php).*$"), "")
    }

    private fun api(a: Account, action: String, extra: String = "") =
        "${base(a.url)}/player_api.php?username=${Net.enc(a.user)}&password=${Net.enc(a.pass)}" + (if (action.isNotEmpty()) "&action=$action" else "") + extra

    /** Testa a conta e devolve um resumo (situação, validade, telas). */
    fun test(a: Account): String {
        val o = org.json.JSONObject(Net.text(api(a, "")))
        val ui = o.optJSONObject("user_info") ?: throw java.io.IOException("resposta sem dados da conta (não parece Xtream)")
        if (ui.optInt("auth", 0) != 1) throw java.io.IOException("usuário ou senha não aceitos")
        val st = ui.optString("status")
        val exp = ui.optString("exp_date").toLongOrNull()
        val vence = if (exp != null && exp > 0) java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(java.util.Date(exp * 1000)) else "sem data"
        return "Conectado · situação ${if (st.equals("Active", true)) "ativa" else st} · vence em $vence · ${ui.optString("max_connections", "?")} tela(s)"
    }

    private fun categories(a: Account, action: String): Map<String, String> {
        val m = HashMap<String, String>()
        val c = Net.open(api(a, action))
        JsonReader(InputStreamReader(Net.stream(c), "UTF-8")).use { r ->
            if (r.peek() != JsonToken.BEGIN_ARRAY) return m
            r.beginArray()
            while (r.hasNext()) { val o = obj(r); m[o["category_id"] ?: ""] = o["category_name"] ?: "" }
            r.endArray()
        }
        return m
    }

    /** Lê um objeto JSON simples como texto (ignora listas e objetos internos). */
    private fun obj(r: JsonReader): Map<String, String> {
        val m = HashMap<String, String>()
        r.beginObject()
        while (r.hasNext()) {
            val k = r.nextName()
            when (r.peek()) {
                JsonToken.STRING -> m[k] = r.nextString()
                JsonToken.NUMBER -> m[k] = r.nextString()
                JsonToken.BOOLEAN -> m[k] = r.nextBoolean().toString()
                JsonToken.NULL -> r.nextNull()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return m
    }

    private fun each(a: Account, action: String, f: (Map<String, String>) -> Unit) {
        val c = Net.open(api(a, action), 180000)
        JsonReader(InputStreamReader(Net.stream(c), "UTF-8")).use { r ->
            if (r.peek() != JsonToken.BEGIN_ARRAY) { r.skipValue(); return }
            r.beginArray()
            while (r.hasNext()) { if (r.peek() == JsonToken.BEGIN_OBJECT) f(obj(r)) else r.skipValue() }
            r.endArray()
        }
    }

    fun load(a: Account): List<Item> {
        val b = base(a.url); val u = Net.enc(a.user); val p = Net.enc(a.pass)
        val src = "xt:" + norm(b + "|" + a.user)
        val out = ArrayList<Item>()
        val liveCat = categories(a, "get_live_categories")
        each(a, "get_live_streams") { o ->
            val id = o["stream_id"] ?: return@each
            val name = o["name"] ?: ""; val g = liveCat[o["category_id"]] ?: ""
            out.add(Item("$src:l$id", name, g, LIVE, "$b/live/$u/$p/$id.m3u8", o["stream_icon"] ?: "", 0, "", "", "", src,
                Classifier.tabs(LIVE, g, name), Classifier.isAdult(g, name), "ts"))
        }
        val vodCat = categories(a, "get_vod_categories")
        each(a, "get_vod_streams") { o ->
            val id = o["stream_id"] ?: return@each
            val name = o["name"] ?: ""; val g = vodCat[o["category_id"]] ?: ""
            val ext = o["container_extension"]?.ifBlank { null } ?: "mp4"
            out.add(Item("$src:m$id", name, g, MOVIE, "$b/movie/$u/$p/$id.$ext", o["stream_icon"] ?: "", Classifier.yearOf(name, o["year"] ?: o["releasedate"] ?: ""),
                o["plot"] ?: "", o["rating"] ?: "", "", src, Classifier.tabs(MOVIE, g, name), Classifier.isAdult(g, name)))
        }
        val serCat = categories(a, "get_series_categories")
        each(a, "get_series") { o ->
            val id = o["series_id"] ?: return@each
            val name = o["name"] ?: ""; val g = serCat[o["category_id"]] ?: ""
            out.add(Item("$src:s$id", name, g, SERIES, "", o["cover"] ?: "", Classifier.yearOf(name, o["releaseDate"] ?: o["release_date"] ?: ""),
                o["plot"] ?: "", o["rating"] ?: "", id, src, Classifier.tabs(SERIES, g, name), Classifier.isAdult(g, name)))
        }
        return out
    }

    fun episodes(s: Item): List<Episode> {
        val acc = Store.accounts.getOrNull(Store.activeAcc) ?: return emptyList()
        val b = base(acc.url); val u = Net.enc(acc.user); val p = Net.enc(acc.pass)
        val o = org.json.JSONObject(Net.text(api(acc, "get_series_info", "&series_id=${s.seriesId}")))
        val eps = o.optJSONObject("episodes") ?: return emptyList()
        val out = ArrayList<Episode>()
        eps.keys().forEach { season ->
            val arr = eps.optJSONArray(season) ?: return@forEach
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val id = e.optString("id"); val ext = e.optString("container_extension", "mp4").ifBlank { "mp4" }
                out.add(Episode("${s.id}:e$id", season.toIntOrNull() ?: 0, e.optInt("episode_num", i + 1), e.optString("title"),
                    "$b/series/$u/$p/$id.$ext", e.optJSONObject("info")?.optString("plot") ?: ""))
            }
        }
        return out.sortedWith(compareBy({ it.season }, { it.num }))
    }
}

/** Listas por endereço: M3U, M3U8 (lista de canais), JSON e TXT. */
object Lists {
    private val VIDEO_RX = Regex("\\.(mp4|mkv|avi|mov)(\\?|$)")
    private val ATTR_RX = HashMap<String, Regex>()
    private val epRx = Regex("^(.*?)[\\s._-]*[Ss](\\d{1,2})\\s*[Ee](\\d{1,3})")

    fun load(ls: ListSrc): List<Item> {
        val url = ls.url.trim()
        val body = Net.text(url)
        val head = body.trimStart().take(400)
        val src = "ls:" + norm(url).hashCode().toUInt().toString(16)
        val raw: List<Triple<String, String, Pair<String, String>>> = when {
            head.startsWith("#EXTM3U") || head.contains("#EXTINF") -> m3u(body)
            head.startsWith("#EXT-X-") -> listOf(Triple(ls.name.ifBlank { "Canal" }, "", url to ""))
            head.startsWith("[") || head.startsWith("{") -> json(body)
            else -> txt(body)
        }
        if (raw.isEmpty()) throw java.io.IOException("a lista está vazia ou num formato não reconhecido")
        val out = ArrayList<Item>()
        val series = LinkedHashMap<String, ArrayList<Episode>>()
        val seriesMeta = HashMap<String, Pair<String, String>>()
        raw.forEachIndexed { i, (name, group, ul) ->
            val (u, logo) = ul
            val low = u.lowercase()
            val m = epRx.find(name)
            if (m != null && (low.contains("/series/") || !low.contains("/live/"))) {
                val sname = m.groupValues[1].trim().ifBlank { name }
                val key = "$src:s" + norm(sname).hashCode().toUInt().toString(16)
                series.getOrPut(key) { ArrayList() }.add(Episode("$key:e$i", m.groupValues[2].toInt(), m.groupValues[3].toInt(), name, u, ""))
                seriesMeta.putIfAbsent(key, sname to group)
                if (logo.isNotEmpty()) seriesMeta[key + "#logo"] = logo to ""
                return@forEachIndexed
            }
            val kind = when {
                low.contains("/movie/") || VIDEO_RX.containsMatchIn(low) -> MOVIE
                else -> LIVE
            }
            out.add(Item("$src:$i:" + norm(name).hashCode().toUInt().toString(16), name, group, kind, u, logo, Classifier.yearOf(name), "", "", "", src,
                Classifier.tabs(kind, group, name), Classifier.isAdult(group, name)))
        }
        for ((key, eps) in series) {
            val (sname, group) = seriesMeta[key] ?: continue
            Catalog.episodes[key] = eps.sortedWith(compareBy({ it.season }, { it.num }))
            out.add(Item(key, sname, group, SERIES, "", seriesMeta["$key#logo"]?.first ?: "", Classifier.yearOf(sname), "", "", key, src,
                Classifier.tabs(SERIES, group, sname), Classifier.isAdult(group, sname)))
        }
        return out
    }

    private fun attr(l: String, k: String) = ATTR_RX.getOrPut(k) { Regex("$k=\"([^\"]*)\"") }.find(l)?.groupValues?.get(1) ?: ""

    fun m3u(body: String): List<Triple<String, String, Pair<String, String>>> {
        val out = ArrayList<Triple<String, String, Pair<String, String>>>()
        var name: String? = null; var group = ""; var logo = ""
        for (line0 in body.lineSequence()) {
            val l = line0.trim()
            if (l.startsWith("#EXTINF", true)) {
                name = l.substringAfterLast(",").trim().ifBlank { attr(l, "tvg-name") }
                group = attr(l, "group-title"); logo = attr(l, "tvg-logo")
            } else if (l.startsWith("#EXTGRP:", true)) {
                group = l.substringAfter(":").trim()
            } else if (l.isNotEmpty() && !l.startsWith("#") && name != null) {
                out.add(Triple(name, group, l to logo)); name = null
            }
        }
        return out
    }

    private fun json(body: String): List<Triple<String, String, Pair<String, String>>> {
        val out = ArrayList<Triple<String, String, Pair<String, String>>>()
        val t = body.trim()
        val arr = if (t.startsWith("[")) org.json.JSONArray(t) else org.json.JSONObject(t).let { it.optJSONArray("items") ?: it.optJSONArray("channels") ?: it.optJSONArray("canais") ?: org.json.JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val n = o.optString("name").ifBlank { o.optString("title").ifBlank { o.optString("nome") } }
            val u = o.optString("url").ifBlank { o.optString("stream_url").ifBlank { o.optString("link") } }
            if (n.isBlank() || u.isBlank()) continue
            out.add(Triple(n, o.optString("group").ifBlank { o.optString("category").ifBlank { o.optString("group_title").ifBlank { o.optString("categoria") } } },
                u to o.optString("logo").ifBlank { o.optString("tvg_logo").ifBlank { o.optString("icon") } }))
        }
        return out
    }

    private fun txt(body: String): List<Triple<String, String, Pair<String, String>>> =
        body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { l ->
            val p = l.split('|', ';').map { it.trim() }
            when {
                p.size >= 3 && p[2].contains("://") -> Triple(p[1], p[0], p[2] to (p.getOrNull(3) ?: ""))
                p.size == 2 && p[1].contains("://") -> Triple(p[0], "", p[1] to "")
                else -> {
                    val c = l.split(',', limit = 2)
                    if (c.size == 2 && c[1].trim().contains("://")) Triple(c[0].trim(), "", c[1].trim() to "")
                    else if (l.contains("://")) Triple(l.substringAfterLast('/').ifBlank { "Canal" }, "", l to "") else null
                }
            }
        }.toList()
}

/** Notícias com leitura resumida (feeds públicos de notícias). */
class News(val cat: String, val title: String, val summary: String, val link: String, val img: String)

object NewsFeed {
    val feeds = listOf(
        "Mercado" to "https://g1.globo.com/rss/g1/economia/",
        "Futebol" to "https://ge.globo.com/rss/ge/futebol/",
        "Tecnologia" to "https://g1.globo.com/rss/g1/tecnologia/",
        "Investimentos" to "https://www.infomoney.com.br/feed/",
        "Nacional" to "https://g1.globo.com/rss/g1/brasil/",
        "Internacional" to "https://g1.globo.com/rss/g1/mundo/",
    )

    fun load(): List<News> {
        val out = ArrayList<News>()
        for ((cat, url) in feeds) {
            try { out.addAll(parse(cat, Net.text(url, 3_000_000)).take(6)) } catch (e: Throwable) {}
        }
        // intercala as categorias para o Início não ficar só com uma
        return out.groupBy { it.cat }.values.let { g -> (0 until 6).flatMap { i -> g.mapNotNull { it.getOrNull(i) } } }
    }

    private fun clean(html: String): String =
        android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("[\\uFFFC\\s]+"), " ").trim()

    fun summary(text: String): String {
        val sents = Regex("(?<=[.!?])\\s+").split(text).filter { it.length > 25 }
        var s = ""
        for (x in sents) { if ((s + " " + x).length > 300) break; s = (s + " " + x).trim() }
        return s.ifBlank { text.take(300) }
    }

    private fun parse(cat: String, xml: String): List<News> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(java.io.StringReader(xml))
        val out = ArrayList<News>()
        var inItem = false; var title = ""; var desc = ""; var link = ""; var img = ""; var tag = ""
        while (p.eventType != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "item") { inItem = true; title = ""; desc = ""; link = ""; img = "" }
                    if (inItem && (tag == "media:content" || tag == "enclosure")) img = p.getAttributeValue(null, "url") ?: img
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (inItem) when (tag) {
                    "title" -> title += p.text
                    "description" -> desc += p.text
                    "link" -> link += p.text.trim()
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item" && inItem) {
                        inItem = false
                        val d = clean(desc)
                        if (img.isEmpty()) img = Regex("<img[^>]+src=\"([^\"]+)\"").find(desc)?.groupValues?.get(1) ?: ""
                        if (title.isNotBlank()) out.add(News(cat, clean(title), summary(d), link, img))
                    }
                    tag = ""
                }
            }
            p.next()
        }
        return out
    }
}
