package com.lumestar.tv

import android.view.KeyEvent as AKey
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** O que tocar: canal ao vivo, filme ou episódio. */
class PlayReq(val id: String, val title: String, val url: String, val live: Boolean, val start: Long, val group: String, val ext: String = "")

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

private const val UA_APP = "LumeStarTV/0.2 (Linux; Android) ExoPlayerLib/1.3.1"
private const val UA_VLC = "VLC/3.0.20 LibVLC/3.0.20"

@OptIn(UnstableApi::class)
private fun httpFactory(): DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory()
    .setUserAgent(UA_APP)
    .setAllowCrossProtocolRedirects(true)
    .setConnectTimeoutMs(15000).setReadTimeoutMs(30000)

@OptIn(UnstableApi::class)
private fun buildPlayer(ctx: android.content.Context, http: DefaultHttpDataSource.Factory): ExoPlayer =
    ExoPlayer.Builder(ctx).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, http))).build()

private fun mediaItem(url: String): MediaItem {
    val low = url.lowercase()
    val b = MediaItem.Builder().setUri(url)
    when {
        low.contains(".m3u8") -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
        low.contains(".mpd") -> b.setMimeType(MimeTypes.APPLICATION_MPD)
    }
    return b.build()
}

/**
 * Reprodutor básico (a versão completa, com imagem, tela, som, legendas e dublagem, vem na entrega 3).
 * OK: pausar/continuar · ◀ ▶: voltar/avançar 10 s · Voltar: sair. Se falhar, tenta de novo após 30 s.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(st: AppState, req: PlayReq) {
    val ctx = LocalContext.current
    val http = remember { httpFactory() }
    val player = remember { buildPlayer(ctx, http) }
    var triedVlc by remember(req) { mutableStateOf(false) }
    var url by remember(req) { mutableStateOf(req.url) }
    var triedTs by remember(req) { mutableStateOf(false) }
    var playing by remember { mutableStateOf(true) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var wait by remember { mutableIntStateOf(0) }
    var showUi by remember { mutableIntStateOf(1) }
    var attempt by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    fun save() { if (!req.live) Store.setProgress(req.id, player.currentPosition, player.duration.takeIf { it > 0 } ?: 0L) }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                buffering = s == Player.STATE_BUFFERING
                if (s == Player.STATE_READY) { err = null; dur = player.duration.coerceAtLeast(0) }
                if (s == Player.STATE_ENDED && !req.live) { save(); st.closePlayer() }
            }
            override fun onIsPlayingChanged(p: Boolean) { playing = p }
            override fun onPlayerError(e: PlaybackException) {
                // alguns servidores de IPTV só aceitam o "nome" do VLC: tenta de novo na hora com ele
                val bad = e.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                if (bad && !triedVlc) { triedVlc = true; http.setUserAgent(UA_VLC); attempt++; return }
                if (req.live && !triedTs && url.contains(".m3u8") && req.ext == "ts") {
                    triedTs = true; url = url.replace(".m3u8", ".ts"); attempt++; return
                }
                err = "Não foi possível reproduzir (${e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')})."
                wait = 30
            }
        }
        player.addListener(l)
        onDispose { save(); player.removeListener(l); player.release() }
    }
    LaunchedEffect(url, attempt) {
        player.setMediaItem(mediaItem(url))
        player.prepare()
        if (req.start > 0 && !req.live) player.seekTo(req.start)
        player.playWhenReady = true
    }
    LaunchedEffect(wait) {
        if (wait > 0) { delay(1000); wait--; if (wait == 0) { err = null; attempt++ } }
    }
    LaunchedEffect(Unit) {
        var n = 0
        while (true) {
            delay(500); pos = player.currentPosition; if (player.duration > 0) dur = player.duration
            if (++n % 10 == 0) save()
        }
    }
    LaunchedEffect(showUi) { if (showUi > 0) { delay(4500); showUi = 0 } }
    LaunchedEffect(Unit) { delay(80); try { focus.requestFocus() } catch (e: Exception) {} }
    BackHandler { save(); st.closePlayer() }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus).focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key.nativeKeyCode) {
                    AKey.KEYCODE_DPAD_CENTER, AKey.KEYCODE_ENTER, AKey.KEYCODE_MEDIA_PLAY_PAUSE, AKey.KEYCODE_NUMPAD_ENTER -> {
                        if (player.isPlaying) player.pause() else player.play(); showUi++; true
                    }
                    AKey.KEYCODE_DPAD_LEFT, AKey.KEYCODE_MEDIA_REWIND -> { if (!req.live) player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); showUi++; true }
                    AKey.KEYCODE_DPAD_RIGHT, AKey.KEYCODE_MEDIA_FAST_FORWARD -> { if (!req.live) player.seekTo(player.currentPosition + 10_000); showUi++; true }
                    AKey.KEYCODE_DPAD_UP, AKey.KEYCODE_DPAD_DOWN, AKey.KEYCODE_INFO -> { showUi++; true }
                    else -> false
                }
            }
    ) {
        AndroidView(factory = { c ->
            PlayerView(c).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
                keepScreenOn = true
            }
        }, modifier = Modifier.fillMaxSize())

        if (buffering && err == null) Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color(0xCC050816)).padding(horizontal = 18.dp, vertical = 10.dp)) {
            T("Carregando…", 14, C.fg, FontWeight.Bold)
        }
        err?.let { m ->
            Column(Modifier.align(Alignment.Center).width(460.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xEE0E1538)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                T(m, 15, C.fg, FontWeight.Bold)
                T(if (wait > 0) "Nova tentativa em $wait s. Aperte Voltar para sair." else "Tentando de novo…", 13, C.muted)
            }
        }
        if (showUi > 0 || !playing) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 40.dp)) {
                T((if (req.live) "AO VIVO · " else "") + req.group.uppercase(), 10, if (req.live) C.bad else C.a1, FontWeight.Bold, maxLines = 1)
                T(req.title, 22, C.fg, FontWeight.ExtraBold, maxLines = 1)
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))).padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    T(if (req.live) "AO VIVO" else fmt(pos), 13, C.fg, FontWeight.Bold)
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x38FFFFFF))) {
                        val fr = if (req.live) 1f else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
                        Box(Modifier.fillMaxWidth(fr).height(6.dp).background(if (req.live) Brush.horizontalGradient(listOf(C.bad, C.bad)) else Brush.horizontalGradient(listOf(C.a1, C.a2))))
                    }
                    if (!req.live) T(fmt(dur), 13, C.fg, FontWeight.Bold)
                }
                T((if (playing) "OK: pausar" else "OK: continuar") + (if (req.live) "" else " · ◀ ▶: 10 segundos") + " · Voltar: sair", 12, C.muted)
            }
        }
    }
}
