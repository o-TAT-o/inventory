package io.github.otato.traininglog.domain.repository

import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.BaselineDetail
import kotlinx.coroutines.flow.Flow

/** 乗り換え用の初期設定（F-DA-05）の保存。 */
interface BaselineRepository {
    /** 初期設定の一覧（一覧に足した順。種目つき）。 */
    fun observeBaselines(): Flow<List<BaselineDetail>>

    /** 種目を、値が空の行として、一覧に足す。すでにある種目は、そのまま。 */
    suspend fun addExercises(exerciseIds: List<String>)

    /** 値を保存する（一覧にない種目なら、足して保存する）。 */
    suspend fun save(baseline: Baseline)

    /** 一覧から外す。 */
    suspend fun remove(exerciseId: String)
}
