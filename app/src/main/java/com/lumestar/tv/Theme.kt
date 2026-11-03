package com.lumestar.tv

import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

/** Cores do LumeStarTV (tema Cosmos, o mesmo do simulador). */
object C {
    val bg = Color(0xFF050816)
    val panel = Color(0xFF0E1538)
    val panel2 = Color(0xFF151E4E)
    val line = Color(0xFF243069)
    val fg = Color(0xFFEEF2FF)
    val muted = Color(0xFF9AA6D6)
    val dim = Color(0xFF5F6AA0)
    val a1 = Color(0xFF5AA8FF)
    val a2 = Color(0xFFB48CFF)
    val glow = Color(0x8C6E96FF)
    val ok = Color(0xFF52D6A8)
    val warn = Color(0xFFFFCF6E)
    val bad = Color(0xFFFF6F8E)
}

object F {
    val body = FontFamily(
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
        Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    )
    val logo = FontFamily(Font(R.font.creepster, FontWeight.Normal))
}

/**
 * Tela de projeto fixa de 960 x 540 "dp", como o simulador (1280 x 720 em 75%).
 * Qualquer TV (HD, Full HD ou 4K) mostra exatamente o mesmo desenho, só que em escala.
 */
@Composable
fun DesignScale(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val wPx = with(d) { maxWidth.toPx() }
        val hPx = with(d) { maxHeight.toPx() }
        val k = minOf(wPx / 960f, hPx / 540f)
        CompositionLocalProvider(LocalDensity provides Density(k, 1f)) { content() }
    }
}

/** Preferências guardadas no aparelho. */
class Prefs(c: Context) {
    private val sp = c.getSharedPreferences("lumestar", Context.MODE_PRIVATE)
    var sounds: Boolean
        get() = sp.getBoolean("sounds", true)
        set(v) = sp.edit().putBoolean("sounds", v).apply()
    var intro: Boolean
        get() = sp.getBoolean("intro", true)
        set(v) = sp.edit().putBoolean("intro", v).apply()
    var volume: Int
        get() = sp.getInt("volume", 2)
        set(v) = sp.edit().putInt("volume", v).apply()
}
