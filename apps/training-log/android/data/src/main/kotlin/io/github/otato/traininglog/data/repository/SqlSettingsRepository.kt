package io.github.otato.traininglog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.otato.traininglog.data.db.TrainingLogDatabase
import io.github.otato.traininglog.domain.logic.DayBoundary
import io.github.otato.traininglog.domain.model.AppSettings
import io.github.otato.traininglog.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 設定は、キーと値の組として保存する。値が無い・読めないものは、初期値にする。 */
internal class SqlSettingsRepository(
    private val db: TrainingLogDatabase,
    private val io: CoroutineDispatcher,
) : SettingsRepository {
    private val queries = db.settingQueries

    override fun observe(): Flow<AppSettings> =
        queries.selectAll().asFlow().mapToList(io).map { rows -> parse(rows.associate { it.key to it.content }) }

    override suspend fun get(): AppSettings = withContext(io) {
        parse(queries.selectAll().executeAsList().associate { it.key to it.content })
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        withContext(io) {
            db.transaction {
                val current = parse(queries.selectAll().executeAsList().associate { it.key to it.content })
                val next = transform(current)
                serialize(next).forEach { (key, value) -> queries.upsert(key, value) }
            }
        }
    }

    private fun parse(map: Map<String, String>): AppSettings {
        val d = AppSettings()
        return AppSettings(
            weightUnit = map.enum("weight_unit", d.weightUnit),
            weightStepKg = map["weight_step_kg"]?.toDoubleOrNull() ?: d.weightStepKg,
            weightStepLb = map["weight_step_lb"]?.toDoubleOrNull() ?: d.weightStepLb,
            themeMode = map.enum("theme_mode", d.themeMode),
            weekStart = map.enum("week_start", d.weekStart),
            dayBoundaryHour = DayBoundary.normalize(map["day_boundary_hour"]?.toIntOrNull() ?: d.dayBoundaryHour),
            homeStripMode = map.enum("home_strip_mode", d.homeStripMode),
            restBetweenSetsSec = map["rest_between_sets_sec"]?.toIntOrNull() ?: d.restBetweenSetsSec,
            restAfterExerciseSec = map["rest_after_exercise_sec"]?.toIntOrNull() ?: d.restAfterExerciseSec,
            autoStartRest = map["auto_start_rest"]?.toBooleanStrictOrNull() ?: d.autoStartRest,
            soundOn = map["sound_on"]?.toBooleanStrictOrNull() ?: d.soundOn,
            vibrationOn = map["vibration_on"]?.toBooleanStrictOrNull() ?: d.vibrationOn,
        )
    }

    private fun serialize(s: AppSettings): Map<String, String> = mapOf(
        "weight_unit" to s.weightUnit.name,
        "weight_step_kg" to s.weightStepKg.toString(),
        "weight_step_lb" to s.weightStepLb.toString(),
        "theme_mode" to s.themeMode.name,
        "week_start" to s.weekStart.name,
        "day_boundary_hour" to DayBoundary.normalize(s.dayBoundaryHour).toString(),
        "home_strip_mode" to s.homeStripMode.name,
        "rest_between_sets_sec" to s.restBetweenSetsSec.toString(),
        "rest_after_exercise_sec" to s.restAfterExerciseSec.toString(),
        "auto_start_rest" to s.autoStartRest.toString(),
        "sound_on" to s.soundOn.toString(),
        "vibration_on" to s.vibrationOn.toString(),
    )

    private inline fun <reified E : Enum<E>> Map<String, String>.enum(key: String, default: E): E =
        this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
