package com.meucoach.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val McCardShape = RoundedCornerShape(28.dp)
val McPillShape = RoundedCornerShape(percent = 50)

val McShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = McCardShape,
        extraLarge = RoundedCornerShape(36.dp),
    )
