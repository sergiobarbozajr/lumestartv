package com.lumestar.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

/**
 * "Novidades da semana": catálogo remoto. O repositório Playlist-canais gera novidades.json toda semana
 * (o que entrou nas listas nativas nos últimos 30 dias + destaques escolhidos à mão em destaques.txt).
 * Muda sem lançar versão nova do app. Sem internet, usa a última cópia salva.
 */
object Novidades {
    private const val URL = "https://raw.githubusercontent.com/sergiobarbozajr/Playlist-canais/main/novidades.json"
    /** endereços e títulos (minúsculos) na ordem do arquivo: destaques primeiro, depois os mais novos */
    var chaves by mutableStateOf<List<String>>(emptyList()); private set

    fun init(c: Context) {
        val cache = File(c.filesDir, "novidades.json")
        Thread {
            try { if (cache.exists()) ler(cache.readText()) } catch (e: Throwable) {}
            try { val txt = Net.read(Net.open(URL, 15000)); ler(txt); cache.writeText(txt) } catch (e: Throwable) {}
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun ler(txt: String) {
        val a = JSONObject(txt).optJSONArray("titulos") ?: return
        chaves = (0 until a.length()).mapNotNull { i -> a.optJSONObject(i) }.flatMap { o ->
            listOfNotNull(o.optString("u").takeIf { it.isNotBlank() }, o.optString("t").lowercase().trim().takeIf { it.isNotBlank() }?.let { "t:$it" })
        }
    }

    /** Itens das listas carregadas que estão nas novidades, na ordem do arquivo (máx. 20). */
    fun itens(todos: List<Item>): List<Item> {
        val ch = chaves; if (ch.isEmpty() || todos.isEmpty()) return emptyList()
        val ordem = ch.withIndex().associate { it.value to it.index }
        return todos.mapNotNull { i -> (ordem[i.url] ?: ordem["t:" + i.title.lowercase().trim()])?.let { it to i } }
            .distinctBy { it.second.title.lowercase() }.sortedBy { it.first }.map { it.second }.take(20)
    }
}
