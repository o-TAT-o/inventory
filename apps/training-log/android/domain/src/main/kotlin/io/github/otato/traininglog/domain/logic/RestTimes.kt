package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.model.WorkoutExercise

/** 休憩の長さ（F-TM-05）。 */
object RestTimes {
    const val STEP_SECONDS = 10
    const val EXTEND_SECONDS = 30

    /**
     * セットを完了したあとの休憩（秒）。
     * 種目の最後のセットを完了して次の種目に進むときは「種目のあとの休憩」、それ以外は「セット間の休憩」。
     * どちらも、ルーティンの種目に設定があればそれを、なければ設定の初期値を使う。
     */
    fun afterSet(
        isLastSetOfExercise: Boolean,
        workoutExercise: WorkoutExercise,
        settings: AppSettings,
    ): Int = if (isLastSetOfExercise) {
        workoutExercise.restAfterSeconds ?: settings.restAfterExerciseSec
    } else {
        workoutExercise.restSeconds ?: settings.restBetweenSetsSec
    }
}
