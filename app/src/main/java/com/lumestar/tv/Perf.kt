package com.lumestar.tv

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Perfil de desempenho, decidido uma vez ao abrir o app.
 *
 * Aparelhos fracos (ex.: Fire TV Stick 2ª geração: Android 5.1, ~900 MB de RAM) não aguentam
 * a abertura animada: no Android < 8 a memória nativa dos gradientes desenhados a cada quadro
 * só é liberada pelo coletor do Java, cresce em poucos segundos e o sistema mata o app.
 * No perfil leve o app pula a abertura, o buraco de minhoca e o diagnóstico de imagem.
 */
object Perf {
    /** Escolha do usuário em Configurações › Som e abertura: automático, sempre completo ou sempre leve. */
    const val AUTO = 0
    const val COMPLETO = 1
    const val LEVE = 2

    /** true = aparelho fraco: sem animações pesadas. */
    var lite = false
        private set
    /** Motivo mostrado no diagnóstico (Configurações › Sobre o aparelho). */
    var reason = ""
        private set
    /** Modo com que o app abriu (mudar nas Configurações vale ao reabrir). */
    var mode = AUTO
        private set

    /**
     * Automático: leve só onde o problema existe de fato.
     * O vazamento de memória dos gradientes é do Android < 8 (no 8+ a memória nativa é
     * liberada junto com o objeto); além disso, aparelhos que o fabricante marca como
     * "pouca memória" (Android Go etc.).
     */
    fun init(c: Context, mode: Int = AUTO) {
        this.mode = mode
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val totalMb = mem.totalMem / (1024 * 1024)
        val motivos = buildList {
            if (Build.VERSION.SDK_INT < 26) add("Android ${Build.VERSION.RELEASE}")
            if (am?.isLowRamDevice == true) add("aparelho de pouca memória")
        }
        when (mode) {
            COMPLETO -> { lite = false; reason = "Modo completo (escolhido nas Configurações)" }
            LEVE -> { lite = true; reason = "Modo leve (escolhido nas Configurações)" }
            else -> {
                lite = motivos.isNotEmpty()
                reason = (if (lite) "Modo leve automático: " + motivos.joinToString(", ") else "Modo completo automático") +
                    " · $totalMb MB de RAM"
            }
        }
    }
}
