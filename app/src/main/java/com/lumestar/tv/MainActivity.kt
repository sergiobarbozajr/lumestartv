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
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val prefs = Prefs(this)
        Store.init(this)
        PP.init(this)
        Tmdb.init(this)
        // diagnóstico de imagem: uma vez por versão, em segundo plano, 20 s depois de abrir (fora da abertura)
        GpuProbe.load(PP.sp, BuildConfig.VERSION_NAME)
        if (GpuProbe.result == null) android.os.Handler(mainLooper).postDelayed({ GpuProbe.run(PP.sp, BuildConfig.VERSION_NAME) }, 20_000)
        KeyMap.init(prefs)
        C.pal = THEMES[prefs.theme] ?: C.pal
        Catalog.rev.let { } // cria o catálogo (e seus estados) na linha da tela, antes da linha de fundo
        OfflineScan.running.let { }
        kotlin.concurrent.thread(name = "catalogo-cache") { Catalog.loadCache(applicationContext) }
        Sfx.enabled = prefs.sounds
        Sfx.setVolume(prefs.volume)
        Sfx.warmUp()
        Tracks.prepare(this)
        createdAt = android.os.SystemClock.uptimeMillis()
        // atalhos para teste (adb): --ez sem_abertura true --es pagina config --es abrir <endereço>
        val args = LaunchArgs(intent?.getBooleanExtra("sem_abertura", false) ?: false, intent?.getStringExtra("pagina"), intent?.getStringExtra("abrir"))
        setContent { LumeApp(prefs, args) { finish() } }
    }

    private var createdAt = 0L
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
        if (mapped != code) return super.dispatchKeyEvent(KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source))
        val skip = IntroSkip.handler
        if (skip != null) {
            if (event.action == KeyEvent.ACTION_UP) skip()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        Sfx.stopLong()
    }
}
