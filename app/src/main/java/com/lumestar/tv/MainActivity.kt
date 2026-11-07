package com.lumestar.tv

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.init(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        // atalho de teste: --es modo tv | celular
        phone = when (intent?.getStringExtra("modo")) { "tv" -> false; "celular" -> true; else -> isPhone(this) }
        if (phone) {
            // celular: em pé, com as barras do sistema; o conteúdo desenha por baixo delas (padding nas telas)
            requestedOrientation = if (isTablet(this)) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        } else {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        val prefs = Prefs(this)
        Store.init(this)
        Secret.init(this) // antes de qualquer senha ser lida (proxy, TMDB, contas)
        PP.init(this)
        Tmdb.init(this)
        // diagnóstico de imagem: uma vez por versão, em segundo plano, 20 s depois de abrir (fora da abertura)
        Perf.init(this, prefs.perf)
        if (Perf.lite) {
            // aparelho fraco: não roda o teste pesado de GPU; IA de imagem fica desligada (nível 0)
            GpuProbe.result = GpuProbe.Result("modo leve", "", 99f, 99f, 99f)
        } else {
            GpuProbe.load(PP.sp, BuildConfig.VERSION_NAME)
            if (GpuProbe.result == null) android.os.Handler(mainLooper).postDelayed({ GpuProbe.run(PP.sp, BuildConfig.VERSION_NAME) }, 20_000)
        }
        KeyMap.init(prefs)
        CustomTheme.init(this)
        // atalhos de teste: --es tema <id>, --ez movimento true
        intent?.getStringExtra("tema")?.let { t -> if (THEMES.containsKey(t)) prefs.theme = t }
        C.pal = THEMES[prefs.theme] ?: C.pal
        Catalog.rev.let { } // cria o catálogo (e seus estados) na linha da tela, antes da linha de fundo
        OfflineScan.running.let { }
        kotlin.concurrent.thread(name = "catalogo-cache") { Catalog.loadCache(applicationContext) }
        Sfx.enabled = prefs.sounds
        Sfx.setVolume(prefs.volume)
        Sfx.warmUp()
        if (!Perf.lite) Tracks.prepare(this) // trilhas da abertura só fazem sentido com a abertura
        createdAt = android.os.SystemClock.uptimeMillis()
        // atalhos para teste (adb): --ez sem_abertura true --es pagina config --es abrir <endereço>
        val args = LaunchArgs((intent?.getBooleanExtra("sem_abertura", false) ?: false) || Perf.lite, intent?.getStringExtra("pagina"), intent?.getStringExtra("abrir"))
        setContent { if (phone) MobileApp(prefs, args) { finish() } else LumeApp(prefs, args) { finish() } }
    }

    private var createdAt = 0L
    private var phone = false
    private val pressed = HashSet<Int>()
    private val swallow = HashSet<Int>()

    /**
     * Ao abrir pelo ícone, o "soltar" do OK apertado na tela da TV chega aqui.
     * Esse soltar sem um apertar correspondente é ignorado, assim como toques no primeiro instante.
     * Durante a abertura, qualquer tecla (apertada e solta aqui) pula para o buraco de minhoca.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (android.os.SystemClock.uptimeMillis() - createdAt < 600) return true
            pressed.add(code)
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (!pressed.remove(code)) return true
        }
        // controle remapeado (Configurações › Mapear controle remoto)
        if (event.action == KeyEvent.ACTION_UP && swallow.remove(code)) return true
        val mapped = KeyMap.handle(code, event.action == KeyEvent.ACTION_DOWN)
        if (mapped == null) { if (event.action == KeyEvent.ACTION_DOWN) swallow.add(code); return true }
        if (mapped != code) return safeDispatch(KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source))
        val skip = IntroSkip.handler
        if (skip != null) {
            if (event.action == KeyEvent.ACTION_UP) skip()
            return true
        }
        return safeDispatch(event)
    }

    /**
     * Falha conhecida do Compose 1.6: ao navegar rápido numa fileira longa, a busca de foco consulta uma capa
     * que acabou de sair da memória e lança IllegalStateException ("...only valid when isAttached is true").
     * Em vez de fechar o app, esse aperto de tecla é ignorado.
     */
    private fun safeDispatch(event: KeyEvent): Boolean = try {
        super.dispatchKeyEvent(event)
    } catch (e: IllegalStateException) {
        val m = e.message ?: ""
        if (m.contains("isAttached") || m.contains("LayoutCoordinate") || e.stackTrace.any { it.className.contains("compose.ui.focus") }) {
            android.util.Log.w("LumeStar", "busca de foco ignorada: $m"); true
        } else throw e
    }

    override fun onPause() {
        super.onPause()
        Sfx.stopLong()
    }
}
