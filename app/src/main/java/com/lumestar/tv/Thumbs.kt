package com.lumestar.tv

import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foto do que está passando agora nos canais que aparecem na tela.
 * Abre o sinal de um canal por vez, em baixa qualidade e sem som, tira uma foto pequena e fecha.
 * Só roda com o app aberto, sem vídeo tocando e fora do modo leve. Cada foto vale 5 minutos.
 */
@UnstableApi
object Thumbs {
    var rev by mutableIntStateOf(0); private set
    private val fotos = object : LinkedHashMap<String, Pair<ImageBitmap, Long>>(64, .75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Pair<ImageBitmap, Long>>?) = size > 60
    }
    private val fila = ArrayDeque<Item>()
    private val falhas = HashMap<String, Long>()
    @Volatile var ligado = true

    fun foto(id: String): ImageBitmap? = synchronized(fotos) { fotos[id]?.first }

    fun pedir(it: Item) {
        if (Perf.lite || !ligado || it.url.isBlank()) return
        val agora = System.currentTimeMillis()
        synchronized(fotos) { fotos[it.id]?.let { (_, t) -> if (agora - t < 5 * 60_000) return } }
        if ((falhas[it.id] ?: 0L) > agora - 15 * 60_000) return
        synchronized(fila) {
            fila.removeAll { x -> x.id == it.id }
            fila.addFirst(it)
            while (fila.size > 16) fila.removeLast()
        }
    }

    fun proximo(): Item? = synchronized(fila) { fila.removeFirstOrNull() }

    fun guardar(id: String, b: android.graphics.Bitmap) { synchronized(fotos) { fotos[id] = b.asImageBitmap() to System.currentTimeMillis() }; rev++ }
    fun falhou(id: String) { falhas[id] = System.currentTimeMillis() }
}

/** Tela de vídeo pequena escondida embaixo do app, usada só para tirar as fotos dos canais. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ThumbHost(ocupado: () -> Boolean) {
    if (Perf.lite) return
    val ctx = LocalContext.current
    val tv = remember { arrayOfNulls<TextureView>(1) }
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            setAudioAttributes(AudioAttributes.DEFAULT, false) // não pega o foco de áudio (a rádio continua)
            trackSelectionParameters = trackSelectionParameters.buildUpon().setMaxVideoSize(854, 480).setForceLowestBitrate(true).build()
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    Box(Modifier.size(96.dp, 54.dp)) {
        AndroidView(factory = { c -> TextureView(c).also { tv[0] = it; player.setVideoTextureView(it) } }, modifier = Modifier.size(96.dp, 54.dp))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            if (ocupado() || !Thumbs.ligado) { if (player.isPlaying) player.stop(); continue }
            val it = Thumbs.proximo() ?: continue
            val quadro = CompletableDeferred<Unit>()
            val l = object : Player.Listener {
                override fun onRenderedFirstFrame() { quadro.complete(Unit) }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { quadro.complete(Unit) }
            }
            player.addListener(l)
            try {
                player.setMediaItem(MediaItem.fromUri(it.url)); player.prepare(); player.play()
                val ok = withTimeoutOrNull(9000) { quadro.await() } != null && player.playerError == null
                if (ok) {
                    delay(900)
                    val b = try { tv[0]?.getBitmap(256, 144) } catch (e: Throwable) { null }
                    if (b != null) Thumbs.guardar(it.id, b) else Thumbs.falhou(it.id)
                } else Thumbs.falhou(it.id)
            } catch (e: Throwable) { Thumbs.falhou(it.id) }
            finally { player.removeListener(l); player.stop(); player.clearMediaItems() }
        }
    }
}
