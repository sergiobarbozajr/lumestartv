package com.lumestar.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Logotipo dos canais ao vivo que vêm sem tvg-logo na lista (ex.: "Globo RJ HD").
 * Usa um arquivo pequeno (nome → logo) gerado toda semana pelo repositório Playlist-canais a partir do
 * banco aberto do iptv-org. Baixa uma vez por semana e guarda no aparelho.
 */
object Logos {
    private const val URL = "https://raw.githubusercontent.com/sergiobarbozajr/Playlist-canais/main/logos.json"
    private val map = ConcurrentHashMap<String, String>()
    private val memo = ConcurrentHashMap<String, String>()
    var rev by mutableIntStateOf(0)
        private set
    private lateinit var file: File

    fun init(c: Context) {
        file = File(c.filesDir, "logos.json")
        Thread {
            try { if (file.exists()) carregar(file.readText()) } catch (e: Throwable) {}
            val velho = !file.exists() || System.currentTimeMillis() - file.lastModified() > 7L * 86400000
            if (velho) try {
                val txt = Net.read(Net.open(URL, 20000))
                carregar(txt); file.writeText(txt)
            } catch (e: Throwable) {}
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun carregar(txt: String) {
        val o = JSONObject(txt)
        val tmp = HashMap<String, String>(o.length() * 2)
        o.keys().forEach { k -> tmp[k] = o.getString(k) }
        map.putAll(tmp); memo.clear()
        Handler(Looper.getMainLooper()).post { rev++ }
    }

    private val RX_BR = Regex("\\[[^\\]]*\\]|\\([^)]*\\)")
    private val RX_TAG = Regex("(?i)\\b(hd|fhd|uhd|sd|4k|h265|hevc|ao vivo|alt|backup|²|³|\\d{3,4}p)\\b")
    private val RX_NAO = Regex("[^a-z0-9 ]+")

    private fun norm(t: String): String {
        val a = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
        return RX_NAO.replace(RX_TAG.replace(RX_BR.replace(a, " "), " "), " ").trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Logo para o nome do canal. Tenta o nome inteiro e depois vai tirando palavras do fim
     * ("globo rj" → "globo"), para achar a marca principal quando não existe o canal regional.
     */
    fun find(title: String): String {
        if (map.isEmpty() || title.isBlank()) return ""
        memo[title]?.let { return it }
        val w = norm(title).split(' ').filter { it.isNotBlank() }
        var r = ""
        for (n in w.size downTo 1) {
            val k = w.take(n).joinToString("")
            if (k.length >= 2) map[k]?.let { r = it }
            if (r.isNotBlank()) break
        }
        memo[title] = r
        return r
    }
}
