package io.github.otato.traininglog.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.otato.traininglog.data.backup.BackupJson
import io.github.otato.traininglog.domain.logic.CsvExport
import io.github.otato.traininglog.domain.model.BackupFormatException
import io.github.otato.traininglog.domain.model.BackupSnapshot
import io.github.otato.traininglog.domain.repository.BackupRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 端末の中の自動バックアップ 1 つ。 */
data class AutoBackupFile(val file: File, val createdAt: Instant)

/**
 * 書き出し・読み込み・自動バックアップの、ファイルまわり（F-DA-02〜04、F-DA-10）。
 *
 * - 書き出し: キャッシュの中にファイルを作り、共有機能に渡す（保存先は、利用者が選ぶ）
 * - 読み込み: 利用者が選んだファイルを読む。置き換える前に、いまのデータを自動バックアップに残す
 * - 自動バックアップ: アプリの中（ほかのアプリからは見えない場所）に、直近 [GENERATIONS] 回分を残す
 */
class BackupFiles(
    private val context: Context,
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    private val exportDir get() = File(context.cacheDir, "exports").apply { mkdirs() }
    private val autoDir get() = File(context.filesDir, "backups").apply { mkdirs() }
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** 全データを JSON に書き出し、共有機能に渡すための Intent を返す。最後に書き出した日を覚える（F-DA-09）。 */
    suspend fun exportJson(): Intent = withContext(Dispatchers.IO) {
        val file = newExportFile("json")
        file.writeText(BackupJson.encode(backup.snapshot()))
        settings.update { it.copy(lastExportAtMillis = clock.millis()) }
        shareIntent(file, "application/json")
    }

    /** 記録を CSV に書き出し、共有機能に渡すための Intent を返す（F-DA-04）。 */
    suspend fun exportCsv(): Intent = withContext(Dispatchers.IO) {
        val file = newExportFile("csv")
        file.writeText(CsvExport.build(backup.snapshot()))
        shareIntent(file, "text/csv")
    }

    /**
     * 利用者が選んだファイルを読む。
     * @throws BackupFormatException 読めないとき（画面に出す文言を持つ）
     */
    suspend fun read(uri: Uri): BackupSnapshot = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // 大きすぎるファイルは、このアプリの書き出しではない
                val bytes = input.readNBytesCompat(MAX_BYTES + 1)
                if (bytes.size > MAX_BYTES) throw BackupFormatException("このファイルは、大きすぎて読み込めません")
                bytes.toString(Charsets.UTF_8)
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        } ?: throw BackupFormatException("ファイルを開けませんでした")
        BackupJson.decode(text)
    }

    /** 自動バックアップを読む。 */
    suspend fun read(file: AutoBackupFile): BackupSnapshot = withContext(Dispatchers.IO) {
        val text = try {
            file.file.readText()
        } catch (e: IOException) {
            throw BackupFormatException("バックアップを開けませんでした")
        }
        BackupJson.decode(text)
    }

    /** 全データを置き換える。置き換える前に、いまのデータを自動バックアップに残す（戻せるように）。 */
    suspend fun restore(snapshot: BackupSnapshot) {
        writeAutoBackup()
        backup.restore(snapshot)
    }

    /** 自動バックアップ（新しい順）。 */
    suspend fun autoBackups(): List<AutoBackupFile> = withContext(Dispatchers.IO) {
        autoDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { AutoBackupFile(it, Instant.ofEpochMilli(it.lastModified())) }
    }

    /**
     * ワークアウトを終えたあとに呼ぶ。設定がオンで、[finishedAt] より後のバックアップがまだなければ、1 つ残す。
     */
    suspend fun autoBackupAfter(finishedAt: Instant) {
        val current = settings.get()
        if (!current.autoBackup) return
        val last = current.lastAutoBackupAtMillis
        if (last != null && last >= finishedAt.toEpochMilli()) return
        writeAutoBackup()
    }

    private suspend fun writeAutoBackup() = withContext(Dispatchers.IO) {
        try {
            val file = File(autoDir, "auto-${LocalDateTime.now(clock).format(stamp)}.json")
            file.writeText(BackupJson.encode(backup.snapshot()))
            autoDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
                .sortedByDescending { it.lastModified() }
                .drop(GENERATIONS)
                .forEach { it.delete() }
            settings.update { it.copy(lastAutoBackupAtMillis = clock.millis()) }
        } catch (e: IOException) {
            // 空き容量がないなど。バックアップが作れなくても、記録は止めない
        }
    }

    private fun newExportFile(extension: String): File {
        // 前に書き出したファイルは、もう共有先に渡っているので消す
        exportDir.listFiles()?.forEach { it.delete() }
        return File(exportDir, "training-log-${LocalDateTime.now(clock).format(stamp)}.$extension")
    }

    private fun shareIntent(file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(file.name, uri)
        return Intent.createChooser(send, "保存先を選ぶ")
    }

    private companion object {
        const val GENERATIONS = 3
        const val MAX_BYTES = 50 * 1024 * 1024
    }
}

/** minSdk 26 では `InputStream.readNBytes` が使えないので、同じことをする。 */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    var total = 0
    while (total < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - total))
        if (read < 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}
