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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val prefs = Prefs(this)
        Store.init(this)
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
