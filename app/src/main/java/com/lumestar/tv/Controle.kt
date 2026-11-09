package com.lumestar.tv

import android.app.Instrumentation
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * "Celular como controle": o celular (mesmo Wi-Fi) abre http://IP-da-TV:8765/controle e vira controle remoto.
 * Segurança: cada celular precisa digitar uma vez o código de 4 números que aparece na TV
 * (Configurações › Controle pelo celular). Depois ele recebe uma chave guardada no navegador.
 * 5 códigos errados seguidos bloqueiam novas tentativas por 1 minuto. "Desconectar celulares" apaga todas as chaves.
 */
object Controle {
    private lateinit var app: Context
    private val rnd = SecureRandom()
    private val fila = Executors.newSingleThreadExecutor()
    private val inst = Instrumentation()

    /** Código mostrado na TV; muda toda vez que o app abre e ao desconectar os celulares. */
    var codigo = ""; private set
    var conectados by mutableIntStateOf(0); private set
    private var erros = 0
    private var bloqueadoAte = 0L

    fun init(c: Context) {
        app = c.applicationContext
        codigo = novoCodigo()
        conectados = chaves().size
    }

    private fun novoCodigo() = String.format("%04d", rnd.nextInt(10000))
    private fun sp() = app.getSharedPreferences("lumestar-controle", Context.MODE_PRIVATE)
    private fun chaves(): Set<String> = sp().getStringSet("chaves", emptySet()) ?: emptySet()

    private var servidor = false
    /** Com o app na tela e a opção ligada, a TV atende o controle no Wi-Fi. */
    fun ligarServidor() { if (!servidor && PP.controleCelular) { PhoneServer.start(); servidor = true } }
    fun desligarServidor() { if (servidor) { PhoneServer.stop(); servidor = false } }

    fun valida(chave: String?) = !chave.isNullOrBlank() && chave in chaves()

    /** Confere o código; certo = devolve a chave nova do celular. */
    @Synchronized fun parear(cod: String): Pair<String?, String> {
        val agora = System.currentTimeMillis()
        if (agora < bloqueadoAte) return null to "Muitas tentativas. Espere 1 minuto."
        if (cod.trim() != codigo) {
            if (++erros >= 5) { erros = 0; bloqueadoAte = agora + 60_000 }
            return null to "Código errado. Confira na TV."
        }
        erros = 0
        val chave = ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val novas = (chaves() + chave).toList().takeLast(8).toSet() // até 8 celulares
        sp().edit().putStringSet("chaves", novas).apply()
        conectados = novas.size
        return chave to "Celular conectado."
    }

    fun desconectarTodos() {
        sp().edit().remove("chaves").apply(); conectados = 0; codigo = novoCodigo()
    }

    private val TECLAS = mapOf(
        "cima" to KeyEvent.KEYCODE_DPAD_UP, "baixo" to KeyEvent.KEYCODE_DPAD_DOWN,
        "esquerda" to KeyEvent.KEYCODE_DPAD_LEFT, "direita" to KeyEvent.KEYCODE_DPAD_RIGHT,
        "ok" to KeyEvent.KEYCODE_DPAD_CENTER, "voltar" to KeyEvent.KEYCODE_BACK,
        "play" to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "menu" to KeyEvent.KEYCODE_MENU,
        "apagar" to KeyEvent.KEYCODE_DEL, "canalmais" to KeyEvent.KEYCODE_CHANNEL_UP, "canalmenos" to KeyEvent.KEYCODE_CHANNEL_DOWN,
    )

    /** Aperta a tecla dentro do app (como se fosse o controle da TV). */
    fun tecla(nome: String): Boolean {
        when (nome) {
            "volmais", "volmenos", "mudo" -> {
                val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val dir = when (nome) { "volmais" -> AudioManager.ADJUST_RAISE; "volmenos" -> AudioManager.ADJUST_LOWER; else -> AudioManager.ADJUST_TOGGLE_MUTE }
                try { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) } catch (e: Throwable) {}
                return true
            }
        }
        val k = TECLAS[nome] ?: return false
        fila.execute { try { inst.sendKeyDownUpSync(k) } catch (e: Throwable) {} }
        return true
    }

    /** Mensagem na TV (ex.: "Celular conectado"). */
    @Volatile var aviso: ((String) -> Unit)? = null
    /** Teclado da TV aberto: recebe o texto direto (com acentos). */
    @Volatile var noTeclado: ((String) -> Unit)? = null
    /** Sem teclado aberto: o texto vira uma pesquisa. */
    @Volatile var semTeclado: ((String) -> Unit)? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Escreve o texto na TV: no teclado aberto ou, se não houver, na Pesquisa. */
    fun texto(t: String) {
        val limpo = t.replace(Regex("[\\r\\n\\t]"), " ").trim().take(200)
        if (limpo.isEmpty()) return
        main.post { (noTeclado ?: semTeclado)?.invoke(limpo) }
    }

    /** QR Code (preto no branco, com margem) com o endereço que já conecta o celular. */
    fun qr(texto: String): androidx.compose.ui.graphics.ImageBitmap? = try {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(texto, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 2))
        val w = m.width; val h = m.height
        val px = IntArray(w * h) { i -> if (m.get(i % w, i / w)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    } catch (e: Throwable) { null }
}
