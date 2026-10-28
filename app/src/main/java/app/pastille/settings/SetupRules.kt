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
    /** What is stored when chosen: [PastilleSettings.PREVIOUS_KEYBOARD] or an IME id. */
    val value: String?,
    /** The language stored with it, or null for the keyboard's own last one. */
    val subtype: Int? = null,
    val label: String,
    val note: String?,
    val available: Boolean,
    val selected: Boolean,
)

/**
 * The same set and grouping as Android's keyboard picker: every enabled input method but Pastille,
 * voice input included, in the system's order; under a keyboard with two or more languages, one row
 * for "whichever language it used last" and one per language. Then "Previous keyboard", then the
 * installed ones Android won't switch to until they're enabled.
 *
 * The selection shows what the keyboard will actually do ([returnKeyboardTarget]): no choice yet, or a
 * keyboard since disabled, selects the first typing keyboard's own row; a language since removed
 * selects its keyboard's row. Nothing is labelled "Main keyboard": while Pastille is up, Android
 * reports Pastille as the default, so there's no honest way to name another one.
 */
fun returnKeyboardChoices(
    chosen: String?,
    chosenSubtype: Int?,
    enabled: List<KeyboardEntry>,
    installed: List<KeyboardEntry>,
): List<ReturnChoice> {
    val target = returnKeyboardTarget(chosen, chosenSubtype, enabled)
    val enabledIds = enabled.map { it.id }.toSet()
    return buildList {
        enabled.forEach { keyboard ->
            val languages = keyboard.subtypes
            val shownSubtype = target?.subtype?.takeIf { target.id == keyboard.id && languages.size > 1 }
            add(
                ReturnChoice(
                    value = keyboard.id,
                    label = keyboard.label,
                    note = when {
                        languages.size > 1 -> "Whichever language it had last"
                        languages.size == 1 -> languages[0].describe()
                        else -> null
                    },
                    available = true,
                    selected = target?.id == keyboard.id && shownSubtype == null,
                ),
            )
            if (languages.size > 1) {
                languages.forEach { language ->
                    add(
                        ReturnChoice(
                            value = keyboard.id,
                            subtype = language.hash,
                            label = language.name,
                            note = listOfNotNull(language.tag, keyboard.label).joinToString(" · "),
                            available = true,
                            selected = shownSubtype == language.hash,
                        ),
                    )
                }
            }
        }
        add(
            ReturnChoice(
                value = PastilleSettings.PREVIOUS_KEYBOARD,
                label = "Previous keyboard",
                note = "Whichever you used before Pastille",
                available = true,
                selected = target == null,
            ),
        )
        installed.filter { it.id !in enabledIds }.distinctBy { it.id }.forEach { keyboard ->
            add(ReturnChoice(value = keyboard.id, label = keyboard.label, note = "Not enabled", available = false, selected = false))
        }
    }
}

private fun SubtypeEntry.describe(): String = listOfNotNull(tag, name.takeIf { it.isNotBlank() && it != tag }).joinToString(" · ")
