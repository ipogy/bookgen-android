package com.bookgen.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

enum class EditorialTooltipPlacement {
    Above,
    Below,
}

/**
 * Shared Material 3 tooltip wrapper for icon-only controls across Co-Author Studio.
 *
 * Each call site owns its own [rememberTooltipState] instance so hover / long-press / keyboard
 * tooltip triggers remain independent per action, and keeps `focusable = false` so Tab navigation
 * moves smoothly across toolbar and editor controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorialTooltipBox(
    label: String,
    modifier: Modifier = Modifier,
    shortcutHint: String? = null,
    placement: EditorialTooltipPlacement = EditorialTooltipPlacement.Below,
    content: @Composable () -> Unit,
) {
    val tooltipText = formatTooltipLabel(label = label, shortcutHint = shortcutHint)
    val anchorPosition = when (placement) {
        EditorialTooltipPlacement.Above -> TooltipAnchorPosition.Above
        EditorialTooltipPlacement.Below -> TooltipAnchorPosition.Below
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = anchorPosition),
        tooltip = {
            PlainTooltip {
                Text(
                    text = tooltipText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        state = rememberTooltipState(),
        modifier = modifier,
        focusable = false,
        content = content,
    )
}

internal fun formatTooltipLabel(label: String, shortcutHint: String? = null): String =
    if (shortcutHint.isNullOrBlank()) label else "$label ($shortcutHint)"
