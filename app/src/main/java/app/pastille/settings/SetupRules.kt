package app.pastille.settings

/**
 * Whether the setup card still asks for photo access. It asks once: declining, or choosing
 * "Select photos", is an answer, and Settings › Photo access is where it changes afterwards.
 */
fun setupNeedsPhotos(granted: Boolean, partialOnly: Boolean, answered: Boolean): Boolean =
    !granted && !partialOnly && !answered

/** What tapping Settings › Photo access does. */
enum class PhotoAccessAction {
    /** The system permission prompt: never answered yet, or "Selected photos" (Android 14 offers "Allow all"). */
    Ask,

    /** The app's page in system settings: already granted (to revoke), or declined before (the prompt may not show again). */
    OpenSystemSettings,
}

fun photoAccessAction(granted: Boolean, partialOnly: Boolean, answered: Boolean): PhotoAccessAction = when {
    granted -> PhotoAccessAction.OpenSystemSettings
    partialOnly -> PhotoAccessAction.Ask
    answered -> PhotoAccessAction.OpenSystemSettings
    else -> PhotoAccessAction.Ask
}

/** One row of Settings › Switch back to. */
data class ReturnChoice(
    /** What is stored when chosen: null (main), [PastilleSettings.PREVIOUS_KEYBOARD], or an IME id. */
    val value: String?,
    val label: String,
    val note: String?,
    val available: Boolean,
    val selected: Boolean,
)

/**
 * Every typing keyboard on the phone, the enabled ones first in the system's order, then
 * "Previous keyboard", then the installed ones Android won't switch to until they're enabled.
 * The first enabled keyboard is the main one and stores null, so it follows the system's order and
 * never appears twice. The selection shows what the keyboard will actually do
 * ([returnKeyboardTarget]): a chosen keyboard that has since been disabled falls back to the main one.
 *
 * [enabled] and [installed] are (id, label) pairs; [installed] may include the enabled ones.
 */
fun returnKeyboardChoices(
    chosen: String?,
    enabled: List<Pair<String, String>>,
    installed: List<Pair<String, String>>,
): List<ReturnChoice> {
    val enabledIds = enabled.map { it.first }
    val previous = chosen == PastilleSettings.PREVIOUS_KEYBOARD
    val target = if (previous) null else returnKeyboardTarget(chosen, enabledIds)
    return buildList {
        enabled.forEachIndexed { index, (id, label) ->
            add(
                ReturnChoice(
                    value = if (index == 0) null else id,
                    label = label,
                    note = if (index == 0) "Main keyboard" else null,
                    available = true,
                    selected = !previous && id == target,
                ),
            )
        }
        add(
            ReturnChoice(
                value = PastilleSettings.PREVIOUS_KEYBOARD,
                label = "Previous keyboard",
                note = "Whichever you used before Pastille",
                available = true,
                selected = previous || target == null,
            ),
        )
        installed.filter { it.first !in enabledIds }.distinctBy { it.first }.forEach { (id, label) ->
            add(ReturnChoice(value = id, label = label, note = "Not enabled", available = false, selected = false))
        }
    }
}
