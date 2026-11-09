package com.lumestar.tv

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

/** Este aparelho é celular/tablet (toque) e não TV. */
fun isPhone(a: Activity): Boolean {
    val ui = a.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
    val tv = ui.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION || a.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    return !tv
}

/**
 * Tamanho da tela no celular/tablet, para o layout se ajustar do aparelho mais simples ao mais novo.
 * Compacto: < 360 dp de largura (celulares pequenos/de entrada). Normal: 360–599 dp.
 * Expandido: 600 dp+ (tablets, dobráveis abertos, celular deitado).
 */
class MWin(val wDp: Int, val hDp: Int) {
    val compact get() = wDp < 360
    val expanded get() = wDp >= 600
    /** largura dos cartões de filme/série e de canal nas fileiras */
    val cardW get() = if (compact) 96 else if (expanded) 136 else 112
    val liveW get() = if (compact) 144 else if (expanded) 208 else 168
    /** destaque do Início: no máximo ~55% da altura (antes 420 dp fixos) */
    val heroH get() = (hDp * if (expanded) 0.6f else 0.55f).toInt().coerceIn(240, if (expanded) 480 else 420)
    /** topo da tela de detalhes */
    val bannerH get() = (hDp * 0.32f).toInt().coerceIn(170, 320)
    val newsW get() = if (compact) 220 else if (expanded) 300 else 250
}

val LocalMWin = androidx.compose.runtime.staticCompositionLocalOf { MWin(390, 800) }

@Composable
private fun rememberMWin(): MWin {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    return remember(cfg.screenWidthDp, cfg.screenHeightDp) { MWin(cfg.screenWidthDp, cfg.screenHeightDp) }
}

/** Tablet (menor lado ≥ 600 dp): pode girar; celular fica em pé fora do vídeo. */
fun isTablet(c: android.content.Context): Boolean = c.resources.configuration.smallestScreenWidthDp >= 600

private val MTABS = listOf("inicio" to "Início", "aovivo" to "Ao vivo", "filmes" to "Filmes", "series" to "Séries", "mais" to "Mais")
private val MTAB_ICON = mapOf("inicio" to "home", "aovivo" to "live", "filmes" to "film", "series" to "series", "mais" to "grid")
private val MORE = listOf(
    Triple("lista", "Minha lista", "heart"), Triple("lancamentos", "Lançamentos", "spark"), Triple("novelas", "Novelas", "novela"),
    Triple("infantil", "Infantil", "kid"), Triple("documentarios", "Documentários", "globe"), Triple("esportes", "Esportes", "ball"),
    Triple("noticias", "Notícias", "news"), Triple("musicas", "Músicas", "music"), Triple("streaming", "Streaming", "grid"),
    Triple("assistente", "Assistente", "bot"), Triple("config", "Configurações", "gear"),
)

/** App no celular: abas embaixo, toque e arrasto; o reprodutor deita a tela. */
@Composable
fun MobileApp(prefs: Prefs, args: LaunchArgs, finish: () -> Unit) {
    val st = remember { AppState(prefs) }
    var intro by remember { mutableStateOf(prefs.intro && !args.skipIntro) }
    androidx.compose.runtime.CompositionLocalProvider(LocalMWin provides rememberMWin()) {
    Box(Modifier.fillMaxSize().background(C.bg)) {
        MobileShell(st, args, finish)
        if (intro) Box(Modifier.fillMaxSize().background(Color.Black).clickable { Sfx.stopLong(); intro = false }) {
            DesignScale { IntroScreen(prefs.sounds) { intro = false } }
        }
    }
    }
    if (intro) BackHandler { Sfx.stopLong(); intro = false }
}

@Composable
private fun MobileShell(st: AppState, args: LaunchArgs, finish: () -> Unit) {
    val ctx = LocalContext.current
    val act = ctx as? Activity
    var tab by remember { mutableStateOf("inicio") }
    val stack = remember { mutableStateListOf<String>() } // páginas abertas por cima (Mais › …, pesquisa)
    LaunchedEffect(Unit) {
        args.page?.let { p -> if (MTABS.any { it.first == p }) tab = p else stack.add(p) }
        args.url?.let { u -> st.play(PlayReq("teste:" + u.hashCode(), "Teste de reprodução", u, !u.contains(".mp4"), 0L, "Teste")) }
        NewsState.load(); Catalog.refresh(ctx)
        Updater.check(ctx) { r -> st.update = r; if (r.status == "new") st.say("Nova versão ${r.remote}: Mais › Configurações › Atualização do app") }
    }
    // vídeo: tela deitada e sem barras; fora dele: em pé com barras
    val playing = st.player != null
    LaunchedEffect(playing) {
        val a = act ?: return@LaunchedEffect
        a.requestedOrientation = if (playing) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else if (isTablet(a)) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val c = WindowCompat.getInsetsController(a.window, a.window.decorView)
        if (playing) { c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; c.hide(WindowInsetsCompat.Type.systemBars()) } else c.show(WindowInsetsCompat.Type.systemBars())
    }

    val p = st.player
    if (p != null) { DesignScale { Box(Modifier.fillMaxSize().background(Color.Black)) { key(p.id, p.url) { PlayerScreen(st, p, touchMode = true) } } }; MToast(st); return }

    ThemeBackdrop()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        MTop(st) { stack.add("pesquisa") }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                "inicio" -> MHome(st) { stack.add(it) }
                "aovivo" -> MLive(st)
                "mais" -> MMore { stack.add(it) }
                else -> MSection(st, tab)
            }
        }
        Row(Modifier.fillMaxWidth().background(C.panel).padding(vertical = 6.dp)) {
            MTABS.forEach { (id, l) ->
                val on = tab == id
                Column(Modifier.weight(1f).clickable { stack.clear(); tab = id }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(MTAB_ICON.getValue(id), if (on) C.a1 else C.dim, 22.dp)
                    T(l, 11, if (on) C.fg else C.dim, FontWeight.SemiBold)
                }
            }
        }
    }
    // páginas por cima
    stack.lastOrNull()?.let { pg ->
        Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                if (pg != "pesquisa") MSubTop(MORE.find { it.first == pg }?.second ?: CFG_NAME(pg) ?: TAB_NAMES[pg] ?: "") { stack.removeAt(stack.lastIndex) }
                Box(Modifier.weight(1f).fillMaxWidth()) { MPage(st, pg, { stack.removeAt(stack.lastIndex) }) { stack.add(it) } }
            }
        }
    }
    st.detail?.let { MDetail(st, it) }
    st.newsOpen?.let { n -> MNews(st, n) }
    st.kb?.let { MKeyboard(st, it) }
    MToast(st)
    BackHandler(enabled = true) {
        when {
            st.kb != null -> st.kb = null
            st.detail != null -> st.detail = null
            st.newsOpen != null -> st.newsOpen = null
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
            tab != "inicio" -> tab = "inicio"
            else -> finish()
        }
    }
}

private fun CFG_NAME(id: String): String? = if (id.startsWith("cfg:")) cfgName(id.removePrefix("cfg:")) else null

@Composable
private fun MTop(st: AppState, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        GradientText("LumeStarTV", TextStyle(fontFamily = F.logo, fontSize = 30.sp))
        Spacer(Modifier.weight(1f))
        val w = Weather.now
        if (w != null && st.prefs.weather && st.prefs.city.isNotBlank()) { T("${w.icon} ${w.temp}°", 14, C.fg, FontWeight.Bold); Spacer(Modifier.width(10.dp)) }
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.panel).clickable { onSearch() }, contentAlignment = Alignment.Center) { Icon("search", C.fg, 20.dp) }
    }
    LaunchedEffect(Unit) { while (true) { if (st.prefs.weather) Weather.refresh(st.prefs.city, pos = st.prefs.cityPos); delay(30 * 60_000L) } }
}

@Composable
private fun MSubTop(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.panel).clickable { back() }, contentAlignment = Alignment.Center) { T("‹", 26, C.fg, FontWeight.Bold) }
        Spacer(Modifier.width(10.dp)); T(title, 19, C.fg, FontWeight.ExtraBold, maxLines = 1)
    }
}

// ---------------- cartões ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MCard(st: AppState, it: Item, caption: String? = null, width: Int = 112, menu: (Item) -> Unit) {
    Tmdb.rev.let { }
    val fav = remember(Store.rev) { Store.favs.contains(it.id) }
    val prog = remember(Store.rev) { Store.progress[it.id] }
    val live = it.kind == LIVE
    Column(Modifier.width(width.dp).combinedClickable(onClick = { openItem(st, it) }, onLongClick = { menu(it) })) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (live) 16f / 9f else 2f / 3f).clip(RoundedCornerShape(12.dp)).background(coverBrush(it.title))) {
            val cover = if (live) it.logo.ifBlank { Logos.rev.let { _ -> Logos.find(it.title) } } else Tmdb.poster(it).ifBlank { it.logo }
            if (cover.isNotBlank()) Img(cover, 240, if (live) 135 else 360, Modifier.fillMaxSize(), crop = !live)
            else T(Tmdb.title(it), 12, Color.White, FontWeight.ExtraBold, Modifier.align(Alignment.BottomStart).padding(8.dp), maxLines = 3)
            Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(28.dp).clip(CircleShape).background(Color(0xB0050816)).clickable { Store.toggleFav(it.id); st.say(if (!fav) "♥ Na Minha lista" else "Removido da Minha lista") }, contentAlignment = Alignment.Center) {
                T(if (fav) "♥" else "♡", 14, if (fav) Color(0xFFFF8FB8) else Color.White, FontWeight.Bold)
            }
            if (prog != null && prog.dur > 0) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x40FFFFFF))) {
                Box(Modifier.fillMaxWidth((prog.pos.toFloat() / prog.dur).coerceIn(0f, 1f)).fillMaxHeight().background(Brush.horizontalGradient(listOf(C.a1, C.a2))))
            }
            if (live && it.id in PP.offline) Box(Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xE0B0203C)).padding(horizontal = 5.dp, vertical = 2.dp)) { T("FORA DO AR", 8, Color.White, FontWeight.Bold) }
        }
        T(caption ?: Tmdb.title(it), 11, C.muted, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun MRow(st: AppState, title: String, items: List<Item>, menu: (Item) -> Unit, caption: ((Item) -> String)? = null, more: (() -> Unit)? = null) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 18.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            T(title, 16, C.fg, FontWeight.ExtraBold, maxLines = 1, modifier = Modifier.weight(1f))
            if (more != null) T("Ver tudo", 12, C.a1, FontWeight.Bold, modifier = Modifier.clickable { more() }.padding(4.dp))
        }
        val win = LocalMWin.current
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items.take(40), key = { it.id }) { i -> MCard(st, i, caption?.invoke(i), if (i.kind == LIVE) win.liveW else win.cardW, menu) }
        }
    }
}

// ---------------- menu do toque longo ----------------
@Composable
private fun rememberMenu(st: AppState): Pair<Item?, (Item?) -> Unit> {
    var m by remember { mutableStateOf<Item?>(null) }
    return m to { v: Item? -> m = v }
}

@Composable
private fun MActions(st: AppState, it: Item, close: () -> Unit) {
    val fav = Store.favs.contains(it.id); val seen = Store.seen.contains(it.id); val p = Store.progress[it.id]
    MSheet(close) {
        T(it.title, 16, C.fg, FontWeight.ExtraBold, maxLines = 2)
        Spacer(Modifier.height(6.dp))
        MAct("▶  Assistir") { close(); openItem(st, it) }
        MAct(if (fav) "♥  Tirar da Minha lista" else "♡  Pôr na Minha lista") { Store.toggleFav(it.id); close(); st.say(if (fav) "Removido da Minha lista" else "♥ Na Minha lista") }
        if (p != null || seen) MAct("⟲  Tirar do histórico") { Store.forget(it.id); close(); st.say("Saiu do Visto e do Continuar assistindo") }
        if (it.kind != LIVE) MAct("ⓘ  Detalhes") { close(); st.detail = it }
    }
}

@Composable
private fun MAct(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 14.dp)) { T(label, 15, C.fg, FontWeight.SemiBold) }
}

/** Folha que sobe de baixo; toque fora fecha. */
@Composable
private fun MSheet(close: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xB002040C)).clickable(onClick = close)) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(C.panel)
            .clickable(enabled = false) {}.navigationBarsPadding().padding(16.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x40FFFFFF)))
            Spacer(Modifier.height(10.dp)); content()
        }
    }
    BackHandler { close() }
}

// ---------------- telas ----------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MHome(st: AppState, go: (String) -> Unit) {
    Catalog.rev.let { }; Store.rev.let { }; Tmdb.rev.let { }
    val (menuItem, setMenu) = rememberMenu(st)
    val hero = remember(Catalog.rev) { heroFrom(Catalog.items.filter { Catalog.visible(it) && it.kind != LIVE }, 5) }
    val cont = Store.progress.entries.sortedByDescending { it.value.at }.mapNotNull { itemFor(it.key) }.distinctBy { it.id }
    val favs = Store.favs.mapNotNull { itemFor(it) }
    val seen = Store.seen.mapNotNull { itemFor(it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        if (Catalog.items.isEmpty()) MEmpty(st, go)
        if (hero.isNotEmpty()) {
            val ps = rememberPagerState(pageCount = { hero.size })
            LaunchedEffect(ps) { while (true) { delay(10_000); ps.animateScrollToPage((ps.currentPage + 1) % hero.size) } }
            HorizontalPager(ps, contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 10.dp) { i -> MHeroCard(st, hero[i]) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                hero.indices.forEach { k -> Box(Modifier.padding(horizontal = 2.dp).height(6.dp).width(if (k == ps.currentPage) 18.dp else 6.dp).clip(RoundedCornerShape(3.dp)).background(if (k == ps.currentPage) C.a1 else Color(0x40FFFFFF))) }
            }
        }
        MRow(st, "Continuar assistindo", cont, setMenu, caption = { i -> Store.progress[i.id]?.let { p -> if (p.dur > 0) "${p.pos * 100 / p.dur}% assistido" else null } ?: i.title })
        MRow(st, "Lançamentos", Catalog.releases(), setMenu, more = { go("lancamentos") })
        val news = NewsState.list
        if (news.isNotEmpty()) Column(Modifier.padding(top = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { T("Notícias", 16, C.fg, FontWeight.ExtraBold, modifier = Modifier.weight(1f)); T("Ver tudo", 12, C.a1, FontWeight.Bold, modifier = Modifier.clickable { go("noticias") }) }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(news.take(8)) { n -> MNewsCard(st, n, LocalMWin.current.newsW) } }
        }
        MRow(st, "Minha lista", favs, setMenu, more = { go("lista") })
        MRow(st, "Visto", seen, setMenu, caption = { "✓ Visto" })
        listOf("filmes", "series").forEach { t -> Catalog.rows(t).take(2).forEach { (name, l) -> MRow(st, "${TAB_NAMES[t]} · $name", l, setMenu) } }
    }
    menuItem?.let { MActions(st, it) { setMenu(null) } }
}

@Composable
private fun MHeroCard(st: AppState, e0: HeroEntry) {
    val m = e0.item?.let { Tmdb.meta(it) }
    val bg = m?.backdropUrl()?.ifBlank { null } ?: e0.img
    Box(Modifier.fillMaxWidth().height(LocalMWin.current.heroH.dp).clip(RoundedCornerShape(22.dp)).background(coverBrush(e0.title))) {
        if (bg.isNotBlank()) Img(bg, 720, 1080, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.8f to C.bg.copy(alpha = .92f))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            T(e0.eyebrow.uppercase(), 10, C.a1, FontWeight.ExtraBold, maxLines = 1)
            T(e0.title, 26, C.fg, FontWeight.ExtraBold, maxLines = 2, lineHeight = 29)
            val meta = e0.meta.ifBlank { m?.let { listOf(if (it.year > 0) "${it.year}" else "", if (it.vote > 0) "★ %.1f".format(it.vote) else "").filter { x -> x.isNotBlank() }.joinToString(" · ") } ?: "" }
            if (meta.isNotBlank()) T(meta, 12, C.muted, FontWeight.SemiBold)
            val text = e0.text.ifBlank { m?.overview ?: "" }
            if (text.isNotBlank()) T(text, 13, Color(0xFFC9D1F3), maxLines = 2, lineHeight = 18)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                e0.item?.let { it ->
                    MBtn("▶ Assistir", primary = true, modifier = Modifier.weight(1f)) { if (it.kind == LIVE) openItem(st, it) else st.play(PlayReq(it.id, it.title, it.url, false, Store.progress[it.id]?.pos ?: 0L, it.group, it.ext)) }
                    MBtn("Detalhes") { openItem(st, it); if (it.kind != LIVE) st.detail = it }
                }
                e0.news?.let { n -> MBtn("Ler resumo", primary = true) { st.newsOpen = n } }
            }
        }
    }
}

@Composable
fun MBtn(label: String, primary: Boolean = false, on: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(42.dp).clip(RoundedCornerShape(12.dp))
        .background(if (primary) Brush.horizontalGradient(listOf(C.a1, C.a2)) else SolidColor(if (on) C.fg else C.panel2))
        .clickable { onClick() }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        T(label, 14, if (primary || on) Color(0xFF050816) else C.fg, FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(if (on) C.fg else Color.Transparent).border(1.dp, if (on) C.fg else C.line, RoundedCornerShape(17.dp)).clickable { onClick() }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        T(label, 13, if (on) C.bg else C.muted, FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MEmpty(st: AppState, go: (String) -> Unit) {
    Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.panel).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T("Adicione sua lista", 18, C.fg, FontWeight.ExtraBold)
        T("Cole o link da sua lista (M3U, M3U8, JSON ou TXT) ou entre com sua conta IPTV (servidor, usuário e senha). Tudo vai sozinho para as abas certas.", 13, C.muted, lineHeight = 19)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MBtn("Adicionar lista", primary = true) { go("cfg:listas") }; MBtn("Conta IPTV") { go("cfg:contas") } }
    }
}

@Composable
private fun MLive(st: AppState) {
    Catalog.rev.let { }; PP.rev.let { }
    val rows = remember(Catalog.rev, Store.adultOpen) { Catalog.rows("aovivo") }
    var cat by remember { mutableStateOf("Todos") }
    val list = if (cat == "Todos") rows.flatMap { it.second }.distinctBy { it.id } else rows.firstOrNull { it.first == cat }?.second ?: emptyList()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { T("Ao vivo", 24, C.fg, FontWeight.ExtraBold, modifier = Modifier.weight(1f)); T("${list.size} canais", 12, C.dim, FontWeight.SemiBold) }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("Todos") + rows.map { it.first }) { c -> MChip(c, c == cat) { cat = c } }
        }
        if (list.isEmpty()) T("Nenhum canal ao vivo. Adicione sua lista em Mais › Configurações.", 13, C.muted, modifier = Modifier.padding(16.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
            items(list, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { openItem(st, c) }.padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(64.dp).height(40.dp).clip(RoundedCornerShape(9.dp)).background(coverBrush(c.title)), contentAlignment = Alignment.Center) {
                        if (c.logo.isNotBlank()) Img(c.logo, 128, 80, Modifier.fillMaxSize(), crop = false) else T(c.title.take(3).uppercase(), 12, Color.White, FontWeight.ExtraBold)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) { T(c.title, 14, C.fg, FontWeight.Bold, maxLines = 1); T(c.group, 12, C.muted, maxLines = 1) }
                    if (c.id in PP.offline) T("FORA DO AR", 9, C.bad, FontWeight.ExtraBold) else T("● AO VIVO", 9, C.bad, FontWeight.ExtraBold)
                }
            }
        }
    }
}

/** Filmes, Séries, Novelas, Infantil…: categorias em chips e grade de capas. */
@Composable
private fun MSection(st: AppState, tab: String, top: (@Composable () -> Unit)? = null) {
    Catalog.rev.let { }; Store.rev.let { }
    val (menuItem, setMenu) = rememberMenu(st)
    val rows = remember(Catalog.rev, Store.adultOpen, tab) { Catalog.rows(tab) }
    var cat by remember(tab) { mutableStateOf("Tudo") }
    var recent by remember(tab) { mutableStateOf(false) }
    val base = if (cat == "Tudo") rows.flatMap { it.second }.distinctBy { it.id } else rows.firstOrNull { it.first == cat }?.second ?: emptyList()
    val list = remember(base, recent) { if (recent) base.sortedByDescending { it.year } else base.sortedBy { it.nt } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            T(TAB_NAMES[tab] ?: tab, 24, C.fg, FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            MChip(if (recent) "Mais recentes" else "A–Z", false) { recent = !recent }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("Tudo") + rows.map { it.first }) { c -> MChip(c, c == cat) { cat = c } }
        }
        top?.invoke()
        if (list.isEmpty()) T("Nada aqui ainda. Os títulos da sua lista aparecem sozinhos nesta aba.", 13, C.muted, modifier = Modifier.padding(16.dp))
        MGrid(st, list, setMenu)
    }
    menuItem?.let { MActions(st, it) { setMenu(null) } }
}

@Composable
private fun MGrid(st: AppState, list: List<Item>, menu: (Item) -> Unit) {
    val live = list.isNotEmpty() && list.all { it.kind == LIVE }
    LazyVerticalGrid(GridCells.Adaptive(if (live) 160.dp else 104.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        items(list, key = { it.id }) { i -> MCard(st, i, width = 400, menu = menu) }
    }
}

@Composable
private fun MMore(open: (String) -> Unit) {
    LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(MORE) { (id, name, ic) ->
            Column(Modifier.clip(RoundedCornerShape(16.dp)).background(C.panel).clickable { open(id) }.padding(14.dp).height(64.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(ic, C.a1, 24.dp); T(name, 14, C.fg, FontWeight.Bold)
            }
        }
    }
}

/** Páginas abertas por cima: Mais › …, pesquisa, configurações. */
@Composable
private fun MPage(st: AppState, pg: String, back: () -> Unit, go: (String) -> Unit) {
    val (menuItem, setMenu) = rememberMenu(st)
    Store.rev.let { }; Catalog.rev.let { }
    when {
        pg == "pesquisa" -> MSearch(st, back, setMenu)
        pg == "lista" -> { val l = Store.favs.mapNotNull { itemFor(it) }; if (l.isEmpty()) T("Toque no ♡ de um título, ou segure o dedo sobre ele, para guardar aqui.", 13, C.muted, modifier = Modifier.padding(16.dp)) else MGrid(st, l, setMenu) }
        pg == "lancamentos" -> MGrid(st, Catalog.releases(), setMenu)
        pg == "noticias" -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(NewsState.list) { n -> MNewsCard(st, n, 0) } }
        pg == "musicas" -> MSection(st, "musicas") { Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { MUSIC_APPS.forEach { AppTile(st, it, false) } } }
        pg == "streaming" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Abre o app oficial de cada serviço instalado no celular. Os que faltam abrem na Play Store.", 13, C.muted, lineHeight = 19)
            STREAM_APPS.chunked(2).forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { r.forEach { AppTile(st, it, false) } } }
        }
        pg == "assistente" -> AssistantPage(st, false, compact = true)
        pg == "config" -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(cfgList()) { (id, name) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.panel).clickable { go("cfg:$id") }.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(name, 15, C.fg, FontWeight.SemiBold, modifier = Modifier.weight(1f)); T("›", 20, C.dim)
                }
            }
        }
        pg.startsWith("cfg:") -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { CfgPanelById(st, pg.removePrefix("cfg:")) }
        TAB_NAMES.containsKey(pg) -> MSection(st, pg)
    }
    menuItem?.let { MActions(st, it) { setMenu(null) } }
}

@Composable
private fun MSearch(st: AppState, back: () -> Unit, menu: (Item) -> Unit) {
    var q by remember { mutableStateOf(st.query) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); try { fr.requestFocus() } catch (e: Exception) {} }
    val res = remember(q, Catalog.rev) { if (q.trim().length >= 2) Catalog.search(q.trim()) else emptyList() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(C.panel).clickable { back() }, contentAlignment = Alignment.Center) { T("‹", 26, C.fg, FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp)).background(C.panel).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (q.isEmpty()) T("Filmes, séries, canais…", 15, C.dim)
                BasicTextField(q, { q = it; st.query = it }, singleLine = true, textStyle = TextStyle(color = C.fg, fontSize = 15.sp), cursorBrush = SolidColor(C.a1),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth().focusRequester(fr))
            }
        }
        if (q.trim().length < 2) T("Digite pelo menos 2 letras. O teclado é o do seu celular.", 13, C.muted, modifier = Modifier.padding(16.dp))
        else if (res.isEmpty()) T("Nada encontrado para “$q”.", 13, C.muted, modifier = Modifier.padding(16.dp))
        MGrid(st, res, menu)
    }
}

@Composable
private fun MNewsCard(st: AppState, n: News, width: Int) {
    Column(Modifier.then(if (width > 0) Modifier.width(width.dp) else Modifier.fillMaxWidth()).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(C.panel2, C.panel))).clickable { st.newsOpen = n }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        T(n.cat.uppercase(), 10, C.a1, FontWeight.ExtraBold); T(n.title, 14, C.fg, FontWeight.Bold, maxLines = 2, lineHeight = 18); T(n.summary, 12, C.muted, maxLines = 2, lineHeight = 16)
    }
}

@Composable
private fun MNews(st: AppState, n: News) {
    Box(Modifier.fillMaxSize().background(C.bg).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            MSubTop("Notícias") { st.newsOpen = null }
            if (n.img.isNotBlank()) Img(n.img, 720, 400, Modifier.fillMaxWidth().height(200.dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T(n.cat.uppercase() + " · leitura de 1 min", 11, C.a1, FontWeight.ExtraBold)
                T(n.title, 22, C.fg, FontWeight.ExtraBold, lineHeight = 27)
                T(n.summary, 15, Color(0xFFC9D1F3), lineHeight = 23)
            }
        }
    }
}

/** Detalhe do título em tela cheia, com temporadas e episódios. */
@Composable
private fun MDetail(st: AppState, it: Item) {
    Store.rev.let { }; Tmdb.rev.let { }
    val tm = Tmdb.meta(it)
    LaunchedEffect(tm) { tm?.let { m -> Tmdb.details(m) {} } }
    var eps by remember(it.id) { mutableStateOf<List<Episode>?>(null) }
    var season by remember(it.id) { mutableIntStateOf(-1) }
    LaunchedEffect(it.id) { if (it.kind == SERIES) Catalog.loadEpisodes(it) { l -> eps = l; season = l.firstOrNull()?.season ?: -1 } }
    val p = Store.progress[it.id]; val fav = Store.favs.contains(it.id); val seen = Store.seen.contains(it.id)
    Box(Modifier.fillMaxSize().background(C.bg)) {
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            item {
                Box(Modifier.fillMaxWidth().height(LocalMWin.current.bannerH.dp).background(coverBrush(it.title + "bg"))) {
                    val bg = tm?.backdropUrl()?.ifBlank { null } ?: it.logo
                    if (bg.isNotBlank()) Img(bg, 1080, 600, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to C.bg)))
                    Box(Modifier.statusBarsPadding().padding(10.dp).size(40.dp).clip(CircleShape).background(Color(0xB0050816)).clickable { st.detail = null }, contentAlignment = Alignment.Center) { T("‹", 26, Color.White, FontWeight.Bold) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T(it.group.uppercase(), 10, C.a1, FontWeight.ExtraBold, maxLines = 1)
                    T(Tmdb.title(it), 24, C.fg, FontWeight.ExtraBold, lineHeight = 28)
                    val yr = if (it.year > 0) it.year else tm?.year ?: 0
                    val rate = if (it.rating.isNotBlank() && it.rating != "0") "★ ${it.rating}" else if ((tm?.vote ?: 0.0) > 0) "★ %.1f".format(tm!!.vote) else ""
                    val meta = listOf(if (yr > 0) "$yr" else "", tm?.cert?.takeIf { c -> c.isNotBlank() }?.let { c -> if (c == "L") "Livre" else "$c anos" } ?: "", rate,
                        if ((tm?.runtime ?: 0) > 0) "${tm!!.runtime} min" else "", if (seen) "✓ Visto" else "").filter { s -> s.isNotBlank() }
                    if (meta.isNotEmpty()) T(meta.joinToString(" · "), 12, C.muted, FontWeight.SemiBold)
                    if (!tm?.genres.isNullOrBlank() && tm?.genres != "—") T(tm!!.genres, 12, C.muted)
                    val plot = it.plot.ifBlank { tm?.overview ?: "" }
                    if (plot.isNotBlank()) T(plot, 14, Color(0xFFC9D1F3), lineHeight = 21)
                    if (!tm?.cast.isNullOrBlank()) T("Elenco: " + tm!!.cast, 12, C.muted)
                    if (it.kind != SERIES) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MBtn(if (p != null) "▶ Continuar" else "▶ Assistir", primary = true, modifier = Modifier.weight(1f)) { st.play(PlayReq(it.id, it.title, it.url, false, p?.pos ?: 0L, it.group, it.ext)) }
                        if (p != null) MBtn("Do início") { st.play(PlayReq(it.id, it.title, it.url, false, 0L, it.group, it.ext)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MBtn(if (fav) "♥ Na Minha lista" else "♡ Minha lista", modifier = Modifier.weight(1f)) { Store.toggleFav(it.id) }
                        if (p != null || seen) MBtn("Tirar do histórico", modifier = Modifier.weight(1f)) { Store.forget(it.id); st.say("Saiu do histórico") }
                    }
                    if (it.kind == SERIES) {
                        val l = eps
                        if (l == null) T("Carregando episódios…", 13, C.muted)
                        else if (l.isEmpty()) T("Nenhum episódio encontrado.", 13, C.muted)
                        else LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { items(l.map { e -> e.season }.distinct()) { s -> MChip("Temporada $s", s == season) { season = s } } }
                    }
                }
            }
            val l = eps
            if (l != null) items(l.filter { e -> e.season == season }, key = { e -> e.id }) { e ->
                val ep = Store.progress[e.id]
                Row(Modifier.fillMaxWidth().clickable { st.play(PlayReq(e.id, "${it.title} · T${e.season} E${e.num}", e.url, false, ep?.pos ?: 0L, it.group)) }.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(9.dp)).background(coverBrush(it.title + e.id)), contentAlignment = Alignment.Center) { T("▶", 18, Color.White) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        T("${e.num}. ${e.title}", 13, C.fg, FontWeight.Bold, maxLines = 2)
                        if (e.plot.isNotBlank()) T(e.plot, 11, C.muted, maxLines = 2)
                        if (ep != null && ep.dur > 0) T("${ep.pos * 100 / ep.dur}% assistido", 11, C.a1, FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** No celular, os campos usam o teclado do próprio aparelho. */
@Composable
private fun MKeyboard(st: AppState, k: KbTarget) {
    var v by remember(k) { mutableStateOf(k.value) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(k) { delay(150); try { fr.requestFocus() } catch (e: Exception) {} }
    fun done() { st.kb = null; k.done(v) }
    MSheet({ st.kb = null }) {
        T(k.label, 16, C.fg, FontWeight.ExtraBold)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(C.bg).border(1.dp, C.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            if (v.isEmpty()) T(k.hint, 14, C.dim, maxLines = 1)
            BasicTextField(v, { v = it }, singleLine = true, textStyle = TextStyle(color = C.fg, fontSize = 15.sp), cursorBrush = SolidColor(C.a1),
                visualTransformation = if (k.secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (k.secret) KeyboardType.Password else if (k.hint.contains("http") || k.label.contains("ndereço") || k.label.contains("URL")) KeyboardType.Uri else KeyboardType.Text, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { done() }), modifier = Modifier.fillMaxWidth().focusRequester(fr))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MBtn("Cancelar", modifier = Modifier.weight(1f)) { st.kb = null }; MBtn("OK", primary = true, modifier = Modifier.weight(1f)) { done() } }
    }
}

@Composable
private fun MToast(st: AppState) {
    val m = st.toast ?: return
    LaunchedEffect(st.toastId) { delay(2600); st.toast = null }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 86.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(22.dp)).background(C.fg).padding(horizontal = 16.dp, vertical = 10.dp)) { T(m, 13, C.bg, FontWeight.Bold, maxLines = 2) }
    }
}
