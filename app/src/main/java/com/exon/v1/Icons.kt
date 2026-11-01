package com.exon.v1

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Tüm ikonlar 24x24 SVG path verisinden (Lucide tarzı, çizgi ikonlar) üretilir.
 */
object SvgIcons {
    private fun svg(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return b.build()
    }

    val Send = svg("send", "M22 2L15 22L11 13L2 9Z", "M22 2L11 13")
    val Back = svg("back", "M19 12L5 12", "M12 19L5 12L12 5")
    val Radar = svg("radar", "M11 3a8 8 0 1 0 0 16a8 8 0 1 0 0 -16", "M21 21L16.7 16.7")
    val Edit = svg("edit", "M12 20L21 20", "M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19L3 20L4 16Z")
    val Check = svg("check", "M20 6L9 17L4 12")
    val Chat = svg("chat", "M21 15a2 2 0 0 1 -2 2L7 17L3 21L3 5a2 2 0 0 1 2 -2L19 3a2 2 0 0 1 2 2Z")
    val Bluetooth = svg("bluetooth", "M7 7L17 17L12 22L12 2L17 7L7 17")

    // Profil avatar ikonları
    val User = svg("user", "M19 21L19 19a4 4 0 0 0 -4 -4L9 15a4 4 0 0 0 -4 4L5 21", "M8 7a4 4 0 1 0 8 0a4 4 0 1 0 -8 0")
    val Bolt = svg("bolt", "M13 2L3 14L12 14L11 22L21 10L12 10Z")
    val Star = svg("star", "M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26Z")
    val Heart = svg("heart", "M20.84 4.61a5.5 5.5 0 0 0 -7.78 0L12 5.67L10.94 4.61a5.5 5.5 0 0 0 -7.78 7.78L4.22 13.45L12 21.23L19.78 13.45L20.84 12.39a5.5 5.5 0 0 0 0 -7.78Z")
    val Moon = svg("moon", "M21 12.79A9 9 0 1 1 11.21 3A7 7 0 0 0 21 12.79Z")
    val Smile = svg("smile", "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", "M8 14s1.5 2 4 2s4 -2 4 -2", "M9 9L9.01 9", "M15 9L15.01 9")

    val avatars: List<ImageVector> = listOf(User, Bolt, Star, Heart, Moon, Smile)
}
