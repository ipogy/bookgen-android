package com.bookgen.app.ui.theme

import android.animation.ValueAnimator
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset

object BookGenMotion {
    const val EnterDurationMs = 260
    const val ExitDurationMs = 175
    const val WaveCycleDurationMs = 1600

    fun slideSpring(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun fadeEnterSpec(): FiniteAnimationSpec<Float> = tween(durationMillis = EnterDurationMs)

    fun fadeExitSpec(): FiniteAnimationSpec<Float> = tween(durationMillis = ExitDurationMs)
}

@Composable
fun rememberPrefersReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
            val animatorsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ValueAnimator.areAnimatorsEnabled()
            } else {
                true
            }
            !animatorsEnabled || scale == 0f
        }.getOrDefault(false)
    }
}
