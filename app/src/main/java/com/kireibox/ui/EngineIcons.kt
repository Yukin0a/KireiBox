package com.kireibox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

// One glyph per engine, drawn on a 24dp grid: KRKR a fish, ONS a scroll, Tyrano a T-rex head, Artemis a moon and star.

internal object EngineIcons {
    val Kirikiri: ImageVector by lazy {
        glyph(
            "Kirikiri",
            "M2 12C4.5 7.6 9 6 12.8 7C14.8 7.5 16.3 8.8 17.3 10.3L22 7V17L17.3 13.7C16.3 15.2 14.8 16.5 12.8 17" +
                "C9 18 4.5 16.4 2 12Z M5.3 11a1.25 1.25 0 1 0 2.5 0a1.25 1.25 0 1 0 -2.5 0Z " +
                "M9 9.2C10 10.3 10.3 11.5 10.2 12.6C10.1 13.7 9.6 14.7 9 15.4C10.8 15.2 11.9 14.1 12.1 12.4C12.3 10.8 11.4 9.5 9 9.2Z",
            PathFillType.EvenOdd,
        )
    }

    val Ons: ImageVector by lazy {
        glyph(
            "Ons",
            "M4 2.8H20A1.6 1.6 0 0 1 20 6H4A1.6 1.6 0 0 1 4 2.8Z M6 6H18V18H6Z M4 18H20A1.6 1.6 0 0 1 20 21.2H4A1.6 1.6 0 0 1 4 18Z " +
                "M8.5 8.4H15.5V9.9H8.5Z M8.5 11.2H15.5V12.7H8.5Z M8.5 14H12.5V15.5H8.5Z",
            PathFillType.EvenOdd,
        )
    }

    val Tyrano: ImageVector by lazy {
        glyph(
            "Tyrano",
            "M2.5 12C2.5 7.5 6 4.5 10.5 4.2C14 4 17 4.8 19.5 6.2C21 7 22 8 22 9.5V11.5L20.6 11.1L20 12.9L18.7 11.9L17.5 13.7L16.2 12.5" +
                "L14.8 13.9L13.2 12.6C11 13.3 7 13 2.5 12Z M9.2 7.8a1.3 1.3 0 1 0 2.6 0a1.3 1.3 0 1 0 -2.6 0Z " +
                "M3.4 14.4C7.8 15.5 13.2 15.4 16.4 14.4L17.3 15.8L18.6 14.8L19.7 16.2L20.8 15.2L21.6 15.8C20.3 18.6 17 20.2 13 20.4" +
                "C8.2 20.6 4.6 18.6 3.4 14.4Z",
            PathFillType.EvenOdd,
        )
    }

    val Artemis: ImageVector by lazy {
        glyph(
            "Artemis",
            "M12 3C7.03 3 3 7.03 3 12S7.03 21 12 21S21 16.97 21 12C21 11.54 20.96 11.08 20.9 10.64C19.92 12.01 18.32 12.9 16.5 12.9" +
                "C13.52 12.9 11.1 10.48 11.1 7.5C11.1 5.69 11.99 4.08 13.36 3.1C12.92 3.04 12.46 3 12 3Z " +
                "M17.3 3.6L18.1 5.7L20.2 6.5L18.1 7.3L17.3 9.4L16.5 7.3L14.4 6.5L16.5 5.7Z",
            PathFillType.NonZero,
        )
    }

    private fun glyph(
        name: String,
        pathData: String,
        fillType: PathFillType,
    ): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
            PathParser().parsePathString(pathData).toNodes(),
            pathFillType = fillType,
            fill = SolidColor(Color.Black),
        ).build()
}
