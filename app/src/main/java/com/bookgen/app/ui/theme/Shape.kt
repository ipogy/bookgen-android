package com.bookgen.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val BookGenShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // radius.badge
    small = RoundedCornerShape(8.dp),       // radius.control
    medium = RoundedCornerShape(12.dp),     // radius.card
    large = RoundedCornerShape(16.dp),      // radius.fab
    extraLarge = RoundedCornerShape(24.dp), // radius.sheet
)

object BookGenRadius {
    val Badge = RoundedCornerShape(4.dp)
    val Control = RoundedCornerShape(8.dp)
    val Card = RoundedCornerShape(12.dp)
    val Fab = RoundedCornerShape(16.dp)
    val Sheet = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp,
    )
    val Chip = RoundedCornerShape(9999.dp)
}

object BookGenSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Section = 32.dp
}
