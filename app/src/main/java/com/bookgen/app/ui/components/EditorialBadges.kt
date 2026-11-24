package com.bookgen.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bookgen.app.domain.model.BookStatus
import com.bookgen.app.domain.model.SectionStatus
import com.bookgen.app.ui.theme.BookGenRadius
import com.bookgen.app.ui.theme.BookGenThemeExtras
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = BookGenThemeExtras.colors.borderSubtle,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = BookGenRadius.Card,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun GenreBadge(
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = BookGenRadius.Badge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun BookStatusBadge(
    status: BookStatus,
    emptyOrFailedCount: Int,
    modifier: Modifier = Modifier,
) {
    val extended = BookGenThemeExtras.colors
    val (bg, fg, label) = when {
        status == BookStatus.GENERATING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "執筆中",
        )
        emptyOrFailedCount > 0 -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "${emptyOrFailedCount}節要再開",
        )
        status == BookStatus.COMPLETED -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            extended.success,
            "完了",
        )
        status == BookStatus.PARTIAL -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "執筆途中",
        )
        status == BookStatus.OUTLINED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "構成済み",
        )
        status == BookStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "要再試行",
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "下書き",
        )
    }

    Surface(
        modifier = modifier,
        shape = BookGenRadius.Badge,
        color = bg,
        contentColor = fg,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun SectionStatusChip(
    status: SectionStatus,
    charCount: Long,
    modifier: Modifier = Modifier,
) {
    val extended = BookGenThemeExtras.colors
    val (bg, fg, label) = when (status) {
        SectionStatus.COMPLETED -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            extended.success,
            "完了 ${charCount}字",
        )
        SectionStatus.GENERATING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "執筆中…",
        )
        SectionStatus.EMPTY -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "要再試行 (空応答)",
        )
        SectionStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "エラー",
        )
        SectionStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "待機中",
        )
    }

    Surface(
        modifier = modifier,
        shape = BookGenRadius.Badge,
        color = bg,
        contentColor = fg,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * M3 Expressive inspired horizontal wavy progress track used in Workspace and Export progress.
 * Animates gently when [isActive] is true and snaps smoothly to flat line on static progress.
 */
@Composable
fun WavyProgressTrack(
    progress: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
        label = "wavyProgressValue",
    )
    val phaseTransition = rememberInfiniteTransition(label = "wavyPhase")
    val phase by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhaseRadians",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp),
    ) {
        val midY = size.height / 2f
        val strokePx = 3.dp.toPx()
        val activeEndX = (size.width * animatedProgress).coerceIn(0f, size.width)

        // Inactive flat track
        if (activeEndX < size.width) {
            drawLine(
                color = trackColor,
                start = Offset(activeEndX, midY),
                end = Offset(size.width, midY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }

        if (activeEndX > 1f) {
            val amplitude = if (isActive && animatedProgress < 1f) 2.2.dp.toPx() else 0f
            if (amplitude <= 0.1f) {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, midY),
                    end = Offset(activeEndX, midY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
            } else {
                val path = Path()
                val wavelengthPx = 22.dp.toPx()
                var x = 0f
                var first = true
                while (x <= activeEndX) {
                    val y = midY + amplitude * sin((x / wavelengthPx) * 2f * PI.toFloat() - phase)
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                    x += 4f
                }
                path.lineTo(
                    activeEndX,
                    midY + amplitude * sin((activeEndX / wavelengthPx) * 2f * PI.toFloat() - phase),
                )
                drawPath(
                    path = path,
                    color = activeColor,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
    }
}
