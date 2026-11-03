package com.lumestar.tv

import android.content.Context
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
    class Plan(val key: String, val effects: List<Effect>, val label: String)

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

    fun summary(): String = "Chip: $chip · " + if (hwAi) "super-resolução neural no chip: sim" else "super-resolução no chip: não encontrada (o app usa a placa de vídeo)"

    /** on: "auto" liga o reforço; gpuOff: a TV perdeu quadros com ele neste vídeo. */
    fun plan(on: Boolean, f: Format?, gpuOff: Boolean): Plan {
        if (!on) return Plan("off", emptyList(), "")
        val h = f?.height ?: 0; val w = f?.width ?: 0
        val hdr = f?.colorInfo?.let { it.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_ST2084 || it.colorTransfer == androidx.media3.common.C.COLOR_TRANSFER_HLG } ?: false
        if (hdr) return Plan("hdr", emptyList(), if (hwAi) "IA da TV · HDR" else "HDR original")
        if (h <= 0) return Plan("wait", emptyList(), "")
        val src = "${h}p"
        if (gpuOff) return Plan("paused", emptyList(), "Reforço pausado (fluidez)")
        return if (hwAi) {
            if (h >= 900 || w >= 1600) Plan("pass", emptyList(), "IA da TV · 4K")
            else Plan("up1080", listOf(AiSharpen(1080, 0.55f, 0.5f)), "IA $src→1080p + IA da TV")
        } else when {
            h < 900 && w < 1600 -> Plan("up1080", listOf(AiSharpen(1080, 0.6f, 0.5f)), "IA $src→1080p")
            h <= 1100 -> Plan("sharp", listOf(AiSharpen(0, 0.4f, 0.4f)), "IA · nitidez 1080p")
            else -> Plan("pass", emptyList(), "4K original")
        }
    }
}

/** Reconstrução bicúbica (Catmull-Rom) + nitidez adaptativa (estilo CAS) + tom suave, na GPU. */
@UnstableApi
class AiSharpen(private val targetHeight: Int, private val sharp: Float, private val tone: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        AiSharpenProgram(useHdr, targetHeight, sharp, tone)
}

@UnstableApi
private class AiSharpenProgram(useHdr: Boolean, private val targetHeight: Int, private val sharp: Float, private val tone: Float) :
    BaseGlShaderProgram(useHdr, 1) {

    private val program: GlProgram = try {
        GlProgram(VS, FS).also {
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

    companion object {
        private const val VS = """
attribute vec4 aFramePosition;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = aFramePosition;
  vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
}
"""
        private const val FS = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uSharp;
uniform float uTone;
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
  vec3 wt = -amp * mix(0.125, 0.2, uSharp);
  vec3 o = clamp((c + (n + s + e + w) * wt) / (1.0 + 4.0 * wt), 0.0, 1.0);
  // tom suave: um pouco mais de contraste no meio e sombras levemente abertas
  float y = dot(o, vec3(0.2126, 0.7152, 0.0722));
  vec3 sc = o * o * (3.0 - 2.0 * o);
  o = mix(o, sc, uTone * 0.22);
  o += uTone * 0.035 * (1.0 - o) * (1.0 - smoothstep(0.0, 0.35, y));
  gl_FragColor = vec4(clamp(o, 0.0, 1.0), 1.0);
}
"""
    }
}
