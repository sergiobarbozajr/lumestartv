package com.lumestar.tv

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import androidx.media3.common.Effect
import androidx.media3.common.Format
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.io.File
import kotlin.math.roundToInt

/**
 * IA de imagem do reprodutor (o mesmo sistema do AstroLume 0.25).
 *  - Vídeo abaixo de 1080p é reconstruído na placa de vídeo para 1080p: ampliação bicúbica (Catmull-Rom),
 *    que recria os pixels que faltam a partir dos vizinhos, + nitidez adaptativa (estilo AMD FidelityFX CAS) + tom suave.
 *  - Em TVs com super-resolução neural no chip (Realtek NNSR, MediaTek AI-SR, Amlogic AI-PQ), o 1080p entregue
 *    é levado a 4K pelo próprio chip; vídeo que já é 1080p ou 4K vai direto, sem processamento.
 *  - Se a TV começar a perder quadros com o reforço, o reprodutor desliga só o reforço e segue tocando.
 */
@UnstableApi
object ImageAi {
    /** tier: 2 = Ultra 1440p, 1 = Alta 1080p, 0 = sem reforço na placa */
    class Plan(val key: String, val effects: List<Effect>, val label: String, val tier: Int, val outH: Int)

    val hwAi: Boolean by lazy {
        val libs = listOf("librtk_libvip_nnsr.so", "libaisr.so", "libmtk_aisr.so", "libpq_aisr.so", "libamlogic_aipq.so", "libaipq.so")
        val dirs = listOf("/vendor/lib", "/vendor/lib64", "/system/lib", "/system/lib64")
        try { libs.any { l -> dirs.any { d -> File("$d/$l").exists() } } } catch (e: Throwable) { false }
    }

    val chip: String by lazy {
        try {
            val soc = if (android.os.Build.VERSION.SDK_INT >= 31) android.os.Build.SOC_MANUFACTURER + " " + android.os.Build.SOC_MODEL else ""
            soc.trim().ifBlank { android.os.Build.HARDWARE }
        } catch (e: Throwable) { android.os.Build.HARDWARE }
    }

    /** O máximo que este aparelho aguenta, medido pelo diagnóstico (GpuProbe). */
    fun maxTier(): Int = GpuProbe.result?.maxTier ?: 1

    /**
     * mode: "auto" (a IA decide), "1440", "1080" ou "off". cap: limite do disjuntor neste vídeo.
     * compare: o botão Comparar está mostrando o original.
     */

    /** Painel HVA (VA) da TCL: escolhido sozinho em TVs TCL quando o perfil está em Automático. */
    val tclPanel: Boolean by lazy { (android.os.Build.MANUFACTURER + " " + android.os.Build.BRAND).lowercase().contains("tcl") }
    fun panelHva(): Boolean = when (PP.panel) { "hva" -> true; "outro" -> false; else -> tclPanel }

    /**
     * mode: "off" desliga; qualquer outro valor liga. cap: disjuntor (0 = pausada neste vídeo).
     * compare: tela dividida (esquerda original, direita com IA). live: TV ao vivo vai direto, sem IA.
     */
    fun plan(mode: String, f: Format?, cap: Int, compare: Boolean, live: Boolean = false): Plan {
        val pn = if (panelHva()) 1f else 0f; val sp = if (compare) 0.5f else 0f
        val h = f?.height ?: 0
        val hdr = f?.colorInfo?.let { it.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_ST2084 || it.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_HLG } ?: false
        if (hdr) return Plan("hdr", emptyList(), if (hwAi) "IA da TV · HDR" else "HDR original", 0, 0)
        if (h <= 0) return Plan("wait", emptyList(), "", 0, 0)
        if (live) return Plan("live", emptyList(), if (hwAi) "Direto · IA da TV" else "Direto", 0, h)
        // Na TV: sem ampliação (upscale pesado fica para a versão de PC). A IA trabalha na resolução do vídeo:
        // nitidez leve + realce de cor por cena; o chip da TV cuida da escala e do movimento (MEMC).
        val sharp = if (PP.aiSharp) 0.4f else 0f
        val depth = if (PP.aiDepth) 0.3f else 0f
        val tag = (if (compare) "c" else "") + sharp + depth + pn
        fun fxNative() = listOf(AiSharpen(0, sharp, 0.3f, true, depth, sp, pn))
        if (mode == "off") return if (compare) Plan("off$tag", fxNative(), "Comparando: original | IA", 0, h) else Plan("off", emptyList(), "", 0, 0)
        val src = "${h}p"
        val chipTv = if (hwAi) " + IA da TV" else ""
        val style = when (PP.colorStyle) { "vivido" -> "Vívido"; "cinema" -> "Cinema"; else -> "Fiel" }
        val p = if (cap <= 0) Plan("none", emptyList(), "IA pausada (TV no limite)$chipTv", 0, h)
        else Plan("scene$tag", fxNative(), "IA $style · $src$chipTv", 1, h)
        return if (compare && p.effects.isEmpty()) Plan("cmp$tag", fxNative(), "Comparando: original | IA", p.tier, h)
        else if (compare) Plan(p.key, p.effects, "Comparando: original | " + p.label, p.tier, p.outH) else p
    }
}

/**
 * Diagnóstico de imagem: mede de verdade quanto a placa de vídeo leva para reconstruir um quadro
 * (720p → 1080p completo e 720p → 1440p leve), escondido, uma vez por versão do app. ~2–3 s.
 */
@UnstableApi
object GpuProbe {
    class Result(val gpu: String, val gl: String, val ms1080: Float, val ms1440: Float, val msLite: Float) {
        // margem: 1080p e 1440p a 30 quadros/s com sobra para o vídeo (orçamento real ~33 ms; usamos 12 e 10)
        val maxTier get() = when { ms1440 in 0.1f..10f -> 2; ms1080 in 0.1f..12f -> 1; else -> 0 }
        fun line() = "Placa: $gpu · 1080p: ${fmt(ms1080)} · 1440p: ${fmt(ms1440)}"
        private fun fmt(v: Float) = if (v <= 0f || v >= 99f) "falhou" else "%.1f ms".format(v)
    }
    @Volatile var result: Result? = null
    @Volatile var running = false

    fun load(sp: android.content.SharedPreferences, ver: String) {
        if (sp.getString("sondaVer", "") != ver) return
        result = Result(sp.getString("sondaGpu", "") ?: "", sp.getString("sondaGl", "") ?: "", sp.getFloat("sonda1080", 99f), sp.getFloat("sonda1440", 99f), sp.getFloat("sondaLeve", 99f))
    }

    /** Roda em segundo plano; done chega na linha da tela. */
    fun run(sp: android.content.SharedPreferences, ver: String, done: (() -> Unit)? = null) {
        if (running) return
        running = true
        Thread({
            val r = try { measure() } catch (e: Throwable) { Result("?", "", 99f, 99f, 99f) }
            result = r
            sp.edit().putString("sondaVer", ver).putString("sondaGpu", r.gpu).putString("sondaGl", r.gl).putFloat("sonda1080", r.ms1080).putFloat("sonda1440", r.ms1440).putFloat("sondaLeve", r.msLite).apply()
            running = false
            android.os.Handler(android.os.Looper.getMainLooper()).post { PP.rev++; done?.invoke() }
        }, "diagnostico-imagem").start()
    }

    private fun measure(): Result {
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2); EGL14.eglInitialize(dpy, v, 0, v, 1)
        val cfgs = arrayOfNulls<EGLConfig>(1); val n = IntArray(1)
        EGL14.eglChooseConfig(dpy, intArrayOf(EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_NONE), 0, cfgs, 0, 1, n, 0)
        val ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        val surf = EGL14.eglCreatePbufferSurface(dpy, cfgs[0], intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0)
        EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
        try {
            val gpu = GLES20.glGetString(GLES20.GL_RENDERER) ?: "?"; val gl = GLES20.glGetString(GLES20.GL_VERSION) ?: ""
            // quadro de teste 1280x720 com textura variada
            val sw = 1280; val sh = 720
            val buf = java.nio.ByteBuffer.allocateDirect(sw * sh * 4)
            val rnd = java.util.Random(3); val row = ByteArray(sw * 4)
            for (y in 0 until sh) { rnd.nextBytes(row); buf.put(row) }
            buf.position(0)
            val src = tex(sw, sh, buf)
            fun bench(fs: String, w: Int, h: Int): Float = try {
                val prog = AiSharpenShaders.program(fs)
                val dst = tex(w, h, null); val fb = IntArray(1); GLES20.glGenFramebuffers(1, fb, 0)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb[0]); GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, dst, 0)
                GLES20.glViewport(0, 0, w, h)
                val draw = { AiSharpenShaders.draw(prog, src, 1f / sw, 1f / sh, 0.5f, 0.45f) }
                repeat(3) { draw() }; GLES20.glFinish()
                val t0 = System.nanoTime(); val frames = 20
                repeat(frames) { draw(); GLES20.glFinish() }
                val ms = (System.nanoTime() - t0) / 1e6f / frames
                GLES20.glDeleteFramebuffers(1, fb, 0); GLES20.glDeleteTextures(1, intArrayOf(dst), 0); GLES20.glDeleteProgram(prog)
                ms
            } catch (e: Throwable) { 99f }
            return Result(gpu, gl, bench(AiSharpenShaders.FS, 1920, 1080), bench(AiSharpenShaders.FS_LITE, 2560, 1440), bench(AiSharpenShaders.FS_LITE, 1280, 720))
        } finally {
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surf); EGL14.eglDestroyContext(dpy, ctx)
        }
    }

    private fun tex(w: Int, h: Int, data: java.nio.ByteBuffer?): Int {
        val t = IntArray(1); GLES20.glGenTextures(1, t, 0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data)
        return t[0]
    }
}

/** Reconstrução bicúbica (Catmull-Rom) + nitidez adaptativa (estilo CAS) + tom suave, na GPU. */
@UnstableApi
class AiSharpen(private val targetHeight: Int, private val sharp: Float, private val tone: Float, private val lite: Boolean = false, private val detail: Float = 0f, private val split: Float = 0f, private val panel: Float = 0f) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        AiSharpenProgram(useHdr, targetHeight, sharp, tone, lite, detail, split, panel)
}

@UnstableApi
private class AiSharpenProgram(useHdr: Boolean, private val targetHeight: Int, private val sharp: Float, private val tone: Float, lite: Boolean, private val detail: Float, private val split: Float, private val panel: Float) :
    BaseGlShaderProgram(useHdr, 1) {

    private val program: GlProgram = try {
        GlProgram(AiSharpenShaders.VS, if (lite) AiSharpenShaders.FS_LITE else AiSharpenShaders.FS).also {
            it.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        }
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var texel = floatArrayOf(1f / 1920f, 1f / 1080f)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texel = floatArrayOf(1f / inputWidth, 1f / inputHeight)
        if (targetHeight > inputHeight) {
            var w = (inputWidth.toFloat() * targetHeight / inputHeight).roundToInt()
            if (w % 2 == 1) w++
            return Size(w, targetHeight)
        }
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexel", texel)
            program.setFloatUniform("uSharp", sharp)
            program.setFloatUniform("uDetail", detail)
            program.setFloatUniform("uSplit", split)
            program.setFloatUniform("uPanel", panel)
            program.setFloatUniform("uVib", SceneAi.vib)
            program.setFloatUniform("uSkin", SceneAi.skin)
            program.setFloatUniform("uShadow", SceneAi.shadow)
            program.setFloatUniform("uWarm", SceneAi.warm)
            program.setFloatUniform("uCurve", SceneAi.curve)
            program.setFloatUniform("uTone", tone)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}

/** Shaders da reconstrução (também usados pelo diagnóstico). FS_LITE: sem a ampliação bicúbica (leitura bilinear do próprio hardware), ~60% menos leituras. */
object AiSharpenShaders {
        const val VS = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
        const val FS = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uSharp;
uniform float uTone;
uniform float uDetail;
uniform float uSplit;
uniform float uPanel;
uniform float uVib;
uniform float uSkin;
uniform float uShadow;
uniform float uWarm;
uniform float uCurve;
varying vec2 vTexSamplingCoord;

vec3 tx(vec2 p) { return texture2D(uTexSampler, p).rgb; }

// Catmull-Rom com 9 leituras bilineares
vec3 bicubic(vec2 uv) {
  vec2 sz = 1.0 / uTexel;
  vec2 sp = uv * sz;
  vec2 t1 = floor(sp - 0.5) + 0.5;
  vec2 f = sp - t1;
  vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
  vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
  vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
  vec2 w3 = f * f * (-0.5 + 0.5 * f);
  vec2 w12 = w1 + w2;
  vec2 t0 = (t1 - 1.0) * uTexel;
  vec2 t3 = (t1 + 2.0) * uTexel;
  vec2 t12 = (t1 + w2 / w12) * uTexel;
  vec3 r = tx(vec2(t0.x, t0.y)) * w0.x * w0.y + tx(vec2(t12.x, t0.y)) * w12.x * w0.y + tx(vec2(t3.x, t0.y)) * w3.x * w0.y;
  r += tx(vec2(t0.x, t12.y)) * w0.x * w12.y + tx(vec2(t12.x, t12.y)) * w12.x * w12.y + tx(vec2(t3.x, t12.y)) * w3.x * w12.y;
  r += tx(vec2(t0.x, t3.y)) * w0.x * w3.y + tx(vec2(t12.x, t3.y)) * w12.x * w3.y + tx(vec2(t3.x, t3.y)) * w3.x * w3.y;
  return clamp(r, 0.0, 1.0);
}

void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = bicubic(uv);
  vec3 n = tx(uv + vec2(0.0, -uTexel.y));
  vec3 s = tx(uv + vec2(0.0, uTexel.y));
  vec3 e = tx(uv + vec2(uTexel.x, 0.0));
  vec3 w = tx(uv + vec2(-uTexel.x, 0.0));
  // nitidez adaptativa: forte em bordas suaves, fraca onde já há contraste (sem halo nem ruído)
  vec3 mn = min(c, min(min(n, s), min(e, w)));
  vec3 mx = max(c, max(max(n, s), max(e, w)));
  vec3 amp = sqrt(clamp(min(mn, 1.0 - mx) / max(mx, vec3(0.0001)), 0.0, 1.0));
  vec3 wt = -amp * mix(0.125, 0.24, clamp(uSharp, 0.0, 1.0));
  vec3 o = clamp((c + (n + s + e + w) * wt) / (1.0 + 4.0 * wt), 0.0, 1.0);
  // reforço de detalhe sem halo: realce local limitado à faixa dos vizinhos (+3%)
  float yl = dot(c, vec3(0.2126, 0.7152, 0.0722));
  float dk = uPanel > 0.5 ? smoothstep(0.02, 0.18, yl) : 1.0; // painel HVA: menos realce nas sombras
  vec3 bl = (n + s + e + w) * 0.25;
  o = clamp(o + (o - bl) * uDetail * dk, mn - 0.03, mx + 0.03);
  // tom suave: um pouco mais de contraste no meio e sombras levemente abertas
  float y = dot(o, vec3(0.2126, 0.7152, 0.0722));
  vec3 sc = o * o * (3.0 - 2.0 * o);
  o = mix(o, sc, uTone * 0.22);
  o += uTone * 0.035 * (1.0 - o) * (1.0 - smoothstep(0.0, 0.35, y));
  // painel HVA (VA): sobe de leve o quase-preto (menos rastro escuro e sombras com detalhe)
  // IA de cena: cores vivas e fiéis. Realça mais as cores apagadas (vibrance) e protege o tom de pele.
  float lu = dot(o, vec3(0.2126, 0.7152, 0.0722));
  float sat = max(o.r, max(o.g, o.b)) - min(o.r, min(o.g, o.b));
  float cr = 0.5 * o.r - 0.4187 * o.g - 0.0813 * o.b;
  float cb = -0.1687 * o.r - 0.3313 * o.g + 0.5 * o.b;
  float skin = smoothstep(0.02, 0.06, cr) * (1.0 - smoothstep(0.17, 0.23, cr)) * smoothstep(-0.22, -0.12, cb) * (1.0 - smoothstep(-0.02, 0.04, cb));
  float vib = uVib * (1.0 - sat) * (1.0 - skin * uSkin);
  o = mix(vec3(lu), o, 1.0 + vib);
  o += uShadow * 0.06 * (1.0 - smoothstep(0.0, 0.3, lu)); // cena escura: abre as sombras sem lavar os pretos
  // estilo de cor: Vívido e Cinema dão mais contraste no meio; Cinema esquenta as luzes e esfria as sombras (sem mexer na pele)
  vec3 sc2 = o * o * (3.0 - 2.0 * o);
  o = mix(o, sc2, uCurve * (1.0 - skin * 0.6));
  o += uWarm * (vec3(0.022, 0.006, -0.02) * smoothstep(0.45, 1.0, lu) + vec3(-0.012, 0.0, 0.016) * (1.0 - smoothstep(0.0, 0.4, lu))) * (1.0 - skin);
  if (uPanel > 0.5) o = o * 0.975 + 0.025 * (1.0 - smoothstep(0.0, 0.25, o));
  vec3 res = clamp(o, 0.0, 1.0);
  if (uSplit > 0.0) {
    if (uv.x < uSplit) res = tx(uv);
    if (abs(uv.x - uSplit) < uTexel.x * 1.2) res = vec3(1.0);
  }
  gl_FragColor = vec4(res, 1.0);
}
"""
    val FS_LITE: String = FS.replace("vec3 c = bicubic(uv);", "vec3 c = tx(uv);")

    fun program(fs: String): Int {
        fun sh(type: Int, src: String): Int { val x = GLES20.glCreateShader(type); GLES20.glShaderSource(x, src); GLES20.glCompileShader(x); val ok = IntArray(1); GLES20.glGetShaderiv(x, GLES20.GL_COMPILE_STATUS, ok, 0); if (ok[0] == 0) throw RuntimeException(GLES20.glGetShaderInfoLog(x)); return x }
        val p = GLES20.glCreateProgram(); GLES20.glAttachShader(p, sh(GLES20.GL_VERTEX_SHADER, VS)); GLES20.glAttachShader(p, sh(GLES20.GL_FRAGMENT_SHADER, fs)); GLES20.glLinkProgram(p)
        return p
    }

    private val quad: java.nio.FloatBuffer = java.nio.ByteBuffer.allocateDirect(4 * 4 * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 0f, 1f, -1f, 1f, 0f, 1f, 1f, 1f, 0f, 1f)); position(0) }

    fun draw(p: Int, tex: Int, tx: Float, ty: Float, sharp: Float, tone: Float) {
        GLES20.glUseProgram(p)
        val a = GLES20.glGetAttribLocation(p, "aFramePosition"); GLES20.glEnableVertexAttribArray(a); GLES20.glVertexAttribPointer(a, 4, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(p, "uTexSampler"), 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(p, "uTexel"), tx, ty)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uSharp"), sharp); GLES20.glUniform1f(GLES20.glGetUniformLocation(p, "uTone"), tone)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
}


/**
 * IA de cena (só em filmes e séries): a cada 2 s olha uma miniatura de 64×36 da imagem e reconhece
 * o tipo de cena pela distribuição de cores — pessoas (tom de pele), paisagem (verde e céu), cena escura
 * ou ambiente/objetos — e ajusta realce de cor, proteção de pele e sombras. Os valores mudam aos poucos
 * (sem pulos) e o shader lê na hora, sem recriar o vídeo.
 */
object SceneAi {
    @Volatile var vib = 0f
    @Volatile var skin = 1f
    @Volatile var shadow = 0f
    @Volatile var warm = 0f
    @Volatile var curve = 0f
    @Volatile var on = false
    var scene = ""
        private set
    private var tVib = 0f; private var tSkin = 1f; private var tShadow = 0f

    fun reset(enabled: Boolean) { on = enabled; vib = 0f; skin = 1f; shadow = 0f; warm = 0f; curve = 0f; tVib = if (enabled) .22f else 0f; tSkin = 1f; tShadow = 0f; scene = "" }

    fun analyze(b: android.graphics.Bitmap) {
        if (!on) return
        val w = b.width; val h = b.height; if (w == 0 || h == 0) return
        var skinN = 0; var green = 0; var sky = 0; var lum = 0f; var satSum = 0f; var n = 0
        val hsv = FloatArray(3)
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val c = b.getPixel(x, y)
                val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val bl = (c and 255) / 255f
                val cr = .5f * r - .4187f * g - .0813f * bl; val cb = -.1687f * r - .3313f * g + .5f * bl
                if (cr in .03f..0.2f && cb in -.2f..0f && r > .2f) skinN++
                android.graphics.Color.colorToHSV(c, hsv)
                if (hsv[1] > .25f && hsv[2] > .2f) { if (hsv[0] in 70f..170f) green++; if (hsv[0] in 190f..240f && y < h / 2) sky++ }
                lum += .2126f * r + .7152f * g + .0722f * bl; satSum += hsv[1]; n++
                x += 2
            }
            y += 2
        }
        if (n == 0) return
        val fSkin = skinN.toFloat() / n; val fNat = (green + sky).toFloat() / n; val l = lum / n; val s = satSum / n
        // cores já muito vivas (desenho animado, show) recebem menos realce, para não estourar
        val tame = if (s > .55f) .5f else 1f
        val pessoas = PP.aiSkin && fSkin > .10f
        val natureza = PP.aiNature && fNat > .25f
        when {
            l < .22f -> { scene = "Cena escura"; tVib = .15f; tSkin = 1f; tShadow = 1f }
            pessoas -> { scene = "Pessoas"; tVib = .18f; tSkin = 1f; tShadow = .3f }
            natureza -> { scene = "Paisagem"; tVib = .38f; tSkin = .6f; tShadow = .2f }
            else -> { scene = "Ambiente"; tVib = .26f; tSkin = .9f; tShadow = .25f }
        }
        if (!PP.aiSkin) tSkin = 0f
        tVib *= tame * when (PP.colorStyle) { "vivido" -> 1.6f; "cinema" -> .8f; else -> 1f }
        if (PP.aiFidelity) tVib = minOf(tVib, .3f) // trava: cores vivas, porém reais
    }

    /** Chamado a cada 0,5 s: aproxima os valores do alvo devagar (troca suave em ~2 s). */
    fun step() {
        if (!on) { vib = 0f; shadow = 0f; warm = 0f; curve = 0f; return }
        vib += (tVib - vib) * .25f; skin += (tSkin - skin) * .25f; shadow += (tShadow - shadow) * .25f
        val (w, c) = when (PP.colorStyle) { "vivido" -> 0f to .15f; "cinema" -> 1f to .3f; else -> 0f to 0f }
        warm += (w - warm) * .25f; curve += (c - curve) * .25f
    }
}
