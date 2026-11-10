package com.lumestar.tv

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

private val main = Handler(Looper.getMainLooper())

/** Transmissão de rede: 5 endereços como o "Abrir fluxo de rede" do VLC. */
@Composable
fun StreamPanel(st: AppState, locked: Boolean) {
    var slot by remember { mutableIntStateOf(0) }
    PP.rev.let { }
    val s = PP.streams[slot]
    T("Cole o endereço de um vídeo ou transmissão e assista direto, como no VLC. Aceita http, https, HLS (.m3u8), DASH (.mpd), .ts, .mp4, .mkv, RTSP, RTMP e UDP.", 13, C.muted, lineHeight = 19)
    Seg("Endereços salvos", (0..4).map { i -> i to PP.streams[i].name.ifBlank { "${i + 1}" } }, slot, locked) { slot = it }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldRow(st, "Nome", s.name, locked, hint = "Ex.: Câmera da praia") { s.name = it.trim(); PP.saveSlots() }
        FieldRow(st, "Endereço", s.url, locked, hint = "Ex.: rtsp://192.168.0.10/stream") { s.url = it.trim(); PP.saveSlots() }
    }
    Seg("Cache (espera antes de tocar)", listOf(300 to "300 ms", 1000 to "1.000 ms", 3000 to "3.000 ms"), s.cache, locked) { s.cache = it; PP.saveSlots() }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn("▶ Reproduzir", locked = locked, primary = true) {
            if (!s.url.contains("://")) { st.say("Digite o endereço primeiro"); return@Btn }
            val low = s.url.lowercase()
            val vod = listOf(".mp4", ".mkv", ".avi", ".mov", ".webm").any { low.substringBefore('?').endsWith(it) }
            st.play(PlayReq("rede:" + s.url.hashCode(), s.name.ifBlank { "Transmissão ${slot + 1}" }, s.url, !vod, 0L, "Transmissão de rede", cacheMs = s.cache))
        }
        Btn("Apagar", locked = locked) { PP.streams[slot] = Slot(); PP.saveSlots() }
    }
    T("Cache menor = começa mais rápido; maior = trava menos em internet instável. O mesmo vale para o \"Cache de rede\" em Qualidade de vídeo.", 11, C.dim)
}

/** Até 5 sites para abrir no navegador da TV. */
@Composable
fun WebPanel(st: AppState, locked: Boolean) {
    val ctx = LocalContext.current
    PP.rev.let { }
    T("Guarde até 5 sites para abrir com um clique no navegador da TV.", 13, C.muted, lineHeight = 19)
    (0..4).forEach { i ->
        val w = PP.webs[i]
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldRow(st, "Site ${i + 1}", w.url, locked, hint = "Ex.: https://www.youtube.com") { w.url = it.trim(); if (w.name.isBlank()) w.name = it.substringAfter("://").substringBefore('/'); PP.saveSlots() }
            if (w.url.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Btn("Abrir ${w.name.ifBlank { "site" }}", locked = locked, primary = true) { openWeb(ctx, st, w.url) }
                Btn("Apagar", locked = locked) { PP.webs[i] = Slot(); PP.saveSlots() }
            }
        }
    }
}

fun openWeb(ctx: Context, st: AppState, url: String) {
    val u = if (url.contains("://")) url else "https://$url"
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { st.say("Nenhum navegador instalado na TV. Instale um (ex.: TV Bro) pela Play Store.") }
}

/** Proxy para listas, contas, notícias e vídeos. */
@Composable
fun ProxyPanel(st: AppState, locked: Boolean) {
    PP.rev.let { }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    T("Use quando sua internet bloqueia o servidor da lista. Vale para listas, contas, notícias e vídeos.", 13, C.muted, lineHeight = 19)
    Opts("Tipo", linkedMapOf("off" to "Desligado", "http" to "HTTP / HTTPS", "socks" to "SOCKS5"), PP.proxyType, locked) { PP.proxyType = it }
    if (PP.proxyType != "off") Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldRow(st, "Endereço", PP.proxyHost, locked, hint = "Ex.: 10.0.0.5 ou proxy.site.com") { PP.proxyHost = it.trim() }
        FieldRow(st, "Porta", PP.proxyPort, locked, hint = "Ex.: 8080") { PP.proxyPort = it.filter { c -> c.isDigit() } }
        FieldRow(st, "Usuário", PP.proxyUser, locked, hint = "Opcional") { PP.proxyUser = it.trim() }
        FieldRow(st, "Senha", PP.proxyPass, locked, secret = true, hint = "Opcional") { PP.proxyPass = it }
    }
    Btn(if (busy) "Testando…" else "Testar conexão", locked = locked, primary = true) {
        if (busy) return@Btn
        busy = true; status = "Testando…"
        thread {
            val t0 = System.currentTimeMillis()
            val r = try { val c = Net.open("https://www.google.com/generate_204", 8000); c.disconnect(); "Funcionando · ${System.currentTimeMillis() - t0} ms" } catch (e: Throwable) { "Falhou: " + Net.why(e) }
            main.post { status = r; busy = false }
        }
    }
    if (status.isNotEmpty()) T(status, 13, if (status.startsWith("Func")) C.ok else C.bad, FontWeight.SemiBold)
}

/** Qualidade, imagem e cache: os mesmos ajustes do reprodutor. */
@Composable
fun VideoPanel(st: AppState, locked: Boolean) {
    PP.rev.let { }
    T("Valem para todos os vídeos. Também dá para mudar durante o vídeo, nos botões Imagem e Tela.", 13, C.muted, lineHeight = 19)
    Opts("Qualidade máxima", QUALITY, PP.quality, locked) { PP.quality = it }
    Opts("Realce de imagem", ENHANCE, PP.enhance, locked) { PP.enhance = it }
    Opts("Modo de imagem", PRESETS, PP.preset, locked) { PP.preset = it }
    if (PP.preset == "pers") {
        Stepper("Brilho", PP.bright, "", locked) { d -> PP.bright = (PP.bright + d * 5).coerceIn(-50, 50) }
        Stepper("Contraste", PP.contrast, "", locked) { d -> PP.contrast = (PP.contrast + d * 5).coerceIn(-50, 50) }
        Stepper("Cor (saturação)", PP.sat, "", locked) { d -> PP.sat = (PP.sat + d * 5).coerceIn(-50, 50) }
    }
    Seg("Cache de rede", listOf(300 to "300 ms", 1000 to "1.000 ms", 3000 to "3.000 ms"), PP.cacheMs, locked) { PP.cacheMs = it }
    Opts("IA de áudio", AUDIO_AI, PP.audioAi, locked) { PP.audioAi = it }
}

/** Testa os canais ao vivo e marca ou esconde os que estão sem sinal. */
object OfflineScan {
    var running by mutableStateOf(false)
    var done by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    var found by mutableIntStateOf(0)
    @Volatile private var stop = false

    fun cancel() { stop = true }

    fun start() {
        if (running) return
        val lives = Catalog.items.filter { it.kind == LIVE }
        if (lives.isEmpty()) return
        running = true; stop = false; done = 0; found = 0; total = lives.size
        val off = java.util.Collections.synchronizedSet(HashSet<String>())
        val n = AtomicInteger(0)
        thread(name = "fora-do-ar") {
            val pool = Executors.newFixedThreadPool(8)
            lives.forEach { item -> pool.execute {
                if (!stop) {
                    if (!alive(item.url)) off.add(item.id)
                    val k = n.incrementAndGet()
                    if (k % 5 == 0 || k == lives.size) { val f = off.size; main.post { done = k; found = f } }
                }
            } }
            pool.shutdown(); pool.awaitTermination(2, java.util.concurrent.TimeUnit.HOURS)
            main.post {
                if (!stop) { PP.offline = off.toMutableSet(); PP.saveOffline() }
                running = false; done = n.get(); found = off.size
            }
        }
    }

    private fun alive(url: String): Boolean = try {
        val e = Cabecalhos.separa(url)
        val c = (URL(e.url).openConnection(PP.proxy()) as HttpURLConnection).apply {
            connectTimeout = 6000; readTimeout = 6000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", e.userAgent ?: "VLC/3.0.20 LibVLC/3.0.20"); setRequestProperty("Range", "bytes=0-2047")
            e.outros.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val code = c.responseCode
        val ok = code in 200..399 && c.inputStream.use { it.read(ByteArray(512)) > 0 }
        c.disconnect(); ok
    } catch (e: Throwable) { false }
}

@Composable
fun OfflinePanel(st: AppState, locked: Boolean) {
    PP.rev.let { }
    val lives = Catalog.items.count { it.kind == LIVE }
    T("O app testa cada canal ao vivo (alguns segundos por canal, 8 de cada vez) e marca os que não respondem. Pode demorar em listas grandes; você pode sair desta tela que o teste continua.", 13, C.muted, lineHeight = 19)
    Opts("Canais sem sinal", linkedMapOf("marcar" to "Marcar \"Fora do ar\"", "ocultar" to "Esconder"), PP.offlineMode, locked) { PP.offlineMode = it; Catalog.rev++ }
    KV(listOf("Canais ao vivo" to "$lives", "Fora do ar (último teste)" to "${PP.offline.size}", "Teste" to if (OfflineScan.running) "${OfflineScan.done} de ${OfflineScan.total} · ${OfflineScan.found} sem sinal" else "parado"))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (OfflineScan.running) Btn("Parar teste", locked = locked) { OfflineScan.cancel() }
        else Btn("Testar canais agora", locked = locked, primary = true) { if (lives == 0) st.say("Nenhum canal ao vivo carregado") else OfflineScan.start() }
        Btn("Limpar marcações", locked = locked) { PP.offline = mutableSetOf(); PP.saveOffline(); Catalog.rev++ }
    }
}

/** Situação da conexão e teste de velocidade. */
@Composable
fun NetPanel(st: AppState, locked: Boolean) {
    val ctx = LocalContext.current
    var speed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    tick.let { }
    KV(netInfo(ctx))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(if (busy) "Medindo…" else "Testar velocidade", locked = locked, primary = true) {
            if (busy) return@Btn
            busy = true; speed = "Baixando arquivo de teste…"
            thread {
                val r = try {
                    val t0 = System.currentTimeMillis()
                    val c = Net.open("https://speed.cloudflare.com/__down?bytes=25000000", 20000)
                    var bytes = 0L; val buf = ByteArray(64 * 1024)
                    c.inputStream.use { s -> while (true) { val k = s.read(buf); if (k < 0 || System.currentTimeMillis() - t0 > 12000) break; bytes += k } }
                    c.disconnect()
                    val sec = (System.currentTimeMillis() - t0) / 1000.0
                    val mbps = bytes * 8 / sec / 1_000_000
                    "%.1f Mbps de download".format(mbps) + "  ·  " + when { mbps >= 25 -> "dá para 4K"; mbps >= 10 -> "dá para Full HD"; mbps >= 5 -> "dá para HD (720p)"; else -> "fraca: use 480p e cache 3.000 ms" }
                } catch (e: Throwable) { "Falhou: " + Net.why(e) }
                main.post { speed = r; busy = false; tick++ }
            }
        }
        Btn("Configurações de rede da TV", locked = locked) {
            try { ctx.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { st.say("Abra as configurações de rede pela TV") }
        }
    }
    if (speed.isNotEmpty()) T(speed, 14, if (speed.startsWith("Falhou")) C.bad else C.ok, FontWeight.Bold)
}

fun netInfo(ctx: Context): List<Pair<String, String>> {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    // activeNetwork só existe no Android 6+; antes disso, acha a rede ativa pela lista de redes
    val n = (if (android.os.Build.VERSION.SDK_INT >= 23) cm.activeNetwork else {
        @Suppress("DEPRECATION") val ai = cm.activeNetworkInfo
        @Suppress("DEPRECATION")
        if (ai == null || !ai.isConnected) null else cm.allNetworks.firstOrNull { cm.getNetworkInfo(it)?.type == ai.type }
    }) ?: return listOf("Conexão" to "Sem internet")
    val cap = cm.getNetworkCapabilities(n)
    val lp = cm.getLinkProperties(n)
    val kind = when {
        cap == null -> "Desconhecida"
        cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Cabo (Ethernet)"
        cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Dados móveis"
        else -> "Outra"
    }
    val ok = cap?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    val ip = lp?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress ?: "—"
    val dns = lp?.dnsServers?.take(2)?.joinToString(", ") { it.hostAddress ?: "" } ?: "—"
    return listOf(
        "Conexão" to kind,
        "Internet" to if (ok) "Funcionando" else "Sem acesso confirmado",
        "IP na rede" to ip,
        "DNS" to dns.ifBlank { "—" },
        "Banda estimada" to (cap?.linkDownstreamBandwidthKbps?.let { "${it / 1000} Mbps" } ?: "—"),
        "Proxy" to if (PP.proxyType == "off") "Desligado" else "${PP.proxyType.uppercase()} ${PP.proxyHost}:${PP.proxyPort}",
    )
}
