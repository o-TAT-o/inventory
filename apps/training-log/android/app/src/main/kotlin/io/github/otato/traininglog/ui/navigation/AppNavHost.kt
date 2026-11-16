package io.github.otato.traininglog.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.otato.traininglog.feature.data.DataRoute
import io.github.otato.traininglog.feature.done.WorkoutDoneRoute
import io.github.otato.traininglog.feature.gear.GearRoute
import io.github.otato.traininglog.feature.settings.InfoKind
import io.github.otato.traininglog.feature.settings.InfoScreen
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
            RoutinesRoute(
                onOpenRoutine = { navController.navigate(Routes.routineEdit(it)) },
                onOpenWorkout = { navController.navigate(Routes.workout(it)) },
                onOpenExercise = { navController.navigate(Routes.exerciseDetail(it)) },
                onCreateExercise = { navController.navigate(Routes.exerciseEdit("new")) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryRoute(onOpenWorkout = { navController.navigate(Routes.workout(it)) })
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenGear = { navController.navigate(Routes.GEAR) },
                onOpenData = { navController.navigate(Routes.DATA) },
                onOpenInfo = { navController.navigate(Routes.info(it.route)) },
            )
        }
        composable(Routes.GEAR) { GearRoute(onBack = { navController.popBackStack() }) }
        composable(Routes.DATA) {
            DataRoute(
                onBack = { navController.popBackStack() },
                onOpenMigration = { navController.navigate(Routes.MIGRATION) },
            )
        }
        composable(Routes.INFO, arguments = stringArg("kind")) { entry ->
            InfoScreen(kind = InfoKind.fromRoute(entry.arg("kind")), onBack = { navController.popBackStack() })
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
                onPicked = { createdItemId ->
                    // ルーティンに足したときは、前の画面（ルーティンの編集）に、足した種目の id を渡す
                    if (createdItemId != null) {
                        navController.previousBackStackEntry?.savedStateHandle?.set(Routes.RESULT_NEW_ROUTINE_ITEM, createdItemId)
                    }
                    navController.popBackStack()
                },
                onCreateExercise = { navController.navigate(Routes.exerciseEdit("new")) },
            )
        }
        composable(Routes.ROUTINE_EDIT, arguments = stringArg("routineId")) { entry ->
            val newItemId by entry.savedStateHandle.getStateFlow<String?>(Routes.RESULT_NEW_ROUTINE_ITEM, null)
                .collectAsStateWithLifecycle()
            RoutineEditRoute(
                routineId = entry.arg("routineId"),
                newItemId = newItemId,
                onNewItemShown = { entry.savedStateHandle[Routes.RESULT_NEW_ROUTINE_ITEM] = null },
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
                onOpenExercise = { navController.navigate(Routes.exerciseEdit(it)) },
            )
        }
    }
}
