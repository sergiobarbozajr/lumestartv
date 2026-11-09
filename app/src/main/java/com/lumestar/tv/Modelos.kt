package com.lumestar.tv

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Desenhos dos cards sem imagem. Todos seguem as cores do tema atual; só o nome muda. */
enum class Modelo(val etiqueta: String) { CANAL(""), FILME("FILME"), SERIE("SÉRIE"), VIDEO("VÍDEO") }

@Composable
fun ModeloCard(tipo: Modelo, nome: String, modifier: Modifier = Modifier, aoVivo: Boolean = tipo == Modelo.CANAL) {
    val a1 = C.a1; val fg = C.fg; val bg = C.bg; val panel = C.panel
    val limpo = nome.replace(Regex("(?i)\\b(hd|fhd|sd|4k|uhd|h265)\\b|\\[[^]]*]|\\([^)]*\\)"), " ").replace(Regex("\\s+"), " ").trim().ifBlank { nome }
    BoxWithConstraints(modifier.background(bg)) {
        val largo = maxWidth > maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // painel com borda luminosa
            val ins = min(w, h) * .06f
            drawRoundRect(panel.copy(alpha = .55f), Offset(ins, ins), Size(w - 2 * ins, h - 2 * ins), CornerRadius(ins * 1.4f))
            drawRoundRect(a1.copy(alpha = .45f), Offset(ins, ins), Size(w - 2 * ins, h - 2 * ins), CornerRadius(ins * 1.4f), style = Stroke(1.2f))
            val s = min(w, h) * (if (largo) .78f else .62f)
            val cx = w / 2; val cy = h * (if (largo) .5f else .44f)
            circuitos(cx, cy, s, a1)
            drawCircle(Brush.radialGradient(listOf(a1.copy(alpha = .5f), Color.Transparent), Offset(cx, cy), s * .75f), s * .75f, Offset(cx, cy))
            when (tipo) {
                Modelo.CANAL -> tv(cx, cy, s, a1, bg)
                Modelo.FILME -> claquete(cx, cy, s, a1, bg)
                Modelo.SERIE -> pipoca(cx, cy, s, a1, bg)
                Modelo.VIDEO -> play(cx, cy, s, a1, bg)
            }
        }
        val tam = if (largo) 12 else 13
        if (tipo == Modelo.CANAL) {
            // nome dentro da tela da TV
            Box(Modifier.align(Alignment.Center).fillMaxWidth(if (largo) .42f else .6f).padding(top = if (largo) 6.dp else 4.dp), contentAlignment = Alignment.Center) {
                BasicText(limpo, style = TextStyle(color = lerp(bg, Color.Black, .6f), fontSize = tam.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = (tam + 1).sp), maxLines = 2)
            }
        } else {
            Column(Modifier.align(if (largo) Alignment.Center else Alignment.Center).fillMaxWidth().padding(horizontal = 6.dp).offset(y = if (largo) 0.dp else (-6).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(bg.copy(alpha = .85f)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    BasicText(tipo.etiqueta, style = TextStyle(color = fg.copy(alpha = .8f), fontSize = 8.sp, fontWeight = FontWeight.Bold))
                }
                Box(Modifier.padding(top = 2.dp).fillMaxWidth().background(Brush.horizontalGradient(listOf(Color.Transparent, a1.copy(alpha = .35f), Color.Transparent))).padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                    BasicText(limpo, style = TextStyle(color = fg, fontSize = tam.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = (tam + 1).sp,
                        shadow = Shadow(Color.Black.copy(alpha = .8f), Offset(0f, 2f), 6f)), maxLines = 2)
                }
            }
        }
        if (aoVivo) AoVivo(Modifier.align(Alignment.TopEnd).padding(5.dp))
    }
}

/** Selo pequeno "● AO VIVO" com a bolinha vermelha piscando de leve. */
@Composable
fun AoVivo(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "aovivo")
    val a by t.animateFloat(1f, .35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pisca")
    Row(modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xD9050816)).padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFFF3B3B).copy(alpha = a)))
        Spacer(Modifier.width(3.dp))
        BasicText("AO VIVO", style = TextStyle(color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold))
    }
}

private fun DrawScope.circuitos(cx: Float, cy: Float, s: Float, a1: Color) {
    val c = a1.copy(alpha = .35f); val w = size.width
    for (lado in listOf(-1f, 1f)) for (i in 0 until 5) {
        val y0 = cy - s * .38f + i * s * .19f
        val x0 = cx + lado * s * .45f
        val jog = s * (.08f + (i % 3) * .05f)
        val x1 = x0 + lado * s * .18f
        val y1 = y0 + (if (i < 2) -jog else if (i > 2) jog else 0f)
        val x2 = if (lado < 0) w * (.04f + i * .02f) else w * (.96f - i * .02f)
        val p = Path().apply { moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y1) }
        drawPath(p, c, style = Stroke(1.1f))
        drawCircle(a1.copy(alpha = .6f), 1.8f, Offset(x2, y1))
    }
    // pontos de luz
    for (k in 0 until 10) {
        val ang = k * 0.63f; val r = s * (.55f + (k % 4) * .08f)
        drawCircle(a1.copy(alpha = .5f), 1.3f, Offset(cx + cos(ang) * r, cy + sin(ang) * r * .6f))
    }
}

private val METAL = listOf(Color(0xFFE2E7EF), Color(0xFF9AA4B4))

private fun DrawScope.tv(cx: Float, cy: Float, s: Float, a1: Color, bg: Color) {
    val bw = s * 1.0f; val bh = s * .66f; val x = cx - bw / 2; val y = cy - bh * .42f
    // antenas
    val topo = Offset(cx, y)
    drawLine(Color(0xFFB8C2D6), topo, Offset(cx - s * .3f, y - s * .38f), 2.2f); drawCircle(Color(0xFFDDE3EC), 2.6f, Offset(cx - s * .3f, y - s * .38f))
    drawLine(Color(0xFFB8C2D6), topo, Offset(cx + s * .24f, y - s * .42f), 2.2f); drawCircle(Color(0xFFDDE3EC), 2.6f, Offset(cx + s * .24f, y - s * .42f))
    drawArc(Brush.verticalGradient(METAL), 180f, 180f, true, Offset(cx - s * .1f, y - s * .07f), Size(s * .2f, s * .14f))
    // pés
    drawRect(Color(0xFF7D8798), Offset(x + bw * .2f, y + bh), Size(bw * .08f, s * .06f))
    drawRect(Color(0xFF7D8798), Offset(x + bw * .72f, y + bh), Size(bw * .08f, s * .06f))
    // corpo metálico e tela
    drawRoundRect(Brush.verticalGradient(METAL, y, y + bh), Offset(x, y), Size(bw, bh), CornerRadius(s * .07f))
    drawRoundRect(Color(0xFF2A2F38), Offset(x + bw * .06f, y + bh * .1f), Size(bw * .72f, bh * .78f), CornerRadius(s * .05f))
    drawRoundRect(Brush.linearGradient(listOf(Color.White, lerp(Color.White, a1, .18f))), Offset(x + bw * .09f, y + bh * .15f), Size(bw * .66f, bh * .68f), CornerRadius(s * .04f))
    // botões
    for (k in 0..1) {
        val cc = Offset(x + bw * .87f, y + bh * (.35f + k * .3f))
        drawCircle(Color(0xFF8A94A5), s * .045f, cc); drawCircle(Color(0xFFE8ECF2), s * .03f, cc)
    }
    drawRoundRect(a1.copy(alpha = .55f), Offset(x, y), Size(bw, bh), CornerRadius(s * .07f), style = Stroke(1.2f))
}

private fun DrawScope.claquete(cx: Float, cy: Float, s: Float, a1: Color, bg: Color) {
    val bw = s * .95f; val x = cx - bw / 2; val y = cy - s * .12f; val bh = s * .52f
    val escuro = Color(0xFF232A35)
    drawRoundRect(escuro, Offset(x, y), Size(bw, bh), CornerRadius(s * .05f))
    // faixa listrada do corpo
    listras(Offset(x, y), Size(bw, s * .1f), escuro)
    // tampa inclinada
    rotate(-12f, Offset(x, y)) {
        listras(Offset(x, y - s * .14f), Size(bw * .98f, s * .11f), escuro)
    }
    drawCircle(Color.White, s * .035f, Offset(x + s * .04f, y + s * .02f))
    drawRoundRect(a1.copy(alpha = .5f), Offset(x, y), Size(bw, bh), CornerRadius(s * .05f), style = Stroke(1.2f))
}

private fun DrawScope.listras(o: Offset, t: Size, escuro: Color) {
    drawRect(escuro, o, t)
    val n = 5; val passo = t.width / n
    for (i in 0 until n) {
        val p = Path().apply {
            moveTo(o.x + i * passo + passo * .25f, o.y + t.height); lineTo(o.x + i * passo + passo * .55f, o.y)
            lineTo(o.x + i * passo + passo * .85f, o.y); lineTo(o.x + i * passo + passo * .55f, o.y + t.height); close()
        }
        drawPath(p, Color(0xFFF2F4F8))
    }
}

private fun DrawScope.pipoca(cx: Float, cy: Float, s: Float, a1: Color, bg: Color) {
    // balde listrado
    val x0 = cx - s * .52f; val x1 = cx + s * .08f; val y0 = cy - s * .12f; val y1 = cy + s * .42f
    val balde = Path().apply { moveTo(x0, y0); lineTo(x1, y0); lineTo(x1 - s * .07f, y1); lineTo(x0 + s * .07f, y1); close() }
    drawPath(balde, Color(0xFF2B323D))
    for (i in 0 until 4) {
        val a = x0 + (x1 - x0) * (i / 4f) + s * .02f; val b = a + (x1 - x0) / 8f
        val p = Path().apply { moveTo(a, y0); lineTo(b, y0); lineTo(b - s * .02f, y1); lineTo(a + s * .01f, y1); close() }
        drawPath(p, Brush.verticalGradient(METAL, y0, y1))
    }
    // pipocas
    val creme = Color(0xFFF3EBD8)
    for (i in 0 until 9) {
        val px = x0 + s * .05f + (i % 5) * (x1 - x0 - s * .1f) / 4f
        val py = y0 - s * (.05f + (i / 5) * .1f + (i % 2) * .03f)
        drawCircle(creme, s * .065f, Offset(px, py)); drawCircle(Color(0xFFD9CDB2), s * .065f, Offset(px, py), style = Stroke(1f))
    }
    // controle remoto
    val rx = cx + s * .16f; val ry = cy - s * .45f; val rw = s * .3f; val rh = s * .88f
    drawRoundRect(Color(0xFF2B323D), Offset(rx, ry), Size(rw, rh), CornerRadius(s * .08f))
    drawRoundRect(Brush.verticalGradient(METAL, ry, ry + rh), Offset(rx, ry), Size(rw, rh), CornerRadius(s * .08f), style = Stroke(1.5f))
    val dc = Offset(rx + rw / 2, ry + rh * .3f)
    drawCircle(Color(0xFF8A94A5), rw * .3f, dc); drawCircle(Color(0xFF2B323D), rw * .14f, dc)
    for (k in 0 until 3) for (j in 0..1) drawRoundRect(Color(0xFF8A94A5), Offset(rx + rw * (.18f + j * .38f), ry + rh * (.58f + k * .1f)), Size(rw * .26f, rh * .05f), CornerRadius(3f))
    drawRoundRect(a1.copy(alpha = .5f), Offset(rx, ry), Size(rw, rh), CornerRadius(s * .08f), style = Stroke(1.2f))
}

private fun DrawScope.play(cx: Float, cy: Float, s: Float, a1: Color, bg: Color) {
    val r = s * .45f; val c = Offset(cx, cy)
    drawCircle(Brush.radialGradient(listOf(Color(0xFF4A5566), Color(0xFF1F2530)), c, r), r, c)
    // malha geométrica
    val pts = (0 until 9).map { k -> val ang = k * 0.7f + 0.3f; val rr = r * (.35f + (k % 3) * .25f); Offset(cx + cos(ang) * rr, cy + sin(ang) * rr) }
    for (i in pts.indices) for (j in i + 1 until pts.size) if ((i + j) % 3 == 0) drawLine(a1.copy(alpha = .35f), pts[i], pts[j], 1f)
    pts.forEach { drawCircle(a1.copy(alpha = .7f), 1.6f, it) }
    // órbita
    rotate(-22f, c) { drawOval(a1.copy(alpha = .85f), Offset(cx - r * 1.15f, cy - r * .42f), Size(r * 2.3f, r * .84f), style = Stroke(s * .05f)) }
    // botão de play
    val q = s * .42f
    drawRoundRect(lerp(bg, a1, .35f), Offset(cx - q / 2, cy - q * .95f), Size(q, q), CornerRadius(q * .25f))
    drawRoundRect(a1, Offset(cx - q / 2, cy - q * .95f), Size(q, q), CornerRadius(q * .25f), style = Stroke(2f))
    val tri = Path().apply { moveTo(cx - q * .16f, cy - q * .72f); lineTo(cx + q * .24f, cy - q * .45f); lineTo(cx - q * .16f, cy - q * .18f); close() }
    drawPath(tri, Brush.linearGradient(listOf(Color.White, a1)))
}
