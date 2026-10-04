package app.pastille.ime

sealed interface PanelState {
    data object Browse : PanelState
    data object Add : PanelState
    data class Actions(val snippetId: Long) : PanelState
    data class Preview(val image: ImageItem) : PanelState
    data object Style : PanelState
}
