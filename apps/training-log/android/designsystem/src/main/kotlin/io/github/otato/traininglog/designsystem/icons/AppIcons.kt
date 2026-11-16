package io.github.otato.traininglog.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * アイコン。デザインの SVG（24 × 24、線の太さ 2、丸い端）と同じ形を、パスの文字列から作る。
 * 色は、使うときの tint で決まる（ここでは黒）。アイコンのライブラリは使わない（軽さのため）。
 */
object AppIcons {
    private fun stroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { data ->
                    addPath(
                        pathData = PathParser().parsePathString(data).toNodes(),
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    private fun filled(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                paths.forEach { data ->
                    addPath(pathData = PathParser().parsePathString(data).toNodes(), fill = SolidColor(Color.Black))
                }
            }
            .build()

    val Back = stroke("back", "M15 5l-7 7 7 7")
    val Close = stroke("close", "M6 6l12 12M18 6L6 18")
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7.5")
    val Plus = stroke("plus", "M12 5v14M5 12h14")
    val Minus = stroke("minus", "M5 12h14")
    val Left = stroke("left", "M15 6l-6 6 6 6")
    val Right = stroke("right", "M9 6l6 6-6 6")
    val Up = stroke("up", "M6 15l6-6 6 6")
    val Down = stroke("down", "M6 9l6 6 6-6")
    val Drag = stroke("drag", "M5 9h14M5 15h14")

    /** 縦に 3 つの点（その行のメニューを開く） */
    val More = stroke("more", "M12 5h.01M12 12h.01M12 19h.01")

    /** 時計（あとから入力のしるし） */
    val Clock = stroke("clock", "M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0", "M12 8v4l3 2")
    val Timer = stroke("timer", "M5 13a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M12 9v4l2.5 2M9.5 3h5")
    val Skip = stroke("skip", "M6 6l8 6-8 6zM18 6v12")
    val Search = stroke("search", "M4 10.5a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0", "M15.5 15.5L20 20")
    val Trash = stroke("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
    val Home = stroke("home", "M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z")
    val List = stroke("list", "M9 7h11M9 12h11M9 17h11M4.5 7h.01M4.5 12h.01M4.5 17h.01")
    val Calendar = stroke("calendar", "M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M4 10h16M8 3v4M16 3v4")
    val Tune = stroke("tune", "M4 7h9M17 7h3M4 17h3M11 17h9", "M13 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M7 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    val Chart = stroke("chart", "M4 20V4M4 20h16M8 15l4-5 3 3 5-7")

    /** 取り込む（下向きの矢印と受け皿） */
    val Import = stroke("import", "M12 4v11M8 11l4 4 4-4M5 19h14")

    /** 乗り換え（行き来する矢印） */
    val Swap = stroke("swap", "M7 7h11l-3-3M17 17H6l3 3")
    val Play = filled("play", "M8 5l11 7-11 7z")
    val Star = filled("star", "M12 3l2.7 5.6 6.1.8-4.5 4.2 1.2 6.1-5.5-3-5.5 3 1.2-6.1-4.5-4.2 6.1-.8z")
}
