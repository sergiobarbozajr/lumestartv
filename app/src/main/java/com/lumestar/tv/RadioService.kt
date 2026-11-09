package com.lumestar.tv

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C as MC
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Rádio em segundo plano: continua tocando fora do app (como o Spotify), com controle na notificação,
 * no controle remoto e no fone. Só a rádio usa isto; vídeo e canal param ao sair do app.
 */
@UnstableApi
class RadioService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val p = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(MC.USAGE_MEDIA).setContentType(MC.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        session = MediaSession.Builder(this, p).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
