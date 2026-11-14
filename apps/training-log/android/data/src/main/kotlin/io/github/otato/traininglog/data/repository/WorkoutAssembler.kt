package io.github.otato.traininglog.data.repository

import io.github.otato.traininglog.domain.logic.Volume
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.SetRecord
import io.github.otato.traininglog.domain.model.Workout
import io.github.otato.traininglog.domain.model.WorkoutDetail
import io.github.otato.traininglog.domain.model.WorkoutExercise
import io.github.otato.traininglog.domain.model.WorkoutExerciseDetail
import io.github.otato.traininglog.domain.model.WorkoutSummary

/** DB から読んだバラバラの行を、画面が使う形（詳細・要約）にまとめる。DB には触れない。 */
internal object WorkoutAssembler {

    fun detail(
        workout: Workout,
        exercises: List<WorkoutExercise>,
        sets: List<SetRecord>,
        gearByWorkoutExercise: Map<String, Set<String>>,
        catalog: Map<String, Exercise>,
    ): WorkoutDetail {
        val setsByExercise = sets.groupBy { it.workoutExerciseId }
        val details = exercises.sortedBy { it.sortOrder }.mapNotNull { workoutExercise ->
            val exercise = catalog[workoutExercise.exerciseId] ?: return@mapNotNull null
            WorkoutExerciseDetail(
                workoutExercise = workoutExercise,
                exercise = exercise,
                sets = setsByExercise[workoutExercise.id].orEmpty().sortedBy { it.sortOrder },
                gearIds = gearByWorkoutExercise[workoutExercise.id].orEmpty(),
            )
        }
        return WorkoutDetail(workout, details)
    }

    fun summary(detail: WorkoutDetail, routineName: String?): WorkoutSummary {
        val done = detail.exercises.filter { it.completedSets.isNotEmpty() }
        return WorkoutSummary(
            workout = detail.workout,
            routineName = routineName,
            completedSetCount = done.sumOf { it.completedSets.size },
            volumeKg = done.sumOf { Volume.ofExercise(it) },
            exerciseNames = done.map { it.exercise.name },
            bodyParts = done.map { it.exercise.bodyPart }.distinct(),
        )
    }

    /** 期間内の複数のワークアウトを、まとめて要約にする。 */
    fun summaries(
        workouts: List<Workout>,
        exercises: List<WorkoutExercise>,
        sets: List<SetRecord>,
        catalog: Map<String, Exercise>,
        routineNames: Map<String, String>,
    ): List<WorkoutSummary> {
        val exercisesByWorkout = exercises.groupBy { it.workoutId }
        return workouts.map { workout ->
            val detail = detail(
                workout = workout,
                exercises = exercisesByWorkout[workout.id].orEmpty(),
                sets = sets,
                gearByWorkoutExercise = emptyMap(),
                catalog = catalog,
            )
            summary(detail, workout.routineId?.let(routineNames::get))
        }
    }
}
