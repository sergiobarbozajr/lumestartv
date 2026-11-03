package com.lumestar.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

val TAB_NAMES = mapOf(
    "aovivo" to "TV ao vivo", "filmes" to "Filmes", "series" to "Séries", "novelas" to "Novelas", "infantil" to "Infantil",
    "documentarios" to "Documentários", "esportes" to "Esportes", "noticias" to "Notícias", "musicas" to "Músicas"
)

/** Item do catálogo a partir de uma chave de progresso/visto (episódio aponta para a série). */
fun itemFor(key: String): Item? = Catalog.byId[key] ?: if (key.contains(":e")) Catalog.byId[key.substringBeforeLast(":e")] else null

@Composable
private fun Head(title: String, desc: String) {
    Column(Modifier.padding(start = 28.dp, top = 56.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        T(title, 28, C.fg, FontWeight.ExtraBold)
        if (desc.isNotBlank()) T(desc, 13, C.muted, lineHeight = 19, modifier = Modifier.width(620.dp))
    }
}

@Composable
private fun Loading() {
    if (Catalog.loading) Box(Modifier.padding(start = 28.dp, top = 8.dp).clip(RoundedCornerShape(20.dp)).background(C.panel2).padding(horizontal = 14.dp, vertical = 6.dp)) {
        T("⟳ " + Catalog.status, 12, C.muted, FontWeight.SemiBold)
    }
}

// ---------------- Início ----------------
@Composable
fun HomePage(st: AppState, locked: Boolean) {
    val rev = Catalog.rev; val srev = Store.rev
    val news = NewsState.list
    var ncat by remember { mutableStateOf("Tudo") }
    val cont = remember(rev, srev) { Store.progress.entries.sortedByDescending { it.value.at }.mapNotNull { e -> itemFor(e.key)?.let { it to e.value } }.distinctBy { it.first.id }.take(20) }
    val seen = remember(rev, srev) { Store.seen.mapNotNull { itemFor(it) }.distinctBy { it.id }.take(30) }
    val hero = remember(rev, news) {
        val items = heroFrom(Catalog.items.filter { Catalog.visible(it) && it.kind != LIVE }, 4)
        val ns = news.filter { it.img.isNotBlank() }.take(3).map { n -> HeroEntry(n.title, "Notícias · ${n.cat}", "Leitura resumida · 1 min", n.summary, n.img, null, n) }
        val out = ArrayList<HeroEntry>()
        for (i in 0 until maxOf(items.size, ns.size)) { ns.getOrNull(i)?.let { out.add(it) }; items.getOrNull(i)?.let { out.add(it) } }
        out.take(6)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            if (hero.isNotEmpty()) Hero(st, hero, locked) else Column(Modifier.padding(start = 28.dp, top = 56.dp)) {
                T("Bem-vindo ao", 15, C.muted, FontWeight.SemiBold)
                GradientText("LumeStarTV", androidx.compose.ui.text.TextStyle(fontFamily = F.logo, fontSize = 64.sp))
            }
        }
        item { Loading() }
        if (Catalog.items.isEmpty() && !Catalog.loading) item { Box(Modifier.padding(start = 28.dp, top = 12.dp)) { EmptySources(st, locked) } }
        item {
            Column(Modifier.padding(start = 28.dp, top = 10.dp)) {
                Row(Modifier.fillMaxWidth().padding(end = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("Notícias", 14, C.fg, FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                LazyRow(contentPadding = PaddingValues(top = 8.dp, end = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Tudo") + NewsFeed.feeds.map { it.first }) { c -> Chip(c, c == ncat, locked) { ncat = c } }
                }
                val shown = news.filter { ncat == "Tudo" || it.cat == ncat }
                if (shown.isEmpty()) T(if (NewsState.loaded) "Sem notícias agora. Confira a internet." else "Carregando notícias…", 12, C.dim, modifier = Modifier.padding(top = 10.dp))
                else LazyRow(contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp, end = 40.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(shown) { n -> NewsCard(st, n, locked) }
                }
            }
        }
        item {
            Column(Modifier.padding(start = 28.dp)) {
                if (cont.isNotEmpty()) ItemRow(st, "Continuar assistindo", cont.map { it.first }, locked,
                    cont.associate { (i, p) -> i.id to "Faltam ${((p.dur - p.pos) / 60000).coerceAtLeast(1)} min" })
                else Hint("Continuar assistindo", "Tudo o que você pausar aparece aqui.")
                if (seen.isNotEmpty()) ItemRow(st, "Visto", seen, locked, seen.associate { it.id to "✓ Visto" })
                else Hint("Visto", "Filmes, séries e novelas que você terminar aparecem aqui.")
            }
        }
        val tabs = listOf("filmes", "series", "aovivo")
        tabs.forEach { tb ->
            val l = Catalog.rows(tb).flatMap { it.second }.let { all -> all.filter { it.logo.isNotBlank() }.ifEmpty { all } }.take(20)
            if (l.isNotEmpty()) item(key = "dest-$tb") { Box(Modifier.padding(start = 28.dp)) { ItemRow(st, "Destaques · ${TAB_NAMES[tb]}", l, locked) } }
        }
    }
}

@Composable
private fun Hint(title: String, text: String) {
    Column(Modifier.padding(top = 10.dp, bottom = 6.dp)) {
        T(title, 14, C.fg, FontWeight.Bold)
        Box(Modifier.padding(top = 8.dp, end = 28.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(14.dp)) { T(text, 12, C.muted) }
    }
}

// ---------------- abas por categoria ----------------
@Composable
fun TabPage(st: AppState, tab: String, title: String, desc: String, locked: Boolean, hero: Boolean = true) {
    val rev = Catalog.rev
    var recent by remember(tab) { mutableStateOf(false) }
    val rows = remember(rev, tab, recent, Store.adultOpen) {
        Catalog.rows(tab).map { (g, l) -> g to if (recent) l.sortedByDescending { it.year } else l }
    }
    val heroL = remember(rev, tab) { if (hero) heroFrom(rows.flatMap { it.second }) else emptyList() }
    val total = rows.sumOf { it.second.size }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { if (heroL.isNotEmpty()) Hero(st, heroL, locked) else Head(title, desc) }
        item { Loading() }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(if (total == 0) "" else "$total títulos · ${rows.size} categorias", 12, C.muted, modifier = Modifier.weight(1f))
                if (total > 0) Chip("Ordem: " + if (recent) "mais recentes" else "A–Z", true, locked) { recent = !recent }
            }
        }
        if (total == 0) item {
            Column(Modifier.padding(start = 28.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (Catalog.items.isEmpty()) EmptySources(st, locked)
                else T("Nada nesta aba na sua lista por enquanto.", 13, C.muted)
            }
        }
        items(rows, key = { "$tab|" + it.first }) { (g, l) -> Box(Modifier.padding(start = 28.dp)) { ItemRow(st, g, l, locked) } }
    }
}

@Composable
fun ReleasesPage(st: AppState, locked: Boolean) {
    val rev = Catalog.rev
    val all = remember(rev) { Catalog.releases() }
    val byTab = remember(all) { all.groupBy { TAB_NAMES[it.tabs.firstOrNull() ?: ""] ?: "Outros" }.toSortedMap() }
    val heroL = remember(all) { heroFrom(all) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { if (heroL.isNotEmpty()) Hero(st, heroL, locked) else Head("Lançamentos", "Tudo da sua lista com lançamento em ${Catalog.year}.") }
        item { Loading() }
        item { T("${all.size} títulos lançados em ${Catalog.year}", 12, C.muted, modifier = Modifier.padding(start = 28.dp, top = 6.dp)) }
        if (all.isEmpty()) item { Box(Modifier.padding(start = 28.dp, top = 10.dp)) { if (Catalog.items.isEmpty()) EmptySources(st, locked) else T("Nenhum título de ${Catalog.year} na sua lista.", 13, C.muted) } }
        byTab.forEach { (t, l) -> item(key = "rel-$t") { Box(Modifier.padding(start = 28.dp)) { ItemRow(st, t, l.sortedBy { it.nt }, locked) } } }
    }
}

@Composable
fun MyListPage(st: AppState, locked: Boolean) {
    val list = remember(Store.rev, Catalog.rev) { Store.favs.toList().reversed().mapNotNull { Catalog.byId[it] }.filter { Catalog.visible(it) } }
    Column(Modifier.fillMaxSize()) {
        Head("Minha lista", "Tudo o que você favoritou. Para adicionar ou remover, aperte OK duas vezes seguidas em qualquer filme, série ou canal.")
        if (list.isEmpty()) Box(Modifier.padding(start = 28.dp, top = 12.dp)) { Hint("Sua lista está vazia", "Vá em Filmes, Séries ou TV ao vivo e aperte OK duas vezes numa capa.") }
        else LazyVerticalGrid(GridCells.Adaptive(118.dp), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(list, key = { it.id }) { Card(st, it, locked, wide = false) }
        }
    }
}

// ---------------- pesquisa ----------------
@Composable
fun SearchPage(st: AppState, locked: Boolean) {
    val field = remember { FocusRequester() }
    val res = remember(st.query, Catalog.rev) { Catalog.search(st.query, 120) }
    val popular = remember(Catalog.rev) { Catalog.byTab.values.flatten().sortedByDescending { it.second.size }.map { it.first }.distinct().take(6) }
    Column(Modifier.fillMaxSize()) {
        Head("Pesquisa", "Aperte OK no campo para abrir o teclado. Enquanto digita, as sugestões aparecem: desça com a seta para escolher.")
        var f by remember { mutableStateOf(false) }
        Row(
            Modifier.padding(start = 28.dp, top = 10.dp).width(620.dp).height(48.dp).clip(RoundedCornerShape(14.dp)).background(C.panel)
                .border(if (f) 2.dp else 1.dp, if (f) C.a1 else C.line, RoundedCornerShape(14.dp))
                .focusRequester(field).focusProperties { canFocus = !locked }
                .onFocusChanged { val x = it.isFocused; if (x && !f) Sfx.tick(); f = x }
                .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); st.kb = KbTarget("Pesquisa", st.query, search = true, ret = field) { st.query = it.trim() } }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon("search", C.muted, 18.dp); Spacer(Modifier.width(12.dp))
            T(st.query.ifBlank { "Buscar filmes, séries e canais…" }, 15, if (st.query.isBlank()) C.dim else C.fg, FontWeight.SemiBold, maxLines = 1)
        }
        if (st.query.isBlank()) {
            if (popular.isNotEmpty()) {
                T("Categorias da sua lista", 14, C.fg, FontWeight.Bold, modifier = Modifier.padding(start = 28.dp, top = 18.dp))
                LazyRow(contentPadding = PaddingValues(start = 28.dp, top = 10.dp, end = 40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(popular) { p -> Chip(p, false, locked) { st.query = p } }
                }
            }
        } else if (res.isEmpty()) T("Nada encontrado para \"${st.query}\". Tente outra palavra ou use a voz.", 13, C.muted, modifier = Modifier.padding(start = 28.dp, top = 18.dp))
        else LazyVerticalGrid(GridCells.Adaptive(118.dp), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(res, key = { it.id }) { Card(st, it, locked, wide = false) }
        }
    }
}

// ---------------- detalhes de filme e série ----------------
@Composable
fun DetailDialog(st: AppState, it: Item) {
    val locked = st.kb != null || st.player != null
    var eps by remember(it.id) { mutableStateOf<List<Episode>?>(if (it.kind == SERIES) null else emptyList()) }
    var season by remember(it.id) { mutableIntStateOf(-1) }
    val playBtn = remember { FocusRequester() }
    LaunchedEffect(it.id) {
        if (it.kind == SERIES) Catalog.loadEpisodes(it) { l -> eps = l; season = l.firstOrNull()?.season ?: -1 }
        delay(80); try { playBtn.requestFocus() } catch (e: Exception) {}
    }
    BackHandler(enabled = !locked) { st.detail = null; st.refocus++ }
    val fav = remember(Store.rev) { Store.favs.contains(it.id) }
    val p = remember(Store.rev) { Store.progress[it.id] }
    Tmdb.rev.let { }
    val tm = Tmdb.meta(it)
    LaunchedEffect(tm) { tm?.let { m -> Tmdb.details(m) {} } }
    Box(Modifier.fillMaxSize().background(Color(0xD902040C)), contentAlignment = Alignment.Center) {
        Row(
            Modifier.width(820.dp).height(440.dp).clip(RoundedCornerShape(20.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(20.dp)).padding(22.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Box(Modifier.width(170.dp).height(255.dp).clip(RoundedCornerShape(12.dp)).background(coverBrush(it.title))) {
                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Bottom) { T(it.title, 14, Color.White, FontWeight.Bold) }
                Img(Tmdb.poster(it).ifBlank { it.logo }, 340, 510, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                T((if (it.kind == SERIES) "SÉRIE" else "FILME") + if (it.group.isNotBlank()) " · " + it.group.uppercase() else "", 10, C.a1, FontWeight.Bold, maxLines = 1)
                T(it.title, 24, C.fg, FontWeight.ExtraBold, maxLines = 2, lineHeight = 28)
                val yr = if (it.year > 0) it.year else tm?.year ?: 0
                val rate = if (it.rating.isNotBlank() && it.rating != "0") "★ ${it.rating}" else if ((tm?.vote ?: 0.0) > 0) "★ %.1f".format(tm!!.vote) else ""
                val meta = listOf(if (yr > 0) yr.toString() else "", tm?.cert?.takeIf { c -> c.isNotBlank() }?.let { c -> if (c == "L") "Livre" else "$c anos" } ?: "", rate, if ((tm?.runtime ?: 0) > 0) "${tm!!.runtime} min" else "", tm?.genres?.takeIf { g -> g != "—" } ?: "", if (Store.seen.contains(it.id)) "✓ Visto" else "").filter { s -> s.isNotBlank() }
                if (meta.isNotEmpty()) T(meta.joinToString(" · "), 12, C.muted, FontWeight.SemiBold, maxLines = 1)
                val plot = it.plot.ifBlank { tm?.overview ?: "" }
                if (plot.isNotBlank()) T(plot, 13, Color(0xFFC9D1F3), maxLines = if (it.kind == SERIES) 3 else 6, lineHeight = 19)
                if (!tm?.cast.isNullOrBlank()) T("Elenco: " + tm!!.cast, 11, C.muted, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (it.kind != SERIES) {
                        Box(Modifier.focusRequester(playBtn)) {
                            Btn(if (p != null) "▶ Continuar" else "▶ Assistir", locked = locked, primary = true) { st.play(PlayReq(it.id, it.title, it.url, false, p?.pos ?: 0L, it.group, it.ext)) }
                        }
                        if (p != null) Btn("Do início", locked = locked) { st.play(PlayReq(it.id, it.title, it.url, false, 0L, it.group, it.ext)) }
                    }
                    Box(if (it.kind == SERIES) Modifier.focusRequester(playBtn) else Modifier) {
                        Btn(if (fav) "♥ Na Minha lista" else "♡ Minha lista", locked = locked) { val on = Store.toggleFav(it.id); st.say(if (on) "♥ Adicionado à Minha lista" else "Removido da Minha lista") }
                    }
                    Btn("Voltar", locked = locked) { st.detail = null; st.refocus++ }
                }
                if (it.kind == SERIES) {
                    val l = eps
                    when {
                        l == null -> T("Carregando episódios…", 12, C.muted)
                        l.isEmpty() -> T("Não encontrei episódios desta série no servidor.", 12, C.muted)
                        else -> {
                            val seasons = l.map { e -> e.season }.distinct()
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(seasons) { s -> Chip("Temporada $s", s == season, locked) { season = s } } }
                            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(l.filter { e -> e.season == season }, key = { e -> e.id }) { e -> EpisodeRow(st, it, e, locked) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(st: AppState, s: Item, e: Episode, locked: Boolean) {
    var f by remember { mutableStateOf(false) }
    val p = remember(Store.rev) { Store.progress[e.id] }
    val seen = remember(Store.rev) { Store.seen.contains(e.id) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (f) Brush.horizontalGradient(listOf(C.a1, C.a2)) else Brush.horizontalGradient(listOf(C.panel2, C.panel2)))
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val x = it.isFocused; if (x && !f) Sfx.tick(); f = x }
            .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); st.play(PlayReq(e.id, "${s.title} · T${e.season} E${e.num}", e.url, false, p?.pos ?: 0L, s.group)) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val fg = if (f) Color(0xFF050816) else C.fg
        T("E${e.num}", 12, fg, FontWeight.ExtraBold)
        T(e.title.ifBlank { "Episódio ${e.num}" }, 12, fg, FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        T(if (seen) "✓ visto" else if (p != null) "parou em ${p.pos / 60000} min" else "", 11, if (f) Color(0xFF0A1030) else C.muted)
    }
}

@Composable
fun NewsDialog(st: AppState, n: News) {
    val btn = remember { FocusRequester() }
    LaunchedEffect(n) { delay(60); try { btn.requestFocus() } catch (e: Exception) {} }
    BackHandler { st.newsOpen = null; st.refocus++ }
    Box(Modifier.fillMaxSize().background(Color(0xD902040C)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(560.dp).clip(RoundedCornerShape(20.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(20.dp)).padding(24.dp).focusGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("NOTÍCIAS · ${n.cat.uppercase()} · LEITURA RESUMIDA", 10, C.a1, FontWeight.Bold)
            Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(coverBrush(n.title))) { Img(n.img, 1040, 300, Modifier.fillMaxSize()) }
            T(n.title, 20, C.fg, FontWeight.ExtraBold, lineHeight = 25)
            T(n.summary, 14, Color(0xFFC9D1F3), lineHeight = 21)
            Box(Modifier.focusRequester(btn)) { Btn("Fechar", locked = false, primary = true) { st.newsOpen = null; st.refocus++ } }
        }
    }
}

