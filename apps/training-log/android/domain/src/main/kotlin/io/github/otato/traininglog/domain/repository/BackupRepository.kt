package io.github.otato.traininglog.domain.repository

import io.github.otato.traininglog.domain.model.BackupSnapshot

interface BackupRepository {
    /** 全データの写しを作る（書き出し・自動バックアップ用）。 */
    suspend fun snapshot(): BackupSnapshot

    /**
     * 全データを、[snapshot] の中身に置き換える（F-DA-03）。1 つのトランザクションで行い、途中で失敗したら、元のまま変わらない。
     * この端末の状態（最後に書き出した日・広告を消した購入など）は、置き換えない。
     */
    suspend fun restore(snapshot: BackupSnapshot)
}
