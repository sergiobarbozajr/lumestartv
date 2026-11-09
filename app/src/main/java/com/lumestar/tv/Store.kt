package com.lumestar.tv

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Senhas guardadas criptografadas com uma chave do próprio aparelho (Android Keystore).
 * Android 6+: chave AES gerada direto no Keystore.
 * Android 5.x: o Keystore ainda não gera AES, então uma chave RSA do Keystore "embrulha"
 * uma chave AES aleatória, guardada embrulhada nas preferências privadas do app.
 */
object Secret {
    private const val ALIAS = "lumestar-senhas"
    private const val ALIAS_RSA = "lumestar-senhas-rsa"
    private var app: Context? = null
    @Volatile private var cached: SecretKey? = null

    fun init(c: Context) { app = c.applicationContext }

    private val legacy get() = Build.VERSION.SDK_INT < 23

    @Synchronized
    private fun key(): SecretKey = cached ?: (if (Build.VERSION.SDK_INT < 23) keyLegacy() else keyModern()).also { cached = it }

    @androidx.annotation.RequiresApi(23)
    private fun keyModern(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }

    @Suppress("DEPRECATION")
    private fun keyLegacy(): SecretKey {
        val c = app ?: throw IllegalStateException("Secret.init não foi chamado")
        val sp = c.getSharedPreferences("lumestar-chave", Context.MODE_PRIVATE)
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS_RSA)) {
            val ini = java.util.Calendar.getInstance()
            val fim = java.util.Calendar.getInstance().apply { add(java.util.Calendar.YEAR, 30) }
            val spec = android.security.KeyPairGeneratorSpec.Builder(c).setAlias(ALIAS_RSA)
                .setSubject(javax.security.auth.x500.X500Principal("CN=LumeStarTV"))
                .setSerialNumber(java.math.BigInteger.ONE).setStartDate(ini.time).setEndDate(fim.time).build()
            java.security.KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
            sp.edit().remove("aesEmbrulhada").commit() // chave RSA nova: a AES antiga não abre mais
        }
        val par = ks.getEntry(ALIAS_RSA, null) as KeyStore.PrivateKeyEntry
        sp.getString("aesEmbrulhada", null)?.let { w ->
            val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL")
            rsa.init(Cipher.DECRYPT_MODE, par.privateKey)
            return SecretKeySpec(rsa.doFinal(Base64.decode(w, Base64.NO_WRAP)), "AES")
        }
        val raw = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL")
        rsa.init(Cipher.ENCRYPT_MODE, par.certificate.publicKey)
        sp.edit().putString("aesEmbrulhada", Base64.encodeToString(rsa.doFinal(raw), Base64.NO_WRAP)).commit()
        return SecretKeySpec(raw, "AES")
    }

    fun enc(plain: String): String = try {
        if (plain.isEmpty()) "" else {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            // Keystore (6+) gera o IV sozinho; com chave de software (5.x) o IV aleatório é nosso
            if (legacy) c.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }))
            else c.init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        }
    } catch (e: Throwable) { "" }

    fun dec(s: String): String = try {
        if (!s.contains(":")) "" else {
            val (iv, data) = s.split(":", limit = 2)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)))
        }
    } catch (e: Throwable) { "" }
}

class Account(var name: String = "", var url: String = "", var user: String = "", var pass: String = "", var info: String = "", var ok: Boolean = false) {
    val filled get() = url.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
}
class ListSrc(var name: String, var url: String, var info: String = "")
class Progress(val pos: Long, val dur: Long, val at: Long)

/** Tudo o que o app guarda do usuário: contas, listas, favoritos, progresso e vistos. */
object Store {
    private lateinit var sp: android.content.SharedPreferences
    /** Muda sempre que favoritos, vistos ou progresso mudam (as telas se atualizam sozinhas). */
    var rev by mutableIntStateOf(0)

    val accounts = Array(3) { Account() }
    var activeAcc = -1
    val lists = ArrayList<ListSrc>()
    val favs = LinkedHashSet<String>()
    val seen = ArrayList<String>()
    val progress = HashMap<String, Progress>()
    var adultPin = "0000"
    var adultOpen = false

    fun init(c: Context) {
        Secret.init(c)
        sp = c.getSharedPreferences("lumestar-dados", Context.MODE_PRIVATE)
        try {
            val a = JSONArray(sp.getString("contas", "[]"))
            for (i in 0 until minOf(3, a.length())) {
                val o = a.getJSONObject(i)
                accounts[i] = Account(o.optString("name"), o.optString("url"), o.optString("user"), Secret.dec(o.optString("pass")), o.optString("info"), o.optBoolean("ok"))
            }
        } catch (e: Exception) {}
        activeAcc = sp.getInt("contaAtiva", -1)
        try {
            val a = JSONArray(sp.getString("listas", "[]"))
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); val u = o.optString("url"); if (!hasList(u)) lists.add(ListSrc(o.optString("name"), u, o.optString("info"))) else removedDup++ }
            if (removedDup > 0) saveLists()
        } catch (e: Exception) {}
        favs.addAll(sp.getString("favoritos", "")!!.split("\n").filter { it.isNotEmpty() })
        seen.addAll(sp.getString("vistos", "")!!.split("\n").filter { it.isNotEmpty() })
        try {
            val o = JSONObject(sp.getString("progresso", "{}") ?: "{}")
            o.keys().forEach { k -> val p = o.getJSONArray(k); progress[k] = Progress(p.getLong(0), p.getLong(1), p.getLong(2)) }
        } catch (e: Exception) {}
        adultPin = sp.getString("pinAdulto", "0000") ?: "0000"
    }

    fun saveAccounts() {
        val a = JSONArray()
        accounts.forEach { x -> a.put(JSONObject().put("name", x.name).put("url", x.url).put("user", x.user).put("pass", Secret.enc(x.pass)).put("info", x.info).put("ok", x.ok)) }
        sp.edit().putString("contas", a.toString()).putInt("contaAtiva", activeAcc).apply()
    }

    /** Quantas listas repetidas foram tiradas ao abrir o app (para avisar uma vez). */
    var removedDup = 0

    /** Endereço "limpo" para comparar: sem espaços, sem barra no fim, protocolo e servidor em minúsculas, sem http/https de diferença. */
    fun normUrl(u: String): String {
        var x = u.trim()
        val i = x.indexOf("://")
        if (i > 0) { val rest = x.substring(i + 3); val slash = rest.indexOf('/').let { if (it < 0) rest.length else it }; x = rest.substring(0, slash).lowercase().removePrefix("www.") + rest.substring(slash) }
        x = x.removeSuffix("/").removeSuffix("?")
        return x
    }

    /** Listas que já vêm de fábrica (públicas e legais, atualizadas toda semana no GitHub). */
    val NATIVAS = listOf(
        "Canais abertos e gratuitos do Brasil (LumeStarTV)" to "https://raw.githubusercontent.com/sergiobarbozajr/Playlist-canais/main/canais-brasil.m3u8",
        "Filmes de domínio público (LumeStarTV)" to "https://raw.githubusercontent.com/sergiobarbozajr/Playlist-canais/main/filmes-dominio-publico.m3u8",
        "Séries de domínio público (LumeStarTV)" to "https://raw.githubusercontent.com/sergiobarbozajr/Playlist-canais/main/series-dominio-publico.m3u8",
        "Cinema em português até 1955 (LumeStarTV)" to "https://raw.githubusercontent.com/sergiobarbozajr/Playlist-canais/main/portugues.m3u8",
        "Filmes e séries livres de 1980 para cá (LumeStarTV)" to "https://raw.githubusercontent.com/sergiobarbozajr/Playlist-canais/main/livres-1980.m3u8",
    )

    /** Na primeira abertura, coloca os canais abertos do Brasil. Se o usuário remover, não volta sozinho. */
    /**
     * De fábrica: canais abertos do Brasil e filmes/séries livres de 1980 para cá.
     * Os clássicos antigos (até 1955) ficam como opção em Listas e formatos; se foram postos sozinhos
     * pela 0.9.0, saem uma vez (quem quiser adiciona de novo pelo botão).
     */
    fun listasDeFabrica() {
        val ed = sp.edit()
        if (!sp.getBoolean("nativasPostas", false)) { addList(NATIVAS[0].first, NATIVAS[0].second); ed.putBoolean("nativasPostas", true) }
        if (!sp.getBoolean("nativasLivres", false)) { addList(NATIVAS[4].first, NATIVAS[4].second); ed.putBoolean("nativasLivres", true) }
        if (sp.getBoolean("nativasPt", false) && !sp.getBoolean("nativasPtTirada", false)) {
            val n = normUrl(NATIVAS[3].second)
            if (lists.removeAll { normUrl(it.url) == n }) saveLists()
            ed.putBoolean("nativasPtTirada", true)
        }
        ed.apply()
    }

    fun hasList(u: String): Boolean { val n = normUrl(u); return n.isNotEmpty() && lists.any { normUrl(it.url) == n } }

    /** Adiciona a lista se ainda não existir. Devolve false quando já estava salva. */
    fun addList(name: String, url: String): Boolean {
        if (hasList(url)) return false
        lists.add(ListSrc(name, url.trim())); saveLists(); return true
    }

    fun saveLists() {
        val a = JSONArray()
        lists.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url).put("info", it.info)) }
        sp.edit().putString("listas", a.toString()).apply()
    }

    fun toggleFav(id: String): Boolean {
        val on = if (favs.contains(id)) { favs.remove(id); false } else { favs.add(id); true }
        sp.edit().putString("favoritos", favs.joinToString("\n")).apply()
        rev++
        return on
    }

    fun setProgress(id: String, pos: Long, dur: Long) {
        if (dur <= 0) return
        if (pos >= dur * 0.95) {
            progress.remove(id)
            seen.remove(id); seen.add(0, id)
            while (seen.size > 200) seen.removeAt(seen.size - 1)
            sp.edit().putString("vistos", seen.joinToString("\n")).apply()
        } else if (pos > 15_000) {
            progress[id] = Progress(pos, dur, System.currentTimeMillis())
        } else return
        val o = JSONObject()
        progress.entries.sortedByDescending { it.value.at }.take(100).forEach { (k, p) -> o.put(k, JSONArray().put(p.pos).put(p.dur).put(p.at)) }
        sp.edit().putString("progresso", o.toString()).apply()
        rev++
    }

    private fun saveProgress() {
        val o = JSONObject()
        progress.entries.sortedByDescending { it.value.at }.take(100).forEach { (k, p) -> o.put(k, JSONArray().put(p.pos).put(p.dur).put(p.at)) }
        sp.edit().putString("progresso", o.toString()).apply()
    }
    private fun saveSeen() = sp.edit().putString("vistos", seen.joinToString("\n")).apply()

    /** Limpa a fileira "Visto". */
    fun clearSeen() { seen.clear(); saveSeen(); rev++ }
    /** Limpa a fileira "Continuar assistindo" (os vídeos voltam a começar do início). */
    fun clearProgress() { progress.clear(); saveProgress(); rev++ }
    /** Tira um título só do histórico (visto e continuar). */
    fun forget(id: String) { seen.remove(id); progress.remove(id); saveSeen(); saveProgress(); rev++ }

    fun setPin(p: String) { adultPin = p; sp.edit().putString("pinAdulto", p).apply() }
}
