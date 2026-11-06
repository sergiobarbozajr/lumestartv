package com.lumestar.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Tema "Anime (sua imagem)": a pessoa manda uma imagem pelo celular e ela vira o fundo do app.
 * A imagem fica só neste aparelho (não vai no APK nem no GitHub). As cores vêm da própria imagem.
 */
object CustomTheme {
    const val ID = "anime"
    private lateinit var file: File
    var rev by mutableIntStateOf(0)
    var bmp: ImageBitmap? = null; private set
    val has get() = bmp != null

    fun init(c: Context) { file = File(c.filesDir, "tema_anime.jpg"); load() }

    fun load() {
        val b = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        if (b == null) { bmp = null; THEMES.remove(ID); rev++; return }
        bmp = b.asImageBitmap()
        THEMES[ID] = palette(b)
        rev++
    }

    /** Recebe a imagem (qualquer tamanho), reduz para no máximo 1920 px de largura e guarda. */
    fun save(bytes: ByteArray): Boolean = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) false else {
            var s = 1; while (o.outWidth / (s * 2) >= 1920) s *= 2
            val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: throw IllegalStateException()
            val out = if (b.width > 1920) Bitmap.createScaledBitmap(b, 1920, b.height * 1920 / b.width, true) else b
            file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }
    } catch (e: Throwable) { false }

    fun remove() { try { file.delete() } catch (e: Exception) {}; load() }

    /** Paleta tirada da imagem: a cor mais viva vira o destaque; a segunda mais viva (outro tom) o detalhe. */
    private fun palette(b: Bitmap): Pal {
        val sm = Bitmap.createScaledBitmap(b, 64, 36, true)
        class P(val c: Int, val h: Float, val s: Float, val v: Float)
        val ps = ArrayList<P>(); val hsv = FloatArray(3)
        var dr = 0f; var dg = 0f; var db = 0f; var dn = 0
        for (y in 0 until 36) for (x in 0 until 64) {
            val c = sm.getPixel(x, y); android.graphics.Color.colorToHSV(c, hsv)
            ps.add(P(c, hsv[0], hsv[1], hsv[2]))
            if (hsv[2] < .35f) { dr += android.graphics.Color.red(c); dg += android.graphics.Color.green(c); db += android.graphics.Color.blue(c); dn++ }
        }
        val vivid = ps.filter { it.v > .45f && it.s > .35f }.sortedByDescending { it.s * it.v }
        fun lift(c: Int, minV: Float): Color { android.graphics.Color.colorToHSV(c, hsv); hsv[2] = hsv[2].coerceAtLeast(minV); hsv[1] = hsv[1].coerceAtMost(.85f); return Color(android.graphics.Color.HSVToColor(hsv)) }
        val a1c = vivid.firstOrNull()?.c ?: 0xFF5AA8FF.toInt()
        android.graphics.Color.colorToHSV(a1c, hsv); val h1 = hsv[0]
        val a2c = vivid.firstOrNull { val d = kotlin.math.abs(it.h - h1); minOf(d, 360 - d) > 60 }?.c ?: a1c
        val base = if (dn > 0) Color(dr / dn / 255f, dg / dn / 255f, db / dn / 255f) else Color(0xFF0A0E1C)
        fun shade(k: Float, add: Float) = Color((base.red * k + add).coerceIn(0f, 1f), (base.green * k + add).coerceIn(0f, 1f), (base.blue * k + add).coerceIn(0f, 1f))
        val a1 = lift(a1c, .9f); val a2 = lift(a2c, .85f)
        return Pal("Anime (sua imagem)", shade(.6f, .02f), shade(.8f, .06f).copy(alpha = .88f), shade(.9f, .1f).copy(alpha = .92f), shade(1f, .16f),
            Color(0xFFDCE6F5), Color(0xFFB9C6DA).copy(alpha = .75f), a1, a2, a1.copy(alpha = .55f), custom = true)
    }
}
