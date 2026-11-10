package com.lumestar.tv

import android.content.Context
import android.os.Build
import android.util.Base64
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Conversor nativo: quando a TV não tem decodificador para o formato (MPEG-2, VC-1, DivX, áudio DTS/AC3…),
 * o FFmpeg embutido (compilado por nós, LGPL, 32 e 64 bits) converte para algo que toda TV toca:
 * vídeo H.264 pelo codificador da própria TV (MediaCodec) — ou MPEG-4 se ele falhar — em até 720p,
 * áudio AAC estéreo, em pedaços HLS numa pasta temporária. Começa a tocar quando já há uns 24 s prontos.
 *
 * A entrada passa por um "proxy" local (127.0.0.1) que busca o vídeo com o User-Agent/Referer do canal
 * e fala HTTPS por ele (o FFmpeg embutido não tem TLS).
 */
object Conversor {
    /** Só em Android 7+ (o que a biblioteca exige), fora do modo leve e se a biblioteca carregou. */
    fun disponivel(): Boolean = Build.VERSION.SDK_INT >= 24 && !Perf.lite && carregou
    private val carregou: Boolean by lazy {
        try { FFmpegKitConfig.getFFmpegVersion(); true } catch (e: Throwable) { android.util.Log.w("LumeStar", "conversor: FFmpeg não carregou", e); false }
    }

    private var sessao: FFmpegSession? = null
    @Volatile private var cancelado = false

    /**
     * Converte [url] (pode ter |User-Agent=…). [soAudio]: o vídeo já toca, só o áudio precisa ser convertido
     * (bem mais rápido: o vídeo é copiado). Chama [pronto] com o endereço local para tocar.
     */
    fun iniciar(ctx: Context, url: String, live: Boolean, soAudio: Boolean, progresso: (Int) -> Unit, pronto: (String) -> Unit, erro: (String) -> Unit) {
        parar()
        cancelado = false
        val dir = File(ctx.cacheDir, "conversao").apply { deleteRecursively(); mkdirs() }
        val saida = File(dir, "saida.m3u8")
        val entrada = ProxyLocal.endereco(url)
        val codificadores = if (soAudio) listOf("copy") else listOf("h264_mediacodec", "mpeg4")
        Thread {
            var avisou = false
            for ((n, cod) in codificadores.withIndex()) {
                if (cancelado) return@Thread
                dir.listFiles()?.forEach { it.delete() }
                val args = argumentos(entrada, cod, live, dir, saida)
                android.util.Log.i("LumeStar", "conversor: começando com $cod")
                var segundos = 0
                val s = FFmpegKit.executeWithArgumentsAsync(args.toTypedArray(), { }, { }, { st -> segundos = (st.time / 1000).toInt(); progresso(segundos) })
                sessao = s
                // espera ficar pronto (ou acabar/falhar)
                val minimo = if (live) 2 else 6
                while (!cancelado) {
                    Thread.sleep(700)
                    val partes = if (saida.exists()) saida.readText().split("#EXTINF").size - 1 else 0
                    val fim = s.state == com.arthenica.ffmpegkit.SessionState.COMPLETED || s.state == com.arthenica.ffmpegkit.SessionState.FAILED
                    if (!avisou && (partes >= minimo || (fim && ReturnCode.isSuccess(s.returnCode) && partes > 0))) {
                        avisou = true; pronto(android.net.Uri.fromFile(saida).toString())
                    }
                    if (fim) break
                }
                if (cancelado) return@Thread
                if (ReturnCode.isSuccess(s.returnCode)) return@Thread
                if (avisou) return@Thread // já estava tocando; o fim antes da hora vira erro do próprio player
                val log = s.allLogsAsString.orEmpty().lines().filter { it.isNotBlank() }.takeLast(4).joinToString(" | ")
                android.util.Log.w("LumeStar", "conversor: $cod falhou: $log")
                if (n == codificadores.lastIndex) { erro("A conversão falhou (${log.take(160)})"); return@Thread }
            }
        }.apply { name = "conversor"; isDaemon = true }.start()
    }

    private fun argumentos(entrada: String, cod: String, live: Boolean, dir: File, saida: File): List<String> {
        val a = mutableListOf("-hide_banner", "-y", "-fflags", "+genpts+discardcorrupt", "-i", entrada,
            "-map", "0:v:0?", "-map", "0:a:0?", "-sn", "-dn")
        when (cod) {
            "copy" -> a += listOf("-c:v", "copy")
            "h264_mediacodec" -> a += listOf("-vf", "scale='min(1280,iw)':-2,format=nv12", "-c:v", "h264_mediacodec", "-b:v", "3500k", "-g", "60")
            else -> a += listOf("-vf", "scale='min(1280,iw)':-2", "-c:v", "mpeg4", "-q:v", "4", "-g", "60", "-threads", "4")
        }
        a += listOf("-c:a", "aac", "-b:a", "160k", "-ac", "2", "-ar", "48000",
            "-f", "hls", "-hls_time", "4", "-hls_segment_filename", File(dir, "p%05d.ts").absolutePath)
        a += if (live) listOf("-hls_list_size", "10", "-hls_flags", "delete_segments") else listOf("-hls_list_size", "0", "-hls_playlist_type", "event")
        a += saida.absolutePath
        return a
    }

    fun parar() {
        cancelado = true
        sessao?.let { try { FFmpegKit.cancel(it.sessionId) } catch (e: Throwable) {} }
        sessao = null
    }
}

/**
 * Mini servidor em 127.0.0.1 só para o FFmpeg: GET /p/<endereço em base64> busca o original com os cabeçalhos do canal
 * (repassa Range para o FFmpeg poder pular no arquivo). Listas HLS têm os endereços reescritos para passar por aqui também.
 */
object ProxyLocal {
    private var porta = 0
    private val http by lazy {
        OkHttpClient.Builder().proxy(PP.proxy()).followRedirects(true).followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }

    @Synchronized private fun ligar(): Int {
        if (porta != 0) return porta
        val ss = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        porta = ss.localPort
        Thread {
            while (true) { val c = try { ss.accept() } catch (e: Throwable) { break }; Thread { atender(c) }.apply { isDaemon = true }.start() }
        }.apply { name = "proxy-local"; isDaemon = true }.start()
        return porta
    }

    private fun b64(s: String) = Base64.encodeToString(s.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** Endereço local que o FFmpeg usa no lugar do original (os cabeçalhos vão junto, no padrão Kodi). */
    fun endereco(url: String): String {
        val e = Cabecalhos.separa(url)
        val nome = e.url.substringBefore('?').substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "").take(40).ifBlank { "video" }
        return "http://127.0.0.1:${ligar()}/p/${b64(url)}/$nome"
    }

    private fun atender(c: Socket) = c.use { s ->
        try {
            val ins = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val linha = ins.readLine() ?: return
            var range: String? = null
            while (true) { val h = ins.readLine() ?: break; if (h.isEmpty()) break; if (h.startsWith("Range:", true)) range = h.substringAfter(':').trim() }
            val cod = linha.split(" ").getOrNull(1)?.removePrefix("/p/")?.substringBefore('/') ?: return
            val original = String(Base64.decode(cod, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
            val e = Cabecalhos.separa(original)
            val rb = Request.Builder().url(e.url).header("User-Agent", e.userAgent ?: "VLC/3.0.20 LibVLC/3.0.20")
            e.outros.forEach { (k, v) -> rb.header(k, v) }
            if (range != null) rb.header("Range", range)
            http.newCall(rb.build()).execute().use { r ->
                val out = s.getOutputStream()
                val tipo = r.header("Content-Type").orEmpty()
                val eLista = tipo.contains("mpegurl", true) || e.url.substringBefore('?').endsWith(".m3u8", true)
                if (eLista && r.isSuccessful) {
                    // reescreve cada endereço da lista para passar pelo proxy, com os mesmos cabeçalhos
                    val base = r.request.url
                    val corpo = r.body?.string().orEmpty().lines().joinToString("\n") { l ->
                        val t = l.trim()
                        when {
                            t.isEmpty() -> l
                            t.startsWith("#") -> Regex("URI=\"([^\"]+)\"").replace(l) { m -> "URI=\"" + endereco(Cabecalhos.junta(base.resolve(m.groupValues[1])?.toString() ?: m.groupValues[1], e.cab)) + "\"" }
                            else -> endereco(Cabecalhos.junta(base.resolve(t)?.toString() ?: t, e.cab))
                        }
                    }.toByteArray()
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/vnd.apple.mpegurl\r\nContent-Length: ${corpo.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(corpo); out.flush(); return
                }
                val cab = StringBuilder("HTTP/1.1 ${r.code} ${r.message.ifBlank { "OK" }}\r\n")
                listOf("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges").forEach { h -> r.header(h)?.let { cab.append("$h: $it\r\n") } }
                cab.append("Connection: close\r\n\r\n")
                out.write(cab.toString().toByteArray())
                r.body?.byteStream()?.use { it.copyTo(out, 64 * 1024) }
                out.flush()
            }
        } catch (e: Throwable) { android.util.Log.w("LumeStar", "proxy local: ${e.message}") }
    }
}
