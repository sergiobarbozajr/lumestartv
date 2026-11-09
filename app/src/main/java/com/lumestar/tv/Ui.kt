package com.lumestar.tv

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cor de capa gerada pelo nome, para quando o título não tem imagem. */
fun coverBrush(seed: String): Brush {
    val h = ((seed.hashCode() % 360) + 360) % 360
    return Brush.linearGradient(listOf(Color(hsla(h.toFloat(), .52f, .24f, 1f)), Color(hsla((h + 48f), .6f, .09f, 1f))))
}

@Composable
fun Img(url: String, w: Int, h: Int, modifier: Modifier = Modifier, crop: Boolean = true) {
    if (url.isBlank()) return
    val ctx = LocalContext.current
    AsyncImage(
        model = remember(url) { ImageRequest.Builder(ctx).data(url).size(w, h).crossfade(true).build() },
        contentDescription = null, contentScale = if (crop) ContentScale.Crop else ContentScale.Fit, modifier = modifier
    )
}

/** Abrir um item: canal toca direto; filme e série mostram os detalhes. */
fun openItem(st: AppState, it: Item) {
    if (it.kind == LIVE) st.play(PlayReq(it.id, it.title, it.url, true, 0L, it.group, it.ext)) else st.detail = it
}

/**
 * Capa de um título ou canal. OK abre; OK duas vezes seguidas favorita (como no simulador).
 */
@Composable
fun Card(st: AppState, it: Item, locked: Boolean, wide: Boolean = it.kind == LIVE, caption: String? = null, onOpen: (() -> Unit)? = null) {
    var f by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fav = remember(Store.rev) { Store.favs.contains(it.id) }
    val prog = remember(Store.rev) { Store.progress[it.id] }
    val w = if (wide) 168.dp else 106.dp
    val h = if (wide) 95.dp else 158.dp
    Column(
        Modifier.width(w)
            .graphicsLayer { val s = if (f) 1.08f else 1f; scaleX = s; scaleY = s }
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val n = it.isFocused; if (n && !f) Sfx.tick(); f = n }
            .clickable(remember { MutableInteractionSource() }, null) {
                val now = SystemClock.uptimeMillis()
                if (st.lastTapId == it.id && now - st.lastTapAt < 450) {
                    st.pendingOpen?.cancel(); st.lastTapId = null
                    val on = Store.toggleFav(it.id)
                    Sfx.ok(); st.say(if (on) "♥ Adicionado à Minha lista" else "Removido da Minha lista")
                } else {
                    st.lastTapId = it.id; st.lastTapAt = now
                    st.pendingOpen?.cancel()
                    st.pendingOpen = scope.launch { delay(450); Sfx.ok(); if (onOpen != null) onOpen() else openItem(st, it) }
                }
            },
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier.fillMaxWidth().height(h)
                // selecionado: sombra colorida em volta (brilho), além da borda
                .shadow(if (f) 16.dp else 0.dp, RoundedCornerShape(10.dp), ambientColor = C.a1, spotColor = C.a1)
                .clip(RoundedCornerShape(10.dp)).background(coverBrush(it.title))
                .border(if (f) 2.dp else 0.dp, if (f) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
        ) {

            Tmdb.rev.let { }
            val cover = if (it.kind == LIVE) it.logo.ifBlank { Logos.rev.let { _ -> Logos.find(it.title) } } else Tmdb.poster(it).ifBlank { it.logo }
            if (wide && it.kind == LIVE) {
                CanalArte(it, cover)
            } else if (wide) {
                LogoArte(it.title, cover, Modifier.fillMaxSize(), pad = 10.dp, tamanhoNome = 17) { ModeloCard(modeloDe(it), it.title, Modifier.fillMaxSize()) }
            } else {
                if (cover.isBlank()) ModeloCard(modeloDe(it), Tmdb.title(it), Modifier.fillMaxSize())
                Img(cover, 212, 316, Modifier.fillMaxSize(), crop = true)
            }
            if (it.kind == LIVE && it.id in PP.offline) Box(Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xE0B0203C)).padding(horizontal = 6.dp, vertical = 2.dp)) { T("FORA DO AR", 8, Color.White, FontWeight.Bold) }
            if (fav) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).clip(CircleShape).background(Color(0xC8050816)), contentAlignment = Alignment.Center) { T("♥", 11, Color(0xFFFF8FB8), FontWeight.Bold) }
            if (prog != null) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxWidth((prog.pos.toFloat() / prog.dur).coerceIn(0f, 1f)).height(3.dp).background(Brush.horizontalGradient(listOf(C.a1, C.a2))))
            }
            if (f) Box(Modifier.matchParentSize().background(Brush.linearGradient(0f to Color.White.copy(alpha = .22f), .45f to Color.Transparent)))
            if (f) Box(Modifier.matchParentSize().border(2.dp, C.a1, RoundedCornerShape(10.dp)))
        }
        T(caption ?: if (wide) Tmdb.title(it) else listOf(it.group, if (it.year > 0) it.year.toString() else "").filter { it.isNotBlank() }.joinToString(" · "), 10, if (f) C.fg else C.muted, maxLines = 1)
    }
}

@Composable
fun ItemRow(st: AppState, title: String, items: List<Item>, locked: Boolean, captions: Map<String, String> = emptyMap()) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(end = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            T(title, 14, C.fg, FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
            T("${items.size}", 11, C.dim, FontWeight.SemiBold)
        }
        LazyRow(contentPadding = PaddingValues(start = 2.dp, end = 40.dp, top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.id }) { Card(st, it, locked, caption = captions[it.id]) }
        }
    }
}

// ---------------- destaque que troca a cada 10 segundos ----------------
class HeroEntry(val title: String, val eyebrow: String, val meta: String, val text: String, val img: String, val item: Item?, val news: News?)

@Composable
fun Hero(st: AppState, list: List<HeroEntry>, locked: Boolean) {
    if (list.isEmpty()) return
    var idx by remember(list) { mutableIntStateOf(0) }
    LaunchedEffect(list) { while (true) { delay(10_000); idx = (idx + 1) % list.size } }
    val e0 = list[idx % list.size]
    Tmdb.rev.let { }
    val m = e0.item?.let { Tmdb.meta(it) }
    val e = if (m == null) e0 else HeroEntry(e0.title, e0.eyebrow,
        e0.meta.ifBlank { listOf(if (m.year > 0) "${m.year}" else "", if (m.vote > 0) "★ %.1f".format(m.vote) else "").filter { x -> x.isNotBlank() }.joinToString(" · ") },
        e0.text.ifBlank { m.overview }, m.posterUrl(500).ifBlank { e0.img }, e0.item, null)
    val bgImg = m?.backdropUrl()?.ifBlank { null } ?: e.img
    val fav = remember(Store.rev, e.item) { e.item?.let { Store.favs.contains(it.id) } ?: false }
    Box(Modifier.fillMaxWidth().height(262.dp).padding(end = 0.dp)) {
        Box(Modifier.fillMaxSize().background(coverBrush(e.title)))
        Img(bgImg, 960, 540, Modifier.fillMaxSize().graphicsLayer { alpha = if (bgImg != e.img) .62f else .5f })
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF5050816), .5f to Color(0xB8050816), 1f to Color(0x26050816))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(.55f to Color.Transparent, 1f to C.bg)))
        if (e.img.isNotBlank() && e.item != null && e.item.kind != LIVE) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 56.dp).width(140.dp).height(210.dp).graphicsLayer { rotationZ = 4f }.clip(RoundedCornerShape(12.dp))) {
                Img(e.img, 280, 420, Modifier.fillMaxSize())
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 26.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(e.eyebrow.uppercase(), 10, C.a1, FontWeight.Bold)
            T(e.title, 30, C.fg, FontWeight.ExtraBold, maxLines = 2, lineHeight = 33)
            if (e.meta.isNotBlank()) T(e.meta, 12, C.muted, FontWeight.SemiBold, maxLines = 1)
            if (e.text.isNotBlank()) T(e.text, 13, Color(0xFFC9D1F3), maxLines = 3, lineHeight = 19)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (e.news != null) Btn("☰ Ler resumo", locked = locked, primary = true) { st.newsOpen = e.news }
                else if (e.item != null) {
                    val p = Store.progress[e.item.id]
                    Btn(if (e.item.kind == LIVE) "▶ Assistir ao vivo" else if (p != null) "▶ Continuar" else "▶ Assistir", locked = locked, primary = true) {
                        if (e.item.kind == SERIES) st.detail = e.item else st.play(PlayReq(e.item.id, e.item.title, e.item.url, e.item.kind == LIVE, p?.pos ?: 0L, e.item.group, e.item.ext))
                    }
                    if (e.item.kind != LIVE) Btn("Mais informações", locked = locked) { st.detail = e.item }
                    Btn(if (fav) "♥ Na Minha lista" else "♡ Minha lista", locked = locked) { val on = Store.toggleFav(e.item.id); st.say(if (on) "♥ Adicionado à Minha lista" else "Removido da Minha lista") }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.indices.forEach { i -> Box(Modifier.size(if (i == idx % list.size) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (i == idx % list.size) C.a1 else Color(0x47FFFFFF))) }
        }
    }
}

fun heroFrom(items: List<Item>, n: Int = 6): List<HeroEntry> {
    if (items.isNotEmpty() && items.all { it.kind == LIVE }) {
        // TV ao vivo: um canal de cada categoria, de preferência com logo
        return items.groupBy { it.group }.values.mapNotNull { g -> g.firstOrNull { it.logo.isNotBlank() } ?: g.firstOrNull() }.take(n).map { heroOf(it) }
    }
    // rápido mesmo com dezenas de milhares de títulos: só olha o que já está guardado
    val withImg = ArrayList<Item>()
    for (it in items) { if (it.kind != LIVE && (it.logo.isNotBlank() || Tmdb.cachedPoster(it).isNotBlank())) { withImg.add(it); if (withImg.size >= 400) break } }
    val pool = (if (withImg.size >= 3) withImg else items).sortedWith(compareByDescending<Item> { it.year }.thenBy { it.title.hashCode() })
    return pool.take(n).map { heroOf(it) }
}

fun heroOf(it: Item) = HeroEntry(
    Tmdb.title(it), listOf(if (it.kind == LIVE) "Ao vivo" else if (it.kind == SERIES) "Série" else "Filme", it.group).filter { s -> s.isNotBlank() }.joinToString(" · "),
    listOf(if (it.year > 0) it.year.toString() else "", if (it.rating.isNotBlank() && it.rating != "0") "★ ${it.rating}" else "", if (it.year == Catalog.year) "LANÇAMENTO" else "").filter { s -> s.isNotBlank() }.joinToString(" · "),
    it.plot, if (it.kind == LIVE) it.logo.ifBlank { Logos.find(it.title) } else it.logo, it, null
)

/** Cartão de notícia com leitura resumida. */
@Composable
fun NewsCard(st: AppState, n: News, locked: Boolean) {
    var f by remember { mutableStateOf(false) }
    Column(
        Modifier.width(240.dp).height(118.dp)
            .graphicsLayer { val s = if (f) 1.05f else 1f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(C.panel2, C.panel)))
            .border(if (f) 2.dp else 1.dp, if (f) C.a1 else C.line, RoundedCornerShape(14.dp))
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val x = it.isFocused; if (x && !f) Sfx.tick(); f = x }
            .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); st.newsOpen = n }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        T(n.cat.uppercase(), 9, C.a1, FontWeight.Bold)
        T(n.title, 12, C.fg, FontWeight.Bold, maxLines = 2, lineHeight = 16)
        T(n.summary, 11, C.muted, maxLines = 2, lineHeight = 15)
    }
}

@Composable
fun Chip(label: String, on: Boolean, locked: Boolean, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.graphicsLayer { val s = if (f) 1.06f else 1f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(30.dp))
            .background(if (f) C.fg else if (on) C.a1.copy(alpha = .16f) else Color.Transparent)
            .border(1.dp, if (f) C.fg else if (on) C.a1 else C.line, RoundedCornerShape(30.dp))
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val x = it.isFocused; if (x && !f) Sfx.tick(); f = x }
            .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); onClick() }
            .padding(horizontal = 13.dp, vertical = 6.dp)
    ) { T(label, 12, if (f) Color(0xFF050816) else if (on) C.fg else C.muted, FontWeight.SemiBold, maxLines = 1) }
}

/** Aviso grande para quando ainda não há conta nem lista. */
@Composable
fun EmptySources(st: AppState, locked: Boolean) {
    Column(
        Modifier.width(620.dp).clip(RoundedCornerShape(16.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        T("COMECE POR AQUI", 10, C.a1, FontWeight.Bold)
        T("Adicione sua conta IPTV ou o endereço da sua lista", 18, C.fg, FontWeight.Bold)
        T("Os canais, filmes e séries aparecem organizados nas abas. Para não digitar no controle, use \"Enviar pelo celular\".", 13, C.muted, lineHeight = 19)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Contas IPTV", locked = locked, primary = true) { st.cfgSel = "contas"; st.page = "config"; st.refocus++ }
            Btn("Listas e enviar pelo celular", locked = locked) { st.cfgSel = "listas"; st.page = "config"; st.refocus++ }
        }
    }
}

/** Sugestões do Início: (texto, atalho em Configurações). */
private val SUGESTOES = listOf(
    "Melhore a imagem: a IA amplia filmes e séries até o máximo que a sua TV aguenta, sem travar." to "ai",
    "Limpe o histórico do que já assistiu e do Continuar assistindo." to "historico",
    "Proteja o conteúdo adulto com um PIN." to "adulto",
    "Mude o tema do app ou use uma imagem sua como fundo." to "tema",
    "Ajuste o som dos vídeos: diálogos nítidos, cinema ou modo noturno." to "audio",
    "Salve até 5 sites para abrir direto do app." to "web",
    "Adicione listas M3U, JSON ou TXT. Dá para enviar pelo celular." to "listas",
    "Conecte até 3 contas IPTV." to "contas",
    "Escolha sua cidade para ver o clima no topo da tela." to "clima",
    "Ligue, desligue ou ajuste os sons de navegação do app." to "som",
)

/**
 * Balão de sugestões: troca sozinho a cada 10 s; parado enquanto está selecionado.
 * ◀ ▶ passam para a anterior/próxima e OK no texto abre a configuração da sugestão.
 */
@Composable
fun SuggestBox(st: AppState, locked: Boolean, modifier: Modifier = Modifier) {
    var i by remember { mutableIntStateOf(0) }
    var focos by remember { mutableIntStateOf(0) } // quantas partes do balão estão com foco
    LaunchedEffect(focos) {
        while (focos == 0) { kotlinx.coroutines.delay(10_000); if (focos == 0) i = (i + 1) % SUGESTOES.size }
    }
    val (texto, cfg) = SUGESTOES[i]
    Column(
        modifier.width(400.dp).clip(RoundedCornerShape(16.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("SUGESTÕES", 10, C.a1, FontWeight.Bold, modifier = Modifier.weight(1f))
            T("${i + 1} de ${SUGESTOES.size}" + if (focos > 0) " · parado" else "", 10, C.dim)
        }
        var f by remember { mutableStateOf(false) }
        Box(
            Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(12.dp)).background(if (f) C.panel2 else C.bg.copy(alpha = .5f))
                .border(1.dp, if (f) C.a1 else Color.Transparent, RoundedCornerShape(12.dp))
                .focusProperties { canFocus = !locked }
                .onFocusChanged { val n = it.isFocused; if (n && !f) Sfx.tick(); if (n != f) focos += if (n) 1 else -1; f = n }
                .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); st.cfgSel = cfg; st.page = "config"; st.refocus++ }
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                T(texto, 11, C.fg, FontWeight.SemiBold, maxLines = 4, lineHeight = 15)
                T(if (f) "OK para abrir esta configuração" else "Configurações › " + (cfgName(cfg) ?: ""), 9, if (f) C.a1 else C.muted, maxLines = 1)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SetaBtn("◀ Anterior", locked, { n -> focos += if (n) 1 else -1 }) { i = (i - 1 + SUGESTOES.size) % SUGESTOES.size }
            SetaBtn("Próxima ▶", locked, { n -> focos += if (n) 1 else -1 }) { i = (i + 1) % SUGESTOES.size }
        }
    }
}

@Composable
private fun SetaBtn(label: String, locked: Boolean, onFoco: (Boolean) -> Unit, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (f) C.fg else C.panel2)
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val n = it.isFocused; if (n && !f) Sfx.tick(); if (n != f) onFoco(n); f = n }
            .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) { T(label, 12, if (f) Color(0xFF050816) else C.fg, FontWeight.SemiBold, maxLines = 1) }
}


/** Qual desenho usar quando não há imagem. */
fun modeloDe(it: Item): Modelo = when {
    it.kind == LIVE -> Modelo.CANAL
    it.kind == SERIES -> Modelo.SERIE
    it.group.isBlank() || it.group.equals("outros", true) -> Modelo.VIDEO
    else -> Modelo.FILME
}

/**
 * Card de canal: foto do que está passando agora (quando já capturada) com o logo pequeno e "AO VIVO";
 * sem foto, o logo com fundo na cor dele; sem logo, a TV desenhada com o nome. Sempre com o selo AO VIVO.
 */
@Composable
fun CanalArte(it: Item, logo: String) {
    Thumbs.rev.let { }
    LaunchedEffect(it.id) { Thumbs.pedir(it) }
    val foto = Thumbs.foto(it.id)
    Box(Modifier.fillMaxSize()) {
        if (foto != null) {
            androidx.compose.foundation.Image(foto, it.title, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            if (logo.isNotBlank()) Box(Modifier.align(Alignment.TopStart).padding(5.dp).size(46.dp, 26.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC050816)).padding(3.dp)) {
                Img(logo, 92, 52, Modifier.fillMaxSize(), crop = false)
            }
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))).padding(horizontal = 7.dp, vertical = 4.dp)) {
                T(it.title, 10, Color.White, FontWeight.Bold, maxLines = 1)
            }
        } else LogoArte(it.title, logo, Modifier.fillMaxSize(), pad = 10.dp, tamanhoNome = 17) { ModeloCard(Modelo.CANAL, it.title, Modifier.fillMaxSize(), aoVivo = false) }
        AoVivo(Modifier.align(Alignment.TopEnd).padding(5.dp))
    }
}
