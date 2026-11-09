package com.lumestar.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C as MC
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.concurrent.thread

// ---------------- apps de streaming e de música ----------------
class TvApp(val name: String, val pkgs: List<String>, val note: String, val site: String = "", val busca: Boolean = false)

val STREAM_APPS = listOf(
    TvApp("Netflix", listOf("com.netflix.ninja", "com.netflix.mediaclient"), "Filmes e séries", "netflix.com"),
    TvApp("Prime Video", listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"), "Amazon", "primevideo.com"),
    TvApp("Disney+", listOf("com.disney.disneyplus"), "Disney, Marvel, Star Wars", "disneyplus.com"),
    TvApp("Max", listOf("com.wbd.stream", "com.hbo.hbonow"), "HBO e Warner", "max.com"),
    TvApp("Globoplay", listOf("com.globo.globotv"), "Novelas e Globo ao vivo", "globoplay.globo.com"),
    TvApp("Apple TV", listOf("com.apple.atve.androidtv.appletv"), "Apple TV+", "tv.apple.com"),
    TvApp("Paramount+", listOf("com.cbs.ott"), "Filmes e esportes", "paramountplus.com"),
    TvApp("YouTube", listOf("com.google.android.youtube.tv", "com.google.android.youtube"), "Vídeos", "youtube.com"),
    TvApp("Pluto TV", listOf("tv.pluto.android"), "Canais grátis", "pluto.tv"),
    TvApp("Crunchyroll", listOf("com.crunchyroll.crunchyroid"), "Animes", "crunchyroll.com"),
    TvApp("Plex", listOf("com.plexapp.android"), "Seus vídeos", "plex.tv"),
    TvApp("Google TV", listOf("com.google.android.videos"), "Filmes comprados e alugados", "tv.google"),
    TvApp("Mercado Play", listOf("com.mercadolibre.play", "com.mercadolibre"), "Grátis com anúncios", "mercadolivre.com.br"),
)

/** Serviços grátis e legais (com anúncios), com filmes e séries dublados ou nacionais. */
val FREE_APPS = listOf(
    TvApp("Pluto TV", listOf("tv.pluto.android"), "Filmes e séries sob demanda, dublados", "pluto.tv"),
    TvApp("NetMovies", listOf("br.com.netmovies", "com.netmovies.app"), "Filmes e séries dublados", "netmovies.com.br", busca = true),
    TvApp("Mercado Play", listOf("com.mercadolibre.play", "com.mercadolibre"), "Filmes e séries com anúncios", "mercadolivre.com.br"),
    TvApp("Plex", listOf("com.plexapp.android"), "Filmes grátis com anúncios", "plex.tv"),
    TvApp("+SBT", listOf("br.com.sbt.app", "com.sbt.sbtvideos"), "Novelas e programas do SBT", "sbt.com.br", busca = true),
    TvApp("Runtime", listOf("tv.runtime.android", "com.runtime.tv"), "Filmes grátis", "runtime.tv", busca = true),
    TvApp("Tela Brasil", listOf("br.com.telabrasil", "com.telabrasil"), "Cinema brasileiro", "telabrasil.com.br", busca = true),
    TvApp("Spcine Play", listOf("br.com.spcineplay", "com.spcine.play"), "Cinema brasileiro e independente", "spcineplay.com.br", busca = true),
    TvApp("Itaú Cultural Play", listOf("br.org.itaucultural.play", "com.itaucultural.play"), "Filmes e séries brasileiras", "itauculturalplay.com.br", busca = true),
)

val MUSIC_APPS = listOf(
    TvApp("Spotify", listOf("com.spotify.tv.android", "com.spotify.music"), "Músicas e podcasts", "spotify.com"),
    TvApp("YouTube Music", listOf("com.google.android.youtube.tvmusic", "com.google.android.apps.youtube.music"), "Clipes e músicas", "music.youtube.com"),
    TvApp("Deezer", listOf("deezer.android.tv", "deezer.android.app"), "Músicas", "deezer.com"),
    TvApp("Amazon Music", listOf("com.amazon.music.tv", "com.amazon.mp3"), "Músicas", "music.amazon.com"),
    TvApp("TIDAL", listOf("com.aspiro.tidal"), "Alta fidelidade", "tidal.com"),
    TvApp("TuneIn", listOf("tunein.player"), "Rádios do mundo", "tunein.com"),
)

fun appIntent(c: Context, a: TvApp): Intent? = a.pkgs.firstNotNullOfOrNull { p ->
    try { c.packageManager.getLeanbackLaunchIntentForPackage(p) ?: c.packageManager.getLaunchIntentForPackage(p) } catch (e: Exception) { null }
}

fun openApp(c: Context, st: AppState, a: TvApp) {
    val i = appIntent(c, a)
    try {
        if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else if (a.busca) c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?c=apps&q=" + Uri.encode(a.name))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        else c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + a.pkgs.first())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { st.say("${a.name} não está instalado. Instale pela Play Store da TV.") }
}

@Composable
fun AppTile(st: AppState, a: TvApp, locked: Boolean) {
    val ctx = LocalContext.current
    val installed = remember(a) { appIntent(ctx, a) != null }
    // logo oficial: o banner/ícone que o próprio app instalado entrega ao Android; se não estiver instalado, o ícone do site da marca
    val banner = remember(a) { appArte(ctx, a) }
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.width(160.dp).height(96.dp)
            .graphicsLayer { val k = if (f) 1.06f else 1f; scaleX = k; scaleY = k }
            .shadow(if (f) 16.dp else 0.dp, RoundedCornerShape(14.dp), ambientColor = C.a1, spotColor = C.a1)
            .clip(RoundedCornerShape(14.dp)).background(coverBrush(a.name))
            .border(if (f) 2.dp else 0.dp, if (f) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val n = it.isFocused; if (n && !f) Sfx.tick(); f = n }
            .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); openApp(ctx, st, a) }
    ) {
        if (banner != null && banner.second) {
            // banner de TV (16:9): ocupa o card inteiro, como na tela inicial da TV
            androidx.compose.foundation.Image(banner.first, a.name, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(horizontal = 10.dp, vertical = 5.dp)) {
                T(if (f) "▶ Abrir" else a.name, 10, Color.White, FontWeight.Bold, maxLines = 1)
            }
        } else if (banner != null) {
            // app instalado sem banner de TV: o ícone oficial grande, fundo na cor dele
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White.copy(alpha = .14f), Color.Transparent))), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(banner.first, a.name, Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)))
            }
            Rodape(a, installed, f)
        } else {
            // não instalado: ícone oficial do site da marca ocupando o card, fundo na cor dele
            LogoArte(a.name, if (a.site.isNotBlank()) "https://www.google.com/s2/favicons?domain=${a.site}&sz=256" else "", Modifier.fillMaxSize().padding(bottom = 20.dp), pad = 14.dp, tamanhoNome = 20)
            Rodape(a, installed, f)
        }
        if (f) Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.linearGradient(0f to Color.White.copy(alpha = .22f), .45f to Color.Transparent)))
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Rodape(a: TvApp, installed: Boolean, f: Boolean) {
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000)))).padding(horizontal = 10.dp, vertical = 4.dp)) {
        T(if (f) (if (installed) "▶ Abrir " + a.name else "⬇ Instalar " + a.name) else a.name + " · " + a.note, 10, if (installed || !f) Color.White else Color(0xFFFFE08A), FontWeight.Bold, maxLines = 1)
    }
}

/** Arte oficial do app instalado: (imagem, éBannerDeTV). Banner de TV quando existe; senão o ícone. */
fun appArte(c: Context, a: TvApp): Pair<androidx.compose.ui.graphics.ImageBitmap, Boolean>? {
    val pm = c.packageManager
    for (p in a.pkgs) {
        try {
            val info = pm.getApplicationInfo(p, 0)
            val ban = try { pm.getApplicationBanner(info) } catch (e: Throwable) { null }
            val d = ban ?: pm.getApplicationIcon(info)
            val w = if (ban != null) 320 else 128; val h = if (ban != null) 180 else 128
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bmp); d.setBounds(0, 0, w, h); d.draw(cv)
            return bmp.asImageBitmap() to (ban != null)
        } catch (e: Throwable) {}
    }
    return null
}

@Composable
fun AppsGrid(st: AppState, apps: List<TvApp>, locked: Boolean, perRow: Int = 4) {
    Column(Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        apps.chunked(perRow).forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { r.forEach { AppTile(st, it, locked) } } }
    }
}

@Composable
fun StreamingPage(st: AppState, locked: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 56.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("Streaming", 30, C.fg, FontWeight.ExtraBold)
        T("Abre o app oficial de cada serviço instalado na TV, com a sua assinatura. Os que não estão instalados abrem na Play Store.", 13, C.muted, lineHeight = 19, modifier = Modifier.width(620.dp))
        Spacer(Modifier.height(6.dp))
        AppsGrid(st, STREAM_APPS, locked)
        Spacer(Modifier.height(10.dp))
        T("Filmes e séries dublados grátis", 20, C.fg, FontWeight.ExtraBold)
        T("Serviços gratuitos e legais, pagos por anúncios: filmes e séries dublados em português do Brasil e cinema nacional. Os que não estão instalados abrem na Play Store.", 13, C.muted, lineHeight = 19, modifier = Modifier.width(620.dp))
        AppsGrid(st, FREE_APPS, locked)
    }
}

/** Atalhos de música no topo da aba Músicas. */
@Composable
fun MusicApps(st: AppState, locked: Boolean) {
    Column(Modifier.padding(start = 28.dp, top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("Apps de música", 14, C.fg, FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.focusGroup()) { MUSIC_APPS.forEach { AppTile(st, it, locked) } }
    }
}

// ---------------- sincronia de áudio e vídeo ----------------
/**
 * Atrasa (ms > 0) ou adianta (ms < 0) o som em relação à imagem.
 * Atrasar = inserir silêncio; adiantar = descartar um trecho. O relógio do vídeo segue o som, então a imagem acompanha.
 */
@UnstableApi
class AvDelay : BaseAudioProcessor() {
    @Volatile var targetMs = 0
    private var appliedMs = 0
    private var dropBytes = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != MC.ENCODING_PCM_16BIT && inputAudioFormat.encoding != MC.ENCODING_PCM_FLOAT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val f = inputAudioFormat
        val bpf = f.bytesPerFrame.coerceAtLeast(1)
        var silence = 0
        val d = targetMs - appliedMs
        if (d != 0) {
            val bytes = (kotlin.math.abs(d).toLong() * f.sampleRate / 1000) * bpf
            if (d > 0) silence = bytes.toInt() else dropBytes += bytes
            appliedMs = targetMs
        }
        if (dropBytes > 0) { val k = minOf(dropBytes, inputBuffer.remaining().toLong()).toInt(); inputBuffer.position(inputBuffer.position() + k); dropBytes -= k }
        val rem = inputBuffer.remaining()
        if (rem == 0 && silence == 0) return
        val out = replaceOutputBuffer(silence + rem)
        if (silence > 0) { val z = ByteArray(minOf(silence, 65536)); var left = silence; while (left > 0) { val n = minOf(left, z.size); out.put(z, 0, n); left -= n } }
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() { appliedMs = 0; dropBytes = 0 } // depois de pular no vídeo, o atraso é aplicado de novo
    override fun onReset() { appliedMs = 0; dropBytes = 0 }
}

/** Situação do reprodutor, para o assistente explicar travamentos. */
object PlayerDiag {
    @Volatile var lastError = ""
    @Volatile var lastTitle = ""
    @Volatile var rebuffers = 0
    @Volatile var lastAt = 0L
}

// ---------------- assistente (diagnóstico no próprio aparelho) ----------------
private class Msg(val me: Boolean, val text: String, val actions: List<Pair<String, () -> Unit>> = emptyList())

private val mainH = Handler(Looper.getMainLooper())

/** Teste de velocidade rápido (até 8 s). */
fun quickSpeed(done: (Double?, String) -> Unit) {
    thread {
        val r: Pair<Double?, String> = try {
            val t0 = System.currentTimeMillis()
            val c = Net.open("https://speed.cloudflare.com/__down?bytes=15000000", 15000)
            var bytes = 0L; val buf = ByteArray(65536)
            c.inputStream.use { s -> while (true) { val k = s.read(buf); if (k < 0 || System.currentTimeMillis() - t0 > 8000) break; bytes += k } }
            c.disconnect()
            val mbps = bytes * 8 / ((System.currentTimeMillis() - t0) / 1000.0) / 1_000_000
            mbps to ""
        } catch (e: Throwable) { null to Net.why(e) }
        mainH.post { done(r.first, r.second) }
    }
}

private fun speedVerdict(m: Double) = when { m >= 25 -> "ótima, dá para 4K"; m >= 10 -> "boa, dá para Full HD"; m >= 5 -> "razoável, dá para HD (720p)"; else -> "fraca para vídeo" }

@Composable
fun AssistantPage(st: AppState, locked: Boolean, compact: Boolean = false) {
    val ctx = LocalContext.current
    val msgs = remember { mutableStateListOf(Msg(false, "Olá! Sou o assistente do LumeStarTV. Funciono aqui na TV, sem internet de terceiros. Escolha uma pergunta à esquerda que eu verifico o aparelho e respondo.")) }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }
    fun me(t: String) = msgs.add(Msg(true, t))
    fun bot(t: String, a: List<Pair<String, () -> Unit>> = emptyList()) = msgs.add(Msg(false, t, a))

    val quick: List<Pair<String, () -> Unit>> = listOf(
        "Por que o canal está travando?" to {
            me("Por que o canal está travando?"); bot("Verificando a conexão e o último vídeo…")
            val info = netInfo(ctx).toMap()
            quickSpeed { m, err ->
                val tips = ArrayList<String>()
                if (info["Internet"] != "Funcionando") tips.add("• A TV está sem acesso confirmado à internet (${info["Conexão"]}). Confira o roteador.")
                if (m == null) tips.add("• Não consegui medir a velocidade: $err.") else tips.add("• Velocidade agora: %.1f Mbps, %s.".format(m, speedVerdict(m)))
                if (PlayerDiag.lastError.isNotBlank()) tips.add("• Último erro em \"${PlayerDiag.lastTitle}\": ${PlayerDiag.lastError}")
                if (PlayerDiag.rebuffers > 2) tips.add("• O último vídeo parou para carregar ${PlayerDiag.rebuffers} vezes: sinal do servidor da lista ou da internet oscilando.")
                if (PP.proxyType != "off") tips.add("• O proxy está ligado; ele pode deixar tudo mais lento.")
                val weak = m != null && m < 8
                if (weak) tips.add("Sugestão: qualidade máxima 720p e cache de 3.000 ms seguram melhor o vídeo nessa internet.")
                else tips.add("Se só um canal trava e a internet está boa, o problema costuma ser o servidor da lista. O app tenta de novo sozinho depois de 30 s.")
                bot(tips.joinToString("\n"), if (weak) listOf("Aplicar 720p + cache 3.000 ms" to { PP.quality = "720"; PP.cacheMs = 3000; bot("Pronto: qualidade até 720p e cache de 3.000 ms. Vale para o próximo vídeo.") }) else emptyList())
            }
        },
        "Testar minha internet" to {
            me("Testar minha internet"); bot("Baixando um arquivo de teste por alguns segundos…")
            quickSpeed { m, err -> bot(if (m == null) "Não consegui testar: $err." else "Download: %.1f Mbps. A internet está %s.".format(m, speedVerdict(m))) }
        },
        "Quais canais estão fora do ar?" to {
            me("Quais canais estão fora do ar?")
            if (Catalog.items.none { it.kind == LIVE }) bot("Ainda não há canais ao vivo carregados. Adicione sua lista em Configurações › Listas e formatos.")
            else { OfflineScan.start(); bot("Comecei a testar os canais (8 de cada vez). Você pode acompanhar em Configurações › Canais fora do ar. No último teste, ${PP.offline.size} estavam sem sinal.",
                listOf("Esconder os que estão fora do ar" to { PP.offlineMode = "ocultar"; Catalog.rev++; bot("Pronto: os canais sem sinal ficam escondidos.") })) }
        },
        "Como adiciono minha lista IPTV?" to {
            me("Como adiciono minha lista IPTV?")
            bot("1. Vá em Configurações › Listas e formatos.\n2. O jeito mais fácil: abra no celular o endereço da caixa roxa \"Enviar pelo celular\" (mesmo Wi-Fi da TV) e cole o link da lista.\n3. Ou aperte \"+ Adicionar lista\" e digite o link no teclado da TV.\nSe for uma conta Xtream (servidor, usuário e senha), use Configurações › Contas IPTV.",
                listOf("Abrir Listas e formatos" to { st.cfgSel = "listas"; st.page = "config" }))
        },
        "Melhorar a imagem para futebol" to {
            me("Melhorar a imagem para futebol")
            PP.preset = "esporte"; PP.aiSharp = true; if (PP.aiUp == "off") PP.aiUp = "auto"; PP.matchHz = true
            bot("Feito: modo de imagem Esporte (mais cor e contraste), nitidez Forte, IA de imagem ligada e a tela na mesma frequência do jogo (movimento mais liso). Para voltar, use o botão Imagem durante o vídeo.")
        },
        "O som está atrasado ou adiantado" to {
            me("O som está atrasado ou adiantado")
            bot("Durante o vídeo, aperte ▼ para ver os botões e abra \"⏱ Sincronia\". Use + para atrasar o som e − para adiantar, de 50 em 50 ms, até a boca bater com a fala. O ajuste fica salvo. Agora está em ${PP.avMs} ms.")
        },
        "Como está a IA de imagem neste aparelho?" to {
            me("Como está a IA de imagem neste aparelho?")
            val r = GpuProbe.result
            bot(if (r == null) "O diagnóstico ainda não rodou. Abra Configurações › IA de imagem e aperte \"Testar o aparelho de novo\"."
                else listOf("Placa de vídeo: ${r.gpu}", "Reconstruir 1080p: " + (if (r.ms1080 < 99f) "%.1f ms por quadro".format(r.ms1080) else "não suportado"), "Reconstruir 1440p: " + (if (r.ms1440 < 99f) "%.1f ms por quadro".format(r.ms1440) else "não suportado"),
                    "IA no chip da TV: " + if (ImageAi.hwAi) "sim" else "não", "Painel: " + if (ImageAi.panelHva()) "HVA (VA)" else "outro",
                    "IA de imagem: " + (if (PP.aiUp == "off") "desligada" else "ligada em filmes e séries (estilo ${COLOR_STYLES[PP.colorStyle]})")).joinToString("\n"))
        },
    )

    val chat: @Composable (Modifier) -> Unit = { mod ->
        LazyColumn(
            mod.clip(RoundedCornerShape(16.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(16.dp)),
            state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(msgs) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.fillMaxWidth(if (m.me) .8f else .95f).clip(RoundedCornerShape(14.dp)).background(if (m.me) C.a1.copy(alpha = .25f) else C.panel2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(m.text, 13, C.fg, if (m.me) FontWeight.SemiBold else FontWeight.Medium, lineHeight = 19)
                        m.actions.forEach { (l, a) -> Btn(l, locked = locked, primary = true) { a() } }
                    }
                }
            }
        }
    }
    if (compact) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { quick.forEach { (q, run) -> Btn(q, locked = locked) { run() } } }
            chat(Modifier.weight(1f).fillMaxWidth())
        }
        return
    }
    Row(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 56.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.width(280.dp).verticalScroll(rememberScrollState()).focusGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("Assistente", 30, C.fg, FontWeight.ExtraBold)
            T("Diagnóstico e ajuda feitos aqui no aparelho.", 12, C.muted)
            Spacer(Modifier.height(4.dp))
            quick.forEach { (q, run) -> Btn(q, locked = locked) { run() } }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(16.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(16.dp)),
            state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(msgs) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.me) Arrangement.End else Arrangement.Start) {
                    Column(Modifier.width(if (m.me) 300.dp else 460.dp).clip(RoundedCornerShape(14.dp)).background(if (m.me) C.a1.copy(alpha = .25f) else C.panel2).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(m.text, 13, C.fg, if (m.me) FontWeight.SemiBold else FontWeight.Medium, lineHeight = 19)
                        m.actions.forEach { (l, a) -> Btn(l, locked = locked, primary = true) { a() } }
                    }
                }
            }
        }
    }
}
