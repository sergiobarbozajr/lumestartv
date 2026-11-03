package com.lumestar.tv

import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

class Pal(val name: String, val bg: Color, val panel: Color, val panel2: Color, val line: Color, val muted: Color, val dim: Color, val a1: Color, val a2: Color, val glow: Color)

val THEMES = linkedMapOf(
    "cosmos" to Pal("Cosmos", Color(0xFF050816), Color(0xFF0E1538), Color(0xFF151E4E), Color(0xFF243069), Color(0xFF9AA6D6), Color(0xFF5F6AA0), Color(0xFF5AA8FF), Color(0xFFB48CFF), Color(0x8C6E96FF)),
    "aurora" to Pal("Aurora", Color(0xFF03110F), Color(0xFF0A2824), Color(0xFF0F3630), Color(0xFF1C5047), Color(0xFF94C7BB), Color(0xFF5A8C80), Color(0xFF3EE6B0), Color(0xFF5AB8FF), Color(0x8C3EE6B0)),
    "nebulosa" to Pal("Nebulosa", Color(0xFF0F0519), Color(0xFF230E38), Color(0xFF2F144A), Color(0xFF4A236E), Color(0xFFC3A0D8), Color(0xFF7F5E99), Color(0xFFFF6FD8), Color(0xFFB48CFF), Color(0x8CFF6FD8)),
    "solar" to Pal("Solar", Color(0xFF130A03), Color(0xFF2C190A), Color(0xFF3A210D), Color(0xFF5A3716), Color(0xFFD8B894), Color(0xFF977554), Color(0xFFFFB347), Color(0xFFFF6F4E), Color(0x8CFFB347)),
)

/** Cores do LumeStarTV. Mudam na hora quando o tema é trocado em Configurações › Tema do app. */
object C {
    var pal by mutableStateOf(THEMES.getValue("cosmos"))
    val bg get() = pal.bg
    val panel get() = pal.panel
    val panel2 get() = pal.panel2
    val line get() = pal.line
    val fg = Color(0xFFEEF2FF)
    val muted get() = pal.muted
    val dim get() = pal.dim
    val a1 get() = pal.a1
    val a2 get() = pal.a2
    val glow get() = pal.glow
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
    val logo = FontFamily(Font(R.font.nanum_brush, FontWeight.Normal))
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
    var theme: String
        get() = sp.getString("tema", "cosmos") ?: "cosmos"
        set(v) = sp.edit().putString("tema", v).apply()
    var city: String
        get() = sp.getString("cidade", "") ?: ""
        set(v) = sp.edit().putString("cidade", v).apply()
    /** posição da cidade escolhida na lista de resultados ("lat,lon"); vazio = procurar pelo nome */
    var cityPos: String
        get() = sp.getString("cidadePos", "") ?: ""
        set(v) = sp.edit().putString("cidadePos", v).apply()
    var weather: Boolean
        get() = sp.getBoolean("clima", true)
        set(v) = sp.edit().putBoolean("clima", v).apply()
    /** teclas do controle trocadas: "codigoApertado=codigoQueVale,..." */
    var keymap: String
        get() = sp.getString("teclas", "") ?: ""
        set(v) = sp.edit().putString("teclas", v).apply()
}
