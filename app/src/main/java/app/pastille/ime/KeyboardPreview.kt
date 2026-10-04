package app.pastille.ime

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.pastille.model.CategoryRecord
import app.pastille.model.SnippetRecord
import app.pastille.settings.KeyboardStyle
import app.pastille.settings.PanelHeight
import app.pastille.settings.TOOLBAR_HEIGHT_DP
import app.pastille.settings.panelHeightDp

// Fixed sample data: the setting must never become an accidental display of private snippets.
private val sampleState = KeyboardUiState(
    categories = listOf(CategoryRecord(id = 1, name = "Work")),
    snippets = listOf(
        SnippetRecord(id = 2, title = "Wi-Fi password", text = ""),
        SnippetRecord(id = 3, title = "Home address", text = ""),
        SnippetRecord(id = 4, title = "Thanks!", text = ""),
        SnippetRecord(id = 5, title = "Sample", text = "", categoryId = 1),
    ),
)

@Composable
private fun isSystemDark(): Boolean =
    LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

@Composable
fun KeyboardStylePreview(style: KeyboardStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = isSystemDark()
    val palette = remember(style, dark) { keyboardPalette(context, style, dark) }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clearAndSetSemantics { },
    ) {
        CompositionLocalProvider(LocalDensity provides Density(density.density * 0.8f, density.fontScale)) {
            KeyboardTheme(palette = palette) {
                KeyboardPanel(
                    state = sampleState.copy(darkTheme = dark),
                    actions = NoKeyboardActions,
                    contentHeight = 120.dp,
                    previewMode = true,
                )
            }
        }
    }
}

@Composable
fun KeyboardHeightPreview(
    style: KeyboardStyle,
    height: PanelHeight,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val dark = isSystemDark()
    val palette = remember(style, dark) { keyboardPalette(context, style, dark) }
    val shortSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(1)
    val longSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp).coerceAtLeast(1)
    val windowHeight = if (landscape) shortSide else longSide
    val panelFraction = panelHeightDp(height, landscape, windowHeight).toFloat() / windowHeight
    val silhouetteHeight = if (landscape) 140.dp else 200.dp
    val aspect = if (landscape) longSide.toFloat() / shortSide else shortSide.toFloat() / longSide
    val panelHeight by animateDpAsState(
        targetValue = silhouetteHeight * panelFraction,
        animationSpec = tween(220, easing = PastilleMotion.EmphasizedDecelerate),
        label = "previewPanelHeight",
    )
    val stripHeight = silhouetteHeight * (TOOLBAR_HEIGHT_DP.toFloat() / windowHeight)
    Box(modifier = modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .height(silhouetteHeight)
                .width(silhouetteHeight * aspect)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(0.8f, 0.6f, 0.7f).forEach { fraction ->
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
            Column(modifier = Modifier.fillMaxWidth().height(panelHeight).background(palette.tray)) {
                Box(Modifier.fillMaxWidth().height(stripHeight).background(palette.strip))
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(6) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(palette.key),
                        )
                    }
                }
            }
        }
    }
}
