package com.lumestar.tv

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.URLEncoder

/** Clima da cidade escolhida, pelo Open-Meteo (grátis, sem cadastro). Atualiza a cada 30 minutos. */
object Weather {
    class Now(val city: String, val temp: Int, val icon: String, val desc: String, val max: Int, val min: Int)

    var now by mutableStateOf<Now?>(null)
    var status by mutableStateOf("")
    private var lastAt = 0L
    private var lastCity = ""
    private val main = Handler(Looper.getMainLooper())

    fun icon(code: Int, day: Boolean): Pair<String, String> = when (code) {
        0 -> (if (day) "☀" else "☾") to "Céu limpo"
        1, 2 -> (if (day) "🌤" else "☁") to "Poucas nuvens"
        3 -> "☁" to "Nublado"
        45, 48 -> "🌫" to "Neblina"
        in 51..57 -> "🌦" to "Garoa"
        in 61..67, in 80..82 -> "🌧" to "Chuva"
        in 71..77, 85, 86 -> "❄" to "Neve"
        in 95..99 -> "⛈" to "Tempestade"
        else -> "☁" to "—"
    }

    /** Busca de novo se mudou a cidade ou se passaram 30 minutos. */
    fun refresh(city: String, force: Boolean = false) {
        val c = city.trim()
        if (c.isEmpty()) { now = null; return }
        if (!force && c == lastCity && System.currentTimeMillis() - lastAt < 30 * 60_000) return
        lastCity = c; lastAt = System.currentTimeMillis()
        Thread {
            val r = try {
                val g = JSONObject(Net.text("https://geocoding-api.open-meteo.com/v1/search?count=1&language=pt&name=" + URLEncoder.encode(c.substringBefore(','), "UTF-8")))
                val p = g.optJSONArray("results")?.optJSONObject(0) ?: throw java.io.IOException("cidade não encontrada")
                val name = p.optString("name")
                val w = JSONObject(Net.text("https://api.open-meteo.com/v1/forecast?latitude=${p.optDouble("latitude")}&longitude=${p.optDouble("longitude")}&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"))
                val cur = w.getJSONObject("current"); val d = w.getJSONObject("daily")
                val (ic, desc) = icon(cur.optInt("weather_code"), cur.optInt("is_day", 1) == 1)
                Now(name, Math.round(cur.optDouble("temperature_2m")).toInt(), ic, desc, Math.round(d.getJSONArray("temperature_2m_max").optDouble(0)).toInt(), Math.round(d.getJSONArray("temperature_2m_min").optDouble(0)).toInt()) to ""
            } catch (e: Throwable) { lastAt = 0; null to (if (e.message == "cidade não encontrada") "Cidade não encontrada. Tente só o nome, ex.: Niterói" else "Clima indisponível: " + Net.why(e)) }
            main.post { if (r.first != null) now = r.first; status = r.second }
        }.start()
    }
}
