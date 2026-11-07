package com.lumestar.tv

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Perfil de desempenho, decidido uma vez ao abrir o app.
 *
 * Aparelhos fracos (ex.: Fire TV Stick 2ª geração: Android 5.1, ~900 MB de RAM) não aguentam
 * a abertura animada: no Android < 7 a memória nativa dos gradientes desenhados a cada quadro
 * só é liberada pelo coletor do Java, cresce em poucos segundos e o sistema mata o app.
 * No perfil leve o app pula a abertura, o buraco de minhoca e o diagnóstico de imagem.
 */
object Perf {
    /** true = aparelho fraco: sem animações pesadas. */
    var lite = false
        private set
    /** Motivo mostrado no diagnóstico (Configurações › Sobre o aparelho). */
    var reason = ""
        private set

    fun init(c: Context) {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val totalMb = mem.totalMem / (1024 * 1024)
        val motivos = buildList {
            if (Build.VERSION.SDK_INT < 24) add("Android ${Build.VERSION.RELEASE}")
            if (am?.isLowRamDevice == true) add("aparelho de pouca memória")
            if (totalMb in 1..1536) add("$totalMb MB de RAM")
        }
        lite = motivos.isNotEmpty()
        reason = if (lite) "Modo leve: " + motivos.joinToString(", ") else "Modo completo"
    }
}
