package com.lumestar.tv

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Campo que abriu o teclado: o texto atual e o que fazer com o texto ao apertar Pronto. */
class KbTarget(val label: String, val value: String, val secret: Boolean = false, val hint: String = "", val search: Boolean = false, val ret: FocusRequester? = null, val done: (String) -> Unit)

private val ROWS = listOf("1234567890", "qwertyuiop", "asdfghjklç", "zxcvbnm,.-", ":/_@?=&#~%")

/**
 * Teclado virtual flutuante, como o do celular: espaço, apagar, falar, cancelar, apagar tudo e pronto.
 * Pronto fecha e mantém o texto; Cancelar fecha sem mudar nada.
 * Na pesquisa, as sugestões aparecem embaixo: desça com a seta e aperte OK para escolher.
 */
@Composable
fun Keyboard(st: AppState, t: KbTarget) {
    var draft by remember(t) { mutableStateOf(t.value) }
    var caps by remember(t) { mutableStateOf(false) }
    var heard by remember(t) { mutableStateOf("") }
    val first = remember { FocusRequester() }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val txt = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!txt.isNullOrBlank()) { draft = txt; heard = "" } else heard = "Não entendi. Tente de novo."
    }
    fun close() {
        st.kb = null
        val r = t.ret
        if (r != null) st.focusLater(r) else st.refocus++
    }
    DisposableEffect(t) {
        val h: (String) -> Unit = { s -> close(); t.done(s) }
        Controle.noTeclado = h
        onDispose { if (Controle.noTeclado === h) Controle.noTeclado = null }
    }
    fun type(s: String) { draft += if (caps && s.length == 1) s.uppercase() else s; Sfx.kb() }
    BackHandler { close() }
    LaunchedEffect(t) { delay(60); try { first.requestFocus() } catch (e: Exception) {} }

    Box(Modifier.fillMaxSize().background(Color(0x99020410)), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.padding(bottom = 18.dp).width(720.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF00E1538))
                .border(1.dp, C.line, RoundedCornerShape(20.dp)).padding(14.dp).focusGroup()
                .onPreviewKeyEvent { ev ->
                    // teclado físico (ou de celular conectado) também digita
                    val ne = ev.nativeKeyEvent
                    if (ev.type == KeyEventType.KeyDown && ne.unicodeChar != 0 && !ne.isSystem && ne.keyCode !in listOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER)) {
                        draft += ne.unicodeChar.toChar(); true
                    } else if (ev.type == KeyEventType.KeyDown && ne.keyCode == android.view.KeyEvent.KEYCODE_DEL) { draft = draft.dropLast(1); true } else false
                },
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(C.bg).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                T(t.label.uppercase(), 10, C.a1, FontWeight.Bold)
                Box(Modifier.width(12.dp))
                val shown = if (t.secret) "•".repeat(draft.length) else draft
                T(if (shown.isEmpty()) t.hint.ifBlank { "Digite aqui…" } else "$shown▏", 16, if (shown.isEmpty()) C.dim else C.fg, FontWeight.SemiBold, maxLines = 1)
            }
            ROWS.forEachIndexed { ri, r ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    r.forEachIndexed { ci, ch ->
                        val label = if (caps && ch.isLetter()) ch.uppercaseChar().toString() else ch.toString()
                        Key(label, Modifier.weight(1f).let { if (ri == 1 && ci == 0) it.focusRequester(first) else it }) { type(ch.toString()) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("https://", "http://", ".com", ".m3u8").forEach { s -> Key(s, Modifier.weight(1f), small = true) { draft += s; Sfx.kb() } }
                Key(if (caps) "⇧ MAIÚSC." else "⇧ maiúsc.", Modifier.weight(1f), small = true, on = caps) { caps = !caps; Sfx.kb() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Key("espaço", Modifier.weight(3f)) { type(" ") }
                Key("⌫ apagar", Modifier.weight(2f)) { draft = draft.dropLast(1); Sfx.kb() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Key("🎤 falar", Modifier.weight(1.5f), small = true) {
                    try {
                        voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR").putExtra(RecognizerIntent.EXTRA_PROMPT, t.label))
                    } catch (e: Exception) { heard = "Este aparelho não tem reconhecimento de voz." }
                }
                Key("cancelar", Modifier.weight(1f), small = true) { close() }
                Key("apagar tudo", Modifier.weight(1.5f), small = true) { draft = ""; Sfx.kb() }
                Key("✓ pronto", Modifier.weight(1.5f), small = true, on = true) { val d = draft; close(); t.done(d) }
            }
            if (heard.isNotEmpty()) T(heard, 12, C.warn)
            if (t.search) {
                val sug = remember(draft, Catalog.rev) { if (draft.isBlank()) emptyList() else Catalog.search(draft, 6) }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (sug.isEmpty()) T(if (draft.isBlank()) "As sugestões aparecem aqui enquanto você digita. Desça com a seta para escolher." else "Sem sugestões para esse texto.", 12, C.dim)
                    sug.forEach { s -> Chip(s.title.take(28), false, false) { close(); t.done(s.title) } }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Key(label: String, modifier: Modifier, small: Boolean = false, on: Boolean = false, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        modifier.height(if (small) 32.dp else 34.dp)
            .graphicsLayer { val s = if (f) 1.08f else 1f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(9.dp))
            .background(if (f) C.fg else if (on) C.a1.copy(alpha = .35f) else C.panel2)
            .onFocusChanged { f = it.isFocused }
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center
    ) { T(label, if (small) 12 else 15, if (f) Color(0xFF050816) else C.fg, FontWeight.Bold, maxLines = 1) }
}
