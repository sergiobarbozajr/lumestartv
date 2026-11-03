package com.lumestar.tv

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- menu ----------------
class Nav(val id: String, val label: String, val icon: String, val desc: String, val soon: String)

private val IC = mapOf(
    "home" to "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z",
    "search" to "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14zM20 20l-4-4",
    "live" to "M3 7h18v12H3zM8 3l4 4 4-4",
    "heart" to "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.5-7 10-7 10z",
    "grid" to "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z",
    "spark" to "M12 3l2.4 5.6L20 11l-5.6 2.4L12 19l-2.4-5.6L4 11l5.6-2.4z",
    "film" to "M4 5h16v14H4zM4 9h16M4 15h16M8 5v4M16 5v4M8 15v4M16 15v4",
    "series" to "M5 8h14v11H5zM8 5h8M10 11l4 2.5-4 2.5z",
    "novela" to "M4 5h16v11H9l-5 4z",
    "kid" to "M12 3a5 5 0 0 1 5 5c0 4-5 7-5 7s-5-3-5-7a5 5 0 0 1 5-5zM12 15l-1 2h2l-1 2v2",
    "globe" to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18",
    "ball" to "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7.5l4 3-1.5 4.5h-5L8 10.5z",
    "news" to "M4 5h13v14H6a2 2 0 0 1-2-2zM17 9h3v8a2 2 0 0 1-2 2M7 9h7M7 13h7",
    "music" to "M9 18V5l11-2v13M9 18a3 3 0 1 1-6 0a3 3 0 1 1 6 0zM20 16a3 3 0 1 1-6 0a3 3 0 1 1 6 0z",
    "bot" to "M12 3v3M7 7h10a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3v-6a3 3 0 0 1 3-3zM9 12h.01M15 12h.01M9.5 15.5h5",
    "gear" to "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6zM12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1",
    "power" to "M12 3v8M6.3 6.3a8 8 0 1 0 11.4 0",
)

val MENU = listOf(
    Nav("inicio", "Início", "home", "Destaques, notícias resumidas, Visto e Continuar assistindo.", "Os destaques que mudam a cada 10 segundos e as fileiras chegam na entrega 2, junto com a sua lista IPTV."),
    Nav("pesquisa", "Pesquisa", "search", "Teclado virtual flutuante com sugestões e busca por voz.", "Chega na entrega 2, já buscando dentro da sua lista."),
    Nav("aovivo", "TV ao vivo", "live", "Canais de TV aberta separados por categoria.", "Chega na entrega 2, lendo os canais da sua lista IPTV."),
    Nav("lista", "Minha lista", "heart", "Tudo o que você favoritar apertando OK duas vezes.", "Chega na entrega 2."),
    Nav("streaming", "Streaming", "grid", "Netflix, Prime Video, Disney+, HBO Max, Apple TV, Globoplay, YouTube, Pluto TV, Mercado Play e Plex.", "Chega na entrega 4, abrindo os apps oficiais instalados na TV."),
    Nav("lancamentos", "Lançamentos", "spark", "Tudo da sua lista com data de lançamento no ano atual.", "Chega na entrega 2."),
    Nav("filmes", "Filmes", "film", "Destaque com sinopse e fileiras por categoria, em ordem alfabética.", "Chega na entrega 2."),
    Nav("series", "Séries", "series", "Destaque com sinopse e fileiras por categoria, em ordem alfabética.", "Chega na entrega 2."),
    Nav("novelas", "Novelas", "novela", "Fileiras por categoria, em ordem alfabética.", "Chega na entrega 2."),
    Nav("infantil", "Infantil", "kid", "Fileiras por categoria, em ordem alfabética.", "Chega na entrega 2."),
    Nav("documentarios", "Documentários", "globe", "Fileiras por categoria, em ordem alfabética.", "Chega na entrega 2."),
    Nav("esportes", "Esportes", "ball", "Fileiras por categoria, em ordem alfabética.", "Chega na entrega 2."),
    Nav("noticias", "Notícias", "news", "Telejornais e leitura resumida: mercado, futebol, tecnologia, investimentos, nacional e internacional.", "Chega na entrega 2."),
    Nav("musicas", "Músicas", "music", "Seus apps de música e as rádios da sua lista.", "Chega na entrega 4."),
    Nav("assistente", "Assistente IA", "bot", "Diagnóstico e ajuda: canal travando, teste de internet, imagem, som e legendas.", "Chega na entrega 4 (IA no aparelho para diagnóstico e na nuvem para conversa)."),
    Nav("config", "Configurações", "gear", "", ""),
)

private val pathCache = HashMap<String, Path>()
private fun iconPath(name: String): Path = pathCache.getOrPut(name) { PathParser().parsePathString(IC[name] ?: "").toPath() }

@Composable
fun Icon(name: String, color: Color, size: Dp = 22.dp) {
    val p = remember(name) { iconPath(name) }
    Canvas(Modifier.size(size)) {
        scale(size.toPx() / 24f, pivot = Offset.Zero) {
            drawPath(p, color, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private val logoBrush = Brush.linearGradient(listOf(Color(0xFF4F9DFF), Color(0xFF7F8BFF), Color(0xFFC9A6FF)))

/** Texto com o degradê da logo (azul → lilás). */
@Composable
fun GradientText(text: String, style: TextStyle, modifier: Modifier = Modifier, brush: Brush = logoBrush) {
    BasicText(
        text, style = style.copy(color = Color.White),
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent { drawContent(); drawRect(brush, blendMode = BlendMode.SrcIn) }
    )
}

@Composable
fun T(text: String, size: Int, color: Color = C.fg, weight: FontWeight = FontWeight.Medium, modifier: Modifier = Modifier, lineHeight: Int = 0, maxLines: Int = Int.MAX_VALUE) {
    BasicText(
        text, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontFamily = F.body, fontSize = size.sp, fontWeight = weight, color = color, lineHeight = if (lineHeight > 0) lineHeight.sp else (size * 1.4f).sp)
    )
}

// ---------------- estado do app ----------------
class AppState(val prefs: Prefs) {
    var page by mutableStateOf("inicio")
    var openT by mutableFloatStateOf(0f)
    var refocus by mutableIntStateOf(0)
    fun closeExit() { exitAsk = false; refocus++ }
    var exitAsk by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var toastId by mutableIntStateOf(0)
    var navHasFocus by mutableStateOf(false)
    val navReq = MENU.associate { it.id to FocusRequester() }
    val exitReq = FocusRequester()
    val contentReq = FocusRequester()
    var update: Updater.Check? by mutableStateOf(null)
    fun say(msg: String) { toast = msg; toastId++ }

    // camadas por cima das telas
    var detail by mutableStateOf<Item?>(null)
    var player by mutableStateOf<PlayReq?>(null)
    var kb by mutableStateOf<KbTarget?>(null)
    var newsOpen by mutableStateOf<News?>(null)
    var query by mutableStateOf("")
    var cfgSel by mutableStateOf("contas")
    // OK duas vezes = favoritar
    var lastTapId: String? = null
    var lastTapAt = 0L
    var pendingOpen: kotlinx.coroutines.Job? = null
    var focusTarget by mutableStateOf<FocusRequester?>(null)
    var focusTick by mutableIntStateOf(0)
    fun focusLater(r: FocusRequester) { focusTarget = r; focusTick++ }
    fun play(r: PlayReq) { pendingOpen?.cancel(); player = r }
    fun closePlayer() { player = null; refocus++ }
    val overlay get() = detail != null || player != null || kb != null || newsOpen != null || exitAsk
}

/** Notícias do Início (carregadas em segundo plano). */
object NewsState {
    var list by mutableStateOf<List<News>>(emptyList())
    var loaded by mutableStateOf(false)
    fun load() {
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        kotlin.concurrent.thread(name = "noticias") { val l = try { NewsFeed.load() } catch (e: Throwable) { emptyList() }; main.post { list = l; loaded = true } }
    }
}

class LaunchArgs(val skipIntro: Boolean = false, val page: String? = null, val url: String? = null)

@Composable
fun LumeApp(prefs: Prefs, args: LaunchArgs = LaunchArgs(), finish: () -> Unit) {
    val st = remember { AppState(prefs) }
    var phase by remember { mutableStateOf(if (prefs.intro && !args.skipIntro) "intro" else "open") }

    DesignScale {
        Box(Modifier.fillMaxSize().background(C.bg)) {
            if (phase != "intro") Shell(st, args, finish)
            if (phase == "intro") {
                Box(Modifier.fillMaxSize()) { IntroScreen(prefs.sounds) { phase = "open" } }
            }
        }
    }
    // qualquer tecla na abertura pula direto para o buraco de minhoca
    if (phase == "intro") BackHandler { Sfx.stopLong(); phase = "open" }
    SideEffect { IntroSkip.handler = if (phase == "intro") ({ Sfx.stopLong(); phase = "open" }) else null }
}

/** Ponte para a Activity: qualquer tecla durante a abertura pula. */
object IntroSkip { var handler: (() -> Unit)? = null }

@Composable
private fun Shell(st: AppState, args: LaunchArgs, finish: () -> Unit) {
    val ctx = LocalContext.current
    // linha do tempo da abertura pelo buraco de minhoca
    LaunchedEffect(Unit) {
        var playing = !st.prefs.sounds
        val start = withFrameNanos { it }
        st.openT = 0f
        while (true) {
            val now = withFrameNanos { it }
            st.openT = ((now - start) / 1e9).toFloat()
            if (!playing) { val pcm = Tracks.opening; if (pcm != null) { playing = true; Sfx.playLong(pcm, st.openT) } }
            if (st.openT >= Opening.END) break
        }
        st.openT = -1f
        withFrameNanos { }; withFrameNanos { }
        args.page?.let { p -> if (MENU.any { it.id == p }) st.page = p }
        try { st.navReq[st.page]?.requestFocus() } catch (e: Exception) {}
        args.url?.let { u -> st.play(PlayReq("teste:" + u.hashCode(), "Teste de reprodução", u, !u.contains(".mp4"), 0L, "Teste")) }
        NewsState.load()
        Catalog.refresh(ctx)
        Updater.check(ctx) { r ->
            st.update = r
            if (r.status == "new") st.say("Nova versão ${r.remote} disponível: Configurações › Atualização do app")
        }
    }
    LaunchedEffect(Unit) {
        // um toque grave para cada opção que sai do buraco
        for (i in 0..MENU.size + 1) {
            delay(((Opening.itemDelay(i) + 0.3f) * 1000).toLong() - (if (i == 0) 0 else ((Opening.itemDelay(i - 1) + 0.3f) * 1000).toLong()))
            Sfx.tick()
        }
    }
    val opening by remember { derivedStateOf { st.openT >= 0f } }
    LaunchedEffect(st.refocus) { if (st.refocus > 0) { delay(60); try { st.navReq[st.page]?.requestFocus() } catch (e: Exception) {} } }
    LaunchedEffect(st.focusTick) { val r = st.focusTarget; if (r != null) { delay(60); try { r.requestFocus() } catch (e: Exception) { st.refocus++ } } }
    BackHandler(enabled = !opening && !st.overlay || st.exitAsk) {
        when {
            st.exitAsk -> st.closeExit()
            !st.navHasFocus -> try { st.navReq[st.page]?.requestFocus() } catch (e: Exception) {}
            st.page != "inicio" -> { st.page = "inicio"; try { st.navReq["inicio"]?.requestFocus() } catch (e: Exception) {} }
            else -> st.exitAsk = true
        }
    }
    val locked by remember { derivedStateOf { st.overlay || st.openT >= 0f } }
    Box(Modifier.fillMaxSize()) {
        // conteúdo (templates) — nasce depois que o buraco fecha
        Box(Modifier.fillMaxSize().padding(start = 48.dp)) {
            Page(st, locked)
            TopBar(st)
        }
        WormholeOverlay(st)
        Sidebar(st, locked)
        st.newsOpen?.let { NewsDialog(st, it) }
        st.detail?.let { DetailDialog(st, it) }
        st.kb?.let { Keyboard(st, it) }
        st.player?.let { PlayerScreen(st, it) }
        if (st.exitAsk) ExitDialog(st, finish)
        Toast(st)
    }
}

@Composable
private fun TopBar(st: AppState) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { if (Store.removedDup > 0) { delay(6000); st.say("${Store.removedDup} lista(s) repetida(s) foram removidas."); Store.removedDup = 0 } }
    LaunchedEffect(Unit) { if (CrashLog.fresh) { delay(4000); st.say("O app fechou sozinho da última vez. O motivo está em Configurações › Sobre o aparelho.") } }
    LaunchedEffect(Unit) { var n = 0; while (true) { now = Date(); if (n++ % 8 == 0 && st.prefs.weather) Weather.refresh(st.prefs.city, pos = st.prefs.cityPos); delay(15000) } }
    val pt = Locale("pt", "BR")
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, end = 24.dp).graphicsLayer { alpha = Opening.block(st.openT, 0) },
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically
    ) {
        val w = Weather.now
        if (w != null && st.prefs.weather && st.prefs.city.isNotBlank()) {
            T("${w.icon} ${w.temp}°", 15, C.fg, FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            T(w.city, 13, C.muted, FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.width(14.dp))
        }
        T(SimpleDateFormat("EEE, d 'de' MMM", pt).format(now), 13, C.muted, FontWeight.SemiBold)
        Spacer(Modifier.width(14.dp))
        T(SimpleDateFormat("HH:mm", pt).format(now), 17, C.fg, FontWeight.Bold)
    }
}

// ---------------- menu lateral ----------------
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun Sidebar(st: AppState, locked: Boolean) {
    val w by animateDpAsState(if (st.navHasFocus) 190.dp else 48.dp, tween(220), label = "nav")
    val d = LocalDensity.current
    val center = with(d) { Offset(480.dp.toPx(), 270.dp.toPx()) }
    Column(
        Modifier.fillMaxHeight().width(w)
            .drawBehind { drawRect(Brush.horizontalGradient(if (st.navHasFocus) listOf(Color(0xFF070C26), Color(0xFA050816)) else listOf(Color(0xF2070C26), Color(0xEB050816))), alpha = Opening.block(st.openT, 0)) }
            .onFocusChanged { st.navHasFocus = it.hasFocus }
            // ao voltar do conteúdo para o menu, o foco vai para a aba aberta (não para a opção mais próxima)
            .focusProperties { enter = { st.navReq[st.page] ?: FocusRequester.Default } }
            .focusGroup()
            .padding(horizontal = 5.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // logo: só o "L" fechado; "LumeStarTV" com o menu aberto
        Box(Modifier.height(40.dp).fillMaxWidth().clipToBounds().graphicsLayer { alpha = Opening.item(st.openT, 0) }, contentAlignment = Alignment.CenterStart) {
            val logo = TextStyle(fontFamily = F.logo, fontSize = 36.sp)
            Box(Modifier.padding(start = 6.dp)) {
                GradientText(if (st.navHasFocus) "LumeStarTV" else "L", logo)
                GradientText(if (st.navHasFocus) "LumeStarTV" else "L", logo, Modifier.offset(y = 24.dp).graphicsLayer { scaleY = -1f; alpha = .22f })
            }
        }
        Spacer(Modifier.height(4.dp))
        MENU.forEachIndexed { i, n -> NavItem(st, n, i + 1, center, locked) }
        Spacer(Modifier.weight(1f))
        NavItem(st, Nav("sair", "Sair do aplicativo", "power", "", ""), MENU.size + 1, center, locked)
    }
}

@Composable
private fun NavItem(st: AppState, n: Nav, idx: Int, center: Offset, locked: Boolean) {
    var focused by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(Offset.Zero) }
    val cur = st.page == n.id
    LaunchedEffect(focused) {
        if (focused && n.id != "sair" && st.page != n.id) { delay(280); st.page = n.id }
    }
    val req = st.navReq[n.id] ?: st.exitReq
    Row(
        Modifier.fillMaxWidth().height(26.dp)
            .onGloballyPositioned { pos = it.boundsInRoot().center }
            .graphicsLayer {
                val p = Opening.item(st.openT, idx)
                translationX = (center.x - pos.x) * (1 - p)
                translationY = (center.y - pos.y) * (1 - p)
                scaleX = .05f + .95f * p; scaleY = scaleX
                rotationZ = 220f * (1 - p)
                alpha = min1(p / .3f)
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Brush.horizontalGradient(listOf(C.a1, C.a2)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
            .focusRequester(req)
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val f = it.isFocused; if (f && !focused) Sfx.tick(); focused = f }
            .clickable(remember { MutableInteractionSource() }, null) {
                Sfx.ok()
                if (n.id == "sair") st.exitAsk = true else st.page = n.id
            }
            
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(3.dp).height(12.dp).offset(x = (-7).dp).background(if (cur && !focused) C.a1 else Color.Transparent, RoundedCornerShape(2.dp)))
        Icon(n.icon, if (focused) Color(0xFF050816) else if (cur) C.fg else C.muted, 17.dp)
        if (st.navHasFocus) {
            Spacer(Modifier.width(14.dp))
            T(n.label, 12, if (focused) Color(0xFF050816) else if (cur) C.fg else C.muted, FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private fun min1(x: Float) = if (x > 1f) 1f else if (x < 0f) 0f else x

// ---------------- páginas ----------------
@Composable
private fun Block(st: AppState, i: Int, content: @Composable () -> Unit) {
    Box(Modifier.graphicsLayer {
        val p = Opening.block(st.openT, i)
        alpha = p; scaleX = .9f + .1f * p; scaleY = scaleX
    }) { content() }
}

@Composable
private fun Page(st: AppState, locked: Boolean) {
    val n = MENU.firstOrNull { it.id == st.page } ?: MENU[0]
    Block(st, 1) {
        Box(Modifier.fillMaxSize()) {
            when (n.id) {
                "config" -> Settings(st, locked)
                "inicio" -> HomePage(st, locked)
                "pesquisa" -> SearchPage(st, locked)
                "lista" -> MyListPage(st, locked)
                "lancamentos" -> ReleasesPage(st, locked)
                "aovivo" -> TabPage(st, "aovivo", "TV ao vivo", n.desc, locked, hero = true)
                "musicas" -> TabPage(st, "musicas", "Músicas", "Rádios e canais de música da sua lista, e os seus apps de música.", locked, hero = false, top = { MusicApps(st, locked) })
                "streaming" -> StreamingPage(st, locked)
                "assistente" -> AssistantPage(st, locked)
                "filmes", "series", "novelas", "infantil", "documentarios", "esportes", "noticias" -> TabPage(st, n.id, n.label, n.desc, locked)
                else -> Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 56.dp)) {
                    T(n.label, 28, C.fg, FontWeight.ExtraBold)
                    Spacer(Modifier.height(6.dp))
                    T(n.desc, 13, C.muted, lineHeight = 19, modifier = Modifier.width(560.dp))
                    Spacer(Modifier.height(20.dp))
                    InfoCard("Em construção", n.soon, "próxima entrega", locked, wide = true)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, tag: String, locked: Boolean, wide: Boolean = false) {
    var f by remember { mutableStateOf(false) }
    Column(
        Modifier.width(if (wide) 712.dp else 226.dp)
            .graphicsLayer { scaleX = if (f) 1.03f else 1f; scaleY = scaleX }
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(C.panel2, C.panel)))
            .border(if (f) 2.dp else 1.dp, if (f) C.a1 else C.line, RoundedCornerShape(14.dp))
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val n = it.isFocused; if (n && !f) Sfx.tick(); f = n }
            .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        T(tag.uppercase(), 10, if (tag == "pronto") C.ok else C.a1, FontWeight.Bold)
        T(title, 15, C.fg, FontWeight.Bold)
        T(body, 12, C.muted, lineHeight = 17)
    }
}

// ---------------- configurações ----------------
class Cfg(val id: String, val name: String, val soon: Int, val desc: String)

private val CFG = listOf(
    Cfg("contas", "Contas IPTV", 1, "Até 3 contas com URL do servidor, usuário e senha (padrão Xtream Codes). A senha fica guardada criptografada no aparelho."),
    Cfg("listas", "Listas e formatos", 1, ""),
    Cfg("tmdb", "Capas e sinopses (TMDB)", 1, ""),
    Cfg("stream", "Transmissão de rede", 1, "Como o \"Abrir fluxo de rede\" do VLC: 5 endereços salvos, com cache de 300, 1.000 ou 3.000 ms."),
    Cfg("web", "Endereços de navegador", 1, "Até 5 sites para abrir direto no navegador da TV."),
    Cfg("proxy", "Proxy", 1, "HTTP, HTTPS ou SOCKS5, com endereço, porta, usuário e senha."),
    Cfg("video", "Qualidade de vídeo", 1, "Baixa 480p, Média 720p, Alta 1080p ou Ultra 2160p, com a IA segurando a imagem quando a internet oscila."),
    Cfg("ai", "IA de imagem", 1, "Desligada, Automática ou Manual (baixa, média, alta, ultra), dividindo o trabalho entre GPU e CPU."),
    Cfg("audio", "IA de áudio", 1, "Automático, Diálogos nítidos, Cinema e Modo noturno, como no AstroLume."),
    Cfg("tema", "Tema do app", 1, "Cosmos, Aurora, Nebulosa e Solar."),
    Cfg("clima", "Clima e cidade", 1, "Cidade e clima no topo da tela, ao lado do relógio."),
    Cfg("som", "Som do aplicativo", 1, ""),
    Cfg("offline", "Canais fora do ar", 1, "Marca ou esconde os canais sem sinal."),
    Cfg("rede", "Rede e internet", 1, "Situação da conexão e teste de velocidade."),
    Cfg("controle", "Mapear controle remoto", 1, "Escolha a tecla do controle para OK, Voltar, Play/Pausa e Favoritar."),
    Cfg("historico", "Limpar histórico", 1, ""),
    Cfg("adulto", "Conteúdo adulto", 1, "Bloqueio com PIN."),
    Cfg("update", "Atualização do app", 1, ""),
    Cfg("sobre", "Sobre o aparelho", 1, ""),
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun Settings(st: AppState, locked: Boolean) {
    var sel by remember { mutableStateOf(st.cfgSel) }
    LaunchedEffect(sel) { st.cfgSel = sel }
    Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 56.dp)) {
        Block(st, 1) { T("Configurações", 30, C.fg, FontWeight.ExtraBold) }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Block(st, 2) {
                // ao voltar do painel para a lista, o foco vai para a opção aberta (não para a mais próxima na altura)
                val reqs = remember { CFG.associate { it.id to FocusRequester() } }
                Column(Modifier.width(230.dp).fillMaxHeight().focusProperties { enter = { reqs[sel] ?: FocusRequester.Default } }.verticalScroll(rememberScrollState()).focusGroup().padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    CFG.forEach { c -> Box(Modifier.focusRequester(reqs.getValue(c.id))) { CfgItem(c, sel == c.id, locked) { sel = c.id } } }
                }
            }
            Block(st, 3) {
                Column(
                    Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = 18.dp).clip(RoundedCornerShape(16.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState()).padding(20.dp).focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) { CfgPanel(st, CFG.first { it.id == sel }, locked) }
            }
        }
    }
}

@Composable
private fun CfgItem(c: Cfg, cur: Boolean, locked: Boolean, onSel: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    LaunchedEffect(f) { if (f) onSel() }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (f) Brush.horizontalGradient(listOf(C.a1, C.a2)) else Brush.horizontalGradient(listOf(if (cur) C.panel else Color.Transparent, if (cur) C.panel else Color.Transparent)))
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val n = it.isFocused; if (n && !f) Sfx.tick(); f = n }
            .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); onSel() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        T(c.name, 13, if (f) Color(0xFF050816) else C.fg, FontWeight.Bold, maxLines = 1)
        T(if (c.soon == 1) "Disponível" else "Entrega ${c.soon}", 11, if (f) Color(0xFF0A1030) else if (c.soon == 1) C.ok else C.dim, maxLines = 1)
    }
}

@Composable
fun Btn(label: String, on: Boolean = false, locked: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier.graphicsLayer { scaleX = if (f) 1.06f else 1f; scaleY = scaleX }
            .clip(RoundedCornerShape(11.dp))
            .background(if (f) Color.White else if (primary) C.a1.copy(alpha = .3f) else if (on) C.a1.copy(alpha = .22f) else C.panel2)
            .border(1.dp, if (on && !f) C.a1 else Color.Transparent, RoundedCornerShape(11.dp))
            .focusProperties { canFocus = !locked }
            .onFocusChanged { val n = it.isFocused; if (n && !f) Sfx.tick(); f = n }
            .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) { T((if (on) "● " else "") + label, 13, if (f) Color(0xFF050816) else C.fg, FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun Seg(label: String, opts: List<Pair<Int, String>>, cur: Int, locked: Boolean, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        T(label.uppercase(), 11, C.muted, FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { opts.forEach { (v, t) -> Btn(t, cur == v, locked) { onPick(v) } } }
    }
}

@Composable
fun KV(rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.bg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { (k, v) ->
            Row { T(k, 13, C.muted, modifier = Modifier.width(150.dp)); T(v, 13, C.fg, FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun CfgPanel(st: AppState, c: Cfg, locked: Boolean) {
    val ctx = LocalContext.current
    T(c.name, 18, C.fg, FontWeight.Bold)
    when (c.id) {
        "som" -> {
            var snd by remember { mutableStateOf(st.prefs.sounds) }
            var vol by remember { mutableIntStateOf(st.prefs.volume) }
            var intro by remember { mutableStateOf(st.prefs.intro) }
            T("Sons de navegação, confirmação e a abertura com o buraco negro.", 13, C.muted)
            Seg("Sons do app", listOf(1 to "Ligado", 0 to "Desligado"), if (snd) 1 else 0, locked) { snd = it == 1; st.prefs.sounds = snd; Sfx.enabled = snd }
            Seg("Volume dos sons", listOf(1 to "Baixo", 2 to "Médio", 3 to "Alto"), vol, locked) { vol = it; st.prefs.volume = it; Sfx.setVolume(it); Sfx.ok() }
            Seg("Abertura animada", listOf(1 to "Ligada", 0 to "Desligada"), if (intro) 1 else 0, locked) { intro = it == 1; st.prefs.intro = intro }
        }
        "update" -> UpdatePanel(st, locked)
        "contas" -> AccountsPanel(st, locked)
        "listas" -> ListsPanel(st, locked)
        "adulto" -> AdultPanel(st, locked)
        "sobre" -> {
            KV(deviceInfo(ctx))
            var err by remember { mutableStateOf(CrashLog.last) }
            if (err.isNotBlank()) {
                LaunchedEffect(Unit) { CrashLog.markSeen(ctx) }
                T("ÚLTIMO ERRO (o app fechou sozinho)", 10, C.bad, FontWeight.Bold)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.bg).padding(12.dp)) { T(err.lines().take(14).joinToString("\n"), 10, C.muted, lineHeight = 13) }
                Btn("Apagar registro de erro", locked = locked) { CrashLog.clear(); err = "" }
            }
        }
        "stream" -> StreamPanel(st, locked)
        "web" -> WebPanel(st, locked)
        "proxy" -> ProxyPanel(st, locked)
        "video" -> VideoPanel(st, locked)
        "offline" -> OfflinePanel(st, locked)
        "rede" -> NetPanel(st, locked)
        "tmdb" -> TmdbPanel(st, locked)
        "historico" -> HistoryPanel(st, locked)
        "ai" -> AiImagePanel(st, locked)
        "audio" -> AiAudioPanel(st, locked)
        "tema" -> ThemePanel(st, locked)
        "clima" -> WeatherPanel(st, locked)
        "controle" -> RemotePanel(st, locked)
        else -> {
            T(c.desc, 13, C.muted, lineHeight = 19)
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(C.a2.copy(alpha = .18f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                T("Chega na entrega ${c.soon}", 12, C.a2, FontWeight.Bold)
            }
            T("Você pode ver e testar como vai ficar no simulador do LumeStarTV.", 12, C.dim)
        }
    }
}

@Composable
private fun UpdatePanel(st: AppState, locked: Boolean) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("") }
    var prog by remember { mutableIntStateOf(-2) }
    val cur = Updater.currentVersion(ctx)
    val u = st.update
    KV(listOf("Versão instalada" to cur, "Última publicada" to (u?.remote?.ifEmpty { "—" } ?: "—"), "Origem" to "GitHub · sergiobarbozajr/lumestartv"))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn("Procurar atualização", locked = locked, primary = true) {
            status = "Procurando no GitHub…"
            Updater.check(ctx) { r ->
                st.update = r
                status = when (r.status) {
                    "new" -> "Nova versão ${r.remote} disponível."
                    "same" -> "Você já tem a versão mais recente."
                    "none" -> "Ainda não há versão publicada."
                    else -> "Não consegui falar com o GitHub. Confira a internet e tente de novo."
                }
            }
        }
        if (u?.status == "new" && u.apkUrl != null) {
            Btn(if (prog in 0..99) "Baixando… $prog%" else "Baixar e instalar ${u.remote}", locked = locked) {
                if (prog in 0..99) return@Btn
                prog = 0
                Updater.downloadAndInstall(ctx, u.apkUrl) { p ->
                    prog = p
                    status = when { p < 0 -> "O download falhou. Tente de novo."; p >= 100 -> "Download pronto. Confirme a instalação na tela da TV."; else -> status }
                }
            }
        }
    }
    if (status.isNotEmpty()) T(status, 13, if (status.startsWith("Nova") || status.startsWith("Download")) C.ok else C.muted, FontWeight.SemiBold)
    if (!Updater.canInstall(ctx)) T("Na primeira atualização a TV pede para liberar \"instalar apps desconhecidos\" para o LumeStarTV. Libere e volte aqui.", 12, C.warn, lineHeight = 17)
}

private fun deviceInfo(ctx: Context): List<Pair<String, String>> {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val dm = DisplayMetrics()
    @Suppress("DEPRECATION")
    (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
    val tv = ctx.packageManager.hasSystemFeature("android.software.leanback")
    return listOf(
        "Aparelho" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        "Tipo" to if (tv) "TV (Android TV / Google TV)" else "Celular ou tablet",
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Processador" to "${Runtime.getRuntime().availableProcessors()} núcleos · ${Build.HARDWARE}",
        "Memória" to "%.1f GB".format(Locale("pt", "BR"), mi.totalMem / 1073741824.0),
        "Tela" to "${dm.widthPixels} × ${dm.heightPixels}",
        "Aplicativo" to "LumeStarTV ${Updater.currentVersion(ctx)}",
    )
}

// ---------------- sair e avisos ----------------
@Composable
private fun ExitDialog(st: AppState, finish: () -> Unit) {
    val stay = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(50); try { stay.requestFocus() } catch (e: Exception) {} }
    Box(Modifier.fillMaxSize().background(Color(0xBF02040C)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(420.dp).clip(RoundedCornerShape(20.dp)).background(C.panel).border(1.dp, C.line, RoundedCornerShape(20.dp)).padding(26.dp).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            T("SAIR", 11, C.a1, FontWeight.Bold)
            T("Sair do LumeStarTV?", 22, C.fg, FontWeight.ExtraBold)
            T("O que você estava assistindo fica salvo em Continuar assistindo.", 13, C.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.focusRequester(stay)) { Btn("Ficar", locked = false) { st.closeExit() } }
                Btn("Sair do aplicativo", locked = false, primary = true) { Sfx.stopLong(); finish() }
            }
        }
    }
}

@Composable
private fun Toast(st: AppState) {
    val msg = st.toast ?: return
    val id = st.toastId
    LaunchedEffect(id) { delay(4500); if (st.toastId == id) st.toast = null }
    Box(Modifier.fillMaxSize().padding(bottom = 26.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.wrapContentSize().clip(RoundedCornerShape(30.dp)).background(C.fg).padding(horizontal = 20.dp, vertical = 10.dp)) {
            T(msg, 13, Color(0xFF050816), FontWeight.Bold)
        }
    }
}
