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
    Gboard("gboard", "Gboard", 320, 0.44f),
    Comfortable("comfortable", "Comfortable", 368, 0.50f),
    Tall("tall", "Tall", 432, 0.55f),
    ;

    companion object {
        val Default = Comfortable

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

fun snippetColumns(widthDp: Int): Int = (widthDp / 168).coerceIn(2, 4)

fun imageColumns(widthDp: Int): Int = (widthDp / 112).coerceIn(2, 6)
