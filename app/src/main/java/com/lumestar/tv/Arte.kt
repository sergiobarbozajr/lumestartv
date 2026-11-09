package com.lumestar.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.concurrent.ConcurrentHashMap

/**
 * Arte dos cards de canal e de app:
 * - logo com fundo transparente: fundo escuro na cor do próprio logo, com um brilho atrás (o logo aparece);
 * - logo com fundo próprio (quadrado): o card usa a cor da borda do logo, e ele parece preencher o card;
 * - sem logo (ou se não carregar): um logo desenhado com o nome, em degradê com sombra. Nunca fica cor sólida vazia.
 */
object Arte {
    /** cor de fundo calculada por endereço de logo (null = ainda não calculou) */
    private val cores = ConcurrentHashMap<String, Pair<Color, Color>>()
    fun cor(url: String) = cores[url]

    fun calcular(url: String, d: Drawable) {
        if (cores.containsKey(url)) return
        try {
            val n = 24
            val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, n, n); d.draw(Canvas(b))
            val cantos = listOf(b.getPixel(0, 0), b.getPixel(n - 1, 0), b.getPixel(0, n - 1), b.getPixel(n - 1, n - 1))
            val opaco = cantos.all { (it ushr 24) > 230 }
            var r = 0f; var g = 0f; var bl = 0f; var peso = 0f
            val hsv = FloatArray(3)
            for (y in 0 until n) for (x in 0 until n) {
                val p = b.getPixel(x, y); val a = (p ushr 24) / 255f
                if (a < .5f) continue
                android.graphics.Color.colorToHSV(p, hsv)
                val w = a * (0.2f + hsv[1] * hsv[2])
                r += (p shr 16 and 255) * w; g += (p shr 8 and 255) * w; bl += (p and 255) * w; peso += w
            }
            val principal = if (peso > 0) Color(r / peso / 255f, g / peso / 255f, bl / peso / 255f) else Color(0xFF3A4A7A)
            cores[url] = if (opaco) {
                val c = Color(cantos[0]); c to c // fundo igual à borda do logo: parece que o logo preenche o card
            } else {
                escuro(principal, .22f) to escuro(principal, .55f) // escuro na cor do logo + brilho
            }
        } catch (e: Throwable) {}
    }

    private fun escuro(c: Color, k: Float) = Color(c.red * k + .02f, c.green * k + .02f, c.blue * k + .03f)
}

@Composable
fun LogoArte(nome: String, url: String, modifier: Modifier = Modifier, pad: Dp = 8.dp, tamanhoNome: Int = 18) {
    val ctx = LocalContext.current
    var falhou by remember(url) { mutableStateOf(url.isBlank()) }
    var cor by remember(url) { mutableStateOf(Arte.cor(url)) }
    val fundo = cor?.let { (a, b) -> if (a == b) Brush.linearGradient(listOf(a, a)) else Brush.radialGradient(listOf(b, a)) } ?: coverBrush(nome)
    Box(modifier.background(fundo), contentAlignment = Alignment.Center) {
        if (!falhou) AsyncImage(
            model = remember(url) { ImageRequest.Builder(ctx).data(url).allowHardware(false).size(400, 240).crossfade(true).build() },
            contentDescription = nome, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(if (cor?.let { it.first == it.second } == true) 0.dp else pad),
            onSuccess = { s -> Arte.calcular(url, s.result.drawable); cor = Arte.cor(url) },
            onError = { falhou = true },
        )
        if (falhou) NomeArte(nome, tamanhoNome)
    }
}

/** Logo desenhado com o nome: letras grossas em degradê, com sombra, sobre um brilho. */
@Composable
fun NomeArte(nome: String, tamanho: Int = 18, modifier: Modifier = Modifier) {
    val limpo = nome.replace(Regex("(?i)\\b(hd|fhd|sd|4k|uhd|h265)\\b|\\[[^]]*]|\\([^)]*\\)"), " ").replace(Regex("\\s+"), " ").trim().ifBlank { nome }
    val t = if (limpo.length > 22) limpo.take(21) + "…" else limpo
    Box(modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.White.copy(alpha = .16f), Color.Transparent))).padding(8.dp), contentAlignment = Alignment.Center) {
        BasicText(
            t,
            style = TextStyle(
                brush = Brush.linearGradient(listOf(Color.White, C.a1.copy(alpha = .95f).compositeOver(Color.White), C.a2)),
                fontSize = (if (t.length > 12) tamanho - 4 else tamanho).sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                shadow = Shadow(Color(0xCC000000), Offset(0f, 3f), 8f), lineHeight = (tamanho + 2).sp,
            ),
            maxLines = 2,
        )
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a))
}
