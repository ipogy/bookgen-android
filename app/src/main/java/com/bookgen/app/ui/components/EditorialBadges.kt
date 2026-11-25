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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bookgen.app.domain.model.BookStatus
import com.bookgen.app.domain.model.SectionStatus
import com.bookgen.app.ui.theme.BookGenMotion
import com.bookgen.app.ui.theme.BookGenRadius
import com.bookgen.app.ui.theme.BookGenThemeExtras
import com.bookgen.app.ui.theme.rememberPrefersReducedMotion
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
    val spec = when {
        status == BookStatus.GENERATING -> BadgeSpec(
            bg = MaterialTheme.colorScheme.tertiaryContainer,
            fg = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Outlined.AutoMode,
            label = "執筆中",
        )
        emptyOrFailedCount > 0 -> BadgeSpec(
            bg = MaterialTheme.colorScheme.secondaryContainer,
            fg = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = Icons.Outlined.WarningAmber,
            label = "${emptyOrFailedCount}節要再開",
        )
        status == BookStatus.COMPLETED -> BadgeSpec(
            bg = MaterialTheme.colorScheme.surfaceContainerHigh,
            fg = extended.success,
            icon = Icons.Outlined.CheckCircle,
            label = "完了",
        )
        status == BookStatus.PARTIAL -> BadgeSpec(
            bg = MaterialTheme.colorScheme.secondaryContainer,
            fg = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = Icons.Outlined.EditNote,
            label = "執筆途中",
        )
        status == BookStatus.OUTLINED -> BadgeSpec(
            bg = MaterialTheme.colorScheme.primaryContainer,
            fg = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Outlined.ViewAgenda,
            label = "構成済み",
        )
        status == BookStatus.FAILED -> BadgeSpec(
            bg = MaterialTheme.colorScheme.errorContainer,
            fg = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Outlined.ErrorOutline,
            label = "要再試行",
        )
        else -> BadgeSpec(
            bg = MaterialTheme.colorScheme.surfaceContainerHigh,
            fg = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Outlined.EditNote,
            label = "下書き",
        )
    }

    Surface(
        modifier = modifier,
        shape = BookGenRadius.Badge,
        color = spec.bg,
        contentColor = spec.fg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = spec.label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun SectionStatusChip(
    status: SectionStatus,
    charCount: Long,
    modifier: Modifier = Modifier,
) {
    val extended = BookGenThemeExtras.colors
    val spec = when (status) {
        SectionStatus.COMPLETED -> BadgeSpec(
            bg = MaterialTheme.colorScheme.surfaceContainerHigh,
            fg = extended.success,
            icon = Icons.Outlined.CheckCircle,
            label = "完了 ${charCount}字",
        )
        SectionStatus.GENERATING -> BadgeSpec(
            bg = MaterialTheme.colorScheme.tertiaryContainer,
            fg = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Outlined.AutoMode,
            label = "執筆中…",
        )
        SectionStatus.EMPTY -> BadgeSpec(
            bg = MaterialTheme.colorScheme.secondaryContainer,
            fg = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = Icons.Outlined.WarningAmber,
            label = "要再試行 (空応答)",
        )
        SectionStatus.FAILED -> BadgeSpec(
            bg = MaterialTheme.colorScheme.errorContainer,
            fg = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Outlined.ErrorOutline,
            label = "エラー",
        )
        SectionStatus.PENDING -> BadgeSpec(
            bg = MaterialTheme.colorScheme.surfaceContainerHigh,
            fg = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Outlined.Schedule,
            label = "待機中",
        )
    }

    Surface(
        modifier = modifier,
        shape = BookGenRadius.Badge,
        color = spec.bg,
        contentColor = spec.fg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
            )
            Text(
                text = spec.label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private data class BadgeSpec(
    val bg: Color,
    val fg: Color,
    val icon: ImageVector,
    val label: String,
)

/**
 * M3 Expressive inspired horizontal wavy progress track used in Workspace and Export progress.
 * Animates gently when [isActive] is true unless system reduced-motion is enabled.
 */
@Composable
fun WavyProgressTrack(
    progress: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val prefersReducedMotion = rememberPrefersReducedMotion()
    val clampedProgress = progress.coerceIn(0f, 1f)
    val shouldWave = isActive && clampedProgress < 1f && !prefersReducedMotion
    val animatedProgressState = animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = if (prefersReducedMotion) {
            tween(durationMillis = 0)
        } else {
            spring(dampingRatio = 0.85f, stiffness = 320f)
        },
        label = "wavyProgressValue",
    )
    val phaseState = if (shouldWave) {
        val phaseTransition = rememberInfiniteTransition(label = "wavyPhase")
        phaseTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = BookGenMotion.WaveCycleDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wavePhaseRadians",
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .drawWithCache {
                val path = Path()
                val strokePx = 3.dp.toPx()
                val strokeStyle = Stroke(width = strokePx, cap = StrokeCap.Round)
                val wavelengthPx = 22.dp.toPx()
                val maxAmplitudePx = 2.2.dp.toPx()
                val midY = size.height / 2f

                onDrawBehind {
                    val curProgress = animatedProgressState.value
                    val activeEndX = (size.width * curProgress).coerceIn(0f, size.width)

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
                        val amplitude = if (shouldWave && curProgress < 1f) maxAmplitudePx else 0f
                        if (amplitude <= 0.1f) {
                            drawLine(
                                color = activeColor,
                                start = Offset(0f, midY),
                                end = Offset(activeEndX, midY),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round,
                            )
                        } else {
                            val phase = phaseState?.value ?: 0f
                            path.rewind()
                            var x = 0f
                            var first = true
                            val freq = (2f * PI.toFloat()) / wavelengthPx
                            while (x <= activeEndX) {
                                val y = midY + amplitude * sin(x * freq - phase)
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
                                midY + amplitude * sin(activeEndX * freq - phase),
                            )
                            drawPath(
                                path = path,
                                color = activeColor,
                                style = strokeStyle,
                            )
                        }
                    }
                }
            },
    )
}
