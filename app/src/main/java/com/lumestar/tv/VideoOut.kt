package com.lumestar.tv

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbMatrix

/**
 * Saída do vídeo.
 * Clássico (TextureView): o vídeo é desenhado dentro da tela do app, que em muitas TVs 4K roda em 1080p;
 * um vídeo 4K é reduzido para 1080p e a TV amplia de novo.
 * Direto (SurfaceView): o vídeo vai para a camada de vídeo da TV, na resolução do painel. 4K chega em 4K
 * e a IA pode entregar acima de 1080p. Se der erro, o reprodutor volta sozinho para o clássico.
 */
@UnstableApi
object VideoOut {
    const val TESTE_URL = "https://dash.akamaized.net/akamai/streamroot/050714/Spring_4Ktest.mpd"
    val TESTE_RESERVA = listOf(
        "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_adv_example_hevc/master.m3u8",
        "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_5MB.mp4",
    )

    /** Modo inicial deste aparelho: Automático usa o direto (Android 7+, fora do modo leve) até ele falhar. */
    /** true enquanto um vídeo está aberto (o diagnóstico pesado de GPU espera o vídeo fechar). */
    @Volatile var tocando = false

    fun direto(): Boolean = Build.VERSION.SDK_INT >= 24 && !Perf.lite && when (PP.videoMode) {
        "direto" -> true; "classico" -> false; else -> !PP.diretoFalhou
    }

    fun nomeModo(): String = when {
        Build.VERSION.SDK_INT < 24 -> "Clássico (Android antigo)"
        Perf.lite -> "Clássico (modo leve)"
        direto() -> "Direto: o vídeo vai na resolução do painel"
        else -> "Clássico: o vídeo segue a resolução da interface"
    }

    /** ColorMatrix do Android (4×5, desvio 0–255) → matriz 4×4 do Media3 (coluna a coluna, desvio pela coluna do alfa). */
    fun cor(cm: ColorMatrix): RgbMatrix {
        val a = cm.array
        val m = FloatArray(16)
        for (r in 0..2) {
            for (c in 0..2) m[c * 4 + r] = a[r * 5 + c]
            m[12 + r] = a[r * 5 + 4] / 255f
        }
        m[15] = 1f
        return RgbMatrix { _, _ -> m }
    }

    fun identidade(cm: ColorMatrix): Boolean {
        val a = cm.array; val i = ColorMatrix().array
        return a.indices.all { kotlin.math.abs(a[it] - i[it]) < 0.004f }
    }

    /** Foto pequena do quadro atual (análise de cena e faixas pretas), nos dois modos. */
    fun foto(v: View?, w: Int, h: Int, cb: (Bitmap) -> Unit) {
        when (v) {
            is TextureView -> try { v.getBitmap(w, h)?.let(cb) } catch (e: Throwable) {}
            is SurfaceView -> if (Build.VERSION.SDK_INT >= 24 && v.holder.surface?.isValid == true) try {
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                android.view.PixelCopy.request(v, b, { r -> if (r == android.view.PixelCopy.SUCCESS) cb(b) }, Handler(Looper.getMainLooper()))
            } catch (e: Throwable) {}
        }
    }
}
