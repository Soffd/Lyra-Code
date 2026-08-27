package com.yukisoffd.lyracode.ssh

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * Character-oriented IME endpoint for the raw terminal.
 *
 * A normal EditText connection may retain the first character as composing text. That is tolerable
 * at a shell prompt because Enter commits it, but breaks modal programs: Vim never receives the `i`
 * that enters insert mode. TYPE_NULL asks compatible keyboards for per-character key events, while
 * commit/composition callbacks remain implemented for keyboards, Unicode text and clipboard paste
 * that use them instead.
 */
class TerminalInputView(context: Context) : EditText(context) {
    var onText: (String) -> Unit = {}
    var onBackspace: () -> Unit = {}
    var onEnter: () -> Unit = {}
    var onTerminalKey: (String) -> Unit = {}

    private val composition = TerminalCompositionState(
        sendText = { dispatchText(it) },
        sendBackspace = { onBackspace() },
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isSingleLine = false
        inputType = InputType.TYPE_NULL
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setTextColor(Color.TRANSPARENT)
        setHintTextColor(Color.TRANSPARENT)
        setBackgroundColor(Color.TRANSPARENT)
        background = null
        isCursorVisible = false
        setPadding(0, 0, 0, 0)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        // Closing and reopening the keyboard may destroy the old InputConnection without first
        // calling finishComposingText(). Its stale word must not affect the next IME session.
        composition.finish()
        BaseInputConnection.removeComposingSpans(text)
        if (text.isNotEmpty()) text.clear()
        Log.d(TAG, "input connection created")
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = imeOptions
        return object : BaseInputConnection(this@TerminalInputView, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                Log.d(TAG, "commitText chars=${text?.length ?: 0}")
                composition.commit(text?.toString().orEmpty())
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                Log.d(TAG, "setComposingText chars=${text?.length ?: 0}")
                composition.update(text?.toString().orEmpty())
                return true
            }

            override fun finishComposingText(): Boolean {
                Log.d(TAG, "finishComposingText")
                composition.finish()
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                Log.d(TAG, "deleteSurroundingText before=$beforeLength after=$afterLength")
                composition.deleteBefore(beforeLength)
                return true
            }

            override fun deleteSurroundingTextInCodePoints(
                beforeLength: Int,
                afterLength: Int,
            ): Boolean {
                Log.d(TAG, "deleteSurroundingTextInCodePoints before=$beforeLength after=$afterLength")
                composition.deleteBefore(beforeLength)
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                Log.d(TAG, "sendKeyEvent action=${event.action} keyCode=${event.keyCode}")
                if (event.action != KeyEvent.ACTION_DOWN) return true
                return dispatchTerminalKey(event)
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                Log.d(TAG, "performEditorAction action=$actionCode")
                finishComposingInput()
                onEnter()
                return true
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = dispatchTerminalKey(event)

    private fun dispatchText(value: String) {
        Log.d(TAG, "dispatchText chars=${value.length}")
        var segmentStart = 0
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\r' || character == '\n') {
                if (index > segmentStart) onText(value.substring(segmentStart, index))
                onEnter()
                // Clipboard text commonly contains CRLF; it represents one terminal Enter.
                if (character == '\r' && index + 1 < value.length && value[index + 1] == '\n') {
                    index++
                }
                segmentStart = index + 1
            }
            index++
        }
        if (segmentStart < value.length) onText(value.substring(segmentStart))
    }

    private fun dispatchTerminalKey(event: KeyEvent): Boolean = when (event.keyCode) {
        KeyEvent.KEYCODE_ESCAPE -> dispatchControlSequence("\u001b")
        KeyEvent.KEYCODE_TAB -> dispatchControlSequence("\t")
        KeyEvent.KEYCODE_DPAD_UP -> dispatchControlSequence("\u001b[A")
        KeyEvent.KEYCODE_DPAD_DOWN -> dispatchControlSequence("\u001b[B")
        KeyEvent.KEYCODE_DPAD_RIGHT -> dispatchControlSequence("\u001b[C")
        KeyEvent.KEYCODE_DPAD_LEFT -> dispatchControlSequence("\u001b[D")
        KeyEvent.KEYCODE_MOVE_HOME -> dispatchControlSequence("\u001b[H")
        KeyEvent.KEYCODE_MOVE_END -> dispatchControlSequence("\u001b[F")
        KeyEvent.KEYCODE_PAGE_UP -> dispatchControlSequence("\u001b[5~")
        KeyEvent.KEYCODE_PAGE_DOWN -> dispatchControlSequence("\u001b[6~")
        KeyEvent.KEYCODE_FORWARD_DEL -> dispatchControlSequence("\u001b[3~")
        KeyEvent.KEYCODE_DEL -> {
            composition.deleteBefore(1)
            true
        }
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            Log.d(TAG, "dispatch enter")
            finishComposingInput()
            onEnter()
            true
        }
        else -> {
            val unicode = event.unicodeChar
            if (unicode == 0) {
                super.onKeyDown(event.keyCode, event)
            } else {
                composition.finish()
                onText(String(Character.toChars(unicode)))
                true
            }
        }
    }

    private fun dispatchControlSequence(sequence: String): Boolean {
        finishComposingInput()
        onTerminalKey(sequence)
        return true
    }

    fun focusAndShowKeyboard() {
        val focused = requestFocus()
        Log.d(TAG, "show keyboard requestFocus=$focused attached=$isAttachedToWindow")
        setSelection(text.length)
        post {
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * Ends the IME's editable word before a terminal control key changes shell/editor mode.
     * Without this boundary, an IME can replace text composed in vim's insert mode after Esc and
     * emit synthetic backspaces into the subsequent `:w` command.
     */
    fun finishComposingInput() {
        composition.finish()
        post {
            BaseInputConnection.removeComposingSpans(text)
            if (text.isNotEmpty()) text.clear()
            if (hasFocus()) {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.let { manager ->
                    manager.restartInput(this)
                    manager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
    }

    private companion object {
        const val TAG = "LyraTerminalInput"
    }
}

/** Mirrors IME composition changes to a terminal, including replacements made by autocorrection. */
internal class TerminalCompositionState(
    private val sendText: (String) -> Unit,
    private val sendBackspace: () -> Unit,
) {
    private var forwarded = ""
    val isComposing: Boolean get() = forwarded.isNotEmpty()

    fun update(next: String) {
        val prefixLength = commonPrefixLength(forwarded, next)
        val removed = forwarded.substring(prefixLength).codePointCount(
            0,
            forwarded.length - prefixLength,
        )
        repeat(removed) { sendBackspace() }
        next.substring(prefixLength).takeIf(String::isNotEmpty)?.let(sendText)
        forwarded = next
    }

    fun commit(text: String) {
        update(text)
        forwarded = ""
    }

    fun finish() {
        forwarded = ""
    }

    fun deleteBefore(count: Int) {
        if (count <= 0) return
        repeat(count) { sendBackspace() }
        forwarded = forwarded.dropLastCodePoints(count)
    }

    private fun commonPrefixLength(first: String, second: String): Int {
        var index = 0
        val limit = minOf(first.length, second.length)
        while (index < limit && first[index] == second[index]) index++
        // Never split a UTF-16 surrogate pair.
        if (index > 0 && index < first.length && first[index - 1].isHighSurrogate()) index--
        return index
    }

    private fun String.dropLastCodePoints(count: Int): String {
        if (isEmpty()) return this
        val actual = count.coerceAtMost(codePointCount(0, length))
        return substring(0, offsetByCodePoints(length, -actual))
    }
}
