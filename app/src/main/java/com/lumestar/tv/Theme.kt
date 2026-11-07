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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

class Pal(val name: String, val bg: Color, val panel: Color, val panel2: Color, val line: Color, val muted: Color, val dim: Color, val a1: Color, val a2: Color, val glow: Color, val img: Int = 0, val custom: Boolean = false)

val THEMES = linkedMapOf(
    "cosmos" to Pal("Cosmos", Color(0xFF050816), Color(0xFF0E1538), Color(0xFF151E4E), Color(0xFF243069), Color(0xFF9AA6D6), Color(0xFF5F6AA0), Color(0xFF5AA8FF), Color(0xFFB48CFF), Color(0x8C6E96FF)),
    "aurora" to Pal("Aurora", Color(0xFF03110F), Color(0xFF0A2824), Color(0xFF0F3630), Color(0xFF1C5047), Color(0xFF94C7BB), Color(0xFF5A8C80), Color(0xFF3EE6B0), Color(0xFF5AB8FF), Color(0x8C3EE6B0)),
    "nebulosa" to Pal("Nebulosa", Color(0xFF0F0519), Color(0xFF230E38), Color(0xFF2F144A), Color(0xFF4A236E), Color(0xFFC3A0D8), Color(0xFF7F5E99), Color(0xFFFF6FD8), Color(0xFFB48CFF), Color(0x8CFF6FD8)),
    "solar" to Pal("Solar", Color(0xFF130A03), Color(0xFF2C190A), Color(0xFF3A210D), Color(0xFF5A3716), Color(0xFFD8B894), Color(0xFF977554), Color(0xFFFFB347), Color(0xFFFF6F4E), Color(0x8CFFB347)),
    // temas com imagem de fundo, os mesmos do AstroLume TV (paleta tirada de cada imagem)
    "galaxia" to space("Galáxia", R.drawable.bg_nebula, 0xFF6CC6FF, 0xFF1A1F2C, 0xFF232A3B, 0xFF161A24, 0xFFDCE6F5, 0xFFB9C6DA),
    "planetas" to space("Planetas", R.drawable.bg_planets, 0xFF9DB8FF, 0xFF1B1E2E, 0xFF262A3E, 0xFF171A27, 0xFFDCE6F5, 0xFFB9C6DA),
    "orbitas" to space("Órbitas", R.drawable.bg_orbits, 0xFFFFC86E, 0xFF1E1C24, 0xFF2A2732, 0xFF19171E, 0xFFF3EADB, 0xFFD6C8B3),
    "mar" to space("Mar alienígena", R.drawable.bg_ocean, 0xFF7FE3E0, 0xFF14242A, 0xFF1C3138, 0xFF111E23, 0xFFDCE6F5, 0xFFB9C6DA),
    "deserto" to space("Deserto", R.drawable.bg_desert, 0xFFFFB067, 0xFF2A1E16, 0xFF38281D, 0xFF231912, 0xFFFBEBDD, 0xFFE4CDB5),
    "plutao" to space("Plutão gelado", R.drawable.bg_pluto, 0xFFA9D4FF, 0xFF1B222C, 0xFF26303D, 0xFF161C24, 0xFFDCE6F5, 0xFFB9C6DA),
    "cratera" to space("Cratera ao sol", R.drawable.bg_crater, 0xFFFFE3A8, 0xFF222121, 0xFF2E2D2C, 0xFF1C1B1B, 0xFFF2EDE6, 0xFFD2CBC2),
    "marte" to space("Marte", R.drawable.bg_mars, 0xFFFF9E7A, 0xFF2E1D16, 0xFF3B2820, 0xFF261811, 0xFFFBE8E0, 0xFFE6C9BC),
    "jupiter" to space("Tempestade em Júpiter", R.drawable.bg_jupiter, 0xFFFFB357, 0xFF25160B, 0xFF33200F, 0xFF1F1209, 0xFFFBEBD6, 0xFFE5CBA6),
    "venus" to space("Vênus vulcânico", R.drawable.bg_lava, 0xFFFF7A2E, 0xFF26170C, 0xFF34200F, 0xFF1F1309, 0xFFFBE7D3, 0xFFE6C6A3),
    "terra" to space("Terra vista da Lua", R.drawable.bg_earthrise, 0xFF6FB6FF, 0xFF1C1E22, 0xFF282B30, 0xFF17191C, 0xFFDCE6F5, 0xFFB9C6DA),
)

/** Tema com foto de fundo: cartões e painéis em vidro escuro tingido pela foto. */
private fun space(name: String, img: Int, accent: Long, card: Long, cardHi: Long, panel: Long, light: Long, dim: Long): Pal {
    val a = Color(accent)
    return Pal(name, Color(panel), Color(card).copy(alpha = .88f), Color(cardHi).copy(alpha = .92f), Color(cardHi).copy(alpha = 1f).let { Color((it.red + .08f).coerceAtMost(1f), (it.green + .08f).coerceAtMost(1f), (it.blue + .08f).coerceAtMost(1f)) },
        Color(light), Color(dim).copy(alpha = .75f), a, Color(light), a.copy(alpha = .55f), img)
}

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
    /** 0 automático · 1 sempre completo · 2 sempre leve (ver Perf) */
    var perf: Int
        get() = sp.getInt("desempenho", 0)
        set(v) = sp.edit().putInt("desempenho", v).apply()
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


/** Foto do tema por trás de tudo, escurecida para o texto continuar legível. */
@androidx.compose.runtime.Composable
fun ThemeBackdrop() {
    val img = C.pal.img
    CustomTheme.rev.let { }
    val cb = if (C.pal.custom) CustomTheme.bmp else null
    if (img == 0 && cb == null) return
    if (cb != null) androidx.compose.foundation.Image(cb, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = .6f)
    else androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(img), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = .55f)
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(C.bg.copy(alpha = .35f), C.bg.copy(alpha = .78f)))))
}
