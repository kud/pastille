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

enum class KeyboardMode(val key: String) {
    Snippets("snippets"),
    Images("images"),
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

// With one mode switched off the keyboard shows only the other, whatever was last open.
fun effectiveMode(saved: KeyboardMode, snippetsEnabled: Boolean, imagesEnabled: Boolean): KeyboardMode = when {
    !snippetsEnabled && imagesEnabled -> KeyboardMode.Images
    !imagesEnabled -> KeyboardMode.Snippets
    else -> saved
}
