package com.lumestar.tv

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import org.json.JSONArray
import java.net.URLEncoder
import java.text.Normalizer

/** Uma rádio online (dados do Radio Browser, banco aberto de rádios que transmitem publicamente). */
class Estacao(val id: String, val nome: String, val url: String, val logo: String, val cidade: String, val estado: String,
              val lat: Double, val lon: Double, val tags: String, val votos: Int, val kbps: Int) {
    var km: Double = -1.0
}

/** Rádio: busca por cidade/estado e toca pelo serviço em segundo plano. */
@UnstableApi
object Radio {
    private const val API = "https://de1.api.radio-browser.info/json/stations/search"
    private var controller: MediaController? = null
    var tocando by mutableStateOf<Estacao?>(null); private set
    var pausado by mutableStateOf(false); private set
    var estado by mutableStateOf(""); private set

    private fun conectar(c: Context, depois: (MediaController) -> Unit) {
        controller?.let { return depois(it) }
        val tok = SessionToken(c, ComponentName(c, RadioService::class.java))
        val f = MediaController.Builder(c, tok).buildAsync()
        f.addListener({
            try {
                val m = f.get(); controller = m
                m.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(p: Boolean) { pausado = !p && m.playbackState != Player.STATE_BUFFERING }
                    override fun onPlaybackStateChanged(s: Int) { estado = when (s) { Player.STATE_BUFFERING -> "Conectando…"; Player.STATE_READY -> ""; else -> estado } }
                    override fun onPlayerError(e: androidx.media3.common.PlaybackException) { estado = "Esta rádio não respondeu. Tente outra." }
                })
                depois(m)
            } catch (e: Throwable) { estado = "Não consegui iniciar a rádio" }
        }, androidx.core.content.ContextCompat.getMainExecutor(c))
    }

    fun tocar(c: Context, e: Estacao) {
        tocando = e; pausado = false; estado = "Conectando…"
        conectar(c.applicationContext) { m ->
            val meta = MediaMetadata.Builder().setTitle(e.nome).setArtist(listOf(e.cidade, e.estado).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Rádio online" })
                .apply { if (e.logo.isNotBlank()) setArtworkUri(Uri.parse(e.logo)) }.build()
            m.setMediaItem(MediaItem.Builder().setUri(e.url).setMediaId(e.id).setMediaMetadata(meta).build())
            m.prepare(); m.play()
        }
    }

    /** Rádios favoritas: guardadas inteiras (não dependem da cidade escolhida). */
    var favoritas by mutableStateOf<List<Estacao>>(emptyList()); private set
    private var favLidas = false
    fun carregarFavoritas() {
        if (favLidas) return; favLidas = true
        favoritas = try {
            val a = org.json.JSONArray(PP.sp.getString("radiosFavoritas", "[]"))
            (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
                Estacao(o.getString("id"), o.getString("nome"), o.getString("url"), o.optString("logo"), o.optString("cidade"), o.optString("estado"),
                    0.0, 0.0, "", 0, o.optInt("kbps")) } }
        } catch (e: Throwable) { emptyList() }
    }
    fun favorita(e: Estacao) = favoritas.any { it.id == e.id }
    /** Liga/desliga a favorita; devolve true se ficou favorita. */
    fun alternarFavorita(e: Estacao): Boolean {
        carregarFavoritas()
        val on = !favorita(e)
        favoritas = if (on) listOf(e) + favoritas else favoritas.filter { it.id != e.id }
        val a = org.json.JSONArray()
        favoritas.forEach { x -> a.put(org.json.JSONObject().put("id", x.id).put("nome", x.nome).put("url", x.url).put("logo", x.logo).put("cidade", x.cidade).put("estado", x.estado).put("kbps", x.kbps)) }
        PP.sp.edit().putString("radiosFavoritas", a.toString()).apply()
        return on
    }

    fun alternar() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }

    /** Para a rádio (ex.: ao abrir um vídeo). */
    fun parar() { controller?.let { it.stop(); it.clearMediaItems() }; tocando = null; pausado = false; estado = "" }

    private fun ler(u: String, todas: LinkedHashMap<String, Estacao>) {
        val a = JSONArray(Net.text(u))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            if (url.isBlank() || o.optInt("lastcheckok", 1) != 1) continue
            val id = o.optString("stationuuid")
            if (todas.containsKey(id)) continue
            todas[id] = Estacao(id, o.optString("name").trim(), url, o.optString("favicon"), "", o.optString("state"),
                o.optDouble("geo_lat", Double.NaN), o.optDouble("geo_long", Double.NaN), o.optString("tags"), o.optInt("votes"), o.optInt("bitrate"))
        }
    }

    /** Busca pelo nome da rádio no Brasil inteiro (ex.: "Jovem Pan", "CBN"), as mais ouvidas primeiro. */
    fun buscarNome(q: String, cb: (List<Estacao>, String) -> Unit) {
        Thread {
            val todas = LinkedHashMap<String, Estacao>(); var erro = ""
            try { ler("$API?countrycode=BR&name=" + URLEncoder.encode(q.trim(), "UTF-8") + "&hidebroken=true&order=clickcount&reverse=true&limit=80", todas) }
            catch (e: Throwable) { erro = "Não consegui buscar: " + Net.why(e) }
            if (todas.size < 5 && erro.isBlank()) try { // sem acento também ("Sao Paulo")
                ler("$API?countrycode=BR&name=" + URLEncoder.encode(semAcento(q.trim()), "UTF-8") + "&hidebroken=true&order=clickcount&reverse=true&limit=80", todas)
            } catch (e: Throwable) {}
            android.os.Handler(android.os.Looper.getMainLooper()).post { cb(todas.values.toList(), if (todas.isEmpty() && erro.isBlank()) "Nenhuma rádio no ar com esse nome." else erro) }
        }.start()
    }

    private fun semAcento(t: String) = Normalizer.normalize(t, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun distancia(a: Double, b: Double, c: Double, d: Double): Double {
        val r = 6371.0; val dLat = Math.toRadians(c - a); val dLon = Math.toRadians(d - b)
        val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(a)) * Math.cos(Math.toRadians(c)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }

    /**
     * Rádios da cidade escolhida: as que ficam perto (até 90 km, quando a rádio informa a localização)
     * ou que citam a cidade no nome, primeiro; depois as mais ouvidas do estado. Só as que estavam no ar no último teste.
     */
    fun buscar(cidade: String, estadoUf: String, lat: Double?, lon: Double?, cb: (perto: List<Estacao>, estado: List<Estacao>, erro: String) -> Unit) {
        Thread {
            val todas = LinkedHashMap<String, Estacao>()
            var erro = ""
            for (st in listOf(estadoUf, semAcento(estadoUf)).distinct().filter { it.isNotBlank() }) try {
                ler("$API?countrycode=BR&state=" + URLEncoder.encode(st, "UTF-8") + "&hidebroken=true&order=clickcount&reverse=true&limit=400", todas)
            } catch (e: Throwable) { erro = "Não consegui buscar as rádios: " + Net.why(e) }
            val nomeCidade = semAcento(cidade.substringBefore(',')).lowercase().trim()
            val perto = ArrayList<Estacao>(); val resto = ArrayList<Estacao>()
            for (e in todas.values) {
                if (lat != null && lon != null && !e.lat.isNaN() && !e.lon.isNaN()) e.km = distancia(lat, lon, e.lat, e.lon)
                val cita = nomeCidade.length >= 3 && semAcento("${e.nome} ${e.tags}").lowercase().contains(nomeCidade)
                if ((e.km in 0.0..90.0) || cita) perto.add(e) else resto.add(e)
            }
            perto.sortWith(compareBy({ if (it.km < 0) 999.0 else it.km }, { -it.votos }))
            android.os.Handler(android.os.Looper.getMainLooper()).post { cb(perto, resto.take(120), if (todas.isEmpty() && erro.isBlank()) "Nenhuma rádio online encontrada para este estado." else erro) }
        }.start()
    }
}

/** Página Rádio: cidade escolhida, rádios por perto e do estado, e a barra "tocando agora". */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun RadioPage(st: AppState, locked: Boolean) {
    val ctx = LocalContext.current
    PP.rev.let { }
    var perto by remember { mutableStateOf<List<Estacao>?>(null) }
    var doEstado by remember { mutableStateOf<List<Estacao>>(emptyList()) }
    var erro by remember { mutableStateOf("") }
    var opcoes by remember { mutableStateOf<List<Weather.Place>>(emptyList()) }
    var buscaMsg by remember { mutableStateOf("") }
    var nomeBusca by remember { mutableStateOf("") }
    var nomeMsg by remember { mutableStateOf("") }
    var achadas by remember { mutableStateOf<List<Estacao>?>(null) }
    // cidade padrão: a do clima; se não houver, Rio de Janeiro
    if (PP.radioEstado.isBlank()) { PP.radioCidade = "Rio de Janeiro, Rio de Janeiro"; PP.radioEstado = "Rio de Janeiro"; PP.radioPos = "-22.9068,-43.1729" }
    val pos = PP.radioPos.split(',').mapNotNull { it.toDoubleOrNull() }
    LaunchedEffect(PP.radioCidade) {
        perto = null; erro = ""
        Radio.buscar(PP.radioCidade, PP.radioEstado, pos.getOrNull(0), pos.getOrNull(1)) { p, e, m -> perto = p; doEstado = e; erro = m }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 28.dp, end = 28.dp, top = 56.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("Rádio", 30, C.fg, FontWeight.ExtraBold)
        T("Rádios online da cidade que você escolher, ou busque pelo nome. A rádio continua tocando quando você sai do app; os controles ficam na notificação e no controle remoto.", 13, C.muted, lineHeight = 19)
        Radio.tocando?.let { e ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.panel).border(1.dp, C.a1, RoundedCornerShape(14.dp)).padding(12.dp).focusGroup(),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LogoArte(e.nome, e.logo, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), pad = 6.dp, tamanhoNome = 11) { ModeloCard(Modelo.RADIO, e.nome, Modifier.fillMaxSize(), aoVivo = false) }
                Column(Modifier.weight(1f)) {
                    T("TOCANDO AGORA", 10, C.a1, FontWeight.Bold)
                    T(e.nome, 16, C.fg, FontWeight.Bold, maxLines = 1)
                    T(Radio.estado.ifBlank { if (Radio.pausado) "Pausada" else "No ar · continua tocando fora do app" }, 11, C.muted, maxLines = 1)
                }
                Btn(if (Radio.pausado) "▶ Tocar" else "⏸ Pausar", locked = locked, primary = true) { Radio.alternar() }
                Btn(if (Radio.favorita(e)) "♥ Favorita" else "♡ Favoritar", locked = locked) { st.say(if (Radio.alternarFavorita(e)) "♥ ${e.nome} nas rádios favoritas" else "${e.nome} saiu das favoritas") }
                Btn("■ Parar", locked = locked) { Radio.parar() }
            }
        }
        Radio.carregarFavoritas()
        if (Radio.favoritas.isNotEmpty()) {
            T("♥ RÁDIOS FAVORITAS (${Radio.favoritas.size})", 11, C.a1, FontWeight.Bold)
            GradeRadios(ctx, Radio.favoritas, locked)
        } else T("Dica: toque uma rádio e use \"♡ Favoritar\" para ela aparecer aqui no topo.", 11, C.dim)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            T("Cidade: " + PP.radioCidade, 14, C.fg, FontWeight.SemiBold)
        }
        FieldRow(st, "Trocar cidade", "", locked, hint = "Digite a cidade (ex.: São Paulo, Macaé)") { q ->
            if (q.isNotBlank()) { buscaMsg = "Procurando…"; Weather.search(q) { l, m -> opcoes = l.filter { it.country.contains("Brasil", true) || it.country.contains("Brazil", true) }.ifEmpty { l }; buscaMsg = if (l.isEmpty()) m.ifBlank { "Não achei essa cidade." } else "" } }
        }
        if (buscaMsg.isNotBlank()) T(buscaMsg, 12, C.muted)
        FieldRow(st, "Buscar rádio", nomeBusca, locked, hint = "Nome da rádio (ex.: Jovem Pan, CBN, Antena 1)") { q ->
            nomeBusca = q; achadas = null
            if (q.isNotBlank()) { nomeMsg = "Procurando…"; Radio.buscarNome(q) { l, m -> achadas = l; nomeMsg = m } } else nomeMsg = ""
        }
        if (nomeMsg.isNotBlank()) T(nomeMsg, 12, C.muted)
        achadas?.takeIf { it.isNotEmpty() }?.let { l ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("RESULTADO PARA \"${nomeBusca.uppercase()}\" (${l.size})", 11, C.muted, FontWeight.Bold)
                Btn("Limpar busca", locked = locked) { achadas = null; nomeBusca = ""; nomeMsg = "" }
            }
            GradeRadios(ctx, l, locked)
        }
        if (opcoes.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.focusGroup()) {
            opcoes.take(5).forEach { p ->
                Btn(p.label, locked = locked) { PP.radioCidade = p.name + ", " + p.region; PP.radioEstado = p.region; PP.radioPos = "${p.lat},${p.lon}"; opcoes = emptyList(); st.say("Rádios de ${p.name}") }
            }
        }
        val l = perto
        when {
            l == null -> T("Buscando rádios no ar…", 13, C.muted)
            erro.isNotBlank() && l.isEmpty() && doEstado.isEmpty() -> T(erro, 13, C.bad)
            else -> {
                T("PERTO DE ${PP.radioCidade.substringBefore(',').uppercase()} (${l.size})", 11, C.muted, FontWeight.Bold)
                if (l.isEmpty()) T("Nenhuma rádio desta cidade informa a localização. Veja as do estado abaixo.", 12, C.dim)
                else GradeRadios(ctx, l, locked)
                if (doEstado.isNotEmpty()) {
                    T("MAIS OUVIDAS EM ${PP.radioEstado.uppercase()} (${doEstado.size})", 11, C.muted, FontWeight.Bold)
                    GradeRadios(ctx, doEstado, locked)
                }
            }
        }
        T("Fonte: Radio Browser (radio-browser.info), banco aberto de rádios com transmissão pública na internet.", 10, C.dim)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun GradeRadios(ctx: Context, l: List<Estacao>, locked: Boolean) {
    Column(Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        l.chunked(5).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { linha.forEach { e -> CardRadio(ctx, e, locked) } }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun CardRadio(ctx: Context, e: Estacao, locked: Boolean) {
    var f by remember { mutableStateOf(false) }
    val ativo = Radio.tocando?.id == e.id
    Column(Modifier.width(150.dp)) {
        Box(
            Modifier.fillMaxWidth().height(84.dp)
                .graphicsLayer { val k = if (f) 1.06f else 1f; scaleX = k; scaleY = k }
                .shadow(if (f) 14.dp else 0.dp, RoundedCornerShape(12.dp), ambientColor = C.a1, spotColor = C.a1)
                .clip(RoundedCornerShape(12.dp))
                .border(if (f || ativo) 2.dp else 0.dp, if (f) Color.White else if (ativo) C.a1 else Color.Transparent, RoundedCornerShape(12.dp))
                .focusProperties { canFocus = !locked }
                .onFocusChanged { val n = it.isFocused; if (n && !f) Sfx.tick(); f = n }
                .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); Radio.tocar(ctx, e) }
        ) {
            LogoArte(e.nome, e.logo, Modifier.fillMaxSize(), pad = 8.dp, tamanhoNome = 13) { ModeloCard(Modelo.RADIO, e.nome, Modifier.fillMaxSize(), aoVivo = false) }
            if (Radio.favorita(e)) Box(Modifier.align(Alignment.TopStart).padding(4.dp).size(18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xC8050816)), contentAlignment = Alignment.Center) { T("♥", 10, Color(0xFFFF8FB8), FontWeight.Bold) }
            if (ativo) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(C.a1).padding(horizontal = 5.dp, vertical = 1.dp)) { T("NO AR", 8, Color(0xFF050816), FontWeight.Bold) }
        }
        T(e.nome, 11, if (f) C.fg else C.muted, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        T(listOfNotNull(if (e.km >= 0) "${e.km.toInt()} km" else null, if (e.kbps > 0) "${e.kbps} kbps" else null).joinToString(" · "), 9, C.dim, maxLines = 1)
    }
}
