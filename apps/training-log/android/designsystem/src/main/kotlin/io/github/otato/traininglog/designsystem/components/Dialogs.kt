package io.github.otato.traininglog.designsystem.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import io.github.otato.traininglog.designsystem.theme.AppTheme

/**
 * 数値を、数値キーボードで直接入力するダイアログ（F-WO-05）。小数（0.25 など）も入力できる。
 * 開いたとき、全体が選択されている。確定すると [onConfirm] に、入力された数を渡す。
 */
@Composable
fun NumberInputDialog(
    title: String,
    initialText: String,
    unitText: String,
    allowDecimal: Boolean,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length))) }
    val focusRequester = remember { FocusRequester() }
    val parsed = field.text.trim().replace(',', '.').toDoubleOrNull()
    val confirm = { parsed?.let(onConfirm) ?: Unit }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                suffix = { Text(unitText) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                isError = parsed == null,
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = parsed != null) { Text("決定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

/** 文字を入力するダイアログ（名前の入力など）。 */
@Composable
fun TextInputDialog(
    title: String,
    initialText: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    allowEmpty: Boolean = false,
) {
    var field by remember { mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length))) }
    val focusRequester = remember { FocusRequester() }
    val valid = allowEmpty || field.text.isNotBlank()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(field.text.trim()) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(field.text.trim()) }, enabled = valid) { Text("決定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

/** 確認のダイアログ。破壊的な操作は、[destructive] を true にして色で知らせる。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) AppTheme.colors.error else AppTheme.colors.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
