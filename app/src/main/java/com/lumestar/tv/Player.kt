package com.lumestar.tv

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.view.Gravity
import android.view.TextureView
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.view.KeyEvent as AKey
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C as MC
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.delay
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

/** O que tocar: canal ao vivo, filme, episódio ou transmissão de rede. */
class PlayReq(val id: String, val title: String, val url: String, val live: Boolean, val start: Long, val group: String, val ext: String = "", val cacheMs: Int = -1, val teste: Boolean = false)

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

private const val UA_APP = "LumeStarTV/0.3 (Linux; Android) ExoPlayerLib/1.3.1"
private const val UA_VLC = "VLC/3.0.20 LibVLC/3.0.20"

val PRESETS = linkedMapOf("padrao" to "Padrão", "vivido" to "Vívido", "cinema" to "Cinema", "esporte" to "Esporte", "noturno" to "Noturno", "pers" to "Personalizado")
private val PRESET_V = mapOf("padrao" to intArrayOf(0, 0, 0), "vivido" to intArrayOf(5, 15, 25), "cinema" to intArrayOf(-5, 10, -8), "esporte" to intArrayOf(5, 10, 15), "noturno" to intArrayOf(-15, -5, -10))
val ENHANCE = linkedMapOf("off" to "Desligado", "auto" to "Auto", "baixa" to "Baixo", "media" to "Médio", "alta" to "Alto", "ultra" to "Ultra")
val AUDIO_AI = linkedMapOf("off" to "Desligado", "auto" to "Automático", "dialogo" to "Diálogos nítidos", "cinema" to "Cinema", "noturno" to "Modo noturno")
val EQS = linkedMapOf("padrao" to "Padrão", "voz" to "Voz", "filme" to "Filme", "musica" to "Música", "esporte" to "Esporte", "noturno" to "Noturno")
private val EQ_V = mapOf("padrao" to intArrayOf(0, 0, 0), "voz" to intArrayOf(-3, 4, 1), "filme" to intArrayOf(4, 0, 2), "musica" to intArrayOf(3, 0, 3), "esporte" to intArrayOf(1, 2, 3), "noturno" to intArrayOf(-4, 2, 0))
val FITS = listOf("Original", "Preencher (sem faixas)", "Zoom 120%")
val PANELS = linkedMapOf("auto" to "Automático", "hva" to "HVA / VA (TCL)", "outro" to "IPS / OLED / outro")
val AI_UP = linkedMapOf("auto" to "Ligada", "off" to "Desligada")
val COLOR_STYLES = linkedMapOf("fiel" to "Fiel à realidade", "vivido" to "Vívido", "cinema" to "Cinema")
val ONOFF = linkedMapOf("1" to "Ligada", "0" to "Desligada")
val QUALITY = linkedMapOf("auto" to "Automática", "480" to "480p", "720" to "720p", "1080" to "1080p", "2160" to "2160p")

/** Filtro de cor do vídeo: modo de imagem + brilho, contraste e cor + realce. */
fun videoMatrix(): ColorMatrix {
    val p = PRESET_V[PP.preset]
    val lvl = when (PP.enhance) { "off" -> 0; "baixa" -> 1; "media", "auto" -> 2; "alta" -> 3; "ultra" -> 4; else -> 2 }
    val b = (p?.get(0) ?: PP.bright) + lvl
    val c = (p?.get(1) ?: PP.contrast) + lvl * 2.5f
    val s = (p?.get(2) ?: PP.sat) + lvl * 5
    val m = ColorMatrix(); m.setSaturation(1 + s / 100f)
    val k = 1 + c / 100f; val t = (1 - k) * 128f + b * 2.55f
    m.postConcat(ColorMatrix(floatArrayOf(k, 0f, 0f, 0f, t, 0f, k, 0f, 0f, t, 0f, 0f, k, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    return m
}

/** Equalizador e nivelador de volume ligados ao áudio do reprodutor. */
class AudioFx(session: Int) {
    private val eq: Equalizer? = try { Equalizer(0, session).apply { enabled = true } } catch (e: Throwable) { null }
    private val dp: DynamicsProcessing? = if (Build.VERSION.SDK_INT >= 28) try {
        val cfg = DynamicsProcessing.Config.Builder(DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2, false, 0, false, 0, false, 0, true).build()
        DynamicsProcessing(0, session, cfg)
    } catch (e: Throwable) { null } else null

    fun apply() {
        val e = EQ_V[PP.eq] ?: intArrayOf(0, 0, 0)
        var bass = e[0] + PP.bass; var mid = e[1]; var tre = e[2] + PP.treble
        when (PP.audioAi) { "dialogo" -> mid += 4; "cinema" -> { bass += 3; tre += 2 }; "noturno" -> bass -= 3; "auto" -> mid += 1 }
        eq?.let { q ->
            try {
                val n = q.numberOfBands.toInt(); val r = q.bandLevelRange; val lo = r[0].toInt(); val hi = r[1].toInt()
                for (i in 0 until n) {
                    val db = when { i == 0 -> bass; i == n - 1 -> tre; i <= n / 3 -> (bass + mid) / 2; i >= n * 2 / 3 -> (tre + mid) / 2; else -> mid }
                    q.setBandLevel(i.toShort(), (db * 100).coerceIn(lo, hi).toShort())
                }
            } catch (e2: Throwable) {}
        }
        if (Build.VERSION.SDK_INT >= 28) dp?.let { d ->
            try {
                val night = PP.audioAi == "noturno"
                val on = night || PP.level || PP.audioAi == "auto"
                d.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, on, 0, 1f, 60f, if (night) 20f else 10f, if (night) -24f else -12f, if (night) 10f else 6f))
                d.enabled = on
            } catch (e2: Throwable) {}
        }
    }

    fun release() { try { eq?.release() } catch (e: Throwable) {}; try { dp?.release() } catch (e: Throwable) {} }
}

class TrackOpt(val label: String, val group: Tracks.Group, val index: Int, val selected: Boolean)

private fun langName(code: String?): String = if (code.isNullOrBlank() || code == "und") "" else Locale(code).getDisplayLanguage(Locale("pt", "BR")).replaceFirstChar { it.uppercase() }

@OptIn(UnstableApi::class)
private fun tracksOf(t: Tracks, type: Int): List<TrackOpt> {
    val out = ArrayList<TrackOpt>()
    for (g in t.groups) if (g.type == type) for (i in 0 until g.length) {
        if (!g.isTrackSupported(i)) continue
        val f = g.getTrackFormat(i)
        val parts = listOf(f.label ?: "", langName(f.language), if (type == MC.TRACK_TYPE_AUDIO && f.channelCount > 0) (if (f.channelCount >= 6) "5.1" else if (f.channelCount == 2) "Estéreo" else "${f.channelCount} canais") else "").filter { it.isNotBlank() }.distinct()
        out.add(TrackOpt(parts.joinToString(" · ").ifBlank { "Faixa ${out.size + 1}" }, g, i, g.isTrackSelected(i)))
    }
    return out
}

@OptIn(UnstableApi::class)
private class Engine(ctx: android.content.Context, cacheMs: Int) {
    val ok: OkHttpDataSource.Factory
    val ds: DefaultDataSource.Factory
    val msf: DefaultMediaSourceFactory
    val player: ExoPlayer
    val avDelay = AvDelay()
    init {
        val b = OkHttpClient.Builder().proxy(PP.proxy()).followRedirects(true).followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        if (PP.proxyType == "http" && PP.proxyUser.isNotBlank()) {
            val cred = Credentials.basic(PP.proxyUser, PP.proxyPass)
            b.proxyAuthenticator { _, resp -> resp.request.newBuilder().header("Proxy-Authorization", cred).build() }
        }
        ok = OkHttpDataSource.Factory(b.build()).setUserAgent(UA_APP)
        ds = DefaultDataSource.Factory(ctx, ok)
        // mais paciência com servidores instáveis: cada pedaço de vídeo tenta de novo até 6 vezes antes de dar erro
        msf = DefaultMediaSourceFactory(ds).setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(6))
        val cache = cacheMs.coerceIn(300, 10000)
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(maxOf(15000, cache * 3), 50000, cache, maxOf(1000, cache * 2)).build()
        val rf = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink? =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<androidx.media3.common.audio.AudioProcessor>(avDelay))
                    .build()
        }
        avDelay.targetMs = PP.avMs
        player = ExoPlayer.Builder(ctx, rf).setMediaSourceFactory(msf).setLoadControl(load).also { b -> if (PP.fullQuality) b.setBandwidthMeter(Bandwidth.get(ctx)) }.build()
        applyQuality()
    }

    fun applyQuality() {
        val p = player.trackSelectionParameters.buildUpon()
        when (PP.quality) {
            "480" -> p.setMaxVideoSize(854, 480); "720" -> p.setMaxVideoSize(1280, 720)
            "1080" -> p.setMaxVideoSize(1920, 1080); "2160" -> p.setMaxVideoSize(3840, 2160)
            else -> p.clearVideoSizeConstraints()
        }
        player.trackSelectionParameters = p.build()
    }

    /** Liga quando um endereço sem ".m3u8" se revela uma lista HLS (comum em canais ao vivo). */
    var forcarHls = false

    fun item(url: String, subUrl: String?): MediaItem {
        val low = url.lowercase()
        val b = MediaItem.Builder().setUri(url)
        when {
            forcarHls -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            low.contains(".m3u8") -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            low.contains(".mpd") -> b.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        if (!subUrl.isNullOrBlank()) {
            val mime = if (subUrl.lowercase().contains(".vtt")) MimeTypes.TEXT_VTT else if (subUrl.lowercase().contains(".ass") || subUrl.lowercase().contains(".ssa")) MimeTypes.TEXT_SSA else MimeTypes.APPLICATION_SUBRIP
            b.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subUrl)).setMimeType(mime).setLanguage("pt").setLabel("Legenda carregada").setSelectionFlags(MC.SELECTION_FLAG_DEFAULT).build()))
        }
        return b.build()
    }

    private var ultimo = ""
    private var comCabecalho = false
    fun load(url: String, subUrl: String?, audioUrl: String?, startMs: Long) {
        if (url != ultimo) { forcarHls = false; ultimo = url }
        // cabeçalhos próprios do canal (url|User-Agent=...&Referer=...): valem só para este vídeo
        val e = Cabecalhos.separa(url)
        if (e.cab.isNotEmpty()) {
            e.userAgent?.let { ok.setUserAgent(it) }
            ok.setDefaultRequestProperties(e.outros)
            comCabecalho = true
        } else if (comCabecalho) {
            ok.setDefaultRequestProperties(emptyMap()); ok.setUserAgent(UA_APP); comCabecalho = false
        }
        val main = msf.createMediaSource(item(e.url, subUrl))
        val src = if (!audioUrl.isNullOrBlank()) MergingMediaSource(main, ProgressiveMediaSource.Factory(ds).createMediaSource(MediaItem.fromUri(audioUrl))) else main
        player.setMediaSource(src, startMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = true
    }
}

/**
 * Reprodutor completo. Com os botões escondidos: OK pausa, ◀ ▶ voltam/avançam 10 s, ▲ ▼ mostram os botões.
 * Com os botões à mostra: navegue e abra os painéis Imagem, Tela, Som, Legendas e Áudio. Voltar fecha painel, botões e depois o vídeo.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(st: AppState, req: PlayReq, touchMode: Boolean = false) {
    val ctx = LocalContext.current
    val eng = remember { Engine(ctx, if (req.cacheMs > 0) req.cacheMs else PP.cacheMs) }
    val player = eng.player
    var triedVlc by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(req.url) }
    var subUrl by remember { mutableStateOf<String?>(null) }
    var audioUrl by remember { mutableStateOf<String?>(null) }
    var triedTs by remember { mutableStateOf(false) }
    var rapidas by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableStateOf(true) }
    var touch by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var startAt by remember { mutableLongStateOf(req.start) }
    var vol by remember { mutableFloatStateOf(1f) }
    // conversor nativo: "oferta" = o formato não toca direto; "convertendo" = FFmpeg trabalhando
    var convOferta by remember { mutableStateOf<String?>(null) } // "video" ou "audio"
    var convendo by remember { mutableStateOf(false) }
    var convSeg by remember { mutableIntStateOf(0) }
    val urlOriginal = remember { req.url }
    fun converter() {
        val modo = convOferta ?: return
        convOferta = null; convendo = true; convSeg = 0; err = null; wait = 0
        Conversor.iniciar(ctx, urlOriginal, req.live, modo == "audio",
            progresso = { sgs -> convSeg = sgs },
            pronto = { local -> scope.launch { convendo = false; startAt = 0L; url = local; attempt++ } },
            erro = { m -> scope.launch { convendo = false; err = m; wait = 0 } })
    }
    DisposableEffect(Unit) { onDispose { Conversor.parar() } }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var vsize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var osd by remember { mutableStateOf("") }
    var osdId by remember { mutableIntStateOf(0) }
    var wantPlay by remember { mutableIntStateOf(1) }
    var aiLabel by remember { mutableStateOf("") }
    var cmpOn by remember { mutableStateOf(false) }
    // estado da IA de imagem neste vídeo: cap = limite do disjuntor (2 Ultra, 1 Alta, 0 sem reforço)
    val ai = remember { object { var key = ""; var cap = 2; var hadFx = false; var compare = false; var lost = 0L; var stable = 0; var tier = 0; var outH = 0; var hot = 0; var bestOn = false; var buffered = false; val hist = LongArray(10); var idx = 0 } }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val holder = remember { arrayOfNulls<Any>(4) } // [container, textureView, subtitleView, audioFx]
    fun say(m: String) { osd = m; osdId++ }
    fun tv() = holder[1] as? TextureView
    fun vview() = holder[1] as? View
    // vídeo direto (camada de vídeo da TV) ou clássico (dentro da tela do app); muda sozinho se o direto falhar
    var direto by remember { mutableStateOf(VideoOut.direto()) }
    var testeMsg by remember { mutableStateOf("") }
    var testeReserva by remember { mutableIntStateOf(0) }
    fun container() = holder[0] as? FrameLayout
    fun fx() = holder[3] as? AudioFx

    fun save() { if (!req.live) Store.setProgress(req.id, player.currentPosition, player.duration.takeIf { it > 0 } ?: 0L) }

    fun layoutVideo() {
        val c = container() ?: return; val v = vview() ?: return
        val W = c.width.toFloat(); val H = c.height.toFloat()
        if (W <= 0 || H <= 0) return
        val va = if (vsize.width > 0 && vsize.height > 0) vsize.width * vsize.pixelWidthHeightRatio / vsize.height else 16f / 9f
        var w = W; var h = W / va
        if (h > H) { h = H; w = H * va }
        val u = when (PP.fit) { 1 -> maxOf(W / w, H / h); 2 -> 1.2f; else -> 1f }
        val sx = u * (1 + PP.fitH / 100f); val sy = u * (1 + PP.fitV / 100f)
        // SurfaceView não aceita escala de View: no modo direto o zoom vira tamanho (a moldura corta o excesso)
        val lw = if (v is SurfaceView) (w * sx).toInt() else w.toInt(); val lh = if (v is SurfaceView) (h * sy).toInt() else h.toInt()
        val lp = v.layoutParams as FrameLayout.LayoutParams
        if (lp.width != lw || lp.height != lh) { lp.width = lw; lp.height = lh; lp.gravity = Gravity.CENTER; v.layoutParams = lp }
        if (v is TextureView) { v.scaleX = sx; v.scaleY = sy }
    }

    fun applySubStyle() {
        val sv = holder[2] as? SubtitleView ?: return
        val fg = if (PP.subColor == 1) 0xFFFFE14D.toInt() else 0xFFFFFFFF.toInt()
        val bg = if (PP.subBg == 2) 0xB0000000.toInt() else 0
        val edge = if (PP.subBg == 1) CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW else if (PP.subBg == 0) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_NONE
        sv.setStyle(CaptionStyleCompat(fg, bg, 0, edge, 0xFF000000.toInt(), null))
        sv.setFractionalTextSize(when (PP.subSize) { 0 -> .045f; 2 -> .075f; else -> .058f })
    }

    /** IA de imagem (só filmes e séries): nitidez leve + realce de cor por cena, na resolução do vídeo. Ao vivo vai direto. */
    fun sceneTag() = if (!req.live && SceneAi.on && SceneAi.scene.isNotBlank()) " · cena: ${SceneAi.scene}" else ""
    fun applyAi() {
        SceneAi.on = !req.live && PP.aiUp != "off" && ai.cap > 0
        ImageAi.direto = direto
        val plan = ImageAi.plan(PP.aiUp, player.videoFormat, ai.cap, ai.compare, req.live)
        aiLabel = plan.label + sceneTag()
        ai.tier = plan.tier; ai.outH = plan.outH
        // modo direto: brilho/contraste/cor entram como mais um efeito (só filmes e séries; ao vivo segue direto)
        val cm = videoMatrix()
        val comCor = direto && !req.live && plan.key != "wait" && !VideoOut.identidade(cm)
        val key = plan.key + (if (comCor) "|" + cm.array.contentHashCode() else "") + (if (direto) "|d" else "|c")
        if (key == ai.key) return
        ai.key = key
        val fx = plan.effects + (if (comCor) listOf(VideoOut.cor(cm)) else emptyList())
        (vview() as? SurfaceView)?.let { sv ->
            // com efeitos, a placa desenha na resolução de saída da IA (até a do painel); sem efeitos, o decodificador manda direto
            val vh = player.videoFormat?.height ?: 0
            val oh = if (plan.outH > 0) plan.outH else vh
            if (fx.isNotEmpty() && oh > 0) {
                val ar = if (vsize.width > 0 && vsize.height > 0) vsize.width * vsize.pixelWidthHeightRatio / vsize.height else 16f / 9f
                sv.holder.setFixedSize(((oh * ar).toInt() / 2) * 2, oh)
            } else sv.holder.setSizeFromLayout()
        }
        if (fx.isEmpty() && !ai.hadFx) return
        try { player.setVideoEffects(fx); ai.hadFx = fx.isNotEmpty() || ai.hadFx } catch (e: Throwable) { ai.cap = 0; SceneAi.on = false; aiLabel = "" }
    }

    fun applyPicture() {
        if (direto) { ai.key = "-"; applyAi() } // no direto o brilho/contraste/cor vai junto com a IA, na placa
        else tv()?.setLayerPaint(Paint().apply { colorFilter = ColorMatrixColorFilter(videoMatrix()) })
        layoutVideo()
    }

    fun applyAiLabel() { aiLabel = ImageAi.plan(PP.aiUp, player.videoFormat, ai.cap, ai.compare, req.live).label + sceneTag() }

    fun stepDown(why: String) {
        if (ai.cap <= 0 || ai.tier <= 0) return
        ai.cap = ai.tier - 1; ai.stable = 0; ai.hist.fill(0L); applyAi()
        say(if (ai.cap >= 1) "$why: IA voltou para a resolução do vídeo" else "$why: IA de imagem pausada neste vídeo")
    }

    /** Disjuntor (a cada 1 s): quadros perdidos em 10 s e temperatura do chip; sobe de novo após 60 s estável. */
    fun watchDrops() {
        val dc = player.videoDecoderCounters ?: return
        dc.ensureUpdated()
        val now = dc.droppedBufferCount.toLong() + dc.skippedOutputBufferCount
        val old = ai.hist[ai.idx]; ai.hist[ai.idx] = now; ai.idx = (ai.idx + 1) % ai.hist.size
        ai.lost = if (old in 1..now) now - old else 0L
        if (!player.isPlaying) return
        if (ai.tier > 0 && ai.lost > 24) { stepDown("A TV estava perdendo quadros"); return }
        if (Build.VERSION.SDK_INT >= 29) {
            val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            ai.hot = pm.currentThermalStatus
            if (ai.tier > 0 && ai.hot >= android.os.PowerManager.THERMAL_STATUS_MODERATE) { stepDown("O chip esquentou"); return }
        }
        if (ai.lost <= 2 && ai.hot < 2) ai.stable++ else ai.stable = 0
        if (ai.stable >= 60 && ai.cap < 2 && PP.aiUp != "off") { ai.cap++; ai.stable = 0; ai.hist.fill(0L); applyAi() }
        // qualidade cheia: pede a melhor versão do vídeo; se a internet engasgar, volta para o automático
        if (PP.fullQuality || (ai.tier == 0 && PP.aiUp != "off")) {
            if (player.playbackState == Player.STATE_BUFFERING) { ai.buffered = true; if (ai.bestOn) { ai.bestOn = false; player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setForceHighestSupportedBitrate(false).build() } }
            else if (!ai.bestOn && !ai.buffered && (ai.stable >= 20 || (PP.fullQuality && pos < 15000)) && tracks.groups.count { it.type == MC.TRACK_TYPE_VIDEO && it.length > 1 } > 0) {
                ai.bestOn = true; player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setForceHighestSupportedBitrate(true).build()
            }
        }
    }

    /** Frequência da tela igual à do vídeo (24/25/30/50/60 Hz), quando a TV aceita; tira os trancos de movimento. */
    fun matchRate() {
        if (!PP.matchHz || Build.VERSION.SDK_INT < 23) return
        val fps = player.videoFormat?.frameRate ?: return
        if (fps <= 0f) return
        val act = ctx as? android.app.Activity ?: return
        val d = act.windowManager.defaultDisplay; val cur = d.mode
        val best = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .firstOrNull { m -> val k = m.refreshRate / fps; kotlin.math.abs(k - Math.round(k)) < 0.01f && Math.round(k) in 1..2 } ?: return
        if (best.modeId != cur.modeId) act.window.attributes = act.window.attributes.also { it.preferredDisplayModeId = best.modeId }
    }

    fun medirFaixas(bmp: android.graphics.Bitmap) {
        fun dark(x: Int, y: Int): Boolean { val p = bmp.getPixel(x, y); return (((p shr 16) and 255) * 3 + ((p shr 8) and 255) * 6 + (p and 255)) / 10 < 20 }
        fun rowDark(y: Int) = (0 until 96 step 4).count { dark(it, y) } >= 22
        fun colDark(x: Int) = (0 until 54 step 3).count { dark(x, it) } >= 17
        var top = 0; while (top < 20 && rowDark(top)) top++
        var bot = 0; while (bot < 20 && rowDark(53 - bot)) bot++
        var lef = 0; while (lef < 36 && colDark(lef)) lef++
        var rig = 0; while (rig < 36 && colDark(95 - rig)) rig++
        val fv = (top + bot) / 54f; val fh = (lef + rig) / 96f
        PP.fit = 0
        PP.fitV = if (fv > .04f) ((1 / (1 - fv) - 1) * 100).toInt().coerceAtMost(40) else 0
        PP.fitH = if (fh > .04f) ((1 / (1 - fh) - 1) * 100).toInt().coerceAtMost(40) else 0
        layoutVideo()
        say(if (PP.fitV == 0 && PP.fitH == 0) "Este vídeo não tem faixas pretas" else "Faixas removidas: " + listOfNotNull(if (PP.fitV > 0) "cima e baixo +${PP.fitV}%" else null, if (PP.fitH > 0) "laterais +${PP.fitH}%" else null).joinToString(" · "))
    }

    /** Tira as faixas pretas: olha uma foto pequena do vídeo e mede as bordas escuras. */
    fun autoBars() {
        var achou = false
        VideoOut.foto(vview(), 96, 54) { b -> achou = true; medirFaixas(b) }
        if (!achou && vview() is TextureView) say("Ainda sem imagem para medir")
    }


    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                buffering = s == Player.STATE_BUFFERING
                if (s == Player.STATE_BUFFERING && dur >= 0 && pos > 0) PlayerDiag.rebuffers++
                if (s == Player.STATE_READY) { err = null; rapidas = 0; if (url.startsWith("file:")) android.util.Log.i("LumeStar", "conversor: tocando o vídeo convertido"); dur = player.duration.coerceAtLeast(0); matchRate() }
                if (s == Player.STATE_ENDED && !req.live) { save(); st.closePlayer() }
            }
            override fun onIsPlayingChanged(p: Boolean) { playing = p }
            override fun onTracksChanged(t: Tracks) {
                if (!url.startsWith("file:") && !convendo && Conversor.disponivel()) {
                    fun semSuporte(tipo: Int) = t.groups.any { it.type == tipo } && t.groups.none { it.type == tipo && it.isSupported }
                    if (semSuporte(MC.TRACK_TYPE_VIDEO)) convOferta = "video"
                    else if (semSuporte(MC.TRACK_TYPE_AUDIO)) convOferta = "audio"
                }
                val hadExt = audioUrl != null && tracksOf(tracks, MC.TRACK_TYPE_AUDIO).size < tracksOf(t, MC.TRACK_TYPE_AUDIO).size
                tracks = t
                if (hadExt) tracksOf(t, MC.TRACK_TYPE_AUDIO).lastOrNull()?.let { o ->
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index)).build()
                }
            }
            override fun onVideoSizeChanged(v: VideoSize) { vsize = v; layoutVideo(); applyAi() }
            override fun onCues(c: CueGroup) { (holder[2] as? SubtitleView)?.setCues(c.cues) }
            override fun onAudioSessionIdChanged(id: Int) { fx()?.release(); holder[3] = AudioFx(id).also { it.apply() } }
            override fun onPlayerError(e: PlaybackException) {
                if (req.teste && e.errorCode in 2000..3999 && testeReserva < VideoOut.TESTE_RESERVA.size) { url = VideoOut.TESTE_RESERVA[testeReserva++]; attempt++; return }
                if (direto && (e.errorCode in 7000..7999 || e.errorCode in 4000..4999)) {
                    // vídeo direto não funcionou nesta TV: volta para o clássico e lembra para os próximos vídeos
                    direto = false; if (PP.videoMode == "auto") PP.diretoFalhou = true; ai.key = ""
                    Reporter.evento(ctx, "video-direto", "Vídeo direto falhou (${e.errorCodeName}); voltou para o clássico", e)
                    startAt = player.currentPosition; attempt++; say("Vídeo direto falhou nesta TV: voltei para o modo clássico"); return
                }
                if (e.errorCode in 7000..7999 && ai.cap > 0) { ai.cap = 0; SceneAi.on = false; ai.key = ""; try { player.setVideoEffects(emptyList()) } catch (x: Throwable) {}; startAt = player.currentPosition; attempt++; say("Reforço de imagem desligado neste vídeo (a TV não aguentou)"); return }
                val bad = e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                if (bad && !triedVlc) { triedVlc = true; eng.ok.setUserAgent(UA_VLC); startAt = player.currentPosition; attempt++; return }
                if (req.live && !triedTs && url.contains(".m3u8") && req.ext == "ts") { triedTs = true; url = url.replace(".m3u8", ".ts"); attempt++; return }
                // ao vivo ficou para trás da janela da transmissão: volta para o ponto ao vivo sem mostrar erro
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) { player.seekToDefaultPosition(); player.prepare(); return }
                // endereço sem extensão que na verdade é HLS: tenta de novo como HLS
                if (!eng.forcarHls && e.errorCode in 3000..3999 && !Cabecalhos.base(url).lowercase().contains(".m3u8") && !Cabecalhos.base(url).lowercase().contains(".mpd")) { eng.forcarHls = true; startAt = player.currentPosition; attempt++; return }
                // queda rápida de rede: até 3 novas tentativas silenciosas (2 s, 4 s, 6 s) antes de mostrar o aviso
                if (e.errorCode in 2000..2999 && rapidas < 3) {
                    rapidas++; val pos = player.currentPosition
                    scope.launch { delay(2000L * rapidas); startAt = pos; attempt++ }
                    return
                }
                // formato que a TV não decodifica: oferece converter (em vez de ficar tentando de novo)
                val formato = e.errorCode in 4000..4999 || e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                if (formato && !url.startsWith("file:") && Conversor.disponivel()) { convOferta = "video"; err = null; wait = 0; return }
                PlayerDiag.lastError = e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' '); PlayerDiag.lastTitle = req.title; PlayerDiag.lastAt = System.currentTimeMillis()
                err = "Não foi possível reproduzir (${e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')})."
                wait = 30
            }
        }
        player.addListener(l)
        onDispose {
            save(); player.removeListener(l); fx()?.release(); player.release()
            (ctx as? android.app.Activity)?.window?.let { w -> if (w.attributes.preferredDisplayModeId != 0) w.attributes = w.attributes.also { it.preferredDisplayModeId = 0 } }
        }
    }
    // Ao sair do app (Home, outro app), vídeo e canal PARAM o som; só a rádio continua tocando em segundo plano.
    val ciclo = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) { save(); player.pause() }
        }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { PlayerDiag.rebuffers = 0; PlayerDiag.lastTitle = req.title; SceneAi.reset(!req.live && PP.aiUp != "off") }
    LaunchedEffect(url, attempt, subUrl, audioUrl) { eng.load(url, subUrl, audioUrl, if (req.live) 0L else startAt) }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait--; if (wait == 0) { err = null; startAt = player.currentPosition; attempt++ } } }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) { delay(500); pos = player.currentPosition; if (player.duration > 0) dur = player.duration; if (++n % 10 == 0) save(); if (n % 2 == 0) watchDrops()
            SceneAi.step()
            if (SceneAi.on && n % 4 == 0 && player.isPlaying) VideoOut.foto(vview(), 64, 36) { b -> try { SceneAi.analyze(b); if (!aiLabel.contains(SceneAi.scene)) applyAiLabel() } catch (e: Throwable) {} }
        }
    }
    // Teste de desempenho: o mesmo vídeo no clássico e no direto com IA; mede quadros perdidos e escolhe o modo.
    if (req.teste) LaunchedEffect(Unit) {
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        suspend fun fase(dir: Boolean, nome: String): Pair<Boolean, String> {
            direto = dir; ai.cap = 2; ai.key = ""
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setForceHighestSupportedBitrate(true).build()
            testeMsg = "Teste de desempenho · $nome…"
            var t = 0; while ((player.videoFormat == null || !player.isPlaying) && t < 60) { delay(500); t++ }
            if (player.videoFormat == null) return false to "$nome: o vídeo de teste não tocou"
            delay(4000)
            val c0 = player.videoDecoderCounters; c0?.ensureUpdated(); val r0 = c0?.renderedOutputBufferCount ?: 0; val d0 = c0?.droppedBufferCount ?: 0
            delay(15000)
            val c1 = player.videoDecoderCounters; c1?.ensureUpdated()
            var r = (c1?.renderedOutputBufferCount ?: 0) - r0; var d = (c1?.droppedBufferCount ?: 0) - d0
            if (r < 0 || d < 0) { r = c1?.renderedOutputBufferCount ?: 0; d = c1?.droppedBufferCount ?: 0 }
            val h = player.videoFormat?.height ?: 0
            val quente = if (Build.VERSION.SDK_INT >= 29) pm.currentThermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE else false
            val caiu = dir && !direto
            val ok = !caiu && !quente && r > 0 && d <= maxOf(3, r / 50)
            val saida = if (dir) (if (ai.outH > 0) ai.outH else h) else minOf(h, ImageAi.telaH)
            return ok to "$nome: ${if (ok) "aprovado" else "reprovado"} · vídeo ${h}p → tela ${saida}p · $d de ${r + d} quadros perdidos" +
                (if (quente) " · TV esquentou" else "") + (if (caiu) " · deu erro e voltou ao clássico" else "")
        }
        val a = fase(false, "Clássico")
        val podeDireto = Build.VERSION.SDK_INT >= 24 && !Perf.lite
        val b = if (podeDireto) fase(true, "Direto + IA") else false to "Direto: não disponível neste aparelho"
        val escolha = if (b.first) "Vídeo direto" else "Clássico"
        if (podeDireto) { PP.diretoFalhou = !b.first; if (PP.videoMode != "auto") PP.videoMode = "auto" }
        player.pause()
        val res = "${a.second}\n${b.second}\nModo escolhido: $escolha"
        PP.testeRes = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US).format(java.util.Date()) + " · " + res.replace("\n", " · ")
        Reporter.evento(ctx, "teste-desempenho", res, null)
        testeMsg = "$res\n\nAperte Voltar para sair."
    }
    LaunchedEffect(showUi, touch, panel) {
        if (showUi && panel == null) { delay(5000); showUi = false; try { rootFocus.requestFocus() } catch (e: Exception) {} }
    }
    LaunchedEffect(wantPlay) { repeat(3) { delay(60); try { playFocus.requestFocus(); return@LaunchedEffect } catch (e: Exception) {} } }
    LaunchedEffect(panel) { if (panel != null) { delay(60); try { panelFocus.requestFocus() } catch (e: Exception) {} } }
    LaunchedEffect(osdId) { if (osdId > 0) { delay(2200); osd = "" } }
    LaunchedEffect(PP.rev) { applyPicture(); applySubStyle(); fx()?.apply(); applyAi() }

    BackHandler {
        when {
            panel != null -> { panel = null; wantPlay++ }
            showUi -> { showUi = false; try { rootFocus.requestFocus() } catch (e: Exception) {} }
            else -> { save(); st.closePlayer() }
        }
    }

    // canais vizinhos para trocar sem sair do vídeo: mesma categoria; se ela tiver só um, todos os ao vivo
    val chans = remember(req.id) {
        if (!req.live) emptyList() else {
            val all = Catalog.items.filter { it.kind == LIVE && Catalog.visible(it) && !(PP.offlineMode == "ocultar" && it.id in PP.offline) }
            val g = all.filter { it.group == req.group }
            if (g.size > 1) g else all
        }
    }
    val chanIdx = remember(req.id) { chans.indexOfFirst { it.id == req.id } }
    fun zap(d: Int) {
        if (!req.live || chans.size < 2) return
        val n = chans[((if (chanIdx < 0) 0 else chanIdx) + d + chans.size) % chans.size]
        st.play(PlayReq(n.id, n.title, n.url, true, 0L, n.group, n.ext))
    }

    fun seek(d: Long) { if (!req.live) { player.seekTo((player.currentPosition + d).coerceAtLeast(0)); say((if (d > 0) "⏩ " else "⏪ ") + fmt(player.currentPosition)) } }
    fun showControls() { showUi = true; touch++ }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .then(if (touchMode) touchGestures(ctx, live = req.live && chans.size > 1, zap = { d -> zap(d) }, playerToggle = { if (player.isPlaying) player.pause() else player.play(); showControls() }, tap = { if (showUi) showUi = false else showControls() }, seek = { d -> seek(d) }, fill = { on -> PP.fit = if (on) 1 else 0; layoutVideo(); say(if (on) "⛶ Preencher a tela" else "▭ Original") }, say = { m -> say(m) }) else Modifier)
            .focusRequester(rootFocus).focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                touch++
                val kc = ev.key.nativeKeyCode
                if (convOferta != null && (kc == AKey.KEYCODE_DPAD_CENTER || kc == AKey.KEYCODE_ENTER || kc == AKey.KEYCODE_NUMPAD_ENTER)) { converter(); return@onKeyEvent true }
                if (kc == AKey.KEYCODE_CHANNEL_UP || kc == AKey.KEYCODE_PAGE_UP) { zap(1); return@onKeyEvent true }
                if (kc == AKey.KEYCODE_CHANNEL_DOWN || kc == AKey.KEYCODE_PAGE_DOWN) { zap(-1); return@onKeyEvent true }
                if (showUi) return@onKeyEvent false
                // ao vivo: ▲ próximo canal, ▼ canal anterior
                if (req.live && chans.size > 1) when (kc) {
                    AKey.KEYCODE_DPAD_UP -> { zap(1); return@onKeyEvent true }
                    AKey.KEYCODE_DPAD_DOWN -> { zap(-1); return@onKeyEvent true }
                }
                when (kc) {
                    AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER, AKey.KEYCODE_MEDIA_PLAY_PAUSE -> { if (player.isPlaying) player.pause() else player.play(); showControls(); wantPlay++; true }
                    AKey.KEYCODE_DPAD_LEFT, AKey.KEYCODE_MEDIA_REWIND -> { seek(-10_000); true }
                    AKey.KEYCODE_DPAD_RIGHT, AKey.KEYCODE_MEDIA_FAST_FORWARD -> { seek(10_000); true }
                    AKey.KEYCODE_DPAD_UP, AKey.KEYCODE_DPAD_DOWN, AKey.KEYCODE_INFO, AKey.KEYCODE_MENU -> { showControls(); wantPlay++; true }
                    else -> false
                }
            }
    ) { Compact {
        key(direto) { AndroidView(factory = { c ->
            FrameLayout(c).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                val t: View = if (direto) SurfaceView(c) else TextureView(c)
                addView(t, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                val sv = SubtitleView(c)
                addView(sv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                holder[0] = this; holder[1] = t; holder[2] = sv
                if (t is SurfaceView) player.setVideoSurfaceView(t) else player.setVideoTextureView(t as TextureView)
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> post { layoutVideo() } }
                keepScreenOn = true
                ai.key = ""; applyPicture(); applySubStyle()
            }
        }, modifier = Modifier.fillMaxSize()) }

        if (testeMsg.isNotBlank()) Column(Modifier.align(Alignment.TopCenter).padding(top = 30.dp).width(620.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xEE0E1538)).border(1.dp, C.a1, RoundedCornerShape(16.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("TESTE DE DESEMPENHO", 10, C.a1, FontWeight.Bold)
            T(testeMsg, 14, C.fg, FontWeight.SemiBold, lineHeight = 20)
        }
        if (buffering && err == null && convOferta == null && !convendo) Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xCC050816)).padding(horizontal = 18.dp, vertical = 10.dp)) { T("Carregando…", 14, C.fg, FontWeight.Bold) }
        convOferta?.let { modo ->
            Column(Modifier.align(Alignment.Center).width(520.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xEE0E1538)).border(1.dp, C.a1, RoundedCornerShape(16.dp))
                .then(if (touchMode) Modifier.clickable { converter() } else Modifier).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(if (modo == "audio") "O som deste vídeo está num formato que a TV não toca." else "Este vídeo está num formato que a TV não toca direto.", 15, C.fg, FontWeight.Bold)
                T("O LumeStarTV pode converter aqui mesmo, em até 720p. Demora um pouco para começar.", 13, C.muted, lineHeight = 18)
                Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.horizontalGradient(listOf(C.a1, C.a2))).padding(horizontal = 16.dp, vertical = 9.dp)) {
                    T("OK  ›  Converter para tocar (pode demorar)", 14, Color(0xFF050816), FontWeight.ExtraBold)
                }
                T("Voltar: sair", 11, C.dim)
            }
        }
        if (convendo) Column(Modifier.align(Alignment.Center).width(460.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xEE0E1538)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("Convertendo para a TV tocar…", 15, C.fg, FontWeight.Bold)
            T(if (convSeg > 0) "$convSeg s prontos · começa a tocar com uns 24 s" else "Abrindo o vídeo…", 13, C.muted)
        }
        err?.let { m ->
            Column(Modifier.align(Alignment.Center).width(460.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xEE0E1538)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(m, 15, C.fg, FontWeight.Bold)
                T(if (wait > 0) "Nova tentativa em $wait s. Aperte Voltar para sair." else "Tentando de novo…", 13, C.muted)
            }
        }
        if (cmpOn) {
            Box(Modifier.align(Alignment.TopStart).padding(start = 40.dp, top = 74.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000)).padding(horizontal = 10.dp, vertical = 5.dp)) { T("ORIGINAL", 12, Color.White, FontWeight.ExtraBold) }
            Box(Modifier.align(Alignment.TopEnd).padding(end = 40.dp, top = 74.dp).clip(RoundedCornerShape(8.dp)).background(C.a1.copy(alpha = .85f)).padding(horizontal = 10.dp, vertical = 5.dp)) { T("COM IA", 12, Color(0xFF050816), FontWeight.ExtraBold) }
        }
        if (osd.isNotEmpty()) Box(Modifier.align(Alignment.TopCenter).padding(top = 90.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xDD050816)).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 9.dp)) { T(osd, 13, C.fg, FontWeight.Bold) }

        if (showUi || !playing) {
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 36.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    T((if (req.live) "AO VIVO · " else "") + req.group.uppercase() + (if (req.live && chanIdx >= 0 && chans.size > 1) "  ·  CANAL ${chanIdx + 1} DE ${chans.size}" else ""), 10, if (req.live) C.bad else C.a1, FontWeight.Bold, maxLines = 1)
                    T(req.title, 21, C.fg, FontWeight.ExtraBold, maxLines = 1)
                }
                val v = vsize
                val badges = listOfNotNull(
                    if (v.height > 0) "${v.height}p" else null,
                    aiLabel.ifBlank { null }?.let { "✦ $it" },
                    if (PP.enhance != "off") "Realce ${ENHANCE[PP.enhance]}" else null,
                    if (PP.preset != "padrao") "☀ ${PRESETS[PP.preset]}" else null,
                    if (PP.audioAi != "off") "♪ ${AUDIO_AI[PP.audioAi]}" else null,
                    if (tracksOf(tracks, MC.TRACK_TYPE_TEXT).any { it.selected }) "CC" else null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { badges.forEach { b -> Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF)).padding(horizontal = 10.dp, vertical = 4.dp)) { T(b, 10, C.fg, FontWeight.Bold) } } }
            }
        }
        if (showUi) {
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEB000000)))).padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 18.dp).focusGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    T(if (req.live) "AO VIVO" else fmt(pos), 12, C.fg, FontWeight.Bold)
                    var bf by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f).height(if (bf) 10.dp else 6.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x38FFFFFF))
                        .border(if (bf) 2.dp else 0.dp, if (bf) C.a1 else Color.Transparent, RoundedCornerShape(5.dp))
                        .onFocusChanged { bf = it.isFocused }
                        .onKeyEvent { ev ->
                            if (ev.type == KeyEventType.KeyDown && !req.live && (ev.key.nativeKeyCode == AKey.KEYCODE_DPAD_LEFT || ev.key.nativeKeyCode == AKey.KEYCODE_DPAD_RIGHT)) {
                                seek(if (ev.key.nativeKeyCode == AKey.KEYCODE_DPAD_RIGHT) maxOf(10_000L, dur / 40) else -maxOf(10_000L, dur / 40)); true
                            } else false
                        }.focusable(!req.live)) {
                        val fr = if (req.live) 1f else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                        Box(Modifier.fillMaxWidth(fr).fillMaxHeight().background(if (req.live) Brush.horizontalGradient(listOf(C.bad, C.bad)) else Brush.horizontalGradient(listOf(C.a1, C.a2))))
                    }
                    if (!req.live) T(fmt(dur), 12, C.fg, FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!req.live) Btn("⟲10", locked = false) { seek(-10_000) }
                    if (req.live && chans.size > 1) Btn("◀ Canal", locked = false) { zap(-1) }
                    Box(Modifier.focusRequester(playFocus)) { Btn(if (playing) "❚❚" else "▶", locked = false, primary = true) { if (player.isPlaying) player.pause() else player.play(); touch++ } }
                    if (!req.live) Btn("10⟳", locked = false) { seek(10_000) }
                    if (req.live && chans.size > 1) Btn("Canal ▶", locked = false) { zap(1) }
                    Btn("−", locked = false) { vol = (vol - .1f).coerceAtLeast(0f); player.volume = vol; say("Volume ${(vol * 100).toInt()}") }
                    Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) { Box(Modifier.fillMaxWidth(vol).fillMaxHeight().background(Color.White)) }
                    Btn("+", locked = false) { vol = (vol + .1f).coerceAtMost(1f); player.volume = vol; say("Volume ${(vol * 100).toInt()}") }
                    Spacer(Modifier.width(4.dp))
                    listOf("imagem" to "☀ Imagem", "tela" to "⛶ Tela", "som" to "♪ Som", "legendas" to "CC Legendas", "audio" to "🗣 Áudio", "sync" to "⏱ Sincronia").forEach { (k, l) ->
                        Btn(l, on = panel == k, locked = false) { panel = if (panel == k) null else k }
                    }
                    Spacer(Modifier.weight(1f))
                    Btn("✕", locked = false) { save(); st.closePlayer() }
                }
            }
        }
        panel?.let { p ->
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 18.dp, bottom = 60.dp).width(360.dp).height(330.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xF50A0E28)).border(1.dp, C.line, RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState()).padding(16.dp).focusGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val r = PP.rev // relê os ajustes quando mudam
                if (r < 0) T("", 1)
                when (p) {
                    "imagem" -> {
                        T("☀ Imagem", 16, C.fg, FontWeight.Bold)
                        Box(Modifier.focusRequester(panelFocus)) { Opts("Modo de imagem", PRESETS, PP.preset) { v -> PP.preset = v; say("Imagem: ${PRESETS[v]}") } }
                        if (PP.preset == "pers") {
                            Stepper("Brilho", PP.bright, "") { d -> PP.bright = (PP.bright + d * 5).coerceIn(-50, 50) }
                            Stepper("Contraste", PP.contrast, "") { d -> PP.contrast = (PP.contrast + d * 5).coerceIn(-50, 50) }
                            Stepper("Cor (saturação)", PP.sat, "") { d -> PP.sat = (PP.sat + d * 5).coerceIn(-50, 50) }
                        } else T("Escolha \"Personalizado\" para ajustar brilho, contraste e cor.", 11, C.dim)
                        if (req.live) T("TV ao vivo: a imagem vai direto para a TV (IA e movimento da própria TV), para trocar de canal na hora. A IA do app atua em filmes e séries.", 11, C.dim, lineHeight = 15)
                        else {
                            Opts("IA de imagem", AI_UP, if (PP.aiUp == "off") "off" else "auto") { v -> PP.aiUp = v; ai.cap = 2; ai.key = "-"; applyAi(); say("IA de imagem: ${AI_UP[v]}") }
                            Btn(if (cmpOn) "◐ Comparar: ligado (original | IA)" else "◐ Comparar: tela dividida", on = cmpOn, locked = false) { ai.compare = !ai.compare; cmpOn = ai.compare; ai.key = "-"; applyAi(); say(if (cmpOn) "Esquerda: original · Direita: com IA" else "Comparação desligada") }
                            Opts("Estilo de cor", COLOR_STYLES, PP.colorStyle) { v -> PP.colorStyle = v; applyAiLabel(); say("Estilo: ${COLOR_STYLES[v]}") }
                            Opts("Nitidez leve", ONOFF, if (PP.aiSharp) "1" else "0") { v -> PP.aiSharp = v == "1"; ai.key = "-"; applyAi(); say("Nitidez leve: ${ONOFF[v]}") }
                        }
                        Opts("Painel da TV", PANELS, PP.panel) { v -> PP.panel = v; say("Painel: ${PANELS[v]}") }
                        val f = player.videoFormat
                        T(listOfNotNull(
                            f?.let { "Entrada ${it.width}×${it.height}" + if (it.frameRate > 0) " · ${Math.round(it.frameRate)} quadros/s" else "" },
                            if (ai.outH > 0) "Saída ${ai.outH}p" else null,
                            "Perdidos: ${ai.lost} em 10 s",
                            if (ai.hot >= 2) "Chip quente" else null,
                        ).joinToString(" · ") + if (aiLabel.isNotBlank()) "\nAgora: $aiLabel" else "", 11, C.a1, FontWeight.SemiBold, lineHeight = 15)
                        Opts("Realce de imagem", ENHANCE, PP.enhance) { v -> PP.enhance = v }
                        Opts("Qualidade máxima", QUALITY, PP.quality) { v -> PP.quality = v; eng.applyQuality(); say("Qualidade: ${QUALITY[v]}") }
                        T("Vale para todos os vídeos e fica salvo. O realce aumenta contraste e cor de forma leve, sem pesar o aparelho.", 11, C.dim)
                    }
                    "tela" -> {
                        T("⛶ Ajuste de tela", 16, C.fg, FontWeight.Bold)
                        val v = vsize
                        T(if (v.width > 0) "Vídeo ${v.width} × ${v.height}" else "Medindo o vídeo…", 11, C.muted)
                        Box(Modifier.focusRequester(panelFocus)) { Opts("Modo", FITS.indices.associate { it.toString() to FITS[it] }, PP.fit.toString()) { s -> PP.fit = s.toInt(); layoutVideo() } }
                        Stepper("Cima e baixo (ampliar)", PP.fitV, "%") { d -> PP.fitV = (PP.fitV + d * 2).coerceIn(0, 40); layoutVideo() }
                        Stepper("Esquerda e direita (ampliar)", PP.fitH, "%") { d -> PP.fitH = (PP.fitH + d * 2).coerceIn(0, 40); layoutVideo() }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("✦ Tirar faixas pretas", locked = false, primary = true) { autoBars() }
                            Btn("Restaurar", locked = false) { PP.fit = 0; PP.fitV = 0; PP.fitH = 0; layoutVideo(); say("Tela restaurada") }
                        }
                    }
                    "som" -> {
                        T("♪ Som", 16, C.fg, FontWeight.Bold)
                        Box(Modifier.focusRequester(panelFocus)) { Opts("IA de áudio", AUDIO_AI, PP.audioAi) { v -> PP.audioAi = v; say("Áudio: ${AUDIO_AI[v]}") } }
                        Opts("Equalizador", EQS, PP.eq) { v -> PP.eq = v }
                        Stepper("Graves", PP.bass, " dB") { d -> PP.bass = (PP.bass + d).coerceIn(-6, 6) }
                        Stepper("Agudos", PP.treble, " dB") { d -> PP.treble = (PP.treble + d).coerceIn(-6, 6) }
                        Opts("Nivelar volume (comerciais e explosões)", linkedMapOf("1" to "Ligado", "0" to "Desligado"), if (PP.level) "1" else "0") { v -> PP.level = v == "1" }
                        Btn("Saída: fone Bluetooth ou soundbar…", locked = false) {
                            try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { say("Abra as configurações de som da TV") }
                        }
                    }
                    "legendas" -> {
                        T("CC Legendas", 16, C.fg, FontWeight.Bold)
                        val subs = tracksOf(tracks, MC.TRACK_TYPE_TEXT)
                        val off = subs.none { it.selected }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            T("LEGENDA", 10, C.muted, FontWeight.Bold)
                            Box(Modifier.focusRequester(panelFocus)) {
                                Btn("Desligada", on = off, locked = false) { player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true).build(); touch++ }
                            }
                            subs.forEach { o -> Btn(o.label, on = o.selected, locked = false) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, false).setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index)).build(); say("Legenda: ${o.label}")
                            } }
                            if (subs.isEmpty()) T("Este vídeo não traz legendas.", 11, C.dim)
                        }
                        Btn("Carregar legenda por endereço (.srt / .vtt)", locked = false) {
                            st.kb = KbTarget("Legenda (.srt ou .vtt)", subUrl ?: "", hint = "https://site.com/legenda.srt") { v -> if (v.contains("://")) { startAt = player.currentPosition; subUrl = v.trim(); say("Carregando legenda…") } }
                        }
                        Opts("Tamanho", linkedMapOf("0" to "Pequena", "1" to "Média", "2" to "Grande"), PP.subSize.toString()) { v -> PP.subSize = v.toInt() }
                        Opts("Cor", linkedMapOf("0" to "Branca", "1" to "Amarela"), PP.subColor.toString()) { v -> PP.subColor = v.toInt() }
                        Opts("Fundo", linkedMapOf("0" to "Sem fundo", "1" to "Sombra", "2" to "Caixa escura"), PP.subBg.toString()) { v -> PP.subBg = v.toInt() }
                        T("Busca automática de legendas na internet precisa de um serviço com cadastro (OpenSubtitles); por isso fica para depois.", 11, C.dim)
                    }
                    "sync" -> {
                        T("⏱ Sincronia de som e imagem", 16, C.fg, FontWeight.Bold)
                        T("Se a boca não bate com a fala: + atrasa o som, − adianta. Fica salvo para os próximos vídeos.", 11, C.muted, lineHeight = 15)
                        Box(Modifier.focusRequester(panelFocus)) {
                            Stepper("Atraso do som", PP.avMs, " ms") { d -> PP.avMs = (PP.avMs + d * 50).coerceIn(-2000, 2000); eng.avDelay.targetMs = PP.avMs; say("Som: ${if (PP.avMs > 0) "+" else ""}${PP.avMs} ms") }
                        }
                        Btn("Voltar para 0 ms", locked = false) { PP.avMs = 0; eng.avDelay.targetMs = 0; say("Sincronia zerada") }
                    }
                    "audio" -> {
                        T("🗣 Áudio e dublagem", 16, C.fg, FontWeight.Bold)
                        val auds = tracksOf(tracks, MC.TRACK_TYPE_AUDIO)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            T("FAIXA DE ÁUDIO", 10, C.muted, FontWeight.Bold)
                            if (auds.isEmpty()) Box(Modifier.focusRequester(panelFocus).focusable()) { T("Carregando faixas…", 12, C.dim) }
                            auds.forEachIndexed { i, o ->
                                Box(if (i == 0) Modifier.focusRequester(panelFocus) else Modifier) {
                                    Btn(o.label, on = o.selected, locked = false) {
                                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index)).build(); say("Áudio: ${o.label}")
                                    }
                                }
                            }
                        }
                        Btn("Carregar áudio/dublagem por endereço", locked = false) {
                            st.kb = KbTarget("Áudio externo (.aac, .ac3, .m4a, .mp3)", audioUrl ?: "", hint = "https://site.com/dublagem.m4a") { v -> if (v.contains("://")) { startAt = player.currentPosition; audioUrl = v.trim(); say("Carregando áudio…") } }
                        }
                        if (audioUrl != null) Btn("Remover áudio externo", locked = false) { startAt = player.currentPosition; audioUrl = null }
                        T("Dublagens avulsas quase nunca existem para baixar. Quando o servidor da sua lista manda mais de um áudio, as faixas aparecem acima.", 11, C.dim)
                    }
                }
            }
        }
    } }
}

/** Grupo de opções em botões (uma escolhida). */
@Composable
fun Opts(label: String, opts: Map<String, String>, cur: String, locked: Boolean = false, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(label.uppercase(), 10, C.muted, FontWeight.Bold)
        val list = opts.entries.toList()
        list.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { (k, v) -> Btn(v, on = k == cur, locked = locked) { onPick(k) } } }
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, unit: String, locked: Boolean = false, onStep: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(label.uppercase(), 10, C.muted, FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("−", locked = locked) { onStep(-1) }
            Box(Modifier.width(70.dp), contentAlignment = Alignment.Center) { T((if (value > 0) "+" else "") + value + unit, 14, C.fg, FontWeight.Bold) }
            Btn("+", locked = locked) { onStep(1) }
        }
    }
}


/**
 * Gestos do celular no vídeo: toque mostra/esconde os botões; toque duplo nas laterais volta/avança 10 s (no meio pausa);
 * arrastar para cima/baixo à esquerda muda o brilho e à direita o volume; pinça com dois dedos preenche a tela.
 */
private fun touchGestures(ctx: android.content.Context, live: Boolean, zap: (Int) -> Unit, playerToggle: () -> Unit, tap: () -> Unit, seek: (Long) -> Unit, fill: (Boolean) -> Unit, say: (String) -> Unit): Modifier {
    val act = ctx as? android.app.Activity
    val am = ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    return Modifier
        .pointerInput(Unit) {
            detectTapGestures(
                // ao vivo: toque à direita próximo canal, à esquerda anterior, no meio mostra os botões
                onTap = { o -> val w = size.width; if (live && o.x > w * .7f) zap(1) else if (live && o.x < w * .3f) zap(-1) else tap() },
                onDoubleTap = { o -> val w = size.width; when { o.x < w * .38f -> seek(-10_000); o.x > w * .62f -> seek(10_000); else -> playerToggle() } })
        }
        .pointerInput(Unit) {
            var left = true; var acc = 0f
            detectVerticalDragGestures(
                onDragStart = { o -> left = o.x < size.width / 2f; acc = 0f },
                onVerticalDrag = { ch, dy ->
                    ch.consume(); acc -= dy
                    val step = size.height / 16f
                    if (kotlin.math.abs(acc) < step) return@detectVerticalDragGestures
                    val n = (acc / step).toInt(); acc -= n * step
                    if (left && act != null) {
                        val lp = act.window.attributes
                        val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                        lp.screenBrightness = (cur + n * 0.0625f).coerceIn(0.05f, 1f); act.window.attributes = lp
                        say("☀ Brilho ${(lp.screenBrightness * 100).toInt()}%")
                    } else {
                        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        val v = (am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) + n).coerceIn(0, max)
                        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, v, 0)
                        say("🔊 Volume ${v * 100 / maxOf(1, max)}%")
                    }
                })
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                var d0 = 0f
                while (true) {
                    val ev = awaitPointerEvent()
                    val ps = ev.changes.filter { it.pressed }
                    if (ps.isEmpty()) break
                    if (ps.size >= 2) {
                        val d = (ps[0].position - ps[1].position).getDistance()
                        if (d0 == 0f) d0 = d
                        else if (d / d0 > 1.25f) { fill(true); d0 = d; ev.changes.forEach { it.consume() } }
                        else if (d / d0 < 0.8f) { fill(false); d0 = d; ev.changes.forEach { it.consume() } }
                    }
                }
            }
        }
}


/**
 * Um medidor de banda só para o app inteiro: ao trocar de canal, o novo já começa com a velocidade
 * medida no anterior (e, na primeira vez, com uma estimativa alta), então abre direto na qualidade cheia
 * em vez de começar borrado e subir aos poucos.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object Bandwidth {
    private var m: androidx.media3.exoplayer.upstream.DefaultBandwidthMeter? = null
    fun get(c: android.content.Context): androidx.media3.exoplayer.upstream.DefaultBandwidthMeter =
        m ?: androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(c.applicationContext).setInitialBitrateEstimate(20_000_000L).build().also { m = it }
}
