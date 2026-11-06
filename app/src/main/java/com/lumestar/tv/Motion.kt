package com.lumestar.tv

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.C as MC
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.util.concurrent.Executor

/**
 * "Suavizar movimento 60 Hz" (experimental).
 * Para vídeos de até ~50 quadros/s, cria um quadro intermediário entre cada par de quadros (mistura ponderada
 * com leve realce), dobrando a cadência: 24→48, 25→50, 30→60. Custo baixo (um desenho extra por quadro),
 * mas não é interpolação por vetores de movimento: em movimentos rápidos pode aparecer leve rastro.
 */
@UnstableApi
class MotionSmooth : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = FrameDoubler()
}

@UnstableApi
private class FrameDoubler : GlShaderProgram {
    private var inputListener: GlShaderProgram.InputListener = object : GlShaderProgram.InputListener {}
    private var outputListener: GlShaderProgram.OutputListener = object : GlShaderProgram.OutputListener {}
    private var errorListener: GlShaderProgram.ErrorListener = GlShaderProgram.ErrorListener { }
    private var errorExecutor: Executor = Executor { it.run() }

    private val program: GlProgram = try {
        GlProgram(VS, FS).also { it.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE) }
    } catch (e: GlUtil.GlException) { throw VideoFrameProcessingException(e) }

    private val all = ArrayList<GlTextureInfo>()
    private val free = ArrayDeque<GlTextureInfo>()
    private var prev: GlTextureInfo? = null
    private var prevTime = MC.TIME_UNSET
    private var w = 0
    private var h = 0
    private var promised = 0 // vezes que avisamos "pode mandar o próximo quadro" ainda não usadas

    private fun offerSlots() {
        // cada quadro de entrada pode gerar até 2 de saída
        while (free.size - 2 * promised >= 2) { promised++; inputListener.onReadyToAcceptInputFrame() }
    }

    override fun setInputListener(l: GlShaderProgram.InputListener) { inputListener = l; promised = 0; if (all.isEmpty()) { promised = 1; l.onReadyToAcceptInputFrame() } else offerSlots() }
    override fun setOutputListener(l: GlShaderProgram.OutputListener) { outputListener = l }
    override fun setErrorListener(executor: Executor, l: GlShaderProgram.ErrorListener) { errorExecutor = executor; errorListener = l }

    private fun ensure(gp: GlObjectsProvider, width: Int, height: Int) {
        if (width == w && height == h && all.isNotEmpty()) return
        releaseTextures()
        w = width; h = height
        repeat(4) { val t = GlUtil.createTexture(w, h, false); val info = gp.createBuffersForTexture(t, w, h); all.add(info); free.add(info) }
        val pt = GlUtil.createTexture(w, h, false); prev = gp.createBuffersForTexture(pt, w, h)
        prevTime = MC.TIME_UNSET
    }

    private fun draw(target: GlTextureInfo, texA: Int, texB: Int, mix: Float) {
        GlUtil.focusFramebufferUsingCurrentContext(target.fboId, target.width, target.height)
        GlUtil.clearFocusedBuffers()
        program.use()
        program.setSamplerTexIdUniform("uTexA", texA, 0)
        program.setSamplerTexIdUniform("uTexB", texB, 1)
        program.setFloatUniform("uMix", mix)
        program.bindAttributesAndUniforms()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun queueInputFrame(glObjectsProvider: GlObjectsProvider, inputTexture: GlTextureInfo, presentationTimeUs: Long) {
        try {
            if (promised > 0) promised--
            ensure(glObjectsProvider, inputTexture.width, inputTexture.height)
            val p = prev!!
            val dt = if (prevTime == MC.TIME_UNSET) 0L else presentationTimeUs - prevTime
            // só cria o intermediário quando o vídeo tem até ~50 quadros/s (intervalo de 19 a 70 ms)
            if (dt in 19_000..70_000 && free.size >= 2) {
                val mid = free.removeFirst()
                draw(mid, p.texId, inputTexture.texId, 0.5f)
                outputListener.onOutputFrameAvailable(mid, prevTime + dt / 2)
            }
            val out = free.removeFirst()
            draw(out, inputTexture.texId, inputTexture.texId, 0f)
            outputListener.onOutputFrameAvailable(out, presentationTimeUs)
            draw(p, inputTexture.texId, inputTexture.texId, 0f) // guarda uma cópia para o próximo intermediário
            prevTime = presentationTimeUs
            inputListener.onInputFrameProcessed(inputTexture)
            offerSlots()
        } catch (e: Exception) {
            errorExecutor.execute { errorListener.onError(VideoFrameProcessingException(e)) }
        }
    }

    override fun releaseOutputFrame(outputTexture: GlTextureInfo) {
        if (all.any { it.texId == outputTexture.texId } && free.none { it.texId == outputTexture.texId }) free.addLast(all.first { it.texId == outputTexture.texId })
        offerSlots()
    }

    override fun signalEndOfCurrentInputStream() { outputListener.onCurrentOutputStreamEnded() }

    override fun flush() {
        free.clear(); free.addAll(all); promised = 0; prevTime = MC.TIME_UNSET
        inputListener.onFlush()
        if (all.isEmpty()) { promised = 1; inputListener.onReadyToAcceptInputFrame() } else offerSlots()
    }

    private fun releaseTextures() {
        try {
            (all + listOfNotNull(prev)).forEach { GlUtil.deleteTexture(it.texId); GlUtil.deleteFbo(it.fboId) }
        } catch (e: Exception) {}
        all.clear(); free.clear(); prev = null
    }

    override fun release() {
        releaseTextures()
        try { program.delete() } catch (e: Exception) {}
    }

    companion object {
        private const val VS = """
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""
        // mistura os dois quadros e devolve um pouco do contraste perdido na média (menos "lavado")
        private const val FS = """
precision mediump float;
uniform sampler2D uTexA;
uniform sampler2D uTexB;
uniform float uMix;
varying vec2 vUv;
void main() {
  vec3 a = texture2D(uTexA, vUv).rgb;
  vec3 b = texture2D(uTexB, vUv).rgb;
  vec3 m = mix(a, b, uMix);
  float d = length(a - b);
  m = mix(m, b, clamp(d * 1.5, 0.0, 0.6) * step(0.01, uMix)); // onde muda muito, puxa para o quadro novo (menos rastro)
  gl_FragColor = vec4(m, 1.0);
}
"""
    }
}
