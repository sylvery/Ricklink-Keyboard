package com.example.richkeyboard

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout

/**
 * Custom keyboard view that hosts the QWERTY key layout and toolbar buttons.
 *
 * The view inflates [R.layout.keyboard_view] and provides public methods
 * for the IME service to:
 * - Wire paste buttons to the rich-text pipeline
 * - Set the active [InputConnection] for key events
 *
 * ## Usage from [RichInputService]
 * ```kotlin
 * val keyboardView = RichKeyboardView(this)
 * keyboardView.setInputConnection(currentInputConnection)
 * keyboardView.onPasteRichClickListener = { onPasteRequested() }
 * return keyboardView
 * ```
 */
class RichKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    // Child views

    private val btnPasteRich: ImageButton
    private val btnPastePlain: ImageButton
    private val btnSettings: ImageButton
    private val keyboardContainer: FrameLayout

    // Callbacks (set by the hosting IME service)

    /** Invoked when the user taps the "Paste Rich" toolbar button. */
    var onPasteRichClickListener: (() -> Unit)? = null

    /** Invoked when the user taps the "Paste Plain" toolbar button. */
    var onPastePlainClickListener: (() -> Unit)? = null

    /** Invoked when the user taps the "Settings" toolbar button. */
    var onSettingsClickListener: (() -> Unit)? = null

    // Active input connection

    private var inputConnection: InputConnection? = null

    init {
        // Inflate the XML layout into this FrameLayout
        LayoutInflater.from(context).inflate(R.layout.keyboard_view, this, true)

        // Bind views
        btnPasteRich = findViewById<ImageButton>(R.id.btn_paste_rich)
        btnPastePlain = findViewById<ImageButton>(R.id.btn_paste_plain)
        btnSettings = findViewById<ImageButton>(R.id.btn_settings)
        keyboardContainer = findViewById<FrameLayout>(R.id.keyboard_container)

        // Wire click listeners
        btnPasteRich.setOnClickListener { onPasteRichClickListener?.invoke() }
        btnPastePlain.setOnClickListener { onPastePlainClickListener?.invoke() }
        btnSettings.setOnClickListener { onSettingsClickListener?.invoke() }

        // Inflate the QWERTY keyboard into the container
        inflateKeyboardKeys()
    }

    // Public API

    /**
     * Sets the [InputConnection] that key-press handlers will write to.
     * The IME service should call this in [android.view.inputmethod.InputMethodService.onStartInputView].
     */
    fun setInputConnection(ic: InputConnection?) {
        inputConnection = ic
    }

    // Key layout

    /**
     * Builds the key rows programmatically and adds them to [keyboardContainer].
     *
     * Each key is a [Button] with a click listener that commits the
     * corresponding character via [inputConnection]. For a production keyboard
     * you would replace this with a proper [android.inputmethodservice.KeyboardView]
     * or a RecyclerView-based grid with key repeat, long-press, swipe, etc.
     */
    private fun inflateKeyboardKeys() {
        val rows = listOf(
            "QWERTYUIOP",
            "ASDFGHJKL",
            "ZXCVBNM"
        )

        for ((index, rowText) in rows.withIndex()) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        topMargin = dpToPx(4)
                    }
                }
            }

            for (char in rowText) {
                val key = createKeyView(char.toString())
                row.addView(key)
            }

            keyboardContainer.addView(row)
        }

        // Bottom row: shift, space, backspace, enter
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(4)
            }
        }

        // Shift key (stub)
        bottomRow.addView(createSpecialKey("SHIFT", 1.5f) {
            // TODO: toggle caps lock / shift state
        })

        // Space bar (wide)
        bottomRow.addView(createSpecialKey("SPACE", 3f) {
            inputConnection?.commitText(" ", 1)
        })

        // Backspace
        bottomRow.addView(createSpecialKey("DEL", 1f) {
            inputConnection?.deleteSurroundingText(1, 0)
        })

        // Enter / Return
        bottomRow.addView(createSpecialKey("ENTER", 1.5f) {
            inputConnection?.commitText("\n", 1)
        })

        keyboardContainer.addView(bottomRow)
    }

    /**
     * Creates a tappable button representing a single alphanumeric key.
     */
    private fun createKeyView(label: String): View {
        val btn = Button(context)
        btn.text = label
        btn.textSize = 16f
        btn.isAllCaps = false
        btn.minWidth = 0
        btn.minimumWidth = 0
        btn.setBackgroundColor(0xFF3A3A3C.toInt())
        btn.setTextColor(0xFFFFFFFF.toInt())

        val keyHeight = dpToPx(44)
        btn.layoutParams = LinearLayout.LayoutParams(
            0,
            keyHeight,
            1f
        ).apply {
            marginStart = dpToPx(2)
            marginEnd = dpToPx(2)
        }
        btn.setOnClickListener {
            inputConnection?.commitText(label.lowercase(), 1)
        }
        return btn
    }

    /**
     * Creates a special (non-alphanumeric) key with a custom [weight].
     */
    private fun createSpecialKey(
        label: String,
        weight: Float,
        onClick: () -> Unit
    ): View {
        val btn = Button(context)
        btn.text = label
        btn.textSize = 14f
        btn.isAllCaps = false
        btn.minWidth = 0
        btn.minimumWidth = 0
        btn.setBackgroundColor(0xFF3A3A3C.toInt())
        btn.setTextColor(0xFFAEAEB2.toInt())

        val keyHeight = dpToPx(44)
        btn.layoutParams = LinearLayout.LayoutParams(
            0,
            keyHeight,
            weight
        ).apply {
            marginStart = dpToPx(2)
            marginEnd = dpToPx(2)
        }
        btn.setOnClickListener { onClick() }
        return btn
    }

    // Helpers

    /**
     * Converts a value in density-independent pixels (dp) to actual pixels
     * based on the current display density.
     */
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
