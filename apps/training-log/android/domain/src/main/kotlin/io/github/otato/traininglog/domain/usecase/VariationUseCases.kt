package io.github.otato.traininglog.domain.usecase

import io.github.otato.traininglog.domain.IdGenerator
import io.github.otato.traininglog.domain.UuidGenerator
import io.github.otato.traininglog.domain.repository.ExerciseRepository

/**
 * 種目の下に、バリエーション（フォーム違い）を作る（F-EX-14、F-EX-15）。
 * 親の部位・器具・記録の種類・重量と回数の数え方・重量の刻み・調整項目を引き継ぐ。その後は個別に変えられ、親を変えても反映しない。
 * バリエーションの下には作れない（入れ子は 2 段まで）。
 */
class AddVariation(
    private val exercises: ExerciseRepository,
    private val ids: IdGenerator = UuidGenerator,
) {
    /**
     * @param parentId 親にする種目の id。バリエーションを渡したときは、その親の下に作る
     * @return 作ったバリエーションの id
     */
    suspend operator fun invoke(parentId: String, name: String): String {
        val given = checkNotNull(exercises.getExercise(parentId)) { "種目が見つかりません: $parentId" }
        val parent = given.parentId?.let { exercises.getExercise(it) } ?: given
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "フォームの名前が空です" }

        val id = ids.next()
        exercises.save(
            parent.copy(
                id = id,
                parentId = parent.id,
                name = trimmed,
                memo = "",
                sortOrder = exercises.nextSortOrder(),
                hidden = false,
            ),
        )
        val fields = exercises.getAdjustmentFields(parent.id)
        if (fields.isNotEmpty()) {
            exercises.saveAdjustmentFields(id, fields.map { it.copy(id = ids.next(), exerciseId = id) })
        }
        return id
    }
}
