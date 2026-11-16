package io.github.otato.traininglog.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.otato.traininglog.feature.done.WorkoutDoneRoute
import io.github.otato.traininglog.feature.history.HistoryRoute
import io.github.otato.traininglog.feature.home.HomeRoute
import io.github.otato.traininglog.feature.migration.MigrationRoute
import io.github.otato.traininglog.feature.picker.ExercisePickerRoute
import io.github.otato.traininglog.feature.picker.PickerMode
import io.github.otato.traininglog.feature.routine.RoutineEditRoute
import io.github.otato.traininglog.feature.routine.RoutinesRoute
import io.github.otato.traininglog.feature.settings.SettingsRoute
import io.github.otato.traininglog.feature.workout.WorkoutRoute
import io.github.otato.traininglog.feature.exercise.ExerciseDetailRoute
import io.github.otato.traininglog.feature.exercise.ExerciseEditRoute

private fun NavBackStackEntry.arg(name: String): String = requireNotNull(arguments?.getString(name)) { "引数がありません: $name" }

private fun stringArg(name: String) = listOf(navArgument(name) { type = NavType.StringType })

/** 画面のつながり。引数の取り出しと、画面を移る操作だけをここに集める。 */
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            HomeRoute(
                onOpenWorkout = { navController.navigate(Routes.workout(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenRoutines = { navController.navigate(Routes.ROUTINES) },
                onOpenMigration = { navController.navigate(Routes.MIGRATION) },
            )
        }
        composable(Routes.ROUTINES) {
            RoutinesRoute(onOpenRoutine = { navController.navigate(Routes.routineEdit(it)) })
        }
        composable(Routes.HISTORY) {
            HistoryRoute(onOpenWorkout = { navController.navigate(Routes.workout(it)) })
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenMigration = { navController.navigate(Routes.MIGRATION) },
            )
        }
        composable(Routes.MIGRATION) {
            MigrationRoute(
                onBack = { navController.popBackStack() },
                onAddExercises = { navController.navigate(Routes.picker("migration", "-")) },
                onEditExercise = { navController.navigate(Routes.exerciseEdit(it)) },
            )
        }
        composable(Routes.WORKOUT, arguments = stringArg("workoutId")) { entry ->
            WorkoutRoute(
                workoutId = entry.arg("workoutId"),
                onBack = { navController.popBackStack() },
                onAddExercise = { navController.navigate(Routes.picker("workout", it)) },
                onOpenExercise = { navController.navigate(Routes.exerciseDetail(it)) },
                onFinished = { id ->
                    navController.navigate(Routes.done(id)) {
                        popUpTo(Routes.WORKOUT) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.DONE, arguments = stringArg("workoutId")) { entry ->
            WorkoutDoneRoute(
                workoutId = entry.arg("workoutId"),
                onClose = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.PICKER, arguments = listOf(navArgument("mode") { type = NavType.StringType }, navArgument("targetId") { type = NavType.StringType })) { entry ->
            ExercisePickerRoute(
                mode = when (entry.arg("mode")) {
                    "routine" -> PickerMode.Routine
                    "migration" -> PickerMode.Migration
                    else -> PickerMode.Workout
                },
                targetId = entry.arg("targetId"),
                onBack = { navController.popBackStack() },
                onCreateExercise = { navController.navigate(Routes.exerciseEdit("new")) },
            )
        }
        composable(Routes.ROUTINE_EDIT, arguments = stringArg("routineId")) { entry ->
            RoutineEditRoute(
                routineId = entry.arg("routineId"),
                onBack = { navController.popBackStack() },
                onAddExercise = { navController.navigate(Routes.picker("routine", it)) },
            )
        }
        composable(Routes.EXERCISE_DETAIL, arguments = stringArg("exerciseId")) { entry ->
            ExerciseDetailRoute(
                exerciseId = entry.arg("exerciseId"),
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.exerciseEdit(it)) },
            )
        }
        composable(Routes.EXERCISE_EDIT, arguments = stringArg("exerciseId")) { entry ->
            ExerciseEditRoute(
                exerciseId = entry.arg("exerciseId"),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
