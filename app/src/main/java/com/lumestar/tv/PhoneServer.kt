package com.lumestar.tv

import android.os.Handler
import android.os.Looper
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * "Enviar pelo celular": enquanto a tela estiver aberta, a TV atende uma página simples
 * na rede de casa. No celular (mesmo Wi-Fi) você cola o link da lista ou os dados da conta.
 * Fecha sozinho ao sair da tela.
 */
object PhoneServer {
    const val PORT = 8765
    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())
    /** Chamado na TV com os campos enviados: tipo ("lista" ou "conta") e os valores. */
    var onReceive: ((Map<String, String>) -> Unit)? = null

    fun address(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    } catch (e: Exception) { null }

    fun start() {
        if (server != null) return
        val s = try {
            ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress(PORT)) }
        } catch (e: Exception) { android.util.Log.w("LumeStar", "enviar pelo celular: porta $PORT indisponível", e); return }
        android.util.Log.i("LumeStar", "enviar pelo celular: ouvindo na porta $PORT")
        server = s
        thread(name = "enviar-pelo-celular") {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: Exception) { break }
                thread { try { handle(c) } catch (e: Exception) {} finally { try { c.close() } catch (e: Exception) {} } }
            }
        }
    }

    fun stop() { try { server?.close() } catch (e: Exception) {}; server = null }

    private fun handle(c: Socket) {
        c.soTimeout = 15000
        val ins = c.getInputStream().buffered()
        val head = StringBuilder()
        while (true) {
            val b = ins.read(); if (b < 0) break
            head.append(b.toChar())
            if (head.endsWith("\r\n\r\n") || head.length > 8192) break
        }
        val first = head.lineSequence().firstOrNull() ?: return
        val len = Regex("(?i)content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        var msg = ""
        if (first.startsWith("POST") && len in 1..20000) {
            val body = ByteArray(len); var got = 0
            while (got < len) { val n = ins.read(body, got, len - got); if (n < 0) break; got += n }
            val form = String(body, 0, got, Charsets.UTF_8).split("&").mapNotNull {
                val kv = it.split("=", limit = 2); if (kv.size == 2) URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv[1], "UTF-8").trim() else null
            }.toMap()
            val ok = (form["tipo"] == "lista" && form["url"].orEmpty().contains("://")) ||
                (form["tipo"] == "conta" && form["servidor"].orEmpty().isNotBlank() && form["usuario"].orEmpty().isNotBlank() && form["senha"].orEmpty().isNotBlank())
            msg = if (ok) { main.post { onReceive?.invoke(form) }; "<p class=ok>Enviado! Confira na TV.</p>" } else "<p class=err>Preencha todos os campos.</p>"
        }
        val html = page(msg).toByteArray(Charsets.UTF_8)
        val out = c.getOutputStream()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(html); out.flush()
    }

    private fun page(msg: String) = """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LumeStarTV · enviar pelo celular</title><style>
body{margin:0;font-family:system-ui,sans-serif;background:#050816;color:#eef2ff;padding:20px}
h1{font-size:22px;margin:0 0 4px}p{color:#9aa6d6;margin:0 0 18px}
form{background:#0e1538;border:1px solid #243069;border-radius:16px;padding:16px;margin-bottom:18px;display:flex;flex-direction:column;gap:10px}
h2{font-size:16px;margin:0}input{font-size:16px;padding:12px;border-radius:10px;border:1px solid #243069;background:#050816;color:#eef2ff}
button{font-size:16px;font-weight:700;padding:12px;border:0;border-radius:10px;background:linear-gradient(90deg,#5aa8ff,#b48cff);color:#050816}
.ok{color:#52d6a8;font-weight:700}.err{color:#ff6f8e;font-weight:700}</style></head><body>
<h1>LumeStarTV</h1><p>Envie para a TV sem digitar no controle.</p>$msg
<form method="post"><h2>Lista (M3U, M3U8, JSON ou TXT)</h2><input type="hidden" name="tipo" value="lista">
<input name="nome" placeholder="Nome (ex.: Minha lista)"><input name="url" type="url" placeholder="Endereço da lista (http://…)" required>
<button>Enviar lista para a TV</button></form>
<form method="post"><h2>Conta IPTV (Xtream)</h2><input type="hidden" name="tipo" value="conta">
<input name="nome" placeholder="Nome da conta"><input name="servidor" placeholder="URL do servidor (ex.: http://servidor.com:8080)" required>
<input name="usuario" placeholder="Usuário" required autocapitalize="off"><input name="senha" type="password" placeholder="Senha" required>
<button>Enviar conta para a TV</button></form></body></html>"""
}
