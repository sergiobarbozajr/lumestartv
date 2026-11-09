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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Desenhos dos cards sem imagem. Sem fundo de template: o próprio ícone ocupa o card e o nome
 * fica DENTRO dele (tela da TV, claquete, balde de pipoca, círculo do play, visor do rádio),
 * diminuindo sozinho até caber. Cores seguem o tema atual.
 */
enum class Modelo { CANAL, FILME, SERIE, VIDEO, RADIO }

/** Tira HD/FHD/[..]/(..) do nome para sobrar só o que importa. */
fun nomeLimpo(nome: String) = nome.replace(Regex("(?i)\\b(hd|fhd|sd|4k|uhd|h265)\\b|\\[[^]]*]|\\([^)]*\\)"), " ")
    .replace(Regex("\\s+"), " ").trim().ifBlank { nome }

@Composable
fun ModeloCard(tipo: Modelo, nome: String, modifier: Modifier = Modifier, aoVivo: Boolean = tipo == Modelo.CANAL) {
    val a1 = C.a1; val bg = C.bg
    val limpo = remember(nome) { nomeLimpo(nome) }
    val tm = rememberTextMeasurer()
    Box(modifier.background(lerp(bg, a1, .07f))) {
        Canvas(Modifier.fillMaxSize()) {
            val m = min(size.width, size.height) * .05f
            val caixa = Rect(m, m, size.width - m, size.height - m)
            val texto: Pair<Rect, Color> = when (tipo) {
                Modelo.CANAL -> tv(caixa, a1)
                Modelo.FILME -> claquete(caixa, a1)
                Modelo.SERIE -> pipoca(caixa, a1)
                Modelo.VIDEO -> play(caixa, a1, bg)
                Modelo.RADIO -> radio(caixa, a1)
            }
            textoQueCabe(tm, limpo, texto.first, texto.second)
        }
        if (aoVivo) AoVivo(Modifier.align(Alignment.TopEnd).padding(4.dp))
    }
}

/**
 * Escreve o texto centralizado dentro do retângulo, começando grande e diminuindo até caber
 * (no máximo 2 linhas). Nunca sai do contorno do ícone.
 */
fun DrawScope.textoQueCabe(tm: TextMeasurer, texto: String, r: Rect, cor: Color, brush: Brush? = null, maxLinhas: Int = 2) {
    if (r.width < 6f || r.height < 6f) return
    val clara = cor.red * .3f + cor.green * .59f + cor.blue * .11f > .5f
    var fs = min(r.height * .46f, r.width * .26f)
    val minimo = 6.dp.toPx()
    var lay: TextLayoutResult
    while (true) {
        val base = TextStyle(
            color = cor, fontSize = fs.toSp(), lineHeight = (fs * 1.08f).toSp(), fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            shadow = if (clara) Shadow(Color.Black.copy(alpha = .7f), Offset(0f, 1.5f), 4f) else null,
        )
        val estilo = if (brush != null) base.merge(TextStyle(brush = brush)) else base
        lay = tm.measure(texto, estilo, TextOverflow.Ellipsis, true, maxLinhas, Constraints.fixedWidth(r.width.toInt().coerceAtLeast(1)))
        val cabe = !lay.hasVisualOverflow && lay.size.height <= r.height && (0 until lay.lineCount).none { lay.isLineEllipsized(it) }
        if (cabe || fs <= minimo) break
        fs *= .9f
    }
    drawText(lay, topLeft = Offset(r.left, r.top + (r.height - lay.size.height) / 2))
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

private val METAL = listOf(Color(0xFFE2E7EF), Color(0xFF9AA4B4))
private val ESCURO = Color(0xFF232A35)

/** Encaixa uma figura de proporção [prop] (largura/altura) centralizada na caixa. */
private fun encaixe(c: Rect, prop: Float): Rect {
    val w = min(c.width, c.height * prop); val h = w / prop
    return Rect(c.center.x - w / 2, c.center.y - h / 2, c.center.x + w / 2, c.center.y + h / 2)
}

/** TV antiga com antenas. O nome vai na tela. */
private fun DrawScope.tv(caixa: Rect, a1: Color): Pair<Rect, Color> {
    val f = encaixe(caixa, 1.45f)
    val ant = f.height * .16f; val pe = f.height * .05f
    val x = f.left; val y = f.top + ant; val bw = f.width; val bh = f.height - ant - pe
    val topo = Offset(f.center.x, y)
    val e = (f.height * .025f).coerceAtLeast(1.5f)
    drawLine(Color(0xFFB8C2D6), topo, Offset(f.center.x - bw * .2f, f.top + e), e); drawCircle(Color(0xFFDDE3EC), e * 1.3f, Offset(f.center.x - bw * .2f, f.top + e))
    drawLine(Color(0xFFB8C2D6), topo, Offset(f.center.x + bw * .16f, f.top + e), e); drawCircle(Color(0xFFDDE3EC), e * 1.3f, Offset(f.center.x + bw * .16f, f.top + e))
    drawArc(Brush.verticalGradient(METAL), 180f, 180f, true, Offset(f.center.x - bw * .07f, y - ant * .35f), Size(bw * .14f, ant * .7f))
    drawRect(Color(0xFF7D8798), Offset(x + bw * .18f, y + bh), Size(bw * .07f, pe))
    drawRect(Color(0xFF7D8798), Offset(x + bw * .75f, y + bh), Size(bw * .07f, pe))
    val r = bh * .1f
    drawRoundRect(Brush.verticalGradient(METAL, y, y + bh), Offset(x, y), Size(bw, bh), CornerRadius(r))
    // tela grande (quase todo o corpo) e coluna de botões estreita
    val tela = Rect(x + bw * .05f, y + bh * .08f, x + bw * .84f, y + bh * .92f)
    drawRoundRect(Color(0xFF2A2F38), tela.topLeft, tela.size, CornerRadius(r * .8f))
    val vidro = Rect(tela.left + bw * .02f, tela.top + bh * .04f, tela.right - bw * .02f, tela.bottom - bh * .04f)
    drawRoundRect(Brush.linearGradient(listOf(Color.White, lerp(Color.White, a1, .22f)), vidro.topLeft, vidro.bottomRight), vidro.topLeft, vidro.size, CornerRadius(r * .6f))
    for (k in 0..1) {
        val cc = Offset(x + bw * .92f, y + bh * (.32f + k * .32f))
        drawCircle(Color(0xFF8A94A5), bw * .03f, cc); drawCircle(Color(0xFFE8ECF2), bw * .02f, cc)
    }
    drawRoundRect(a1.copy(alpha = .6f), Offset(x, y), Size(bw, bh), CornerRadius(r), style = Stroke(1.2f))
    return vidro.deflate(vidro.height * .08f) to Color(0xFF151A22)
}

/** Claquete de cinema. O nome vai na lousa. */
private fun DrawScope.claquete(caixa: Rect, a1: Color): Pair<Rect, Color> {
    val f = encaixe(caixa, 1.25f)
    val tampa = f.height * .15f; val folga = f.height * .1f
    val x = f.left; val bw = f.width; val y = f.top + tampa + folga; val bh = f.bottom - y
    val r = bh * .06f
    drawRoundRect(ESCURO, Offset(x, y), Size(bw, bh), CornerRadius(r))
    listras(Offset(x, y), Size(bw, bh * .17f))
    // tampa inclinada: o ângulo é limitado para a ponta não sair do card
    val ang = Math.toDegrees(asin(((folga + tampa * .2f) / bw).toDouble().coerceIn(0.0, .2))).toFloat()
    rotate(-ang, Offset(x, y)) { listras(Offset(x, y - tampa - 1f), Size(bw * .98f, tampa)) }
    drawCircle(Color.White, bh * .035f, Offset(x + bh * .05f, y + bh * .02f))
    drawRoundRect(a1.copy(alpha = .55f), Offset(x, y), Size(bw, bh), CornerRadius(r), style = Stroke(1.2f))
    // linha de giz sob o nome
    val lousa = Rect(x + bw * .07f, y + bh * .24f, x + bw * .93f, y + bh * .9f)
    drawLine(Color.White.copy(alpha = .18f), Offset(lousa.left, lousa.bottom), Offset(lousa.right, lousa.bottom), 1f)
    return lousa to Color.White
}

private fun DrawScope.listras(o: Offset, t: Size) {
    drawRect(ESCURO, o, t)
    val n = 6; val passo = t.width / n
    for (i in 0 until n) {
        val p = Path().apply {
            moveTo(o.x + i * passo + passo * .2f, o.y + t.height); lineTo(o.x + i * passo + passo * .5f, o.y)
            lineTo(o.x + i * passo + passo * .85f, o.y); lineTo(o.x + i * passo + passo * .55f, o.y + t.height); close()
        }
        drawPath(p, Color(0xFFF2F4F8))
    }
}

/** Balde de pipoca com controle remoto encostado. O nome vai na etiqueta do balde. */
private fun DrawScope.pipoca(caixa: Rect, a1: Color): Pair<Rect, Color> {
    val f = encaixe(caixa, 1.0f)
    val topoPipoca = f.height * .2f
    val y0 = f.top + topoPipoca; val y1 = f.bottom
    val x0 = f.left + f.width * .04f; val x1 = f.right - f.width * .1f
    // controle remoto atrás, à direita
    val rw = f.width * .2f; val rh = f.height * .62f; val rx = f.right - rw; val ry = f.top + f.height * .06f
    rotate(12f, Offset(rx + rw / 2, ry + rh / 2)) {
        drawRoundRect(Color(0xFF2B323D), Offset(rx, ry), Size(rw, rh), CornerRadius(rw * .3f))
        drawRoundRect(Brush.verticalGradient(METAL, ry, ry + rh), Offset(rx, ry), Size(rw, rh), CornerRadius(rw * .3f), style = Stroke(1.5f))
        drawCircle(Color(0xFF8A94A5), rw * .28f, Offset(rx + rw / 2, ry + rh * .22f))
    }
    // pipocas
    val creme = Color(0xFFF3EBD8)
    val rp = (x1 - x0) * .085f
    for (i in 0 until 11) {
        val px = x0 + rp + (i % 6) * (x1 - x0 - 2 * rp) / 5f
        val py = y0 - rp * (.4f + (i / 6) * 1.3f + (i % 2) * .5f)
        drawCircle(creme, rp, Offset(px, py)); drawCircle(Color(0xFFD9CDB2), rp, Offset(px, py), style = Stroke(1f))
    }
    // balde listrado
    val inc = (x1 - x0) * .08f
    val balde = Path().apply { moveTo(x0, y0); lineTo(x1, y0); lineTo(x1 - inc, y1); lineTo(x0 + inc, y1); close() }
    drawPath(balde, Color(0xFF2B323D))
    for (i in 0 until 5) {
        val a = x0 + (x1 - x0) * (i / 5f) + (x1 - x0) * .03f; val b = a + (x1 - x0) / 10f
        val p = Path().apply { moveTo(a, y0); lineTo(b, y0); lineTo(b - inc * .3f, y1); lineTo(a + inc * .3f, y1); close() }
        drawPath(p, Brush.verticalGradient(METAL, y0, y1))
    }
    drawPath(balde, a1.copy(alpha = .55f), style = Stroke(1.2f))
    // etiqueta onde vai o nome
    val h = y1 - y0
    val et = Rect(x0 + inc + (x1 - x0) * .02f, y0 + h * .24f, x1 - inc - (x1 - x0) * .02f, y0 + h * .8f)
    drawRoundRect(Color(0xF01B2029), et.topLeft, et.size, CornerRadius(et.height * .12f))
    drawRoundRect(a1.copy(alpha = .7f), et.topLeft, et.size, CornerRadius(et.height * .12f), style = Stroke(1.2f))
    return et.deflate(et.height * .08f) to Color.White
}

/** Círculo com botão de play e órbita. O nome vai na metade de baixo do círculo. */
private fun DrawScope.play(caixa: Rect, a1: Color, bg: Color): Pair<Rect, Color> {
    val f = encaixe(caixa, 1f)
    val r = f.width / 2; val c = f.center
    drawCircle(Brush.radialGradient(listOf(Color(0xFF4A5566), Color(0xFF1F2530)), c, r), r, c)
    rotate(-18f, c) { drawOval(a1.copy(alpha = .45f), Offset(c.x - r * .98f, c.y - r * .36f), Size(r * 1.96f, r * .72f), style = Stroke(r * .04f)) }
    drawCircle(a1.copy(alpha = .6f), r, c, style = Stroke(1.2f))
    val q = r * .56f
    val bt = Offset(c.x - q / 2, c.y - r * .78f)
    drawRoundRect(lerp(bg, a1, .35f), bt, Size(q, q), CornerRadius(q * .25f))
    drawRoundRect(a1, bt, Size(q, q), CornerRadius(q * .25f), style = Stroke(2f))
    val tri = Path().apply { moveTo(bt.x + q * .34f, bt.y + q * .24f); lineTo(bt.x + q * .74f, bt.y + q * .5f); lineTo(bt.x + q * .34f, bt.y + q * .76f); close() }
    drawPath(tri, Brush.linearGradient(listOf(Color.White, a1)))
    // faixa de texto: corda do círculo, larga o bastante para não encostar na borda
    return Rect(c.x - r * .8f, c.y - r * .1f, c.x + r * .8f, c.y + r * .55f) to Color.White
}

/** Rádio antigo com alça. O nome vai no visor. */
private fun DrawScope.radio(caixa: Rect, a1: Color): Pair<Rect, Color> {
    val f = encaixe(caixa, 1.4f)
    val alca = f.height * .14f
    val x = f.left; val y = f.top + alca; val bw = f.width; val bh = f.height - alca
    val e = (bh * .045f).coerceAtLeast(2f)
    drawRoundRect(Color(0xFFB8C2D6), Offset(x + bw * .22f, f.top + e / 2), Size(bw * .56f, alca * 1.6f), CornerRadius(alca), style = Stroke(e))
    drawRoundRect(Brush.verticalGradient(METAL, y, y + bh), Offset(x, y), Size(bw, bh), CornerRadius(bh * .1f))
    // visor (largo, em cima) com o nome
    val visor = Rect(x + bw * .06f, y + bh * .1f, x + bw * .94f, y + bh * .52f)
    drawRoundRect(Color(0xFF151A22), visor.topLeft, visor.size, CornerRadius(bh * .05f))
    drawRoundRect(a1.copy(alpha = .8f), visor.topLeft, visor.size, CornerRadius(bh * .05f), style = Stroke(1.2f))
    // alto-falante e botões
    val gy = y + bh * .6f; val gh = bh * .3f
    val cx = x + bw * .3f; val cy = gy + gh / 2
    drawCircle(Color(0xFF2B323D), gh * .55f, Offset(cx, cy))
    for (i in -2..2) for (j in -2..2) if (i * i + j * j <= 5) drawCircle(Color(0xFF8A94A5), gh * .05f, Offset(cx + i * gh * .17f, cy + j * gh * .17f))
    for (k in 0..1) {
        val cc = Offset(x + bw * (.66f + k * .16f), cy)
        drawCircle(Color(0xFF8A94A5), gh * .32f, cc); drawCircle(Color(0xFFE8ECF2), gh * .2f, cc)
    }
    drawRoundRect(a1.copy(alpha = .55f), Offset(x, y), Size(bw, bh), CornerRadius(bh * .1f), style = Stroke(1.2f))
    return visor.deflate(visor.height * .1f) to lerp(Color.White, a1, .35f)
}

/** Página escondida (só pelo teste: --es pagina modelos) para conferir os desenhos com nomes curtos e longos. */
@Composable
fun GaleriaModelos() {
    val nomes = listOf("SBT", "Canal Rural", "TV Assembleia Legislativa do Estado do Rio de Janeiro HD")
    Column(Modifier.fillMaxSize().padding(start = 120.dp, top = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (largo in listOf(true, false)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in Modelo.values()) for (n in if (largo) nomes.take(2) else nomes.drop(1).take(1) + nomes.last()) {
                val mod = if (largo) Modifier.size(96.dp, 54.dp) else Modifier.size(56.dp, 84.dp)
                ModeloCard(t, if (t == Modelo.CANAL || t == Modelo.RADIO) n else if (n == "SBT") "Up" else "O Homem que Sabia Demais e Outras Histórias", mod.clip(RoundedCornerShape(8.dp)))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in Modelo.values()) ModeloCard(t, nomes.last(), Modifier.size(160.dp, 90.dp).clip(RoundedCornerShape(10.dp)))
        }
    }
}
