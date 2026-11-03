package com.lumestar.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.concurrent.thread

/** Linha de campo: aperte OK para abrir o teclado virtual. */
@Composable
fun FieldRow(st: AppState, label: String, value: String, locked: Boolean, secret: Boolean = false, hint: String = "", onDone: (String) -> Unit) {
    var f by remember { mutableStateOf(false) }
    val req = remember { FocusRequester() }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.bg).padding(start = 14.dp, top = 5.dp, bottom = 5.dp, end = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 13, C.muted, FontWeight.SemiBold, modifier = Modifier.width(130.dp))
        Box(
            Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(C.panel2)
                .border(if (f) 2.dp else 1.dp, if (f) C.a1 else C.line, RoundedCornerShape(10.dp))
                .focusRequester(req).focusProperties { canFocus = !locked }
                .onFocusChanged { val x = it.isFocused; if (x && !f) Sfx.tick(); f = x }
                .clickable(remember { MutableInteractionSource() }, null) { Sfx.ok(); st.kb = KbTarget(label, value, secret, hint, ret = req, done = onDone) }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val shown = if (value.isEmpty()) (hint.ifBlank { "Vazio · OK para digitar" }) else if (secret) "•".repeat(minOf(value.length, 14)) else value
            T(shown, 13, if (value.isEmpty()) C.dim else C.fg, FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
fun AccountsPanel(st: AppState, locked: Boolean) {
    val ctx = LocalContext.current
    var slot by remember { mutableIntStateOf(Store.activeAcc.coerceAtLeast(0)) }
    var tick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    tick.let { }
    val a = Store.accounts[slot]
    T("Até 3 contas no padrão Xtream Codes (URL do servidor + usuário + senha). A conta em uso alimenta TV ao vivo, Filmes, Séries e as demais abas. A senha fica guardada criptografada no aparelho.", 13, C.muted, lineHeight = 19)
    Seg("Contas salvas", (0..2).map { i -> i to (Store.accounts[i].name.ifBlank { "Conta ${i + 1}" } + if (Store.activeAcc == i) " · em uso" else "") }, slot, locked) { slot = it }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldRow(st, "Nome da conta", a.name, locked, hint = "Ex.: Minha IPTV") { a.name = it.trim(); Store.saveAccounts(); tick++ }
        FieldRow(st, "URL do servidor", a.url, locked, hint = "Ex.: http://servidor.com:8080") { a.url = it.trim(); a.ok = false; a.info = ""; Store.saveAccounts(); tick++ }
        FieldRow(st, "Usuário", a.user, locked) { a.user = it.trim(); a.ok = false; a.info = ""; Store.saveAccounts(); tick++ }
        FieldRow(st, "Senha", a.pass, locked, secret = true) { a.pass = it; a.ok = false; a.info = ""; Store.saveAccounts(); tick++ }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn(if (busy) "Conectando…" else "Conectar e testar", locked = locked, primary = true) {
            if (busy) return@Btn
            if (!a.filled) { a.info = "Preencha URL do servidor, usuário e senha."; tick++; return@Btn }
            busy = true; a.info = "Conectando ao servidor…"; tick++
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            thread { val r = try { Xtream.test(a) to true } catch (e: Throwable) { Net.why(e) to false }; main.post { a.info = r.first; a.ok = r.second; busy = false; Store.saveAccounts(); tick++ } }
        }
        Btn(if (Store.activeAcc == slot) "✓ Conta em uso" else "Usar esta conta", locked = locked) {
            if (!a.ok) { st.say("Teste a conexão desta conta primeiro"); return@Btn }
            Store.activeAcc = slot; Store.saveAccounts(); tick++
            st.say("Conta em uso: ${a.name.ifBlank { "Conta ${slot + 1}" }}. Atualizando as abas…")
            Catalog.refresh(ctx) { st.say(it) }
        }
        Btn("Apagar dados", locked = locked) {
            Store.accounts[slot] = Account(); if (Store.activeAcc == slot) Store.activeAcc = -1
            Store.saveAccounts(); tick++; st.say("Dados da conta apagados"); Catalog.refresh(ctx)
        }
    }
    if (a.info.isNotBlank()) T(a.info, 13, if (a.ok) C.ok else C.muted, FontWeight.SemiBold, lineHeight = 18)
    PhoneBox(st)
}

@Composable
fun ListsPanel(st: AppState, locked: Boolean) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    tick.let { }
    T("Cole o endereço de uma lista M3U, M3U8, JSON ou TXT. Cada canal, filme e série vai sozinho para a aba certa, pelo grupo (group-title) e pelo nome.", 13, C.muted, lineHeight = 19)
    PhoneBox(st)
    if (Store.lists.isEmpty()) T("Nenhuma lista ainda.", 13, C.dim) else T("LISTAS SALVAS (${Store.lists.size})", 10, C.muted, FontWeight.Bold)
    Store.lists.toList().forEachIndexed { i, l ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.bg).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                T(l.name, 13, C.fg, FontWeight.Bold, maxLines = 1)
                T(l.url, 11, C.dim, maxLines = 1)
                if (l.info.isNotBlank()) T(l.info, 11, C.muted, maxLines = 1)
            }
            Btn("Remover", locked = locked) { Store.lists.removeAt(i); Store.saveLists(); tick++; Catalog.refresh(ctx) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Btn("+ Adicionar lista", locked = locked, primary = true) {
            st.kb = KbTarget("Endereço da lista", "", hint = "http://servidor/lista.m3u") { url ->
                val u = url.trim()
                if (!u.contains("://")) { st.say("O endereço precisa começar com http:// ou https://"); return@KbTarget }
                Store.lists.add(ListSrc("Lista ${Store.lists.size + 1}", u)); Store.saveLists(); tick++
                st.say("Lista adicionada. Carregando…")
                Catalog.refresh(ctx) { st.say(it) }
            }
        }
        Btn(if (Catalog.loading) "Atualizando…" else "Atualizar agora", locked = locked) { Catalog.refresh(ctx) { st.say(it) } }
    }
    if (Catalog.status.isNotBlank()) T(Catalog.status, 12, C.muted, lineHeight = 17)
    T("Formatos aceitos: M3U, M3U8, JSON e TXT. Se o servidor da lista for bloqueado na sua internet, use Configurações › Proxy.", 12, C.dim, lineHeight = 17)
}

/** Caixa "Enviar pelo celular": liga a página na rede de casa só enquanto estiver na tela. */
@Composable
fun PhoneBox(st: AppState) {
    val ctx = LocalContext.current
    val ip = remember { PhoneServer.address() }
    DisposableEffect(Unit) {
        val mine: (Map<String, String>) -> Unit = { f ->
            if (f["tipo"] == "lista") {
                Store.lists.add(ListSrc(f["nome"].orEmpty().ifBlank { "Lista ${Store.lists.size + 1}" }, f["url"].orEmpty())); Store.saveLists()
                st.say("Lista recebida do celular. Carregando…")
                Catalog.refresh(ctx) { st.say(it) }
            } else {
                val i = Store.accounts.indexOfFirst { !it.filled }.let { if (it < 0) Store.activeAcc.coerceAtLeast(0) else it }
                val a = Account(f["nome"].orEmpty().ifBlank { "Conta ${i + 1}" }, f["servidor"].orEmpty(), f["usuario"].orEmpty(), f["senha"].orEmpty())
                Store.accounts[i] = a; Store.saveAccounts()
                st.say("Conta recebida do celular. Testando…")
                val main = android.os.Handler(android.os.Looper.getMainLooper())
                thread {
                    val r = try { Xtream.test(a) to true } catch (e: Throwable) { Net.why(e) to false }
                    main.post {
                        a.info = r.first; a.ok = r.second
                        if (a.ok) { Store.activeAcc = i; Store.saveAccounts(); st.say("Conta conectada. Atualizando as abas…"); Catalog.refresh(ctx) { st.say(it) } }
                        else { Store.saveAccounts(); st.say("A conta não conectou: ${r.first}") }
                    }
                }
            }
        }
        PhoneServer.onReceive = mine
        PhoneServer.start()
        onDispose { PhoneServer.stop(); if (PhoneServer.onReceive === mine) PhoneServer.onReceive = null }
    }
    var f by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(C.a2.copy(alpha = if (f) .22f else .12f))
        .border(if (f) 2.dp else 1.dp, if (f) C.a1 else C.a2.copy(alpha = .4f), RoundedCornerShape(12.dp))
        .onFocusChanged { val n = it.isFocused; if (n && !f) Sfx.tick(); f = n }.focusable()
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        T("ENVIAR PELO CELULAR", 10, C.a2, FontWeight.Bold)
        if (ip == null) T("A TV não está conectada a uma rede.", 13, C.fg, FontWeight.SemiBold)
        else {
            T("No celular, conectado ao mesmo Wi-Fi da TV, abra no navegador:", 12, C.muted)
            T("http://$ip:${PhoneServer.PORT}", 20, C.fg, FontWeight.ExtraBold)
            T("Cole o link da lista ou os dados da conta e toque em Enviar. A TV recebe na hora.", 12, C.muted)
        }
    }
}

@Composable
fun AdultPanel(st: AppState, locked: Boolean) {
    var tick by remember { mutableIntStateOf(0) }
    tick.let { }
    T("Categorias adultas (+18, XXX, Adulto…) ficam escondidas de todas as abas e da pesquisa. Para mostrar, digite o PIN. O bloqueio volta sozinho quando o app é fechado.", 13, C.muted, lineHeight = 19)
    KV(listOf("Situação" to if (Store.adultOpen) "Liberado até fechar o app" else "Bloqueado com PIN", "PIN padrão" to "0000 (troque abaixo)"))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (Store.adultOpen) Btn("Bloquear agora", locked = locked, primary = true) { Store.adultOpen = false; Catalog.rev++; tick++; st.say("Conteúdo adulto bloqueado") }
        else Btn("Liberar com PIN", locked = locked, primary = true) {
            st.kb = KbTarget("PIN", "", secret = true, hint = "4 números") { pin ->
                if (pin.trim() == Store.adultPin) { Store.adultOpen = true; Catalog.rev++; tick++; st.say("Conteúdo adulto liberado até fechar o app") } else st.say("PIN incorreto")
            }
        }
        Btn("Trocar PIN", locked = locked) {
            st.kb = KbTarget("PIN atual", "", secret = true) { old ->
                if (old.trim() != Store.adultPin) { st.say("PIN incorreto"); return@KbTarget }
                st.kb = KbTarget("Novo PIN (4 números)", "", secret = true) { n ->
                    if (Regex("\\d{4}").matches(n.trim())) { Store.setPin(n.trim()); st.say("PIN trocado") } else st.say("O PIN precisa ter 4 números")
                }
            }
        }
    }
}
