package com.lumestar.tv

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private class IntP(val k: String, val d: Int) : ReadWriteProperty<Any, Int> {
    override fun getValue(thisRef: Any, property: KProperty<*>) = PP.sp.getInt(k, d)
    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int) { PP.sp.edit().putInt(k, value).apply(); PP.rev++ }
}
private class StrP(val k: String, val d: String) : ReadWriteProperty<Any, String> {
    override fun getValue(thisRef: Any, property: KProperty<*>) = PP.sp.getString(k, d) ?: d
    override fun setValue(thisRef: Any, property: KProperty<*>, value: String) { PP.sp.edit().putString(k, value).apply(); PP.rev++ }
}
private class BoolP(val k: String, val d: Boolean) : ReadWriteProperty<Any, Boolean> {
    override fun getValue(thisRef: Any, property: KProperty<*>) = PP.sp.getBoolean(k, d)
    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) { PP.sp.edit().putBoolean(k, value).apply(); PP.rev++ }
}

class Slot(var name: String = "", var url: String = "", var cache: Int = 1000)

/** Ajustes do reprodutor e das configurações da entrega 3, guardados no aparelho. */
object PP {
    lateinit var sp: SharedPreferences
    var rev by mutableIntStateOf(0)

    fun init(c: Context) {
        sp = c.getSharedPreferences("lumestar-reprodutor", Context.MODE_PRIVATE)
        fun slots(key: String): List<Slot> = try {
            val a = JSONArray(sp.getString(key, "[]")); List(5) { i -> a.optJSONObject(i)?.let { Slot(it.optString("name"), it.optString("url"), it.optInt("cache", 1000)) } ?: Slot() }
        } catch (e: Exception) { List(5) { Slot() } }
        streams = slots("fluxos").toMutableList(); webs = slots("sites").toMutableList()
        offline = (sp.getString("foraDoAr", "") ?: "").split("\n").filter { it.isNotEmpty() }.toMutableSet()
        proxyPass = Secret.dec(sp.getString("proxySenha", "") ?: "")
        applyProxyAuth()
    }

    // imagem
    var preset by StrP("imgModo", "padrao")
    var bright by IntP("brilho", 0)
    var contrast by IntP("contraste", 0)
    var sat by IntP("cor", 0)
    var enhance by StrP("realce", "auto")      // off | auto | baixa | media | alta | ultra
    var quality by StrP("qualidade", "auto")
    var aiUp by StrP("reforcoIa", "auto")      // auto | 1440 | 1080 | off
    var matchHz by BoolP("frequenciaTela", true)   // "Suavização 100 Hz": entrega a cadência certa para o MEMC da TV
    var radioCidade by StrP("radioCidade", "")        // "Macaé, Rio de Janeiro"
    var radioEstado by StrP("radioEstado", "")
    var radioPos by StrP("radioPos", "")              // "lat,lon"
    var controleCelular by BoolP("controleCelular", true)  // celular como controle remoto (com código de pareamento)
    var videoMode by StrP("videoDireto", "auto")   // auto | direto | classico
    var diretoFalhou by BoolP("diretoFalhou", false)
    var audioPcm by BoolP("audioPcm", false)        // o som "direto" (AC3/E-AC3 pelo sistema) falhou nesta TV: usa PCM
    var audioMode by StrP("saidaSom", "auto")       // auto | direto | pcm
    /** Som decodificado no app (PCM) em vez de mandar AC3/E-AC3 direto para a TV/soundbar. */
    fun somPcm() = when (audioMode) { "pcm" -> true; "direto" -> false; else -> audioPcm }
    var testeRes by StrP("testeDesempenho", "")
    var enviarErros by BoolP("enviarErros", true)
    var colorStyle by StrP("estiloCor", "fiel")    // fiel | vivido | cinema
    var aiSkin by BoolP("iaPele", true)
    var aiNature by BoolP("iaPaisagem", true)
    var aiDepth by BoolP("iaProfundidade", true)
    var aiFidelity by BoolP("iaFidelidade", true)
    var aiSharp by BoolP("nitidezLeve", true)
    var fullQuality by BoolP("qualidadeCheia", true)
    var avMs by IntP("sincroniaMs", 0)          // + atrasa o som, − adianta
    var panel by StrP("painel", "auto")        // auto | hva | outro   // auto | 480 | 720 | 1080 | 2160
    // tela
    var fit by IntP("telaModo", 0)              // 0 original · 1 preencher · 2 zoom
    var fitV by IntP("telaVertical", 0)
    var fitH by IntP("telaHorizontal", 0)
    var faixasAuto by BoolP("faixasAuto", true)     // mede e tira as faixas pretas sozinho em cada vídeo
    // som
    var audioAi by StrP("iaAudio", "auto")     // off | auto | dialogo | cinema | noturno
    var eq by StrP("equalizador", "padrao")
    var bass by IntP("graves", 0)
    var treble by IntP("agudos", 0)
    var level by BoolP("nivelar", true)
    // legendas
    var subSize by IntP("legTamanho", 1)
    var subColor by IntP("legCor", 0)
    var subBg by IntP("legFundo", 1)
    // rede
    var cacheMs by IntP("cache", 1000)
    var proxyType by StrP("proxyTipo", "off")  // off | http | socks
    var proxyHost by StrP("proxyEndereco", "")
    var proxyPort by StrP("proxyPorta", "")
    var proxyUser by StrP("proxyUsuario", "")
    var proxyPass: String = ""
        set(v) { field = v; sp.edit().putString("proxySenha", Secret.enc(v)).apply(); applyProxyAuth(); rev++ }
    var offlineMode by StrP("foraDoArModo", "marcar") // marcar | ocultar

    var streams: MutableList<Slot> = MutableList(5) { Slot() }
    var webs: MutableList<Slot> = MutableList(5) { Slot() }
    var offline: MutableSet<String> = mutableSetOf()

    fun saveSlots() {
        fun arr(l: List<Slot>) = JSONArray().apply { l.forEach { put(JSONObject().put("name", it.name).put("url", it.url).put("cache", it.cache)) } }.toString()
        sp.edit().putString("fluxos", arr(streams)).putString("sites", arr(webs)).apply(); rev++
    }

    fun saveOffline() { sp.edit().putString("foraDoAr", offline.joinToString("\n")).apply(); rev++ }

    /** Proxy em uso (ou sem proxy). Vale para listas, contas, notícias e reprodução. */
    fun proxy(): Proxy {
        val port = proxyPort.trim().toIntOrNull() ?: return Proxy.NO_PROXY
        if (proxyType == "off" || proxyHost.isBlank()) return Proxy.NO_PROXY
        return Proxy(if (proxyType == "socks") Proxy.Type.SOCKS else Proxy.Type.HTTP, InetSocketAddress.createUnresolved(proxyHost.trim(), port))
    }

    private fun applyProxyAuth() {
        val u = proxyUser; val p = proxyPass
        if (u.isBlank()) { java.net.Authenticator.setDefault(null); return }
        java.net.Authenticator.setDefault(object : java.net.Authenticator() {
            override fun getPasswordAuthentication(): java.net.PasswordAuthentication? =
                if (requestorType == RequestorType.PROXY) java.net.PasswordAuthentication(u, p.toCharArray()) else null
        })
    }
}
