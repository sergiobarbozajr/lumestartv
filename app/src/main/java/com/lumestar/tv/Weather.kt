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

    class Place(val name: String, val region: String, val country: String, val lat: Double, val lon: Double) {
        val label get() = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
    }

    /** Procura cidades pelo nome (até 8), para a pessoa escolher a certa. */
    fun search(q: String, cb: (List<Place>, String) -> Unit) {
        Thread {
            val r = try {
                val g = JSONObject(Net.text("https://geocoding-api.open-meteo.com/v1/search?count=8&language=pt&name=" + URLEncoder.encode(q.trim().substringBefore(','), "UTF-8")))
                val a = g.optJSONArray("results"); val out = ArrayList<Place>()
                if (a != null) for (i in 0 until a.length()) { val p = a.getJSONObject(i); out.add(Place(p.optString("name"), p.optString("admin1"), p.optString("country"), p.optDouble("latitude"), p.optDouble("longitude"))) }
                out to (if (out.isEmpty()) "Nenhuma cidade encontrada com esse nome." else "")
            } catch (e: Throwable) { emptyList<Place>() to "Não consegui procurar: " + Net.why(e) }
            main.post { cb(r.first, r.second) }
        }.start()
    }

    /** Busca de novo se mudou a cidade ou se passaram 30 minutos. pos = "lat,lon" da cidade escolhida. */
    fun refresh(city: String, force: Boolean = false, pos: String = "") {
        val c = city.trim()
        if (c.isEmpty()) { now = null; return }
        if (!force && c == lastCity && System.currentTimeMillis() - lastAt < 30 * 60_000) return
        lastCity = c; lastAt = System.currentTimeMillis()
        Thread {
            val r = try {
                var lat = pos.substringBefore(',', "").toDoubleOrNull(); var lon = pos.substringAfter(',', "").toDoubleOrNull()
                var name = c.substringBefore(',')
                if (lat == null || lon == null) {
                    val g = JSONObject(Net.text("https://geocoding-api.open-meteo.com/v1/search?count=1&language=pt&name=" + URLEncoder.encode(c.substringBefore(','), "UTF-8")))
                    val p = g.optJSONArray("results")?.optJSONObject(0) ?: throw java.io.IOException("cidade não encontrada")
                    name = p.optString("name"); lat = p.optDouble("latitude"); lon = p.optDouble("longitude")
                }
                val w = JSONObject(Net.text("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"))
                val cur = w.getJSONObject("current"); val d = w.getJSONObject("daily")
                val (ic, desc) = icon(cur.optInt("weather_code"), cur.optInt("is_day", 1) == 1)
                Now(name, Math.round(cur.optDouble("temperature_2m")).toInt(), ic, desc, Math.round(d.getJSONArray("temperature_2m_max").optDouble(0)).toInt(), Math.round(d.getJSONArray("temperature_2m_min").optDouble(0)).toInt()) to ""
            } catch (e: Throwable) { lastAt = 0; null to (if (e.message == "cidade não encontrada") "Cidade não encontrada. Tente só o nome, ex.: Niterói" else "Clima indisponível: " + Net.why(e)) }
            main.post { if (r.first != null) now = r.first; status = r.second }
        }.start()
    }
}
