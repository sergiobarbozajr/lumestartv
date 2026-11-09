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

    private var users = 0

    /** Cada tela que mostra o endereço liga o servidor; ele só desliga quando a última sai. */
    fun start() {
        users++
        if (server != null) return
        val s = try {
            ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), PORT)) }
        } catch (e: Exception) { android.util.Log.w("LumeStar", "enviar pelo celular: porta $PORT indisponível", e); return }
        android.util.Log.i("LumeStar", "enviar pelo celular: ouvindo na porta $PORT")
        server = s
        thread(name = "enviar-pelo-celular") {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: Exception) { android.util.Log.i("LumeStar", "enviar pelo celular: parou de ouvir (${e.message})"); break }
                thread { try { handle(c) } catch (e: Exception) {} finally { try { c.close() } catch (e: Exception) {} } }
            }
        }
    }

    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        android.util.Log.i("LumeStar", "enviar pelo celular: desligado")
        try { server?.close() } catch (e: Exception) {}; server = null
    }

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
        val caminho = first.split(" ").getOrNull(1)?.substringBefore("?") ?: "/"
        if (caminho.startsWith("/controle") || caminho == "/parear" || caminho == "/tecla" || caminho == "/texto") {
            controle(c, first, caminho, head.toString(), ins, len); return
        }
        var msg = ""
        val boundary = Regex("(?i)content-type:\\s*multipart/form-data;\\s*boundary=([^\\r\\n;]+)").find(head)?.groupValues?.get(1)?.trim('"')
        if (first.startsWith("POST") && onReceive == null) {
            // a página fica ligada por causa do controle, mas a tela de receber listas não está aberta
            msg = "<p class=err>Na TV, abra Configurações › Contas IPTV (ou Listas) para receber e envie de novo.</p>"
        } else if (first.startsWith("POST") && boundary != null && len in 1..20_000_000) {
            // imagem do tema (envio de arquivo)
            val body = ByteArray(len); var got = 0
            while (got < len) { val n = ins.read(body, got, len - got); if (n < 0) break; got += n }
            val data = filePart(body, got, boundary)
            msg = if (data != null && CustomTheme.save(data)) { main.post { onReceive?.invoke(mapOf("tipo" to "tema")) }; "<p class=ok>Imagem recebida! O tema Anime já está na TV.</p>" }
                  else "<p class=err>Não consegui ler a imagem. Use uma foto JPG ou PNG.</p>"
        } else if (first.startsWith("POST") && len in 1..20000) {
            val body = ByteArray(len); var got = 0
            while (got < len) { val n = ins.read(body, got, len - got); if (n < 0) break; got += n }
            val form = String(body, 0, got, Charsets.UTF_8).split("&").mapNotNull {
                val kv = it.split("=", limit = 2); if (kv.size == 2) URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv[1], "UTF-8").trim() else null
            }.toMap()
            val ok = (form["tipo"] == "lista" && form["url"].orEmpty().contains("://")) ||
                (form["tipo"] == "tmdb" && form["chave"].orEmpty().length >= 20) ||
                (form["tipo"] == "conta" && form["servidor"].orEmpty().isNotBlank() && form["usuario"].orEmpty().isNotBlank() && form["senha"].orEmpty().isNotBlank())
            val dup = ok && form["tipo"] == "lista" && Store.hasList(form["url"].orEmpty())
            msg = if (dup) "<p class=err>Essa lista já está salva na TV. Não foi enviada de novo.</p>" else if (ok) { main.post { onReceive?.invoke(form) }; "<p class=ok>Enviado! Confira na TV.</p>" } else "<p class=err>Preencha todos os campos.</p>"
        }
        val html = page(msg).toByteArray(Charsets.UTF_8)
        val out = c.getOutputStream()
        out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(html); out.flush()
    }

    private fun lerForm(ins: java.io.InputStream, len: Int): Map<String, String> {
        if (len !in 1..4000) return emptyMap()
        val body = ByteArray(len); var got = 0
        while (got < len) { val n = ins.read(body, got, len - got); if (n < 0) break; got += n }
        return String(body, 0, got, Charsets.UTF_8).split("&").mapNotNull {
            val kv = it.split("=", limit = 2); if (kv.size == 2) URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv[1], "UTF-8") else null
        }.toMap()
    }

    private fun responder(c: Socket, status: String, tipo: String, corpo: String, extra: String = "") {
        val b = corpo.toByteArray(Charsets.UTF_8)
        val out = c.getOutputStream()
        out.write("HTTP/1.1 $status\r\nContent-Type: $tipo; charset=utf-8\r\nContent-Length: ${b.size}\r\nCache-Control: no-store\r\n${extra}Connection: close\r\n\r\n".toByteArray())
        out.write(b); out.flush()
    }

    /** Celular como controle: pareamento com código e teclas. */
    private fun controle(c: Socket, first: String, caminho: String, head: String, ins: java.io.InputStream, len: Int) {
        val chave = Regex("(?i)^cookie:.*\\blume=([0-9a-f]{32})", RegexOption.MULTILINE).find(head)?.groupValues?.get(1)
        val ok = Controle.valida(chave)
        val post = first.startsWith("POST")
        when {
            caminho == "/parear" && post -> {
                val (nova, msg) = Controle.parear(lerForm(ins, len)["codigo"].orEmpty())
                if (nova != null) {
                    main.post { Controle.aviso?.invoke("Celular conectado como controle remoto") }
                    responder(c, "303 See Other", "text/plain", "", "Location: /controle\r\nSet-Cookie: lume=$nova; Max-Age=31536000; Path=/; HttpOnly; SameSite=Strict\r\n")
                } else responder(c, "200 OK", "text/html", paginaParear(msg))
            }
            caminho == "/tecla" && post -> {
                if (!ok) responder(c, "403 Forbidden", "text/plain", "parear")
                else responder(c, if (Controle.tecla(lerForm(ins, len)["k"].orEmpty())) "200 OK" else "400 Bad Request", "text/plain", "ok")
            }
            caminho == "/texto" && post -> {
                if (!ok) responder(c, "403 Forbidden", "text/plain", "parear")
                else { Controle.texto(lerForm(ins, len)["t"].orEmpty()); responder(c, "200 OK", "text/plain", "ok") }
            }
            !ok && caminho == "/controle" && first.contains("?c=") -> {
                // QR Code da TV: o código já vem no endereço, conecta sem digitar
                val cod = Regex("[?&]c=(\\d{4})").find(first)?.groupValues?.get(1).orEmpty()
                val (nova, msg) = Controle.parear(cod)
                if (nova != null) {
                    main.post { Controle.aviso?.invoke("Celular conectado como controle remoto") }
                    responder(c, "303 See Other", "text/plain", "", "Location: /controle\r\nSet-Cookie: lume=$nova; Max-Age=31536000; Path=/; HttpOnly; SameSite=Lax\r\n")
                } else responder(c, "200 OK", "text/html", paginaParear("$msg O código muda quando a TV é desconectada; leia o QR Code de novo."))
            }
            else -> responder(c, "200 OK", "text/html", if (ok) paginaControle() else paginaParear(""))
        }
    }

    private const val ESTILO = """<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>body{margin:0;font-family:system-ui,sans-serif;background:#050816;color:#eef2ff;padding:18px;-webkit-user-select:none;user-select:none}
h1{font-size:20px;margin:0 0 4px}p{color:#9aa6d6;margin:0 0 14px}
input{font-size:22px;padding:12px;border-radius:10px;border:1px solid #243069;background:#0e1538;color:#eef2ff;width:100%;box-sizing:border-box}
button{font-size:18px;font-weight:700;border:0;border-radius:14px;background:#16204a;color:#eef2ff;padding:14px;touch-action:manipulation}
button:active{background:#5aa8ff;color:#050816}.p{background:linear-gradient(90deg,#5aa8ff,#b48cff);color:#050816}
.err{color:#ff6f8e;font-weight:700}.g{display:grid;gap:10px}</style>"""

    private fun paginaParear(msg: String) = """<!doctype html><html lang="pt-BR"><head>$ESTILO<title>LumeStarTV · controle</title></head><body>
<h1>Controle pelo celular</h1><p>Digite o código de 4 números que aparece na TV em Configurações › Controle pelo celular. É só uma vez neste celular.</p>
${if (msg.isNotBlank()) "<p class=err>$msg</p>" else ""}
<form method="post" action="/parear" class="g"><input name="codigo" inputmode="numeric" maxlength="4" pattern="[0-9]{4}" placeholder="0000" required autofocus>
<button class="p">Conectar</button></form></body></html>"""

    private fun paginaControle() = """<!doctype html><html lang="pt-BR"><head>$ESTILO<title>LumeStarTV · controle</title>
<style>.pad{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;max-width:360px;margin:0 auto 14px}.pad button{height:74px;font-size:26px}
.ok{border-radius:50%;background:linear-gradient(135deg,#5aa8ff,#b48cff);color:#050816}.lin{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;max-width:360px;margin:0 auto 10px}
#st{text-align:center;min-height:18px;font-size:13px}</style></head><body>
<h1>LumeStarTV</h1><p id=st>Controle conectado</p>
<div class=lin><button data-k=voltar>↩ Voltar</button><button data-k=play>⏯</button><button data-k=menu>☰ Menu</button></div>
<div class=pad><span></span><button data-k=cima>▲</button><span></span>
<button data-k=esquerda>◀</button><button class=ok data-k=ok>OK</button><button data-k=direita>▶</button>
<span></span><button data-k=baixo>▼</button><span></span></div>
<div class=lin><button data-k=volmenos>🔉 −</button><button data-k=mudo>🔇</button><button data-k=volmais>🔊 +</button></div>
<div class=lin><button data-k=canalmenos>CH −</button><button data-k=apagar>⌫</button><button data-k=canalmais>CH +</button></div>
<form id=f class=g style="max-width:360px;margin:16px auto 0"><input id=t placeholder="Digitar na TV (pesquisa, senha…)" autocomplete="off"><button class=p>Enviar texto</button></form>
<script>
const st=document.getElementById('st');
function enviar(u,b){fetch(u,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:b}).then(r=>{if(r.status==403)location.reload();st.textContent=r.ok?'Controle conectado':'A TV não respondeu'}).catch(()=>st.textContent='A TV não respondeu. O app está aberto?')}
document.querySelectorAll('button[data-k]').forEach(b=>{let t;const k=()=>{enviar('/tecla','k='+b.dataset.k);if(navigator.vibrate)navigator.vibrate(15)};
b.addEventListener('pointerdown',e=>{e.preventDefault();k();if(/cima|baixo|esquerda|direita|vol/.test(b.dataset.k))t=setTimeout(function r(){k();t=setTimeout(r,140)},450)});
['pointerup','pointerleave','pointercancel'].forEach(ev=>b.addEventListener(ev,()=>clearTimeout(t)))});
document.getElementById('f').onsubmit=e=>{e.preventDefault();const i=document.getElementById('t');if(i.value){enviar('/texto','t='+encodeURIComponent(i.value));i.value=''}};
</script></body></html>"""

    /** Pega os bytes do arquivo enviado num formulário multipart. */
    private fun filePart(b: ByteArray, n: Int, boundary: String): ByteArray? {
        fun find(p: ByteArray, from: Int): Int { outer@ for (i in from..n - p.size) { for (j in p.indices) if (b[i + j] != p[j]) continue@outer; return i }; return -1 }
        val fn = find("filename=".toByteArray(), 0); if (fn < 0) return null
        val st = find("\r\n\r\n".toByteArray(), fn); if (st < 0) return null
        val end = find(("\r\n--" + boundary).toByteArray(), st + 4); if (end < 0) return null
        return b.copyOfRange(st + 4, end)
    }

    private fun page(msg: String) = """<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LumeStarTV · enviar pelo celular</title><style>
body{margin:0;font-family:system-ui,sans-serif;background:#050816;color:#eef2ff;padding:20px}
h1{font-size:22px;margin:0 0 4px}p{color:#9aa6d6;margin:0 0 18px}
form{background:#0e1538;border:1px solid #243069;border-radius:16px;padding:16px;margin-bottom:18px;display:flex;flex-direction:column;gap:10px}
h2{font-size:16px;margin:0}input{font-size:16px;padding:12px;border-radius:10px;border:1px solid #243069;background:#050816;color:#eef2ff}
button{font-size:16px;font-weight:700;padding:12px;border:0;border-radius:10px;background:linear-gradient(90deg,#5aa8ff,#b48cff);color:#050816}
.ok{color:#52d6a8;font-weight:700}.err{color:#ff6f8e;font-weight:700}</style></head><body>
<h1>LumeStarTV</h1><p>Envie para a TV sem digitar no controle. <a href="/controle" style="color:#5aa8ff">Usar o celular como controle remoto ›</a></p>$msg
<form method="post"><h2>Lista (M3U, M3U8, JSON ou TXT)</h2><input type="hidden" name="tipo" value="lista">
<input name="nome" placeholder="Nome (ex.: Minha lista)"><input name="url" type="url" placeholder="Endereço da lista (http://…)" required>
<button>Enviar lista para a TV</button></form>
<form method="post"><h2>Conta IPTV (Xtream)</h2><input type="hidden" name="tipo" value="conta">
<input name="nome" placeholder="Nome da conta"><input name="servidor" placeholder="URL do servidor (ex.: http://servidor.com:8080)" required>
<input name="usuario" placeholder="Usuário" required autocapitalize="off"><input name="senha" type="password" placeholder="Senha" required>
<button>Enviar conta para a TV</button></form>
<form method="post"><h2>Chave do TMDB (capas e sinopses)</h2><input type="hidden" name="tipo" value="tmdb">
<input name="chave" placeholder="Chave da API do TMDB" required autocapitalize="off" autocomplete="off" spellcheck="false">
<button>Enviar chave para a TV</button></form>
<form method="post" enctype="multipart/form-data"><h2>Imagem do tema Anime</h2>
<input type="file" name="imagem" accept="image/*" required>
<button>Enviar imagem para a TV</button></form></body></html>"""
}
