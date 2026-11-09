package com.lumestar.tv

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Roteiro da abertura (tempos em segundos), sorteado uma vez com semente fixa.
 * O mesmo roteiro alimenta a imagem e o som, então os estalos batem com o plasma.
 */
class IntroPlan {
    companion object {
        const val TB = 4.7      // big bang
        const val NAME = 5.5    // o nome começa a acender
        const val BLACK = 8.6   // apagão
        const val END = 9.0     // abre o app pelo buraco de minhoca
    }

    class Star(val x: Float, val y: Float, val r: Float, val p: Float)
    class Bolt(val t: Double, val life: Double, val pts: FloatArray) // pts em unidades de R, ângulo já aplicado (x,y alternados)
    class Blob(val t: Double, val ang: Float, val vx: Float, val vy: Float, val life: Float, val violet: Boolean)
    class Spark(val vx: Float, val vy: Float, val life: Float, val col: Int, val w: Float)
    class Arc(val t: Double, val life: Double, val a: Int, val b: Int, val fa: FloatArray, val ang: Float, val len: Float, val amp: Float, val seed: Float, val hue: Float, val sound: Boolean)

    private val rnd = Random(2026)
    private fun rf(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)

    val stars = List(354) { Star(rnd.nextFloat() * 1280, rnd.nextFloat() * 720, rnd.nextFloat() * 1.3f + 0.2f, rnd.nextFloat() * 6) }
    val stars2 = List(542) { Star(rnd.nextFloat() * 1280, rnd.nextFloat() * 720, rnd.nextFloat() * 1.3f + 0.2f, rnd.nextFloat() * 6) }

    val holeBolts = ArrayList<Double>()
    val bolts = ArrayList<Bolt>()
    val blobs = ArrayList<Blob>()
    val sparks: List<Spark>
    val arcs = ArrayList<Arc>()
    val tendrils = List(10) { List(6) { k -> floatArrayOf((k / 6f * 2 * PI).toFloat() + rnd.nextFloat() * 0.6f, rf(30f, 70f)) } }
    val letterSeed = List(10) { rnd.nextFloat() * 9 }

    init {
        // raios do disco do buraco negro (antes do big bang)
        var t = 0.8
        while (t < 4.5) {
            val rate = if (t > 3.6) 9.0 else 4.2
            t += -kotlin.math.ln(1 - rnd.nextDouble()) / rate
            if (t >= 4.5) break
            val a = rnd.nextDouble() * PI * 2
            val len = 0.7 + rnd.nextDouble() * 1.1
            val pts = FloatArray(18)
            for (i in 0..8) {
                val rr = 1.04 + len * i / 8
                val j = if (i == 0) 0.0 else (rnd.nextDouble() - 0.5) * 0.22
                pts[i * 2] = (cos(a) * rr - sin(a) * j).toFloat()
                pts[i * 2 + 1] = ((sin(a) * rr + cos(a) * j) * 0.75).toFloat()
            }
            bolts.add(Bolt(t, 0.16 + rnd.nextDouble() * 0.12, pts))
            if (holeBolts.isEmpty() || t - holeBolts.last() > 0.12) holeBolts.add(t)
        }
        // bolhas de plasma que escapam do disco
        t = 0.6
        while (t < 3.8) {
            t += 1.0 / 14 * (0.5 + rnd.nextDouble())
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            blobs.add(Blob(t, a, cos(a) * rf(18f, 48f), sin(a) * rf(14f, 38f), rf(1.2f, 2.2f), rnd.nextBoolean()))
        }
        val cols = intArrayOf(0xFFFFFF, 0x96BEFF, 0xBEA0FF, 0xFFD696, 0x78E6FF)
        sparks = List(560) { i ->
            val a = rnd.nextDouble() * PI * 2
            val sp = rnd.nextDouble().pow(0.55) * (480 + rnd.nextDouble() * 420)
            Spark((cos(a) * sp).toFloat(), (sin(a) * sp).toFloat(), rf(1.3f, 3.5f), cols[i % cols.size], rf(0.7f, 2.9f))
        }
        // plasma que nasce das próprias letras (sem raios caindo)
        var lastSound = 0.0
        t = 6.6
        while (t < 8.55) {
            t += 1.0 / 60
            if (rnd.nextFloat() >= 0.16f) continue
            val i = rnd.nextInt(10)
            val between = rnd.nextFloat() < 0.45f && i < 9
            val snd = t - lastSound > 0.35
            if (snd) lastSound = t
            arcs.add(
                if (between) Arc(t, rf(0.45f, 0.85f).toDouble(), i, i + 1, floatArrayOf(rf(.3f, .8f), rf(.25f, .75f), rf(.2f, .7f), rf(.25f, .75f)), 0f, 0f, rf(5f, 11f), rnd.nextFloat() * 9, rf(250f, 300f), snd)
                else Arc(t, rf(0.4f, 0.8f).toDouble(), i, -1, floatArrayOf(rf(.2f, .8f), rf(.15f, .85f), 0f, 0f), rnd.nextFloat() * 2 * PI.toFloat(), rf(25f, 75f), rf(4f, 8f), rnd.nextFloat() * 9, rf(200f, 290f), snd)
            )
        }
    }
}

/** HSL (graus, 0..1, 0..1) para cor ARGB com transparência a. */
fun hsla(h: Float, s: Float, l: Float, a: Float): Int {
    val c = (1 - kotlin.math.abs(2 * l - 1)) * s
    val hp = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1 - kotlin.math.abs(hp % 2 - 1))
    val (r1, g1, b1) = when {
        hp < 1 -> Triple(c, x, 0f); hp < 2 -> Triple(x, c, 0f); hp < 3 -> Triple(0f, c, x)
        hp < 4 -> Triple(0f, x, c); hp < 5 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2
    return argb(a, r1 + m, g1 + m, b1 + m)
}

fun argb(a: Float, r: Float, g: Float, b: Float): Int =
    ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or ((r.coerceIn(0f, 1f) * 255).toInt() shl 16) or ((g.coerceIn(0f, 1f) * 255).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255).toInt()

fun rgba(rgb: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (rgb and 0xFFFFFF)

/** Desenhos compartilhados pela abertura e pelo buraco de minhoca. */
object Space {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val path = Path()

    fun radial(c: Canvas, x: Float, y: Float, r: Float, colors: IntArray, stops: FloatArray?, additive: Boolean = false) {
        if (r <= 0.5f) return
        fill.shader = RadialGradient(x, y, r, colors, stops, Shader.TileMode.CLAMP)
        fill.xfermode = if (additive) add else null
        c.drawCircle(x, y, r, fill)
        fill.shader = null; fill.xfermode = null
    }

    /** Disco de acreção achatado (metade de trás ou da frente), como no simulador. */
    fun disk(c: Canvas, cx: Float, cy: Float, R: Float, t: Float, front: Boolean, spin: Float, s: Float) {
        c.save()
        c.translate(cx, cy)
        if (front) c.clipRect(-R * 4, 0f, R * 4, R * 4)
        c.scale(1f, 0.27f)
        val r1 = R * 3.3f; val p0 = 1.05f / 3.3f
        fun st(v: Float) = p0 + v * (1 - p0)
        fill.shader = RadialGradient(0f, 0f, r1,
            intArrayOf(0, 0, argb(.95f, 1f, .965f, .91f), argb(.85f, .784f, .686f, 1f), argb(.42f, .353f, .549f, 1f), argb(0f, .157f, .235f, .627f)),
            floatArrayOf(0f, p0 - 0.001f, st(0f), st(.16f), st(.45f), 1f), Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, r1, fill)
        fill.shader = null
        fill.xfermode = add
        for (i in 0 until 90) {
            val rr = R * (1.25f + ((i * 37) % 100) / 100f * 1.9f)
            val a = i * 2.39f + t * (1.4f / (rr / R)) * spin
            fill.color = rgba(0xFFFAF0, .25f + ((i * 13) % 10) / 22f)
            c.drawCircle(cos(a) * rr, sin(a) * rr, (1.4f + (i % 3)) * s, fill)
        }
        fill.xfermode = null
        c.restore()
    }

    private val oval = android.graphics.RectF()
    private val CORES = IntArray(37)
    private val POS = FloatArray(37) { it / 36f }

    /** Cor do disco: branco-azulado na borda de dentro, azul no meio, roxo por fora. */
    private fun corDisco(f: Float, a: Float): Int {
        val r: Float; val g: Float; val b: Float
        if (f < .35f) { val k = f / .35f; r = .92f + (.55f - .92f) * k; g = .94f + (.62f - .94f) * k; b = 1f }
        else { val k = (f - .35f) / .65f; r = .55f + (.62f - .55f) * k; g = .62f + (.36f - .62f) * k; b = 1f + (.95f - 1f) * k }
        return argb(a.coerceIn(0f, 1f), r, g, b)
    }

    /**
     * Anéis de faixas girando: elipses (achatadas por [ys]) de r0 a r1 (em unidades de R), só no arco
     * [de, de+varre] graus. O lado esquerdo é mais claro (o disco vem na nossa direção), como no 3D.
     */
    private fun aneis(c: Canvas, cx: Float, cy: Float, R: Float, t: Float, spin: Float, ys: Float, r0: Float, r1: Float, n: Int, de: Float, varre: Float, forca: Float, dop: Float) {
        val w = (r1 - r0) / n * R * 2.4f
        c.save(); c.translate(cx, cy); c.scale(1f, ys)
        stroke.strokeCap = Paint.Cap.BUTT; stroke.strokeWidth = w; stroke.xfermode = add
        for (i in 0 until n) {
            val f = i / (n - 1f); val rr = (r0 + (r1 - r0) * f) * R
            val base = forca * (1 - f).pow(.9f)
            val fase = t * spin * 1.6f / (rr / R).pow(1.5f)
            for (k in 0..36) {
                val m = (2 * PI * k / 36).toFloat()
                val d = 1 + dop * -cos(m)
                val ruido = .35f + .65f * (.5f + .5f * sin(3 * (m - fase) + i * 2.1f)) * (.6f + .4f * sin(7 * (m - fase * 1.3f) + i * 5.3f))
                CORES[k] = corDisco(f, base * d * ruido)
            }
            stroke.shader = android.graphics.SweepGradient(0f, 0f, CORES, POS)
            oval.set(-rr, -rr, rr, rr)
            c.drawArc(oval, de, varre, false, stroke)
        }
        stroke.shader = null; stroke.xfermode = null; stroke.strokeCap = Paint.Cap.ROUND
        c.restore()
    }

    /**
     * Buraco negro no estilo da prévia 3D: disco achatado atrás, o mesmo disco curvado pela gravidade
     * por cima e por baixo da esfera, anel de fótons, sombra e o disco da frente passando na frente.
     */
    fun blackHole(c: Canvas, cx: Float, cy: Float, R: Float, t: Float, spin: Float, s: Float) {
        if (R <= 0.5f) return
        val ys = .17f
        radial(c, cx, cy, R * 3.6f, intArrayOf(argb(.28f, .47f, .35f, .86f), argb(.12f, .35f, .27f, .78f), argb(0f, .16f, .12f, .47f)), floatArrayOf(0f, .4f, 1f), true)
        aneis(c, cx, cy, R, t, spin, ys, 1.3f, 4.2f, 30, 180f, 180f, .3f, .5f)      // disco de trás
        aneis(c, cx, cy, R, t, spin, .86f, 1.03f, 1.95f, 20, 180f, 180f, .85f, .45f) // curvado por cima
        aneis(c, cx, cy, R, t, spin, .92f, 1.02f, 1.4f, 9, 0f, 180f, .5f, .45f)       // imagem de baixo
        stroke.xfermode = add
        stroke.strokeWidth = 6 * s; stroke.color = argb(.35f, .67f, .59f, 1f); c.drawCircle(cx, cy, R * 1.02f, stroke)
        stroke.strokeWidth = 1.6f * s; stroke.color = argb(.95f, .92f, .91f, 1f); c.drawCircle(cx, cy, R * 1.02f, stroke)
        stroke.xfermode = null
        fill.color = 0xFF000000.toInt()
        c.drawCircle(cx, cy, R, fill)
        aneis(c, cx, cy, R, t, spin, ys, 1.3f, 4.2f, 30, 0f, 180f, .4f, .5f)        // disco da frente
    }

    /** Filamento de plasma ondulado entre dois pontos (vem do próprio objeto, não do céu). */
    fun plasma(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, amp: Float, t: Float, seed: Float, a: Float, w: Float, hue: Float) {
        if (a <= 0.01f) return
        val n = 26
        val dx = x2 - x1; val dy = y2 - y1
        val len = max(1f, hypot(dx, dy)); val nx = -dy / len; val ny = dx / len
        path.reset()
        for (i in 0..n) {
            val u = i.toFloat() / n
            val env = sin(PI.toFloat() * u)
            val o = env * amp * (sin(u * 7 + t * 9 + seed) * .6f + sin(u * 17 - t * 13 + seed * 2.3f) * .3f + sin(u * 31 + t * 21 + seed * .7f) * .15f)
            val x = x1 + dx * u + nx * o; val y = y1 + dy * u + ny * o
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        stroke.xfermode = add
        stroke.strokeWidth = w * 4; stroke.color = hsla(hue, .9f, .62f, a * .09f); c.drawPath(path, stroke)
        stroke.strokeWidth = w * 2; stroke.color = hsla(hue, .95f, .72f, a * .32f); c.drawPath(path, stroke)
        stroke.strokeWidth = w * .8f; stroke.color = rgba(0xFFFFFF, a * .75f); c.drawPath(path, stroke)
        stroke.xfermode = null
    }

    /** Estrelas em lotes: 5 brilhos x 2 tamanhos = 10 chamadas de desenho por quadro. */
    private val starBuf = Array(10) { FloatArray(2400) }
    private val starN = IntArray(10)
    fun stars(c: Canvas, list: List<IntroPlan.Star>, t: Float, alpha: Float, s: Float, map: (IntroPlan.Star) -> Pair<Float, Float>) {
        if (alpha <= 0.01f) return
        starN.fill(0)
        for (st in list) {
            val tw = sin(t * 1.6f + st.p); val lv = (tw * tw * 4.999f).toInt()
            val big = if (st.r > .85f) 5 else 0
            val k = lv + big
            val n = starN[k]
            if (n + 2 > starBuf[k].size) continue
            val (x, y) = map(st)
            starBuf[k][n] = x; starBuf[k][n + 1] = y; starN[k] = n + 2
        }
        val old = stroke.strokeCap
        stroke.strokeCap = Paint.Cap.SQUARE
        for (k in 0 until 10) {
            if (starN[k] == 0) continue
            stroke.strokeWidth = (if (k >= 5) 1.4f else .8f) * s
            stroke.color = rgba(0xFFFFFF, alpha * (.45f + .55f * ((k % 5) + .5f) / 5f))
            c.drawPoints(starBuf[k], 0, starN[k], stroke)
        }
        stroke.strokeCap = old
    }
}

/** Abertura: buraco negro → big bang → nome com plasma → apagão. Qualquer tecla pula. */
@Composable
fun IntroScreen(soundOn: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val done by rememberUpdatedState(onDone)
    val plan = Tracks.plan
    var t by remember { mutableFloatStateOf(0f) }
    val typeface = remember { ResourcesCompat.getFont(ctx, R.font.nanum_brush) ?: Typeface.SERIF }
    val text = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface } }

    LaunchedEffect(Unit) {
        // a imagem começa na hora; o som entra assim que estiver pronto, já no ponto certo
        var playing = !soundOn
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t = ((now - start) / 1e9).toFloat()
            if (!playing) { val pcm = Tracks.intro; if (pcm != null) { playing = true; Sfx.playLong(pcm, t) } }
            if (t >= IntroPlan.END) break
        }
        done()
    }

    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { cc ->
            val c = cc.nativeCanvas
            val W = size.width; val H = size.height
            val s = min(W / 1280f, H / 720f)
            val cx = W / 2; val cy = H / 2
            c.drawColor(0xFF01030B.toInt())
            drawIntro(c, plan, text, t, W, H, s, cx, cy)
        }
    }
}

private fun easeIn(x: Float) = x * x * x
private val sparkBuf = FloatArray(560 * 4)

private fun drawIntro(c: Canvas, plan: IntroPlan, text: Paint, t: Float, W: Float, H: Float, s: Float, cx: Float, cy: Float) {
    val TB = IntroPlan.TB.toFloat()
    val ox = cx - 640 * s; val oy = cy - 360 * s
    if (t >= IntroPlan.BLACK) { c.drawColor(0xFF000000.toInt()); return }
    // estrelas
    if (t < TB) {
        val sk = if (t > 3.6f) min(1f, (t - 3.6f) / 1.1f) else 0f
        val pull = easeIn(sk) * .85f
        Space.stars(c, plan.stars, t, min(1f, t / 1.2f) * (1 - sk), s) { st ->
            Pair(ox + (st.x + (640 - st.x) * pull) * s, oy + (st.y + (360 - st.y) * pull) * s)
        }
    } else {
        val k = t - TB
        val ex = min(1f, 1 - (1 - min(1f, k / 1.8f)).pow(3))
        val sa = max(0f, min(1f, (k - .25f) / 1.8f))
        Space.stars(c, plan.stars2, t * 0.94f, sa, s) { st -> Pair(cx + (st.x - 640) * ex * s, cy + (st.y - 360) * ex * s) }
    }
    // buraco negro e plasma do disco
    if (t < TB) {
        val e = 1 - (1 - min(1f, t / 1.7f)).pow(3)
        val shr = if (t > 3.6f) max(.015f, 1 - easeIn(min(1f, (t - 3.6f) / 1.1f))) else 1f
        val R = 720 * .12f * s * e * shr
        val spin = 1 + (if (t > 3.6f) (t - 3.6f) * 7 else 0f)
        Space.blackHole(c, cx, cy, R, t, spin, s)
        // raios elétricos tirados a pedido (a entrada fica só com o buraco negro e o disco)
        if (false) for (b in plan.bolts) {
            val age = t - b.t.toFloat()
            if (age < 0 || age > b.life) continue
            val a = 1 - age / b.life.toFloat()
            val p = Path()
            for (i in 0..8) { val x = cx + b.pts[i * 2] * R; val y = cy + b.pts[i * 2 + 1] * R; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
            Space.stroke.xfermode = Space.add
            Space.stroke.strokeWidth = 5 * s; Space.stroke.color = rgba(0x9B8CFF, a * .35f); c.drawPath(p, Space.stroke)
            Space.stroke.strokeWidth = 1.6f * s; Space.stroke.color = argb(a, .84f, .89f, 1f); c.drawPath(p, Space.stroke)
            Space.stroke.xfermode = null
        }
        for (bl in plan.blobs) {
            val age = t - bl.t.toFloat()
            if (age < 0 || age > bl.life) continue
            val k = age / bl.life
            val x = cx + cos(bl.ang) * R * 1.1f + bl.vx * age * s
            val y = cy + sin(bl.ang) * R * .8f + bl.vy * age * s
            val rr = (2 + k * 5) * 3 * s
            val col = if (bl.violet) 0xB496FF else 0x6EAAFF
            Space.radial(c, x, y, rr, intArrayOf(rgba(col, .8f * (1 - k)), rgba(col, 0f)), null, true)
        }
        if (t > 3.9f) {
            val q = (t - 3.9f) / .8f
            Space.radial(c, cx, cy, (12 + q * 30) * s, intArrayOf(rgba(0xFFFFFF, min(1f, q)), rgba(0xC8B4FF, 0f)), null, true)
        }
    } else {
        val k = t - TB
        val cr = (40 + k * 300) * s
        Space.radial(c, cx, cy, cr, intArrayOf(rgba(0xFFFFFF, max(0f, .9f - k * .35f)), rgba(0xBEA0FF, max(0f, .55f - k * .2f)), rgba(0x5A8CFF, max(0f, .3f - k * .1f)), rgba(0x283CA0, 0f)), floatArrayOf(0f, .3f, .7f, 1f), true)
        Space.stroke.xfermode = Space.add
        for (i in 0 until 3) {
            val r = (k - i * .18f) * 820 * s
            if (r > 0 && r < 1500 * s) {
                val a = max(0f, .85f * (1 - r / (1300 * s)))
                Space.stroke.strokeWidth = 12f * s; Space.stroke.color = rgba(0x8FB4FF, a * .25f)
                c.drawOval(cx - r, cy - r * .92f, cx + r, cy + r * .92f, Space.stroke)
                Space.stroke.strokeWidth = (4 - i) * s; Space.stroke.color = rgba(if (i == 0) 0xE6F0FF else 0xB4A0FF, a)
                c.drawOval(cx - r, cy - r * .92f, cx + r, cy + r * .92f, Space.stroke)
            }
        }
        // faíscas do big bang: velocidade amortecida 0,982 por quadro de 60 Hz
        val n = k * 60
        val dmp = (1 - 0.982f.pow(n)) / (1 - 0.982f) / 60f
        val dmpPrev = (1 - 0.982f.pow(max(0f, n - 2))) / (1 - 0.982f) / 60f
        for (q in 0 until 4) for (ci in 0 until 5) {
            var n = 0
            for ((j, sp) in plan.sparks.withIndex()) {
                if (j % 5 != ci || k > sp.life) continue
                val aq = ((1 - k / sp.life) * 3.999f).toInt()
                if (aq != q || n + 4 > sparkBuf.size) continue
                sparkBuf[n] = cx + sp.vx * dmpPrev * s; sparkBuf[n + 1] = cy + sp.vy * dmpPrev * s
                sparkBuf[n + 2] = cx + sp.vx * dmp * s; sparkBuf[n + 3] = cy + sp.vy * dmp * s
                n += 4
            }
            if (n == 0) continue
            Space.stroke.strokeWidth = (1.2f + q * .5f) * s
            Space.stroke.color = rgba(plan.sparks[ci].col, (q + .5f) / 4f)
            c.drawLines(sparkBuf, 0, n, Space.stroke)
        }
        Space.stroke.xfermode = null
        if (k < 1.1f) { Space.fill.color = rgba(0xFFFFFF, max(0f, 1 - k * 1.3f)); c.drawRect(0f, 0f, W, H, Space.fill) }
    }
    // nome LumeStarTV
    if (t >= IntroPlan.NAME) drawName(c, plan, text, t, s, cx, cy)
}

private fun drawName(c: Canvas, plan: IntroPlan, p: Paint, t: Float, s: Float, cx: Float, cy: Float) {
    val word = "LumeStarTV"
    val start = IntroPlan.NAME.toFloat()
    val inK = min(1f, (t - start) / .9f)
    val e = 1 - (1 - inK).pow(3)
    val fade = if (t > IntroPlan.BLACK - .18) max(0f, ((IntroPlan.BLACK - t) / .18).toFloat()) else 1f
    val sc = .9f + .1f * e
    p.textSize = 178 * s * sc
    p.shader = null; p.clearShadowLayer()
    val ww = p.measureText(word)
    val x0 = cx - ww / 2
    val fm = p.fontMetrics
    val base = cy - (fm.ascent + fm.descent) / 2
    val h = (fm.descent - fm.ascent)
    val xs = FloatArray(11) { i -> x0 + p.measureText(word, 0, i) }
    // letras apagadas
    p.color = rgba(0x141935, e * fade)
    c.drawText(word, x0, base, p)
    // descarga: no máximo 4% dos quadros, bem discreta
    val bucket = (t * 60).toInt()
    val zap = t > 6.6f && t < 8.55f && ((bucket * 2654435761L) ushr 16 and 0xFFL) < 10L
    val grad = LinearGradient(x0, 0f, x0 + ww, 0f, intArrayOf(0xFF4F9DFF.toInt(), 0xFF7F8BFF.toInt(), 0xFFC9A6FF.toInt()), floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
    for (i in word.indices) {
        val age = t - (start + i * .12f)
        if (age < 0) continue
        val k = min(1f, age / .8f)
        val op = if (k < .3f) k / .3f else 1f
        val br = if (k < .3f) 4 - 1.2f * (k / .3f) else 2.8f - 1.8f * ((k - .3f) / .7f)
        val glowR = (if (k < .3f) 26f * (k / .3f) else 26f - 14f * ((k - .3f) / .7f)) * s
        val ch = word.substring(i, i + 1)
        p.shader = grad
        p.alpha = (op * fade * 255).toInt()
        p.setShadowLayer(max(1f, glowR), 0f, 0f, if (k < .3f) rgba(0xBFE6FF, op * fade) else rgba(0x968CFF, .65f * fade))
        c.drawText(ch, xs[i], base, p)
        p.clearShadowLayer(); p.shader = null
        val white = ((br - 1) / 3f).coerceIn(0f, 1f) + (if (zap) .06f else 0f)
        if (white > 0.01f) { p.color = rgba(0xFFFFFF, white * op * fade); c.drawText(ch, xs[i], base, p) }
    }
    p.alpha = 255
    if (fade <= 0f) return
    // plasma que nasce de dentro das letras
    val lh = h * .82f
    for (i in word.indices) {
        val ts = start + i * .12f
        val age = t - ts
        if (age < 0) continue
        val lcx = (xs[i] + xs[i + 1]) / 2; val lcy = cy
        if (age < .45f) {
            val k = age / .45f
            val r = lh * (.3f + k * .6f)
            Space.radial(c, lcx, lcy, r, intArrayOf(argb(.9f * (1 - k) * fade, .92f, .88f, 1f), argb(.5f * (1 - k) * fade, .667f, .47f, 1f), 0), floatArrayOf(0f, .4f, 1f), true)
            plan.tendrils[i].forEachIndexed { j, d ->
                val l = d[1] * s * (.4f + k)
                Space.plasma(c, lcx, lcy, lcx + cos(d[0]) * l, lcy + sin(d[0]) * l, 7 * s, t, plan.letterSeed[i] + j, (1 - k) * fade, .8f * s, 265f + j * 12)
            }
        } else if (t < 8.55f) {
            val pulse = .5f + .5f * sin(t * 6 + plan.letterSeed[i] * 3)
            Space.radial(c, lcx, lcy, lh * .55f, intArrayOf(rgba(0xA078FF, (.05f + .04f * pulse) * fade), 0), null, true)
        }
    }
    for (a in plan.arcs) {
        val age = t - a.t.toFloat()
        if (age < 0 || age > a.life) continue
        val k = age / a.life.toFloat()
        val alpha = sin(PI.toFloat() * k).pow(1.5f) * fade
        val ax = xs[a.a] + (xs[a.a + 1] - xs[a.a]) * a.fa[0]
        val ay = cy - lh / 2 + lh * a.fa[1]
        if (a.b >= 0) {
            val bx = xs[a.b] + (xs[a.b + 1] - xs[a.b]) * a.fa[2]
            val by = cy - lh / 2 + lh * a.fa[3]
            Space.plasma(c, ax, ay, bx, by, a.amp * s, t, a.seed, alpha, .9f * s, a.hue)
        } else {
            Space.plasma(c, ax, ay, ax + cos(a.ang) * a.len * s, ay + sin(a.ang) * a.len * s, a.amp * s, t, a.seed, alpha, .9f * s, a.hue)
        }
    }
}
