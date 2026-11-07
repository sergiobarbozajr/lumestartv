package com.lumestar.tv

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Sons do LumeStarTV, todos gerados por código (sem arquivos de áudio),
 * na mesma receita do simulador: buraco negro abafado, plasma, big bang,
 * acorde escuro do nome, zumbido elétrico, apagão e buraco de minhoca.
 */
object Synth {
    const val SR = 24000

    // ---------- blocos básicos ----------
    class Bq {
        private var b0 = 0.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        private fun set(type: Int, f: Double, q: Double) {
            val fc = f.coerceIn(10.0, SR * 0.45)
            val w = 2 * PI * fc / SR
            val al = sin(w) / (2 * q)
            val cw = cos(w)
            val a0: Double
            when (type) {
                0 -> { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2 } // passa-baixa
                1 -> { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2 } // passa-alta
                else -> { b0 = al; b1 = 0.0; b2 = -al } // passa-faixa
            }
            a0 = 1 + al; a1 = -2 * cw; a2 = 1 - al
            b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
        }
        fun lp(f: Double, q: Double = 0.707) = also { set(0, f, q) }
        fun hp(f: Double, q: Double = 0.707) = also { set(1, f, q) }
        fun bp(f: Double, q: Double = 1.0) = also { set(2, f, q) }
        fun run(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    class Buf(sec: Double) {
        val d = FloatArray((sec * SR).toInt() + 1)
        fun add(i: Int, v: Double) { if (i >= 0 && i < d.size) d[i] += v.toFloat() }
    }

    private fun expDecay(t: Double, len: Double) = if (t < 0) 0.0 else exp(ln(1e-4) * t / len)
    private fun ramp(t: Double, a: Double, b: Double, va: Double, vb: Double) = when {
        t <= a -> va; t >= b -> vb; else -> va + (vb - va) * (t - a) / (b - a)
    }
    private fun expSweep(t: Double, len: Double, f0: Double, f1: Double) = f0 * (f1 / f0).pow(min(max(t, 0.0), len) / len)
    private fun saw(ph: Double) = 2 * (ph - Math.floor(ph + 0.5))
    private fun tri(ph: Double) = 2 * kotlin.math.abs(2 * (ph - Math.floor(ph + 0.5))) - 1
    private fun sq(ph: Double) = if (ph - Math.floor(ph) < 0.5) 1.0 else -1.0

    // ---------- camadas da abertura ----------
    fun hole(b: Buf, t0: Double, dur: Double, rnd: Random) {
        val f = Bq(); val s = (t0 * SR).toInt(); val n = (dur * SR).toInt()
        var ph = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            if (i % 64 == 0) f.lp(260 + 150 * sin(2 * PI * 0.17 * t), 0.9)
            val env = when { t < 1.6 -> t / 1.6 * 0.6; t > dur - 1.5 -> max(0.0, (dur - t) / 1.5) * 0.6; else -> 0.6 } + 0.16 * sin(2 * PI * 0.33 * t) * min(1.0, t)
            val nz = f.run(rnd.nextDouble() * 2 - 1) * env * 1.6
            ph += 41.0 / SR
            val subEnv = when { t < 2 -> t / 2 * 0.22; t > dur - 1.5 -> max(0.0, (dur - t) / 1.5) * 0.22; else -> 0.22 }
            b.add(s + i, nz + sin(2 * PI * ph) * subEnv)
        }
    }

    fun zap(b: Buf, t0: Double, rnd: Random, level: Double = 1.0) {
        val s = (t0 * SR).toInt()
        val bp = Bq().bp(2200 + rnd.nextDouble() * 3200, 3.5)
        val lp = Bq().lp(5500.0)
        val gates = ArrayList<DoubleArray>()
        var tt = 0.0
        repeat(4 + rnd.nextInt(5)) {
            tt += 0.008 + rnd.nextDouble() * 0.03
            gates.add(doubleArrayOf(tt, tt + 0.006 + rnd.nextDouble() * 0.012, 0.12 + rnd.nextDouble() * 0.22))
        }
        val end = tt + 0.08
        val sqf = 90 + rnd.nextDouble() * 60
        var ph = 0.0
        for (i in 0 until (end * SR).toInt()) {
            val t = i.toDouble() / SR
            var g = 0.0
            for (gg in gates) if (t >= gg[0] && t < gg[1]) g = gg[2]
            val nz = lp.run(bp.run(rnd.nextDouble() * 2 - 1) * g * 3.0)
            ph += sqf / SR
            val sqv = sq(ph) * 0.025 * exp(ln(0.0001 / 0.025) * t / (tt + 0.05))
            b.add(s + i, (nz + sqv) * level)
        }
    }

    fun riser(b: Buf, t0: Double, len: Double, rnd: Random) {
        val s = (t0 * SR).toInt(); val bp = Bq(); val lp = Bq().lp(1400.0)
        var ph = 0.0
        for (i in 0 until ((len + 0.05) * SR).toInt()) {
            val t = i.toDouble() / SR
            if (i % 32 == 0) bp.bp(expSweep(t, len, 200.0, 3800.0), 2.0)
            val g = if (t < len * 0.9) t / (len * 0.9) else max(0.0, 1 - (t - len * 0.9) / (len * 0.1 + 0.05))
            ph += expSweep(t, len, 45.0, 340.0) / SR
            b.add(s + i, bp.run(rnd.nextDouble() * 2 - 1) * g * 0.5 * 2.2 + lp.run(saw(ph)) * g * 0.08)
        }
    }

    fun boom(b: Buf, t0: Double, rnd: Random) {
        val s = (t0 * SR).toInt(); val lp = Bq(); var ph = 0.0
        for (i in 0 until (3.3 * SR).toInt()) {
            val t = i.toDouble() / SR
            ph += expSweep(t, 1.8, 130.0, 24.0) / SR
            if (i % 32 == 0) lp.lp(expSweep(t, 1.6, 3000.0, 90.0), 0.7)
            b.add(s + i, sin(2 * PI * ph) * expDecay(t, 3.2) * 0.9 + lp.run(rnd.nextDouble() * 2 - 1) * 0.9 * expDecay(t, 2.8))
        }
    }

    /** Acorde escuro em ré menor com reverberação, uma batida grave e um toque por letra que acende. */
    fun reveal(b: Buf, t0: Double, n: Int, rnd: Random) {
        val dur = 6.2
        val bus = DoubleArray((dur * SR).toInt())
        // sopro que antecede o nome
        val hp = Bq().hp(300.0); val bpS = Bq()
        for (i in 0 until (0.52 * SR).toInt()) {
            val t = i.toDouble() / SR
            if (i % 32 == 0) bpS.bp(expSweep(t, 0.5, 200.0, 1400.0), 0.8)
            val g = if (t < 0.47) t / 0.47 * 0.3 else max(0.0, 0.3 * (1 - (t - 0.47) / 0.03))
            bus[i] += bpS.run(hp.run(rnd.nextDouble() * 2 - 1)) * g * 2.5
        }
        val o = 0.5
        val freqs = doubleArrayOf(73.42, 87.31, 110.0, 146.83)
        val phs = DoubleArray(8)
        val lp = Bq()
        val open = n * 0.12 + 0.4
        var sub = 0.0
        for (i in 0 until ((dur - o) * SR).toInt()) {
            val t = i.toDouble() / SR
            if (i % 32 == 0) {
                val fc = if (t < open) 180 + (950 - 180) * t / open else expSweep(t - open, 4.6 - open, 950.0, 240.0)
                lp.lp(fc, 1.4)
            }
            var v = 0.0
            for (k in 0 until 8) {
                val f = freqs[k / 2] * 2.0.pow((if (k % 2 == 0) -7 else 7) / 1200.0)
                phs[k] += f / SR; v += saw(phs[k])
            }
            val g = when { t < 0.35 -> t / 0.35 * 0.3; t < 2 -> 0.3; else -> 0.3 * expDecay(t - 2, 2.8) }
            sub += expSweep(t, 2.6, 58.0, 34.0) / SR
            val sg = if (t < 0.06) t / 0.06 * 0.6 else 0.6 * expDecay(t - 0.06, 3.5)
            bus[(o * SR).toInt() + i] += lp.run(v / 8.0) * g * 2.2 + sin(2 * PI * sub) * sg
        }
        val notes = doubleArrayOf(146.83, 174.61, 196.0, 220.0, 261.63, 293.66, 349.23, 392.0, 440.0, 523.25)
        for (k in 0 until n) {
            val ti = o + k * 0.12; val si = (ti * SR).toInt()
            val bpn = Bq().bp(420.0 + k * 85, 7.0); val nl = Bq().lp(1200.0)
            var ph = 0.0
            for (i in 0 until (0.95 * SR).toInt()) {
                val t = i.toDouble() / SR
                val ng = if (t < 0.015) t / 0.015 * 0.16 else 0.16 * expDecay(t - 0.015, 0.3)
                val og = if (t < 0.02) t / 0.02 * 0.07 else 0.07 * expDecay(t - 0.02, 0.88)
                ph += notes[k % notes.size] / SR
                val idx = si + i
                if (idx < bus.size) bus[idx] += bpn.run(rnd.nextDouble() * 2 - 1) * ng * 6 + nl.run(tri(ph)) * og
            }
        }
        val te = o + n * 0.12 + 0.25; var gp = 0.0
        for (i in 0 until (2.1 * SR).toInt()) {
            val t = i.toDouble() / SR; gp += 1174.66 / SR
            val g = if (t < 0.05) t / 0.05 * 0.025 else 0.025 * expDecay(t - 0.05, 1.95)
            val idx = (te * SR).toInt() + i
            if (idx < bus.size) bus[idx] += sin(2 * PI * gp) * g
        }
        // reverberação simples (Schroeder): 4 pentes + 2 passa-tudo
        val wet = reverb(bus)
        val s = (t0 * SR).toInt()
        for (i in bus.indices) b.add(s + i, bus[i] + wet[i] * 0.5)
    }

    private fun reverb(x: DoubleArray): DoubleArray {
        val sc = SR / 44100.0
        val combs = intArrayOf(1557, 1617, 1491, 1422).map { (it * sc * 1.6).toInt() }
        val out = DoubleArray(x.size)
        for (d in combs) {
            val buf = DoubleArray(d); var p = 0; var lpS = 0.0
            for (i in x.indices) {
                val y = buf[p]
                lpS = y * 0.7 + lpS * 0.3
                buf[p] = x[i] + lpS * 0.86
                p = (p + 1) % d
                out[i] += y * 0.25
            }
        }
        for (d0 in intArrayOf(225, 556)) {
            val d = (d0 * sc).toInt().coerceAtLeast(1); val buf = DoubleArray(d); var p = 0
            for (i in out.indices) {
                val bv = buf[p]; val y = -out[i] + bv
                buf[p] = out[i] + bv * 0.5
                p = (p + 1) % d
                out[i] = y
            }
        }
        return out
    }

    /**
     * Abafa a eletricidade: tira o chiado agudo (dois passa-baixas), deixa um corpo grave
     * e joga num eco escuro e longo, como um estalo ouvido de dentro de uma nave.
     */
    private fun muffle(src: Buf, dst: Buf) {
        val l1 = Bq().lp(820.0, 0.6); val l2 = Bq().lp(1100.0, 0.7); val body = Bq().lp(180.0, 0.9)
        val dry = DoubleArray(src.d.size)
        for (i in dry.indices) { val x = src.d[i].toDouble(); val m = l2.run(l1.run(x)); dry[i] = m * 1.5 + body.run(x) * 1.1 }
        val wet = reverb(dry); val dark = Bq().lp(600.0)
        for (i in dry.indices) dst.add(i, dry[i] * 0.75 + dark.run(wet[i]) * 0.7)
    }

    fun buzz(b: Buf, t0: Double, len: Double) {
        val s = (t0 * SR).toInt(); val bp = Bq().bp(900.0, 1.8); var p1 = 0.0; var p2 = 0.0; var pl = 0.0
        for (i in 0 until (len * SR).toInt()) {
            val t = i.toDouble() / SR
            p1 += 100.0 / SR; p2 += 150.0 / SR; pl += 13.0 / SR
            val env = ramp(t, 0.0, 0.15, 0.0, 1.0) * ramp(t, len - 0.05, len, 1.0, 0.0)
            val g = (0.05 + 0.035 * sq(pl)) * env
            b.add(s + i, bp.run(saw(p1) + sq(p2)) * g * 2.0)
        }
    }

    fun powerDown(b: Buf, t0: Double, rnd: Random) {
        val s = (t0 * SR).toInt(); val lp = Bq().lp(600.0); val nl = Bq().lp(1800.0); var p = 0.0; var ps = 0.0
        for (i in 0 until (0.62 * SR).toInt()) {
            val t = i.toDouble() / SR
            p += expSweep(t, 0.55, 160.0, 22.0) / SR; ps += 45.0 / SR
            b.add(s + i, lp.run(saw(p)) * 0.3 * expDecay(t, 0.6) * 1.6 + nl.run(rnd.nextDouble() * 2 - 1) * 0.35 * expDecay(t, 0.08) * 1.5 + sin(2 * PI * ps) * 0.5 * expDecay(t, 0.5))
        }
    }

    fun holeOpen(b: Buf, t0: Double, rnd: Random) {
        val L = 1.9; val s = (t0 * SR).toInt(); val bp = Bq(); var p1 = 0.0; var p2 = 0.0
        for (i in 0 until ((L + 0.05) * SR).toInt()) {
            val t = i.toDouble() / SR
            if (i % 32 == 0) bp.bp(300 + 180 * sin(2 * PI * 2.2 * t), 2.5)
            val g = when { t < 0.45 -> t / 0.45 * 0.32; t < L - 0.4 -> 0.32; else -> max(0.0, 0.32 * (L - t) / 0.4) }
            p1 += 36.0 / SR; p2 += 36.6 / SR
            val og = if (t < 0.5) t / 0.5 * 0.4 else max(0.0, 0.4 * (L - t) / (L - 0.5))
            b.add(s + i, bp.run(rnd.nextDouble() * 2 - 1) * g * 3.0 + (sin(2 * PI * p1) + sin(2 * PI * p2)) * og * 0.5)
        }
    }

    fun implode(b: Buf, t0: Double, rnd: Random) {
        val s = (t0 * SR).toInt(); val lp = Bq(); var p = 0.0
        val ps = DoubleArray(3); val tf = doubleArrayOf(261.63, 329.63, 392.0)
        for (i in 0 until (2.1 * SR).toInt()) {
            val t = i.toDouble() / SR
            var v = 0.0
            if (t < 0.36) {
                if (i % 32 == 0) lp.lp(expSweep(t, 0.35, 3000.0, 120.0), 1.0)
                val g = if (t < 0.3) 0.05 + 0.3 * t / 0.3 else max(0.0, 0.35 * (0.36 - t) / 0.06)
                v += lp.run(rnd.nextDouble() * 2 - 1) * g * 1.6
            }
            if (t >= 0.32) {
                val tt = t - 0.33
                p += expSweep(tt, 0.77, 90.0, 30.0) / SR
                v += sin(2 * PI * p) * (if (tt < 0.03) max(0.0, tt + 0.01) / 0.04 * 0.6 else 0.6 * expDecay(tt - 0.03, 1.05))
            }
            if (t >= 0.4) {
                for (k in 0 until 3) {
                    ps[k] += tf[k] / 2 / SR
                    val tk = t - 0.4
                    val a = 0.1 + k * 0.05
                    v += tri(ps[k]) * (if (tk < a) tk / a * 0.03 else 0.03 * expDecay(tk - a, 1.6 - a))
                }
            }
            b.add(s + i, v)
        }
    }

    // ---------- trilhas prontas ----------
    fun introTrack(plan: IntroPlan): ShortArray {
        val rnd = Random(7)
        val b = Buf(IntroPlan.END + 0.3)
        hole(b, 0.0, IntroPlan.TB + 0.1, rnd)
        // eletricidade vai para uma trilha própria, abafada depois (no espaço o som chega surdo e distante)
        val e = Buf(IntroPlan.END + 0.3)
        plan.holeBolts.forEach { zap(e, it, rnd, 0.9) }
        riser(b, 3.55, 1.15, rnd)
        boom(b, IntroPlan.TB, rnd)
        zap(e, IntroPlan.TB + 0.14, rnd); zap(e, IntroPlan.TB + 0.32, rnd)
        reveal(b, 5.0, 10, rnd)
        for (i in 0 until 10) zap(e, IntroPlan.NAME + i * 0.12, rnd, 0.7)
        buzz(e, 6.65, 1.9)
        plan.arcs.filter { it.sound }.forEach { zap(e, it.t, rnd, 0.6) }
        muffle(e, b)
        powerDown(b, IntroPlan.BLACK, rnd)
        return toPcm(b.d)
    }

    fun openingTrack(): ShortArray {
        val rnd = Random(11)
        val b = Buf(2.6)
        holeOpen(b, 0.0, rnd)
        implode(b, Opening.TC - 0.15, rnd)
        return toPcm(b.d)
    }

    /** Tecla do teclado virtual: batida abafada e encorpada. */
    fun kbPcm(): ShortArray {
        val b = Buf(0.12); val f = Bq().lp(520.0, 1.2); val rnd = Random(3); var p = 0.0
        for (i in b.d.indices) {
            val t = i.toDouble() / SR
            p += expSweep(t, 0.04, 224.0, 140.0) / SR
            b.d[i] = (f.run(rnd.nextDouble() * 2 - 1) * 0.10 * expDecay(t, 0.07) * 4 + sin(2 * PI * p) * 0.18 * expDecay(t, 0.12) * 2).toFloat()
        }
        return toPcm(b.d)
    }

    fun tickPcm(): ShortArray {
        val b = Buf(0.08); val lp = Bq().lp(900.0, 0.7); var p = 0.0
        for (i in b.d.indices) {
            val t = i.toDouble() / SR
            p += expSweep(t, 0.05, 560.0, 420.0) / SR
            b.d[i] = (lp.run(tri(p)) * 0.06 * expDecay(t, 0.07) * 4).toFloat()
        }
        return toPcm(b.d)
    }

    fun okPcm(): ShortArray {
        val b = Buf(0.2); val lp = Bq().lp(1000.0, 0.8); var p = 0.0; var q = 0.0
        for (i in b.d.indices) {
            val t = i.toDouble() / SR
            p += (330 + 110 * min(1.0, t / 0.07)) / SR
            q += expSweep(t, 0.06, 140.0, 90.0) / SR
            b.d[i] = (lp.run(tri(p)) * 0.08 * expDecay(t, 0.18) * 3 + sin(2 * PI * q) * 0.12 * expDecay(t, 0.12) * 2).toFloat()
        }
        return toPcm(b.d)
    }

    private fun toPcm(d: FloatArray): ShortArray {
        var peak = 0f
        for (v in d) peak = max(peak, kotlin.math.abs(v))
        val norm = if (peak > 0.98f) 0.98 / peak else 1.0
        return ShortArray(d.size) { (tanh(d[it] * norm * 1.1) * 32000).toInt().toShort() }
    }
}

/** Toca os sons gerados. Cada som tem sua faixa própria, para não atrasar a navegação. */
object Sfx {
    @Volatile var enabled = true
    @Volatile var gain = 0.7f
    private val exec = Executors.newSingleThreadExecutor()
    private var tick: AudioTrack? = null
    private var ok: AudioTrack? = null
    private var long: AudioTrack? = null
    private var kbT: AudioTrack? = null

    fun setVolume(level: Int) { gain = when (level) { 0 -> 0f; 1 -> 0.35f; 3 -> 1f; else -> 0.7f } }

    private fun make(pcm: ShortArray): AudioTrack {
        // AudioTrack.Builder só existe no Android 6+ (API 23); antes disso, construtor antigo
        val t = if (android.os.Build.VERSION.SDK_INT < 23) {
            @Suppress("DEPRECATION")
            AudioTrack(android.media.AudioManager.STREAM_MUSIC, Synth.SR, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, pcm.size * 2, AudioTrack.MODE_STATIC)
        } else {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Synth.SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }
        t.write(pcm, 0, pcm.size)
        return t
    }

    private fun replay(t: AudioTrack) {
        try {
            t.setVolume(gain)
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Throwable) {}
    }

    fun warmUp() = exec.execute {
        try { if (tick == null) tick = make(Synth.tickPcm()); if (ok == null) ok = make(Synth.okPcm()); if (kbT == null) kbT = make(Synth.kbPcm()) } catch (e: Throwable) {}
    }

    fun tick() { if (enabled && gain > 0f) exec.execute { tick?.let { replay(it) } } }
    fun ok() { if (enabled && gain > 0f) exec.execute { ok?.let { replay(it) } } }
    fun kb() { if (enabled && gain > 0f) exec.execute { kbT?.let { replay(it) } } }

    /** Toca uma trilha longa (abertura) a partir de "fromSec" segundos, para ficar junto com a imagem. */
    fun playLong(pcm: ShortArray?, fromSec: Float = 0f) {
        stopLong()
        if (!enabled || gain <= 0f || pcm == null) return
        val frame = (fromSec * Synth.SR).toInt()
        if (frame >= pcm.size - Synth.SR / 10) return
        try {
            val t = make(pcm)
            t.setVolume(gain)
            if (frame > 0) t.setPlaybackHeadPosition(frame)
            t.play()
            long = t
        } catch (e: Throwable) {}
    }

    fun stopLong() {
        val t = long ?: return
        long = null
        try { t.stop(); t.release() } catch (e: Throwable) {}
    }
}

/**
 * Trilhas da abertura prontas antes de precisar delas. Na primeira vez são geradas
 * em segundo plano e guardadas no aparelho; nas próximas aberturas só são lidas do arquivo.
 */
object Tracks {
    val plan = IntroPlan()
    @Volatile var intro: ShortArray? = null
    @Volatile var opening: ShortArray? = null
    @Volatile private var started = false

    fun prepare(c: android.content.Context) {
        if (started) return
        started = true
        val dir = java.io.File(c.cacheDir, "sons").apply { mkdirs() }
        val ver = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "0" } catch (e: Exception) { "0" }
        kotlin.concurrent.thread(name = "sons-abertura", priority = Thread.MAX_PRIORITY) {
            intro = load(dir, "abertura-$ver") ?: try { Synth.introTrack(plan).also { save(dir, "abertura-$ver", it) } } catch (e: Throwable) { null }
            opening = load(dir, "buraco-$ver") ?: try { Synth.openingTrack().also { save(dir, "buraco-$ver", it) } } catch (e: Throwable) { null }
            dir.listFiles()?.forEach { if (!it.name.endsWith("-$ver.pcm")) it.delete() }
        }
    }

    private fun load(dir: java.io.File, name: String): ShortArray? = try {
        val f = java.io.File(dir, "$name.pcm")
        if (!f.exists() || f.length() < 1000) null else {
            val bytes = f.readBytes()
            val out = ShortArray(bytes.size / 2)
            java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(out)
            out
        }
    } catch (e: Throwable) { null }

    private fun save(dir: java.io.File, name: String, pcm: ShortArray) {
        try {
            val bb = java.nio.ByteBuffer.allocate(pcm.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            bb.asShortBuffer().put(pcm)
            val tmp = java.io.File(dir, "$name.tmp")
            tmp.writeBytes(bb.array())
            tmp.renameTo(java.io.File(dir, "$name.pcm"))
        } catch (e: Throwable) {}
    }
}
