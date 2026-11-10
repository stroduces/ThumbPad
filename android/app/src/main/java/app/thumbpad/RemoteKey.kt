package app.thumbpad

internal data class RemoteKey(val usage: Int, val label: String) {
    val modifier get() = usage in 0xE0..0xE7
    val legacyShortcut get() = when (usage) {
        0x28 -> 5
        0x29 -> 6
        0x2B -> 7
        0x2A -> 8
        else -> null
    }
}

// USB HID keyboard usages. The companion translates physical keys for the host layout.
internal val remoteKeys = buildList {
    fun group(start: Int, labels: List<String>) {
        labels.forEachIndexed { index, label -> add(RemoteKey(start + index, label)) }
    }
    group(0xE0, listOf("Left Control", "Left Shift", "Left Option / Alt", "Left Command / Win",
        "Right Control", "Right Shift", "Right Option / Alt", "Right Command / Win"))
    group(0x28, listOf("Enter", "Escape", "Backspace", "Tab", "Space"))
    group(0x49, listOf("Insert / Help", "Home", "Page Up", "Delete", "End", "Page Down",
        "Right Arrow", "Left Arrow", "Down Arrow", "Up Arrow"))
    group(0x04, ('A'..'Z').map(Char::toString))
    group(0x1E, "1234567890".map(Char::toString))
    group(0x2D, listOf("−", "=", "[", "]", "\\"))
    group(0x33, listOf(";", "'", "`", ",", ".", "/", "Caps Lock"))
    group(0x3A, (1..12).map { "F$it" })
    group(0x68, (13..20).map { "F$it" })
    group(0x53, listOf("Num Lock / Clear", "Keypad /", "Keypad *", "Keypad −", "Keypad +",
        "Keypad Enter") + "1234567890".map { "Keypad $it" } + "Keypad .")
}

internal fun remoteKey(usage: Int, defaultUsage: Int): RemoteKey =
    remoteKeys.firstOrNull { it.usage == usage } ?: remoteKeys.first { it.usage == defaultUsage }
