package app.pastille.settings

enum class KeyboardStyle(val key: String) {
    Auto("auto"),
    GboardDark("gboard_dark"),
    GboardLight("gboard_light"),
    MaterialYou("material_you"),
    ;

    companion object {
        fun fromKey(key: String?): KeyboardStyle = entries.firstOrNull { it.key == key } ?: Auto
    }
}

// Portrait heights are total panel heights in dp (toolbar included, nav bar excluded);
// landscape ones are fractions of the window height.
enum class PanelHeight(val key: String, val label: String, val portraitDp: Int, val landscapeFraction: Float) {
    Compact("compact", "Compact", 272, 0.38f),
    Standard("gboard", "Standard", 320, 0.44f),
    Roomy("comfortable", "Roomy", 368, 0.50f),
    Tall("tall", "Tall", 432, 0.55f),
    ;

    companion object {
        val Default = Roomy

        fun fromKey(key: String?): PanelHeight = entries.firstOrNull { it.key == key } ?: Default
    }
}

// Declared in tab-bar order: Snippets, Stickers, Images.
enum class KeyboardMode(val key: String, val label: String) {
    Snippets("snippets", "Snippets"),
    Stickers("stickers", "Stickers"),
    Images("images", "Images"),
    ;

    companion object {
        fun fromKey(key: String?): KeyboardMode = entries.firstOrNull { it.key == key } ?: Snippets
    }
}

const val TOOLBAR_HEIGHT_DP = 48
private const val PORTRAIT_CAP = 0.5f

// Total panel height in dp for the window the keyboard sits in.
fun panelHeightDp(preset: PanelHeight, landscape: Boolean, windowHeightDp: Int): Int {
    val total = if (landscape) {
        windowHeightDp * preset.landscapeFraction
    } else {
        minOf(preset.portraitDp.toFloat(), windowHeightDp * PORTRAIT_CAP)
    }
    return total.toInt().coerceAtLeast(TOOLBAR_HEIGHT_DP + 96)
}

fun tileColumns(widthDp: Int): Int = (widthDp / 195).coerceIn(2, 4)

// A switched-off mode gives way to the first enabled one in bar order.
fun effectiveMode(saved: KeyboardMode, enabled: Set<KeyboardMode>): KeyboardMode = when {
    saved in enabled -> saved
    else -> KeyboardMode.entries.firstOrNull { it in enabled } ?: KeyboardMode.Snippets
}

// Square sticker cells about 72dp wide: columns follow the width, rows the panel height.
fun stickerColumns(widthDp: Int): Int = ((widthDp - 12) / 72).coerceIn(4, 10)

// The last enabled mode can't be switched off.
fun canSwitchOff(mode: KeyboardMode, enabled: Set<KeyboardMode>): Boolean = mode !in enabled || enabled.size > 1
