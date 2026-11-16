package io.github.otato.traininglog.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.otato.traininglog.designsystem.theme.AppTheme

/** [AppCellField] で打てる文字の種類。 */
enum class CellKeyboard {
    /** 整数（回数・秒数） */
    Integer,

    /** 小数（重量。0.25 など） */
    Decimal,

    /** マイナスも打てる小数（補助のついた加重）。数字キーボードには「−」がないので、文字のキーボードを出す */
    SignedDecimal,
}

/**
 * 一覧の中に並べる、数値の入力欄（乗り換え用の初期設定など）。高さ 48dp、枠つき、数字は中央に太字。
 * [enabled] が false のときは、「—」を点線の枠で出す（その種目では使わない欄）。
 * 打てない文字は、入力した時点で取り除く。
 */
@Composable
fun AppCellField(
    value: String,
    onValueChange: (String) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboard: CellKeyboard = CellKeyboard.Integer,
    imeAction: ImeAction = ImeAction.Next,
    maxLength: Int = DEFAULT_MAX_LENGTH,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = if (enabled) value else "—",
        onValueChange = { raw -> onValueChange(filterCellText(raw, keyboard, maxLength)) },
        enabled = enabled,
        singleLine = true,
        textStyle = AppTheme.typography.heading.copy(
            fontSize = 16.sp,
            fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal,
            color = if (enabled) colors.fg else colors.sub,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = when (keyboard) {
                CellKeyboard.Integer -> KeyboardType.Number
                CellKeyboard.Decimal -> KeyboardType.Decimal
                CellKeyboard.SignedDecimal -> KeyboardType.Text
            },
            imeAction = imeAction,
        ),
        modifier = modifier
            .height(48.dp)
            .semantics { this.contentDescription = contentDescription },
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (enabled) {
                            Modifier
                                .background(colors.surface, shape)
                                .border(1.5.dp, colors.line, shape)
                        } else {
                            Modifier.drawBehind {
                                drawRoundRect(
                                    color = colors.line,
                                    cornerRadius = CornerRadius(10.dp.toPx()),
                                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                                )
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}

private const val DEFAULT_MAX_LENGTH = 8

/** 入力された文字から、打てない文字を取り除く（全角の数字・記号は半角に直す）。[maxLength] を超える分は切る。 */
internal fun filterCellText(raw: String, keyboard: CellKeyboard, maxLength: Int = DEFAULT_MAX_LENGTH): String {
    val normalized = raw.map { ch ->
        when (ch) {
            in '０'..'９' -> '0' + (ch - '０')
            '．', '。' -> '.'
            '，', ',' -> '.'
            '−', '－', 'ー' -> '-'
            else -> ch
        }
    }
    return buildString {
        normalized.forEach { ch ->
            when {
                ch.isDigit() && ch in '0'..'9' -> append(ch)
                ch == '.' && keyboard != CellKeyboard.Integer && '.' !in this -> append(ch)
                ch == '-' && keyboard == CellKeyboard.SignedDecimal && isEmpty() -> append(ch)
            }
        }
    }.take(maxLength)
}
