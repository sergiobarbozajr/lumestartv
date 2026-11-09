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

    /**
     * Algumas TVs (ex.: TCL) entregam o quadro só num canto e o resto vem verde (área vazia do decodificador).
     * Acha a parte com imagem, recorta e amplia; se quase tudo for verde ou preto, descarta (o card usa o logo).
     */
    fun recortar(b: android.graphics.Bitmap): android.graphics.Bitmap? {
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        fun vazio(c: Int): Boolean {
            val r = c shr 16 and 255; val g = c shr 8 and 255; val bl = c and 255
            return (g > 120 && r < 70 && bl < 70) || (r < 10 && g < 10 && bl < 10)
        }
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1; var cheios = 0
        for (y in 0 until h step 2) for (x in 0 until w step 2) if (!vazio(px[y * w + x])) {
            cheios++; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        if (x1 < 0) return null
        val bw = x1 - x0 + 2; val bh = y1 - y0 + 2
        // a parte com imagem precisa ter um tamanho mínimo e estar quase toda preenchida
        if (bw < w / 6 || bh < h / 6 || cheios < (bw / 2) * (bh / 2) * .6f) return null
        if (bw >= w - 8 && bh >= h - 8) return android.graphics.Bitmap.createScaledBitmap(b, 256, 144, true)
        val cx = x0.coerceIn(0, w - 1); val cy = y0.coerceIn(0, h - 1)
        val corte = android.graphics.Bitmap.createBitmap(b, cx, cy, bw.coerceAtMost(w - cx), bh.coerceAtMost(h - cy))
        return android.graphics.Bitmap.createScaledBitmap(corte, 256, 144, true)
    }

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
                    val b = try { tv[0]?.getBitmap(640, 360)?.let { x -> Thumbs.recortar(x) } } catch (e: Throwable) { null }
                    if (b != null) Thumbs.guardar(it.id, b) else Thumbs.falhou(it.id)
                } else Thumbs.falhou(it.id)
            } catch (e: Throwable) { Thumbs.falhou(it.id) }
            finally { player.removeListener(l); player.stop(); player.clearMediaItems() }
        }
    }
}
