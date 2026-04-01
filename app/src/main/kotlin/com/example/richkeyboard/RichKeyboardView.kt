package com.example.richkeyboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputConnection
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

    // ── Child views ──────────────────────────────────────────────────────

    private val btnPasteRich: ImageButton
    private val btnPastePlain: ImageButton
    private val btnSettings: ImageButton
    private val keyboardContainer: FrameLayout

    // ── Callbacks (set by the hosting IME service) ───────────────────────

    /** Invoked when the user taps the "Paste Rich" toolbar button. */
    var onPasteRichClickListener: (() -> Unit)? = null

    /** Invoked when the user taps the "Paste Plain" toolbar button. */
    var onPastePlainClickListener: (() -> Unit)? = null

    /** Invoked when the user taps the "Settings" toolbar button. */
    var onSettingsClickListener: (() -> Unit)? = null

    // ── Active input connection ──────────────────────────────────────────

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

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Sets the [InputConnection] that key-press handlers will write to.
     * The IME service should call this in [android.view.inputmethod.InputMethodService.onStartInputView].
     */
    fun setInputConnection(ic: InputConnection?) {
        inputConnection = ic
    }

    // ── Key layout ───────────────────────────────────────────────────────

    /**
     * Builds the key rows programmatically and adds them to [keyboardContainer].
     *
     * Each key is a simple [View] with a click listener that commits the
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
                    // Add vertical spacing between rows (skip first row)
                    if (index > 0) {
                        topMargin = 4
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
            )
        }

        // Shift key (stub)
        bottomRow.addView(createSpecialKey("⇧", 1.5f) {
            // TODO: toggle caps lock / shift state
        })

        // Space bar (wide)
        bottomRow.addView(createSpecialKey("Space", 3f) {
            inputConnection?.commitText(" ", 1)
        })

        // Backspace
        bottomRow.addView(createSpecialKey("⌫", 1f) {
            inputConnection?.deleteSurroundingText(1, 0)
        })

        // Enter / Return
        bottomRow.addView(createSpecialKey("↵", 1.5f) {
            inputConnection?.commitText("\n", 1)
        })

        keyboardContainer.addView(bottomRow)
    }

    /**
     * Creates a tappable view representing a single alphanumeric key.
     */
    private fun createKeyView(label: String): View {
        return com.google.android.material.button.MaterialButton(
            context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            layoutParams = LinearLayout.LayoutParams(
                0,
                resources.getDimensionPixelSize(android.R.dimen.app_icon_size),
                1f
            ).apply {
                marginStart = 2
                marginEnd = 2
            }
            setOnClickListener {
                inputConnection?.commitText(label.lowercase(), 1)
            }
        }
    }

    /**
     * Creates a special (non-alphanumeric) key with a custom [weight].
     */
    private fun createSpecialKey(
        label: String,
        weight: Float,
        onClick: () -> Unit
    ): View {
        return com.google.android.material.button.MaterialButton(
            context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            layoutParams = LinearLayout.LayoutParams(
                0,
                resources.getDimensionPixelSize(android.R.dimen.app_icon_size),
                weight
            ).apply {
                marginStart = 2
                marginEnd = 2
            }
            setOnClickListener { onClick() }
        }
    }
}
