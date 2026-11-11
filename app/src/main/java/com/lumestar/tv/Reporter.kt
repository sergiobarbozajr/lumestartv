package com.lumestar.tv

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Relatório automático de erros: quando o app fecha sozinho (ou algo importante falha, como o vídeo direto),
 * manda um resumo para o serviço de erros (Cloudflare), que abre um registro no GitHub do projeto.
 * Nunca envia endereço de lista, usuário, senha, nome de canal ou o que foi assistido: o texto passa por uma
 * limpeza antes. Pode ser desligado em Configurações › Sobre o aparelho.
 */
object Reporter {
    private const val CONFIG = "https://raw.githubusercontent.com/sergiobarbozajr/lumestartv/main/config/servicos.json"
    @Volatile private var url = ""
    private var enviadosHoje = 0
    private var dia = 0L

    fun init(c: Context) {
        val cache = File(c.filesDir, "servicos.json")
        Thread {
            try { if (cache.exists()) url = JSONObject(cache.readText()).optString("erros") } catch (e: Throwable) {}
            try {
                val txt = Net.read(Net.open(CONFIG, 15000))
                url = JSONObject(txt).optString("erros"); cache.writeText(txt)
            } catch (e: Throwable) {}
            enviarFechamento(c)
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** O fechamento da última vez (gravado pelo CrashLog), uma vez só. */
    private fun enviarFechamento(c: Context) {
        val f = File(c.filesDir, "ultimo-erro.txt")
        if (!f.exists()) return
        val sp = c.getSharedPreferences("lumestar", Context.MODE_PRIVATE)
        if (sp.getLong("erroEnviado", 0L) == f.lastModified()) return
        if (enviar(c, "fechamento", f.readText())) sp.edit().putLong("erroEnviado", f.lastModified()).apply()
    }

    /** Falha importante sem fechar o app (ex.: vídeo direto voltou para o clássico, resultado do teste). */
    fun evento(c: Context, tipo: String, texto: String, e: Throwable?) {
        val t = texto + if (e != null) "\n" + android.util.Log.getStackTraceString(e) else ""
        Thread { enviar(c, tipo, t) }.start()
    }

    private val RX_URL = Regex("(?i)\\b(?:https?|rtmp|rtsp|udp|rtp)://\\S+")
    private val RX_CRED = Regex("(?i)(user(name)?|pass(word)?|senha|usuario|token|key|chave)=[^&\\s]+")
    private val RX_IP = Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}(:\\d+)?\\b")
    private val RX_MAIL = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+")

    /** Tira do texto tudo que poderia identificar a pessoa ou a lista dela. */
    fun limpar(t: String): String = RX_MAIL.replace(RX_IP.replace(RX_CRED.replace(RX_URL.replace(t, "<endereço>"), "$1=<oculto>"), "<ip>"), "<email>")

    /** Assinatura curta do erro (tipo + primeiras linhas do app) para juntar os repetidos num registro só. */
    private fun assinatura(tipo: String, t: String): String {
        val linhas = t.lines().map { it.trim() }
        val exc = linhas.firstOrNull { it.contains("Exception") || it.contains("Error") }?.substringBefore(":")?.substringAfterLast(' ') ?: ""
        val nossas = linhas.filter { it.startsWith("at com.lumestar") }.take(3).joinToString("|")
        val md = MessageDigest.getInstance("SHA-1").digest("$tipo|$exc|$nossas".toByteArray())
        return md.take(5).joinToString("") { "%02x".format(it) }
    }

    @Synchronized
    private fun enviar(c: Context, tipo: String, bruto: String): Boolean {
        if (!PP.enviarErros || url.isBlank()) return false
        val hoje = System.currentTimeMillis() / 86_400_000L
        if (hoje != dia) { dia = hoje; enviadosHoje = 0 }
        if (enviadosHoje >= 10) return false
        val texto = limpar(bruto).take(6000)
        val resumo = texto.lines().firstOrNull { it.contains("Exception") || it.contains("Error") }?.trim()?.take(120)
            ?: texto.lineSequence().firstOrNull { it.isNotBlank() }?.take(120) ?: tipo
        val j = JSONObject()
            .put("app", "LumeStarTV").put("versao", BuildConfig.VERSION_NAME)
            .put("aparelho", "${Build.MANUFACTURER} ${Build.MODEL}").put("modelo", try { Device.modelo(c) } catch (e: Throwable) { "" })
            .put("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})").put("chip", Build.HARDWARE)
            .put("desempenho", Perf.reason).put("tipo", tipo).put("assinatura", assinatura(tipo, texto))
            .put("resumo", resumo).put("texto", texto)
            .put("ia", "${PP.aiUp} · saída ${PP.videoMode}${if (PP.diretoFalhou) " (direto falhou antes)" else ""} · som ${PP.audioMode}${if (PP.audioPcm) " (som direto falhou antes)" else ""}")
        return try {
            val con = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            con.requestMethod = "POST"; con.doOutput = true; con.connectTimeout = 15000; con.readTimeout = 15000
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            con.setRequestProperty("X-Lume", "lumestartv-1")
            con.outputStream.use { it.write(j.toString().toByteArray()) }
            val ok = con.responseCode in 200..299
            con.disconnect()
            if (ok) enviadosHoje++
            ok
        } catch (e: Throwable) { false }
    }
}
