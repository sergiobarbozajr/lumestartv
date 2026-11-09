package com.lumestar.tv

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ---------------- Capas e sinopses (TMDB) ----------------
@Composable
fun TmdbPanel(st: AppState, locked: Boolean) {
    Tmdb.rev.let { }
    var status by remember { mutableStateOf("") }
    var good by remember { mutableStateOf(false) }
    T("Já vem ligado: o app busca no TMDB (themoviedb.org), em português, o nome, a capa, a imagem de fundo, a sinopse, a nota, os gêneros, o elenco, a classificação indicativa e o trailer dos filmes e séries das suas listas. Não precisa configurar nada. Se quiser, coloque uma chave sua abaixo.", 13, C.muted, lineHeight = 19)
    KV(listOf(
        "Chave" to if (Tmdb.userKey.isNotBlank()) "sua (" + Tmdb.userKey.take(4) + "…)" else if (Tmdb.key.isNotBlank()) "nativa do app" else "não colocada",
        "Situação" to if (Tmdb.on) "Ligado" else if (Tmdb.key.isBlank()) "Desligado" else "Chave recusada",
        "Títulos com capa" to "${Tmdb.count}",
    ))
    FieldRow(st, "Chave própria (opcional)", Tmdb.userKey, locked, secret = true, hint = "32 letras e números") { k ->
        status = "Testando a chave…"; good = false
        Tmdb.test(k) { msg, ok -> status = msg; good = ok; if (ok) { Tmdb.setKey(k); st.say("TMDB ligado. As capas vão aparecendo nas abas.") } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (Tmdb.key.isNotBlank()) Btn("Testar de novo", locked = locked, primary = true) { status = "Testando…"; Tmdb.test(Tmdb.key) { m, ok -> status = m; good = ok } }
        Btn("Buscar capas de novo", locked = locked) { Tmdb.clear(); st.say("Capas apagadas. Elas voltam conforme você navega.") }
        if (Tmdb.userKey.isNotBlank()) Btn("Usar a chave padrão", locked = locked) { Tmdb.setKey(""); status = "" }
    }
    if (status.isNotBlank()) T(status, 13, if (good) C.ok else if (status.endsWith("…")) C.muted else C.bad, FontWeight.SemiBold)
    if (Tmdb.status.isNotBlank()) T(Tmdb.status, 12, C.bad)
    PhoneBox(st)
    T("Este produto usa a API do TMDB, mas não é endossado nem certificado pelo TMDB.", 11, C.dim)
}

// ---------------- IA de imagem ----------------
/** Linha com título, explicação e interruptor (como na maquete). A linha inteira recebe o foco do controle. */
@Composable
fun TogRow(title: String, desc: String, on: Boolean, locked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    var f by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (f) C.panel2 else C.panel)
            .border(1.dp, if (f) C.a1 else C.line, RoundedCornerShape(12.dp))
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val n = it.isFocused; if (n && !f) Sfx.tick(); f = n }
            .clickable(remember { MutableInteractionSource() }, null) { if (enabled) { Sfx.ok(); onToggle(!on) } }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            T(title, 13, if (enabled) C.fg else C.muted, FontWeight.Bold)
            T(desc, 11, C.muted, lineHeight = 15)
        }
        val green = Color(0xFF54E6A8)
        Box(Modifier.width(44.dp).height(26.dp).clip(RoundedCornerShape(50)).background(if (on) green else C.line).padding(3.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.width(20.dp).height(20.dp).clip(RoundedCornerShape(50)).background(Color.White))
        }
    }
}

@Composable
private fun AiBlock(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.bg.copy(alpha = .6f)).border(1.dp, C.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(title, 14, C.fg, FontWeight.Bold)
        content()
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AiImagePanel(st: AppState, locked: Boolean) {
    PP.rev.let { }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0x24B08CFF)).border(1.dp, Color(0x66B08CFF), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp)) {
        T("✦ Aplicado só a Filmes e Séries", 12, Color(0xFFD7C6FF), FontWeight.SemiBold)
    }
    T("A IA observa a cena e ajusta a cor na hora: identifica pessoas, paisagem, objetos e ambiente, e realça com cores vivas mantendo os tons reais. A análise usa uma amostra pequena da imagem a cada 2 segundos, somada ao hardware da TV, por isso é instantânea. A TV ao vivo vai direto para a TV, leve e com troca de canal na hora.", 13, C.muted, lineHeight = 19)
    Opts("IA de imagem", AI_UP, if (PP.aiUp == "off") "off" else "auto", locked) { PP.aiUp = it }
    T(ImageAi.resumoAuto() + " Se a TV perder quadros ou esquentar, a IA desce sozinha e sobe de novo quando estabiliza.", 12, C.ok, lineHeight = 17)
    KV(listOf(
        "Cena detectada agora" to if (SceneAi.on && SceneAi.scene.isNotBlank()) SceneAi.scene else "abra um filme ou série",
        "IA no chip da TV" to if (ImageAi.hwAi) "sim (${ImageAi.chip})" else "não encontrada",
    ))
    AiBlock("✦ Realce inteligente de cor") {
        Opts("Estilo de cor", COLOR_STYLES, PP.colorStyle, locked) { PP.colorStyle = it }
        TogRow("Tom de pele natural", "Reconhece pele e mantém o tom realista, sem deixar alaranjado nem pálido.", PP.aiSkin, locked) { PP.aiSkin = it }
        TogRow("Paisagem e natureza", "Realça o verde das matas e o azul do céu e do mar, sem exagero artificial.", PP.aiNature, locked) { PP.aiNature = it }
        TogRow("Objetos e profundidade", "Reforça o contorno e o relevo dos objetos, dando mais nitidez à cena.", PP.aiDepth, locked) { PP.aiDepth = it }
        TogRow("Fidelidade de cor", "Trava o realce para nunca estourar as cores: vivas, porém reais.", PP.aiFidelity, locked) { PP.aiFidelity = it }
    }
    AiBlock("Movimento e nitidez") {
        TogRow("Suavização 100 Hz", "O movimento fluido é feito pelo recurso de imagem da própria TV (MEMC). O app ajusta a frequência da tela ao vídeo para o MEMC trabalhar sem trancos. Ative \"Movimento/MEMC\" nos ajustes de imagem da TV.", PP.matchHz, locked) { PP.matchHz = it }
        Btn("Abrir ajustes de imagem da TV", locked = locked) {
            val ok = listOf(android.provider.Settings.ACTION_DISPLAY_SETTINGS, android.provider.Settings.ACTION_SETTINGS).any { a ->
                try { ctx.startActivity(android.content.Intent(a).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
            }
            if (!ok) st.say("Use o botão de configurações do controle: Imagem › Movimento")
        }
        TogRow("Nitidez leve", "Realça os detalhes de cada quadro na hora, sem atraso perceptível.", PP.aiSharp, locked) { PP.aiSharp = it }
        TogRow("Qualidade cheia na troca", "Abre filmes, episódios e canais já na maior resolução, acabando com o embaçado inicial. Se a internet engasgar, baixa sozinho.", PP.fullQuality, locked) { PP.fullQuality = it }
    }
    AiBlock("Reservado para a versão de PC") {
        TogRow("IA pesada — rede neural + criação de quadros", "Reconstrução por rede neural e quadros intermediários precisam de placa de vídeo dedicada (como a RTX 3070). Na TV a ampliação automática acima já usa o máximo seguro.", false, locked, enabled = false) { }
    }
    Opts("Painel da TV", PANELS, PP.panel, locked) { PP.panel = it }
    T("HVA é o painel VA da TCL: pretos profundos e alto contraste. Nesse perfil a IA segura a nitidez nas sombras (evita halos) e sobe de leve o quase-preto. Automático usa HVA em TVs TCL" + if (ImageAi.tclPanel) " (esta é uma TCL)." else ".", 11, C.dim, lineHeight = 15)
    Opts("Realce de cor e contraste", ENHANCE, PP.enhance, locked) { PP.enhance = it }
    Opts("Modo de imagem", PRESETS, PP.preset, locked) { PP.preset = it }
    if (PP.preset == "pers") {
        Stepper("Brilho", PP.bright, "", locked) { d -> PP.bright = (PP.bright + d * 5).coerceIn(-50, 50) }
        Stepper("Contraste", PP.contrast, "", locked) { d -> PP.contrast = (PP.contrast + d * 5).coerceIn(-50, 50) }
        Stepper("Cor (saturação)", PP.sat, "", locked) { d -> PP.sat = (PP.sat + d * 5).coerceIn(-50, 50) }
    }
    Opts("Ao oscilar a internet", QUALITY, PP.quality, locked) { PP.quality = it }
    T("\"Automática\" deixa o vídeo baixar a qualidade sozinho quando a internet piora, em vez de travar.", 11, C.dim)
}

// ---------------- IA de áudio ----------------
@Composable
fun AiAudioPanel(st: AppState, locked: Boolean) {
    PP.rev.let { }
    T("Ajusta o som de todos os vídeos. Diálogos nítidos destaca as vozes; Cinema reforça graves e agudos; Modo noturno segura explosões e comerciais altos.", 13, C.muted, lineHeight = 19)
    Opts("IA de áudio", AUDIO_AI, PP.audioAi, locked) { PP.audioAi = it }
    Opts("Equalizador", EQS, PP.eq, locked) { PP.eq = it }
    Stepper("Graves", PP.bass, " dB", locked) { d -> PP.bass = (PP.bass + d).coerceIn(-6, 6) }
    Stepper("Agudos", PP.treble, " dB", locked) { d -> PP.treble = (PP.treble + d).coerceIn(-6, 6) }
    Opts("Nivelar volume", linkedMapOf("1" to "Ligado", "0" to "Desligado"), if (PP.level) "1" else "0", locked) { PP.level = it == "1" }
}

// ---------------- Tema ----------------
@Composable
fun ThemePanel(st: AppState, locked: Boolean) {
    T("Muda as cores do app inteiro na hora. Os temas com foto (como no AstroLume TV) colocam a imagem por trás das telas.", 13, C.muted, lineHeight = 19)
    CustomTheme.rev.let { }
    THEMES.entries.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (id, p) ->
                Column(Modifier.width(132.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(132.dp).height(72.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(p.bg, p.panel2, p.a1, p.a2)))
                        .border(if (C.pal === p) 2.dp else 1.dp, if (C.pal === p) p.a1 else C.line, RoundedCornerShape(10.dp))) {
                        if (p.img != 0) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(p.img), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        if (p.custom) CustomTheme.bmp?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                    }
                    Btn(p.name, on = C.pal === p, locked = locked) { C.pal = p; st.prefs.theme = id }
                }
            }
        }
    }
    T("TEMA ANIME (SUA IMAGEM)", 10, C.muted, FontWeight.Bold)
    T(if (CustomTheme.has) "Sua imagem está salva só neste aparelho. Para trocar, mande outra pelo celular." else "Mande uma imagem do celular (anime, filme, paisagem…) e ela vira o fundo do app. As cores do tema saem da própria imagem. Ela fica só neste aparelho.", 12, C.muted, lineHeight = 17)
    PhoneBox(st)
    if (CustomTheme.has) Btn("Remover minha imagem", locked = locked) { CustomTheme.remove(); if (C.pal.custom) { C.pal = THEMES.getValue("cosmos"); st.prefs.theme = "cosmos" }; st.say("Imagem removida") }
}

// ---------------- Clima ----------------
@Composable
fun WeatherPanel(st: AppState, locked: Boolean) {
    var city by remember { mutableStateOf(st.prefs.city) }
    var on by remember { mutableStateOf(st.prefs.weather) }
    var found by remember { mutableStateOf<List<Weather.Place>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    fun searchCity(q: String) {
        if (q.isBlank()) return
        msg = "Procurando \"$q\"…"; found = emptyList()
        Weather.search(q) { l, m -> found = l; msg = if (l.isNotEmpty()) "Escolha a cidade certa:" else m }
    }
    T("Mostra o clima no topo da tela, ao lado do relógio. Usa o Open-Meteo, grátis e sem cadastro. Atualiza a cada 30 minutos.", 13, C.muted, lineHeight = 19)
    Seg("Clima no topo", listOf(1 to "Ligado", 0 to "Desligado"), if (on) 1 else 0, locked) { on = it == 1; st.prefs.weather = on }
    KV(listOf("Cidade salva" to city.ifBlank { "nenhuma" }) + (Weather.now?.takeIf { city.isNotBlank() }?.let { w -> listOf("Agora" to "${w.icon} ${w.temp}° · ${w.desc}", "Hoje" to "máx. ${w.max}° · mín. ${w.min}°") } ?: emptyList()))
    FieldRow(st, "Procurar cidade", "", locked, hint = "Digite o nome e aperte OK (ex.: Niterói)") { v -> searchCity(v) }
    if (msg.isNotBlank()) T(msg, 13, if (found.isNotEmpty()) C.a1 else C.muted, FontWeight.SemiBold)
    if (found.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        found.forEach { p ->
            Btn(p.label, on = p.label == city, locked = locked) {
                city = p.label; st.prefs.city = p.label; st.prefs.cityPos = "${p.lat},${p.lon}"
                found = emptyList(); msg = "Cidade salva: ${p.label}"
                Weather.refresh(p.label, true, "${p.lat},${p.lon}")
                st.say("Clima de ${p.name} no topo da tela")
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn("Atualizar agora", locked = locked) { Weather.refresh(city, true, st.prefs.cityPos) }
        if (city.isNotBlank()) Btn("Apagar cidade", locked = locked) { city = ""; st.prefs.city = ""; st.prefs.cityPos = ""; Weather.now = null; msg = "" }
    }
    if (Weather.status.isNotBlank()) T(Weather.status, 12, C.bad)
}

// ---------------- Mapear controle remoto ----------------
/** Trocas de tecla: a tecla apertada (do seu controle) passa a valer como outra. */
object KeyMap {
    val ACTIONS = linkedMapOf(
        KeyEvent.KEYCODE_DPAD_CENTER to "OK",
        KeyEvent.KEYCODE_BACK to "Voltar",
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Play / Pausa",
        KeyEvent.KEYCODE_INFO to "Botões do vídeo",
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "Avançar 10 s",
        KeyEvent.KEYCODE_MEDIA_REWIND to "Voltar 10 s",
    )
    /** Teclas que nunca são trocadas, para ninguém ficar sem conseguir navegar. */
    private val SAFE = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER)

    var map = HashMap<Int, Int>()
    var capture by mutableStateOf<Int?>(null) // ação esperando uma tecla
    var msg by mutableStateOf("")
    var rev by mutableIntStateOf(0)
    private var prefs: Prefs? = null

    fun init(p: Prefs) {
        prefs = p
        map = HashMap(p.keymap.split(',').mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { (a, b) -> a.toIntOrNull()?.let { x -> b.toIntOrNull()?.let { y -> x to y } } } }.toMap())
    }

    private fun save() { prefs?.keymap = map.entries.joinToString(",") { "${it.key}=${it.value}" }; rev++ }

    fun reset() { map.clear(); save(); msg = "Controle voltou ao padrão." }

    /** Chamado pela tela principal com cada tecla. Devolve o código que vale (ou null para engolir). */
    fun handle(code: Int, down: Boolean): Int? {
        val act = capture
        if (act != null) {
            if (!down) return null
            if (code == KeyEvent.KEYCODE_BACK) { capture = null; msg = "Cancelado."; return null }
            if (code in SAFE) { msg = "Essa tecla é usada para navegar e não pode ser trocada. Aperte outra (ex.: botões coloridos, números, ★)."; return null }
            map[code] = act; capture = null; save()
            msg = "\"${KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")}\" agora vale como ${ACTIONS[act]}."
            return null
        }
        return map[code] ?: code
    }

    fun keysFor(act: Int) = map.filter { it.value == act }.keys.joinToString(", ") { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") }
}

@Composable
fun RemotePanel(st: AppState, locked: Boolean) {
    KeyMap.rev.let { }
    T("Use uma tecla do seu controle (botões coloridos, números, ★, Netflix…) como atalho. Escolha a função, aperte \"Definir tecla\" e depois a tecla no controle. As setas, OK e Voltar continuam funcionando sempre.", 13, C.muted, lineHeight = 19)
    KeyMap.ACTIONS.forEach { (code, name) ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.bg).padding(start = 14.dp, top = 5.dp, bottom = 5.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            T(name, 13, C.fg, FontWeight.SemiBold, modifier = Modifier.width(150.dp))
            T(KeyMap.keysFor(code).ifBlank { "padrão" }, 12, C.muted, modifier = Modifier.weight(1f), maxLines = 1)
            Btn(if (KeyMap.capture == code) "Aperte a tecla…" else "Definir tecla", on = KeyMap.capture == code, locked = locked) {
                KeyMap.capture = if (KeyMap.capture == code) null else code; KeyMap.msg = if (KeyMap.capture != null) "Aperte agora a tecla do controle para \"$name\"." else ""
            }
        }
    }
    if (KeyMap.msg.isNotBlank()) T(KeyMap.msg, 13, C.a1, FontWeight.SemiBold)
    Btn("Voltar ao padrão", locked = locked) { KeyMap.reset() }
}

// ---------------- Limpar histórico ----------------
@Composable
fun HistoryPanel(st: AppState, locked: Boolean) {
    Store.rev.let { }
    var ask by remember { mutableStateOf("") } // pede OK duas vezes antes de apagar
    T("Apaga o que aparece em \"Visto\" e em \"Continuar assistindo\" na tela inicial. Para tirar um título só, abra o título e use \"Tirar do histórico\". Favoritos da Minha lista não são apagados.", 13, C.muted, lineHeight = 19)
    KV(listOf("Visto" to "${Store.seen.size} títulos", "Continuar assistindo" to "${Store.progress.size} títulos"))
    fun confirm(k: String, done: () -> Unit) { if (ask == k) { done(); ask = "" } else ask = k }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn(if (ask == "visto") "Aperte OK de novo para apagar o Visto" else "Limpar \"Visto\"", locked = locked, primary = ask == "visto") {
            confirm("visto") { Store.clearSeen(); st.say("Visto apagado") }
        }
        Btn(if (ask == "cont") "Aperte OK de novo para apagar o Continuar" else "Limpar \"Continuar assistindo\"", locked = locked, primary = ask == "cont") {
            confirm("cont") { Store.clearProgress(); st.say("Continuar assistindo apagado") }
        }
        Btn(if (ask == "tudo") "Aperte OK de novo para apagar tudo" else "Limpar todo o histórico", locked = locked, primary = ask == "tudo") {
            confirm("tudo") { Store.clearSeen(); Store.clearProgress(); st.say("Histórico apagado") }
        }
    }
    T("Ao limpar o \"Continuar assistindo\", os filmes e episódios voltam a começar do início.", 11, C.dim)
}
