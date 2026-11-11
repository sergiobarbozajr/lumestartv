package com.lumestar.tv

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Faixas pretas medidas em vários quadros (cerca de 8 em 4 s).
 * A faixa de verdade é escura E parada em todos os quadros; uma cena escura muda de um quadro
 * para outro ou tem algum ponto claro. Assim a cena noturna não vira "faixa" e o corte não passa do ponto.
 */
object Faixas {
    const val W = 192
    const val H = 108
    private const val ESCURO = 28 // luminância: preto de vídeo (16) + ruído de compressão

    /** Ampliação em % (cima e baixo, laterais). null = não deu para medir (imagem toda escura ou foto preta). */
    fun medir(quadros: List<Bitmap>): Pair<Int, Int>? {
        val qs = quadros.filter { it.width == W && it.height == H }
        if (qs.size < 4) return null
        val n = qs.size
        val claroLinha = IntArray(H); val claroCol = IntArray(W)
        val mediaLinha = Array(n) { IntArray(H) }; val mediaCol = Array(n) { IntArray(W) }
        var claroTotal = 0L
        val px = IntArray(W * H)
        qs.forEachIndexed { q, b ->
            b.getPixels(px, 0, W, 0, 0, W, H)
            val somaCol = IntArray(W)
            for (y in 0 until H) {
                var s = 0
                for (x in 0 until W) {
                    val p = px[y * W + x]
                    val l = (((p shr 16) and 255) * 3 + ((p shr 8) and 255) * 6 + (p and 255)) / 10
                    s += l; somaCol[x] += l
                    if (l > ESCURO) { claroLinha[y]++; claroCol[x]++; claroTotal++ }
                }
                mediaLinha[q][y] = s / W
            }
            for (x in 0 until W) mediaCol[q][x] = somaCol[x] / H
        }
        // tudo escuro: foto preta (algumas TVs no modo direto) ou cena escura demais para decidir
        if (claroTotal < W.toLong() * H * n / 200) return null

        // faixa: quase nenhum ponto claro (aceita logo ou ruído) e brilho parado entre os quadros
        fun parada(m: Array<IntArray>, i: Int) = (0 until n).maxOf { m[it][i] } - (0 until n).minOf { m[it][i] } <= 6
        fun linhaFaixa(y: Int) = claroLinha[y] <= W * n / 50 && parada(mediaLinha, y)
        fun colFaixa(x: Int) = claroCol[x] <= H * n / 50 && parada(mediaCol, x)

        var top = 0; while (top < H * 3 / 10 && linhaFaixa(top)) top++
        var bot = 0; while (bot < H * 3 / 10 && linhaFaixa(H - 1 - bot)) bot++
        var lef = 0; while (lef < W * 3 / 10 && colFaixa(lef)) lef++
        var rig = 0; while (rig < W * 3 / 10 && colFaixa(W - 1 - rig)) rig++

        // faixas de cinema são iguais dos dois lados; se uma é bem menor (legenda ou logo nela), usa a menor para não cortar
        val v = if (abs(top - bot) > 4) 2 * minOf(top, bot) else top + bot
        val h = if (abs(lef - rig) > 6) 2 * minOf(lef, rig) else lef + rig
        return pct(v / H.toFloat()) to pct(h / W.toFloat())
    }

    private fun pct(f: Float) = if (f > .03f) ((1 / (1 - f) - 1) * 100).roundToInt().coerceAtMost(40) else 0

    /** Duas medições "iguais" (diferença de até 2%). */
    fun perto(a: Pair<Int, Int>, b: Pair<Int, Int>) = abs(a.first - b.first) <= 2 && abs(a.second - b.second) <= 2
}
