package com.lumestar.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Abertura do app pelo buraco de minhoca: só o buraco girando na tela escura,
 * as opções do menu saem dele para a esquerda, ele se fecha e então nascem os templates.
 */
object Opening {
    const val TC = 1.75f    // o buraco termina de fechar
    const val TB2 = 2.15f   // os templates começam a nascer
    const val END = 3.6f    // tudo no lugar

    private fun ease(x: Float): Float { val k = x.coerceIn(0f, 1f); return 1 - (1 - k).pow(3) }

    /** Progresso (0..1) do item i do menu saindo do buraco. */
    fun item(t: Float, i: Int): Float = if (t < 0f) 1f else ease((t - (0.42f + i * 0.062f)) / 0.52f)
    fun itemDelay(i: Int) = 0.42f + i * 0.062f

    /** Progresso (0..1) do template i nascendo depois que o buraco fecha. */
    fun block(t: Float, i: Int): Float = if (t < 0f) 1f else ease((t - (TB2 + 0.06f + i * 0.11f)) / 0.7f)

    /** Escuridão por cima do conteúdo (some depois que o buraco fecha). */
    fun dark(t: Float): Float = if (t < 0f) 0f else if (t < TB2) 1f else max(0f, 1 - (t - TB2) / 0.55f)
}

@Composable
fun WormholeOverlay(st: AppState) {
    val t0 = st.openT
    if (t0 < 0f || t0 > Opening.TB2 + 0.7f) return
    Canvas(Modifier.fillMaxSize()) {
        val t = st.openT
        drawIntoCanvas { cc ->
            val c = cc.nativeCanvas
            val W = size.width; val H = size.height
            val s = min(W / 1280f, H / 720f)
            val cx = W / 2; val cy = H / 2
            Space.fill.color = rgba(0x01030B, Opening.dark(t))
            c.drawRect(0f, 0f, W, H, Space.fill)
            val TC = Opening.TC
            if (t < TC + .25f) {
                val grow = min(1f, t / .45f)
                val e = 1 - (1 - grow).pow(3)
                val close = if (t > TC - .35f) max(0f, 1 - (t - (TC - .35f)) / .6f) else 1f
                val R = 62f * s * e * close.pow(1.4f)
                val spin = t * (1 + (if (t > TC - .35f) (t - TC + .35f) * 6 else 0f))
                wormhole(c, cx, cy, R, spin, min(1f, grow * 1.2f), s)
            }
            if (t > TC && t < Opening.TB2 + .4f) {
                val k = (t - TC) / (Opening.TB2 + .4f - TC)
                Space.radial(c, cx, cy, (10 + k * 120) * s, intArrayOf(rgba(0xFFFFFF, .9f * (1 - k)), rgba(0xBEA0FF, .5f * (1 - k)), 0), floatArrayOf(0f, .3f, 1f), true)
            }
        }
    }
}

private val armPath = android.graphics.Path()

private fun wormhole(c: android.graphics.Canvas, cx: Float, cy: Float, R: Float, t: Float, glow: Float, s: Float) {
    if (R < .5f) return
    Space.radial(c, cx, cy, R * 3.2f, intArrayOf(0, rgba(0xC8B4FF, .55f * glow), rgba(0x6E8CFF, .28f * glow), 0), floatArrayOf(0f, .19f, .4f, 1f), true)
    Space.stroke.xfermode = Space.add
    for (arm in 0 until 5) {
        val off = arm / 5f * 2 * Math.PI.toFloat()
        armPath.reset()
        for (i in 0..60) {
            val u = i / 60f
            val r = R * (.95f + u * 2.1f)
            val a = off - t * 2.6f + u * 4.2f
            val x = cx + cos(a) * r; val y = cy + sin(a) * r * .92f
            if (i == 0) armPath.moveTo(x, y) else armPath.lineTo(x, y)
        }
        Space.stroke.strokeWidth = R * .3f; Space.stroke.color = hsla(250f + arm * 14, .9f, .72f, .14f * glow)
        c.drawPath(armPath, Space.stroke)
        Space.stroke.strokeWidth = R * .14f; Space.stroke.color = hsla(250f + arm * 14, .9f, .72f, .45f * glow)
        c.drawPath(armPath, Space.stroke)
    }
    Space.stroke.xfermode = null
    Space.radial(c, cx, cy, R * 1.08f, intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), rgba(0xFFFAF0, .9f * glow), rgba(0xB496FF, 0f)), floatArrayOf(0f, .82f, .92f, 1f))
    Space.radial(c, cx - R * .2f, cy - R * .2f, R * .8f, intArrayOf(rgba(0x786EDC, .25f * glow), 0), null)
}
