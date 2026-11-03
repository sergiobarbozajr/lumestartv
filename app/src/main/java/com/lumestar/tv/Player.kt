package com.lumestar.tv

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.view.Gravity
import android.view.TextureView
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.key.type
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
class PlayReq(val id: String, val title: String, val url: String, val live: Boolean, val start: Long, val group: String, val ext: String = "", val cacheMs: Int = -1)

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
    init {
        val b = OkHttpClient.Builder().proxy(PP.proxy()).followRedirects(true).followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        if (PP.proxyType == "http" && PP.proxyUser.isNotBlank()) {
            val cred = Credentials.basic(PP.proxyUser, PP.proxyPass)
            b.proxyAuthenticator { _, resp -> resp.request.newBuilder().header("Proxy-Authorization", cred).build() }
        }
        ok = OkHttpDataSource.Factory(b.build()).setUserAgent(UA_APP)
        ds = DefaultDataSource.Factory(ctx, ok)
        msf = DefaultMediaSourceFactory(ds)
        val cache = cacheMs.coerceIn(300, 10000)
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(maxOf(15000, cache * 3), 50000, cache, maxOf(1000, cache * 2)).build()
        player = ExoPlayer.Builder(ctx).setMediaSourceFactory(msf).setLoadControl(load).build()
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

    fun item(url: String, subUrl: String?): MediaItem {
        val low = url.lowercase()
        val b = MediaItem.Builder().setUri(url)
        when {
            low.contains(".m3u8") -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            low.contains(".mpd") -> b.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        if (!subUrl.isNullOrBlank()) {
            val mime = if (subUrl.lowercase().contains(".vtt")) MimeTypes.TEXT_VTT else if (subUrl.lowercase().contains(".ass") || subUrl.lowercase().contains(".ssa")) MimeTypes.TEXT_SSA else MimeTypes.APPLICATION_SUBRIP
            b.setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subUrl)).setMimeType(mime).setLanguage("pt").setLabel("Legenda carregada").setSelectionFlags(MC.SELECTION_FLAG_DEFAULT).build()))
        }
        return b.build()
    }

    fun load(url: String, subUrl: String?, audioUrl: String?, startMs: Long) {
        val main = msf.createMediaSource(item(url, subUrl))
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
fun PlayerScreen(st: AppState, req: PlayReq) {
    val ctx = LocalContext.current
    val eng = remember { Engine(ctx, if (req.cacheMs > 0) req.cacheMs else PP.cacheMs) }
    val player = eng.player
    var triedVlc by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(req.url) }
    var subUrl by remember { mutableStateOf<String?>(null) }
    var audioUrl by remember { mutableStateOf<String?>(null) }
    var triedTs by remember { mutableStateOf(false) }
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
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var vsize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var osd by remember { mutableStateOf("") }
    var osdId by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val holder = remember { arrayOfNulls<Any>(4) } // [container, textureView, subtitleView, audioFx]
    fun say(m: String) { osd = m; osdId++ }
    fun tv() = holder[1] as? TextureView
    fun container() = holder[0] as? FrameLayout
    fun fx() = holder[3] as? AudioFx

    fun save() { if (!req.live) Store.setProgress(req.id, player.currentPosition, player.duration.takeIf { it > 0 } ?: 0L) }

    fun layoutVideo() {
        val c = container() ?: return; val t = tv() ?: return
        val W = c.width.toFloat(); val H = c.height.toFloat()
        if (W <= 0 || H <= 0) return
        val va = if (vsize.width > 0 && vsize.height > 0) vsize.width * vsize.pixelWidthHeightRatio / vsize.height else 16f / 9f
        var w = W; var h = W / va
        if (h > H) { h = H; w = H * va }
        val lp = t.layoutParams as FrameLayout.LayoutParams
        if (lp.width != w.toInt() || lp.height != h.toInt()) { lp.width = w.toInt(); lp.height = h.toInt(); lp.gravity = Gravity.CENTER; t.layoutParams = lp }
        val u = when (PP.fit) { 1 -> maxOf(W / w, H / h); 2 -> 1.2f; else -> 1f }
        t.scaleX = u * (1 + PP.fitH / 100f); t.scaleY = u * (1 + PP.fitV / 100f)
    }

    fun applyPicture() {
        tv()?.setLayerPaint(Paint().apply { colorFilter = ColorMatrixColorFilter(videoMatrix()) })
        layoutVideo()
    }

    fun applySubStyle() {
        val sv = holder[2] as? SubtitleView ?: return
        val fg = if (PP.subColor == 1) 0xFFFFE14D.toInt() else 0xFFFFFFFF.toInt()
        val bg = if (PP.subBg == 2) 0xB0000000.toInt() else 0
        val edge = if (PP.subBg == 1) CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW else if (PP.subBg == 0) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_NONE
        sv.setStyle(CaptionStyleCompat(fg, bg, 0, edge, 0xFF000000.toInt(), null))
        sv.setFractionalTextSize(when (PP.subSize) { 0 -> .045f; 2 -> .075f; else -> .058f })
    }

    /** Tira as faixas pretas: olha uma foto pequena do vídeo e mede as bordas escuras. */
    fun autoBars() {
        val bmp = try { tv()?.getBitmap(96, 54) } catch (e: Throwable) { null } ?: run { say("Ainda sem imagem para medir"); return }
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

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                buffering = s == Player.STATE_BUFFERING
                if (s == Player.STATE_READY) { err = null; dur = player.duration.coerceAtLeast(0) }
                if (s == Player.STATE_ENDED && !req.live) { save(); st.closePlayer() }
            }
            override fun onIsPlayingChanged(p: Boolean) { playing = p }
            override fun onTracksChanged(t: Tracks) {
                val hadExt = audioUrl != null && tracksOf(tracks, MC.TRACK_TYPE_AUDIO).size < tracksOf(t, MC.TRACK_TYPE_AUDIO).size
                tracks = t
                if (hadExt) tracksOf(t, MC.TRACK_TYPE_AUDIO).lastOrNull()?.let { o ->
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(o.group.mediaTrackGroup, o.index)).build()
                }
            }
            override fun onVideoSizeChanged(v: VideoSize) { vsize = v; layoutVideo() }
            override fun onCues(c: CueGroup) { (holder[2] as? SubtitleView)?.setCues(c.cues) }
            override fun onAudioSessionIdChanged(id: Int) { fx()?.release(); holder[3] = AudioFx(id).also { it.apply() } }
            override fun onPlayerError(e: PlaybackException) {
                val bad = e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                if (bad && !triedVlc) { triedVlc = true; eng.ok.setUserAgent(UA_VLC); startAt = player.currentPosition; attempt++; return }
                if (req.live && !triedTs && url.contains(".m3u8") && req.ext == "ts") { triedTs = true; url = url.replace(".m3u8", ".ts"); attempt++; return }
                err = "Não foi possível reproduzir (${e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')})."
                wait = 30
            }
        }
        player.addListener(l)
        onDispose { save(); player.removeListener(l); fx()?.release(); player.release() }
    }
    LaunchedEffect(url, attempt, subUrl, audioUrl) { eng.load(url, subUrl, audioUrl, if (req.live) 0L else startAt) }
    LaunchedEffect(wait) { if (wait > 0) { delay(1000); wait--; if (wait == 0) { err = null; startAt = player.currentPosition; attempt++ } } }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) { delay(500); pos = player.currentPosition; if (player.duration > 0) dur = player.duration; if (++n % 10 == 0) save() }
    }
    LaunchedEffect(showUi, touch, panel) {
        if (showUi && panel == null) { delay(5000); showUi = false; try { rootFocus.requestFocus() } catch (e: Exception) {} }
    }
    LaunchedEffect(Unit) { delay(80); try { playFocus.requestFocus() } catch (e: Exception) {} }
    LaunchedEffect(panel) { if (panel != null) { delay(60); try { panelFocus.requestFocus() } catch (e: Exception) {} } }
    LaunchedEffect(osdId) { if (osdId > 0) { delay(2200); osd = "" } }
    LaunchedEffect(PP.rev) { applyPicture(); applySubStyle(); fx()?.apply() }

    BackHandler {
        when {
            panel != null -> { panel = null; try { playFocus.requestFocus() } catch (e: Exception) {} }
            showUi -> { showUi = false; try { rootFocus.requestFocus() } catch (e: Exception) {} }
            else -> { save(); st.closePlayer() }
        }
    }

    fun seek(d: Long) { if (!req.live) { player.seekTo((player.currentPosition + d).coerceAtLeast(0)); say((if (d > 0) "⏩ " else "⏪ ") + fmt(player.currentPosition)) } }
    fun showControls() { showUi = true; touch++ }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus).focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                touch++
                if (showUi) return@onKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_NUMPAD_ENTER, AKey.KEYCODE_MEDIA_PLAY_PAUSE -> { if (player.isPlaying) player.pause() else player.play(); showControls(); try { playFocus.requestFocus() } catch (e: Exception) {}; true }
                    AKey.KEYCODE_DPAD_LEFT, AKey.KEYCODE_MEDIA_REWIND -> { seek(-10_000); true }
                    AKey.KEYCODE_DPAD_RIGHT, AKey.KEYCODE_MEDIA_FAST_FORWARD -> { seek(10_000); true }
                    AKey.KEYCODE_DPAD_UP, AKey.KEYCODE_DPAD_DOWN, AKey.KEYCODE_INFO, AKey.KEYCODE_MENU -> { showControls(); try { playFocus.requestFocus() } catch (e: Exception) {}; true }
                    else -> false
                }
            }
    ) {
        AndroidView(factory = { c ->
            FrameLayout(c).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                val t = TextureView(c)
                addView(t, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
                val sv = SubtitleView(c)
                addView(sv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                holder[0] = this; holder[1] = t; holder[2] = sv
                player.setVideoTextureView(t)
                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> post { layoutVideo() } }
                keepScreenOn = true
                applyPicture(); applySubStyle()
            }
        }, modifier = Modifier.fillMaxSize())

        if (buffering && err == null) Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xCC050816)).padding(horizontal = 18.dp, vertical = 10.dp)) { T("Carregando…", 14, C.fg, FontWeight.Bold) }
        err?.let { m ->
            Column(Modifier.align(Alignment.Center).width(460.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xEE0E1538)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(m, 15, C.fg, FontWeight.Bold)
                T(if (wait > 0) "Nova tentativa em $wait s. Aperte Voltar para sair." else "Tentando de novo…", 13, C.muted)
            }
        }
        if (osd.isNotEmpty()) Box(Modifier.align(Alignment.TopCenter).padding(top = 90.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xDD050816)).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 9.dp)) { T(osd, 13, C.fg, FontWeight.Bold) }

        if (showUi || !playing) {
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 36.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    T((if (req.live) "AO VIVO · " else "") + req.group.uppercase(), 10, if (req.live) C.bad else C.a1, FontWeight.Bold, maxLines = 1)
                    T(req.title, 21, C.fg, FontWeight.ExtraBold, maxLines = 1)
                }
                val v = vsize
                val badges = listOfNotNull(
                    if (v.height > 0) "${v.height}p" else null,
                    if (PP.enhance != "off") "✦ Realce ${ENHANCE[PP.enhance]}" else null,
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
                    Box(Modifier.focusRequester(playFocus)) { Btn(if (playing) "❚❚" else "▶", locked = false, primary = true) { if (player.isPlaying) player.pause() else player.play(); touch++ } }
                    if (!req.live) Btn("10⟳", locked = false) { seek(10_000) }
                    Btn("−", locked = false) { vol = (vol - .1f).coerceAtLeast(0f); player.volume = vol; say("Volume ${(vol * 100).toInt()}") }
                    Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) { Box(Modifier.fillMaxWidth(vol).fillMaxHeight().background(Color.White)) }
                    Btn("+", locked = false) { vol = (vol + .1f).coerceAtMost(1f); player.volume = vol; say("Volume ${(vol * 100).toInt()}") }
                    Spacer(Modifier.width(4.dp))
                    listOf("imagem" to "☀ Imagem", "tela" to "⛶ Tela", "som" to "♪ Som", "legendas" to "CC Legendas", "audio" to "🗣 Áudio").forEach { (k, l) ->
                        Btn(l, on = panel == k, locked = false) { panel = if (panel == k) null else k }
                    }
                    Spacer(Modifier.weight(1f))
                    Btn("✕", locked = false) { save(); st.closePlayer() }
                }
            }
        }
        panel?.let { p ->
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 22.dp, bottom = 70.dp).width(380.dp).height(360.dp)
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
    }
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
