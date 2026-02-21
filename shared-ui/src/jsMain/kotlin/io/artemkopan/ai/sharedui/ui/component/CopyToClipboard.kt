package io.artemkopan.ai.sharedui.ui.component

actual fun copyToClipboard(text: String) {
    js("window.navigator.clipboard.writeText(text)")
}
