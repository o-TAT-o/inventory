package io.github.otato.traininglog.ui.navigation

import android.net.Uri

/**
 * 画面の行き先。引数つきの行き先は、パターン（{...} 入り）と、実際の行き先を作る関数を対にして持つ。
 * id には「preset:bench_press」のような記号が入るので、行き先に入れるときは必ず Uri.encode する。
 */
object Routes {
    // 下のタブ
    const val HOME = "home"
    const val ROUTINES = "routines"
    const val HISTORY = "history"

    const val SETTINGS = "settings"

    /** 乗り換え用の初期設定（F-DA-05） */
    const val MIGRATION = "migration"

    const val WORKOUT = "workout/{workoutId}"
    fun workout(workoutId: String) = "workout/${Uri.encode(workoutId)}"

    const val DONE = "done/{workoutId}"
    fun done(workoutId: String) = "done/${Uri.encode(workoutId)}"

    /** 種目を選ぶ。mode は workout・routine・migration、targetId はそのワークアウト・ルーティンの id（migration では使わない） */
    const val PICKER = "picker/{mode}/{targetId}"
    fun picker(mode: String, targetId: String) = "picker/$mode/${Uri.encode(targetId)}"

    const val ROUTINE_EDIT = "routine/{routineId}"
    fun routineEdit(routineId: String) = "routine/${Uri.encode(routineId)}"

    /**
     * 種目選択から、ルーティンの編集へ返す値のキー。ルーティンに足した種目（ルーティンの種目）の id。
     * 受け取ったルーティンの編集は、その種目の目標をすぐ入れられるように開く。
     */
    const val RESULT_NEW_ROUTINE_ITEM = "newRoutineItemId"

    const val EXERCISE_DETAIL = "exercise/{exerciseId}"
    fun exerciseDetail(exerciseId: String) = "exercise/${Uri.encode(exerciseId)}"

    /** 種目の編集。新しく作るときは id に「new」を渡す */
    const val EXERCISE_EDIT = "exercise-edit/{exerciseId}"
    fun exerciseEdit(exerciseId: String) = "exercise-edit/${Uri.encode(exerciseId)}"

    val topLevel = setOf(HOME, ROUTINES, HISTORY)
}
