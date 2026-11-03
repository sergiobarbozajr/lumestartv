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
        Sfx.enabled = prefs.sounds
        Sfx.setVolume(prefs.volume)
        Sfx.warmUp()
        setContent { LumeApp(prefs) { finish() } }
    }

    /** Durante a abertura, qualquer tecla do controle pula direto para o buraco de minhoca. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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
