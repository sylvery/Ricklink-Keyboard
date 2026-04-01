package com.example.richkeyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodService

/**
 * A specialized IME (Input Method Editor) that preserves rich-text formatting
 * (hyperlinks, bold, italic, etc.) when pasting from the clipboard.
 *
 * ## How it works
 *
 * 1. A [ClipboardManager.OnPrimaryClipChangedListener] watches the system
 *    clipboard for HTML-specific MIME types (e.g. `text/html`).
 * 2. When a paste action is triggered, the service inspects the current
 *    [EditorInfo] to determine whether the receiving application advertises
 *    rich-content support via [EditorInfo.contentMimeTypes].
 * 3. If the editor supports rich content, the service uses
 *    [InputConnection.commitContent] with an [InputContentInfo] wrapper to
 *    inject the HTML payload.
 * 4. If the editor does **not** support rich content, the service falls back
 *    to plain-text insertion via [InputConnection.commitText].
 */
class RichInputService : InputMethodService() {

    companion object {
        private const val TAG = "RichInputService"

        /** MIME type for HTML content on the clipboard. */
        private const val MIME_TYPE_TEXT_HTML = "text/html"

        /** MIME type for plain text (used as a fallback). */
        private const val MIME_TYPE_TEXT_PLAIN = "text/plain"

        /**
         * Array of MIME types this IME can provide via [InputConnection.commitContent].
         * Editors that accept rich content will advertise matching types in
         * [EditorInfo.contentMimeTypes].
         */
        val SUPPORTED_CONTENT_MIME_TYPES: Array<String> = arrayOf(
            MIME_TYPE_TEXT_HTML
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Clipboard monitoring
    // ──────────────────────────────────────────────────────────────────────

    private var clipboardManager: ClipboardManager? = null

    /**
     * Listener that fires whenever the primary clip on the system clipboard
     * changes. We use it to keep an internal cache of whether the current
     * clipboard item contains HTML, so paste can decide the transfer strategy
     * without blocking the UI thread with clipboard queries.
     */
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        Log.d(TAG, "Primary clipboard clip changed.")
        // Future enhancement: pre-parse and cache the HTML content here so
        // that paste operations are instant. For the skeleton we simply log.
    }

    // ──────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        Log.d(TAG, "RichInputService created – clipboard listener registered.")
    }

    override fun onDestroy() {
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        Log.d(TAG, "RichInputService destroyed – clipboard listener unregistered.")
        super.onDestroy()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Input view
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inflates the keyboard layout. Replace this with your actual keyboard
     * view (e.g. a custom [KeyboardView]) when you build the UI layer.
     *
     * For now this returns a minimal placeholder so the service compiles
     * and can be tested end-to-end with a "Paste Rich" debug button.
     */
    override fun onCreateInputView(): View {
        // TODO: inflate a real keyboard layout, e.g.
        //   return layoutInflater.inflate(R.layout.keyboard_view, null)
        return View(this)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Rich-text paste logic
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Call this method from a paste button in your keyboard UI.
     *
     * It reads the current clipboard, determines whether the target editor
     * supports HTML content, and chooses the appropriate commit strategy.
     */
    fun onPasteRequested() {
        val ic: InputConnection = currentInputConnection ?: run {
            Log.w(TAG, "No InputConnection available – ignoring paste request.")
            return
        }
        val editorInfo: EditorInfo = currentInputEditorInfo ?: run {
            Log.w(TAG, "No EditorInfo available – ignoring paste request.")
            return
        }

        // ── 1. Read clipboard ────────────────────────────────────────────
        val clip: ClipData? = clipboardManager?.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Log.d(TAG, "Clipboard is empty.")
            return
        }
        val clipDescription: ClipDescription? = clip.description
        val clipItem: ClipData.Item = clip.getItemAt(0)

        // ── 2. Check if the clipboard contains HTML ──────────────────────
        val hasHtml = clipDescription?.hasMimeType(MIME_TYPE_TEXT_HTML) == true

        // ── 3. Check if the editor supports rich content ─────────────────
        val editorSupportsHtml = editorSupportsRichContent(editorInfo)

        if (hasHtml && editorSupportsHtml) {
            // ── 4a. Rich paste via commitContent ─────────────────────────
            val htmlText: String? = clipItem.htmlText?.toString()
            if (htmlText != null) {
                pasteAsRichContent(ic, htmlText)
            } else {
                Log.w(TAG, "Clipboard advertises text/html but htmlText was null. Falling back to plain text.")
                pasteAsPlainText(ic, clipItem)
            }
        } else {
            // ── 4b. Fallback: plain text ─────────────────────────────────
            if (!hasHtml) Log.d(TAG, "Clipboard does not contain HTML – using plain text fallback.")
            if (!editorSupportsHtml) Log.d(TAG, "Editor does not support rich content – using plain text fallback.")
            pasteAsPlainText(ic, clipItem)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Capability check
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Determines whether the current editor advertises rich-content support
     * for one of our [SUPPORTED_CONTENT_MIME_TYPES].
     *
     * The editor signals support through [EditorInfo.contentMimeTypes],
     * which is a [String] array of MIME type filters (may contain wildcards
     * like `text/*`).
     */
    private fun editorSupportsRichContent(editorInfo: EditorInfo): Boolean {
        val acceptedTypes: Array<String>? = editorInfo.contentMimeTypes
        if (acceptedTypes.isNullOrEmpty()) return false

        for (accepted in acceptedTypes) {
            for (supported in SUPPORTED_CONTENT_MIME_TYPES) {
                if (mimeTypesMatch(accepted, supported)) return true
            }
        }
        return false
    }

    /**
     * Compares two MIME type strings, supporting the `*` wildcard in the
     * sub-type portion (e.g. `text/*` matches `text/html`).
     */
    private fun mimeTypesMatch(filter: String, candidate: String): Boolean {
        if (filter == candidate) return true

        // Handle wildcard sub-type: "text/*"
        val filterParts = filter.split('/')
        val candidateParts = candidate.split('/')
        if (filterParts.size == 2 && candidateParts.size == 2) {
            return filterParts[0] == candidateParts[0] && filterParts[1] == "*"
        }
        return false
    }

    // ──────────────────────────────────────────────────────────────────────
    // Commit strategies
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Sends the HTML content to the editor using the Commit Content API.
     *
     * This wraps the HTML in an [InputContentInfo] and calls
     * [InputConnection.commitContent]. The editor is then responsible for
     * rendering / storing the rich text.
     *
     * @param ic          The active [InputConnection].
     * @param htmlContent The raw HTML string captured from the clipboard.
     */
    private fun pasteAsRichContent(ic: InputConnection, htmlContent: String) {
        Log.d(TAG, "Pasting rich HTML content (${htmlContent.length} chars) via commitContent.")

        // Create an InputContentInfo that carries the HTML payload.
        // The content URI can be used for content-provider backed data;
        // for clipboard HTML we pass the string directly via extras.
        val inputContentInfo = InputContentInfo(
            /* contentUri = */ android.net.Uri.EMPTY,
            /* description  = */ ClipDescription(
                "Rich text from clipboard",
                SUPPORTED_CONTENT_MIME_TYPES
            ),
            /* linkUri      = */ null
        )

        // Attach the actual HTML string in the extras bundle so the
        // receiving editor can retrieve it.
        val extras = Bundle().apply {
            putString("com.example.richkeyboard.EXTRA_HTML_CONTENT", htmlContent)
        }

        val consumed = ic.commitContent(inputContentInfo, /* flags */ 0, extras)
        if (!consumed) {
            Log.w(TAG, "Editor did not consume commitContent – falling back to plain text.")
            pasteAsPlainText(ic, null, fallbackText = htmlContent)
        }
    }

    /**
     * Falls back to plain-text insertion using [InputConnection.commitText].
     *
     * @param ic     The active [InputConnection].
     * @param item   Optional [ClipData.Item] to extract plain text from.
     * @param fallbackText Explicit fallback text when item is null or has no text.
     */
    private fun pasteAsPlainText(
        ic: InputConnection,
        item: ClipData.Item?,
        fallbackText: String? = null
    ) {
        val plainText: CharSequence? = item?.text
            ?: item?.htmlText?.let { htmlToPlainText(it) }
            ?: fallbackText

        if (plainText.isNullOrEmpty()) {
            Log.w(TAG, "No usable text found on clipboard.")
            return
        }

        Log.d(TAG, "Pasting plain text: \"$plainText\"")
        ic.commitText(plainText, /* newCursorPosition */ 1)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Very basic HTML-to-plain-text conversion for fallback scenarios.
     * Strips all HTML tags and decodes a handful of common entities.
     * Replace with a proper Html.fromHtml() call for production use.
     */
    private fun htmlToPlainText(html: CharSequence): String {
        return html.toString()
            .replace(Regex("<[^>]*>"), "")   // strip tags
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }
}
