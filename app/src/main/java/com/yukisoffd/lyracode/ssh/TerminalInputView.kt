package com.yukisoffd.lyracode.ssh

import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * A real text editor used as the IME endpoint for the raw terminal.
 *
 * Some Android keyboards refuse to send composing text or clipboard input to a bare [android.view.View]
 * with a hand-written BaseInputConnection. Using EditText's native connection keeps the full system
 * keyboard, symbols and clipboard available. The text is visually hidden and every IME operation is
 * mirrored to the PTY callbacks instead of being rendered by this view.
 */
class TerminalInputView(context: Context) : EditText(context) {
    var onText: (String) -> Unit = {}
    var onBackspace: () -> Unit = {}
    var onEnter: () -> Unit = {}

    private val composition = TerminalCompositionState(
        sendText = { dispatchText(it) },
        sendBackspace = { onBackspace() },
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isSingleLine = false
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_VARIATION_NORMAL
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_ACTION_NONE
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
        val nativeConnection = super.onCreateInputConnection(outAttrs) ?: return null
        Log.d(TAG, "input connection created")
        // This is intentionally a normal text field. Password variations trigger restricted
        // keyboards on several OEM systems and disable clipboard and symbol input.
        outAttrs.inputType = inputType
        outAttrs.imeOptions = imeOptions
        return object : InputConnectionWrapper(nativeConnection, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                Log.d(TAG, "commitText chars=${text?.length ?: 0}")
                composition.commit(text?.toString().orEmpty())
                val handled = super.commitText(text, newCursorPosition)
                trimBackingBufferWhenIdle()
                return handled
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                Log.d(TAG, "setComposingText chars=${text?.length ?: 0}")
                composition.update(text?.toString().orEmpty())
                return super.setComposingText(text, newCursorPosition)
            }

            override fun finishComposingText(): Boolean {
                Log.d(TAG, "finishComposingText")
                composition.finish()
                return super.finishComposingText()
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                Log.d(TAG, "deleteSurroundingText before=$beforeLength after=$afterLength")
                composition.deleteBefore(beforeLength)
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(
                beforeLength: Int,
                afterLength: Int,
            ): Boolean {
                Log.d(TAG, "deleteSurroundingTextInCodePoints before=$beforeLength after=$afterLength")
                composition.deleteBefore(beforeLength)
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                Log.d(TAG, "sendKeyEvent action=${event.action} keyCode=${event.keyCode}")
                if (event.action != KeyEvent.ACTION_DOWN) return true
                return dispatchTerminalKey(event)
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                Log.d(TAG, "performEditorAction action=$actionCode")
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
        KeyEvent.KEYCODE_DEL -> {
            composition.deleteBefore(1)
            true
        }
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            Log.d(TAG, "dispatch enter")
            composition.finish()
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

    private fun trimBackingBufferWhenIdle() {
        if (text.length <= MAX_BACKING_CHARS || composition.isComposing) return
        post {
            if (!composition.isComposing && text.length > MAX_BACKING_CHARS) {
                text.clear()
            }
        }
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

    private companion object {
        const val MAX_BACKING_CHARS = 4096
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
