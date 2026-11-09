package com.lumestar.tv

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

/**
 * Identificação real do aparelho para "Sobre o aparelho".
 * O Android costuma informar só o nome comercial genérico ("Smart TV Pro") e a resolução da interface
 * (1920 × 1080), mesmo em TVs 4K. Aqui procuramos o modelo nas propriedades do sistema e a resolução
 * física do painel nos modos de tela que a TV aceita.
 */
object Device {
    /** Lê uma propriedade do sistema (android.os.SystemProperties é interna; acesso por reflexão). */
    fun prop(key: String): String = try {
        val c = Class.forName("android.os.SystemProperties")
        (c.getMethod("get", String::class.java).invoke(null, key) as? String)?.trim() ?: ""
    } catch (e: Throwable) { "" }

    private val MODELO_RX = Regex("\\b([A-Z]{1,3}\\d{3,4}[A-Z]{0,3}(?:[- ]?[A-Z0-9]{1,4})?)\\b")

    /** Todas as propriedades (getprop) que falam de modelo/produto; só leitura. */
    private fun propsModelo(): List<Pair<String, String>> = try {
        val p = Runtime.getRuntime().exec("getprop")
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        Regex("\\[([^\\]]+)\\]: \\[([^\\]]*)\\]").findAll(out).map { it.groupValues[1] to it.groupValues[2].trim() }
            .filter { (k, v) -> v.isNotBlank() && Regex("model|product|tcl|sku|project|brand|series|customer", RegexOption.IGNORE_CASE).containsMatchIn(k) }
            .toList()
    } catch (e: Throwable) { emptyList() }

    /**
     * Modelo comercial (ex.: "A400M"). Ordem: chaves conhecidas de fabricantes, nome do aparelho
     * nas configurações e, por último, qualquer propriedade de modelo/produto que pareça um código de TV.
     */
    fun modelo(c: Context): String {
        val generico = Build.MODEL.trim()
        val conhecidas = listOf(
            "ro.product.tcl.model", "ro.tcl.model", "persist.sys.tcl.model", "ro.vendor.tcl.model", "ro.odm.tcl.model",
            "ro.product.model.display", "ro.product.odm.model", "ro.product.vendor.model", "ro.product.system.model",
            "ro.boot.model", "ro.product.marketname", "ro.vendor.product.model",
        )
        conhecidas.map { prop(it) }.firstOrNull { it.isNotBlank() && !it.equals(generico, true) }?.let { return it }
        val nome = try { android.provider.Settings.Global.getString(c.contentResolver, "device_name") ?: "" } catch (e: Throwable) { "" }
        MODELO_RX.find(nome)?.let { return it.value }
        for ((_, v) in propsModelo()) MODELO_RX.find(v)?.let { m -> if (!m.value.equals(generico, true)) return m.value }
        return ""
    }

    /** Código interno do produto (ajuda a identificar quando o modelo não aparece). */
    fun codigo(): String = listOf(prop("ro.product.name"), prop("ro.product.device"), Build.DEVICE).firstOrNull { it.isNotBlank() } ?: ""

    class Tela(val painelW: Int, val painelH: Int, val atualW: Int, val atualH: Int, val uiW: Int, val uiH: Int, val hz: List<Int>, val hdr: List<String>) {
        val painel4k get() = maxOf(painelW, painelH) >= 3800
        fun nome(): String {
            val tipo = when { maxOf(painelW, painelH) >= 7600 -> "8K"; painel4k -> "4K Ultra HD"; maxOf(painelW, painelH) >= 2500 -> "QHD"; maxOf(painelW, painelH) >= 1900 -> "Full HD"; else -> "HD" }
            return "$tipo · $painelW × $painelH"
        }
    }

    /** Resolução física do painel, modo atual, interface do app, frequências e HDR. */
    fun tela(c: Context): Tela {
        val dm = c.resources.displayMetrics
        var uiW = dm.widthPixels; var uiH = dm.heightPixels
        var pw = 0; var ph = 0; var aw = 0; var ah = 0
        val hz = sortedSetOf<Int>(); val hdr = ArrayList<String>()
        try {
            val d = (c.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
            val real = android.graphics.Point(); @Suppress("DEPRECATION") d.getRealSize(real); uiW = real.x; uiH = real.y
            if (Build.VERSION.SDK_INT >= 23) {
                d.supportedModes.forEach { m ->
                    if (m.physicalWidth * m.physicalHeight > pw * ph) { pw = m.physicalWidth; ph = m.physicalHeight }
                    hz.add(Math.round(m.refreshRate))
                }
                aw = d.mode.physicalWidth; ah = d.mode.physicalHeight
            } else hz.add(Math.round(d.refreshRate))
            if (Build.VERSION.SDK_INT >= 24) d.hdrCapabilities?.supportedHdrTypes?.forEach { t ->
                hdr.add(when (t) { 1 -> "Dolby Vision"; 2 -> "HDR10"; 3 -> "HLG"; 4 -> "HDR10+"; else -> "HDR" })
            }
        } catch (e: Throwable) {}
        // algumas TVs só mostram o modo 4K na propriedade do sistema
        Regex("(\\d{3,4})\\s*[x×]\\s*(\\d{3,4})").find(prop("sys.display-size").ifBlank { prop("vendor.display-size") })?.let { m ->
            val w = m.groupValues[1].toInt(); val h = m.groupValues[2].toInt()
            if (w * h > pw * ph) { pw = w; ph = h }
        }
        if (pw == 0) { pw = uiW; ph = uiH }
        if (aw == 0) { aw = pw; ah = ph }
        return Tela(pw, ph, aw, ah, uiW, uiH, hz.toList(), hdr.distinct())
    }
}
