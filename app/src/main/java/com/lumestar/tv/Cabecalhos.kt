package com.lumestar.tv

import java.net.URLDecoder

/**
 * Cabeçalhos HTTP próprios de um canal (User-Agent, Referer…), guardados no padrão Kodi dentro do
 * próprio endereço: "https://x/canal.m3u8|User-Agent=...&Referer=...". Assim o formato do cache,
 * do Item e do PlayReq não muda. Todo código que olha o endereço usa só a parte antes do "|".
 */
object Cabecalhos {
    class Endereco(val url: String, val cab: Map<String, String>) {
        val userAgent get() = cab.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
        /** os outros cabeçalhos (Referer, Origin, Cookie…), sem o User-Agent */
        val outros get() = cab.filterKeys { !it.equals("User-Agent", true) }
    }

    /** Separa "url|Chave=valor&..." em endereço + cabeçalhos. Sem "|", devolve o endereço como está. */
    fun separa(u: String): Endereco {
        val i = u.indexOf('|')
        if (i < 0) return Endereco(u.trim(), emptyMap())
        val base = u.substring(0, i).trim()
        val m = LinkedHashMap<String, String>()
        for (par in u.substring(i + 1).split('&')) {
            val k = par.substringBefore('=', "").trim(); val v = par.substringAfter('=', "").trim()
            if (k.isEmpty() || v.isEmpty()) continue
            val valor = try { if (v.contains('%')) URLDecoder.decode(v.replace("+", "%2B"), "UTF-8") else v } catch (e: Throwable) { v }
            m[nomePadrao(k)] = valor
        }
        return Endereco(base, m)
    }

    /** Só o endereço (sem os cabeçalhos). */
    fun base(u: String) = u.substringBefore('|').trim()

    /** Junta endereço + cabeçalhos no padrão Kodi (valores codificados para não quebrar no "&"). */
    fun junta(url: String, cab: Map<String, String>): String {
        val limpos = cab.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        if (limpos.isEmpty()) return url
        val (b, antigos) = separa(url).let { it.url to it.cab }
        val todos = LinkedHashMap(antigos); limpos.forEach { (k, v) -> todos[nomePadrao(k)] = v.trim() }
        return b + "|" + todos.entries.joinToString("&") { "${it.key}=" + android.net.Uri.encode(it.value) }
    }

    /** User-Agent / Referer com a grafia de sempre, aceitando referrer, http-referrer, user_agent… */
    fun nomePadrao(k: String): String = when (k.trim().lowercase().removePrefix("http-").replace('_', '-')) {
        "user-agent", "useragent" -> "User-Agent"
        "referer", "referrer" -> "Referer"
        "origin" -> "Origin"
        "cookie" -> "Cookie"
        else -> k.trim()
    }
}
