package io.github.otato.traininglog.feature.migration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.otato.traininglog.designsystem.components.CellKeyboard
import io.github.otato.traininglog.domain.logic.Baselines
import io.github.otato.traininglog.domain.logic.WeightUnits
import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.BaselineDetail
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.RecordType
import io.github.otato.traininglog.domain.model.WeightUnit
import io.github.otato.traininglog.domain.repository.BaselineRepository
import io.github.otato.traininglog.domain.repository.SettingsRepository
import io.github.otato.traininglog.ui.common.label
import io.github.otato.traininglog.ui.common.tag
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一覧の 1 つの入力欄。[initialText] は、画面を開いたときの値（そのあとは、画面が打った文字を持つ）。 */
data class CellUi(
    val initialText: String,
    val enabled: Boolean,
    val keyboard: CellKeyboard,
    val description: String,
)

data class MigrationRowUi(
    val exerciseId: String,
    val name: String,
    /** 「バーベル · 合計」「ダンベル · 片手 · 片側」「自重 · 回数のみ」 */
    val meta: String,
    val weight: CellUi,
    /** 回数（時間の種目では秒数） */
    val reps: CellUi,
    /** 直近のワークアウトでやったセット数（空欄でもよい） */
    val sets: CellUi,
    val best: CellUi,
)

data class MigrationUiState(
    val loaded: Boolean = false,
    val weightUnitLabel: String = "kg",
    val rows: List<MigrationRowUi> = emptyList(),
    /** 値が入っている種目の数 */
    val filledCount: Int = 0,
)

/**
 * 乗り換え用の初期設定（F-DA-05、F-DA-06）。前のアプリの画面を見ながら、種目ごとの直近の値と自己ベストを入れる。
 * 入力は、打つたびにその場で保存する（保存し忘れない。F-WO-07 と同じ考え方）。
 */
class MigrationViewModel(
    private val baselines: BaselineRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private data class Source(
        val rows: List<BaselineDetail> = emptyList(),
        val unit: WeightUnit = WeightUnit.Kg,
        val loaded: Boolean = false,
    )

    private val source: StateFlow<Source> = combine(baselines.observeBaselines(), settings.observe()) { rows, s ->
        Source(rows, s.weightUnit, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Source())

    val state: StateFlow<MigrationUiState> = source.map { s ->
        MigrationUiState(
            loaded = s.loaded,
            weightUnitLabel = WeightUnits.label(s.unit),
            rows = s.rows.map { row(it, s.unit) },
            filledCount = s.rows.count { it.hasValues },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MigrationUiState())

    /**
     * 打った分だけ反映した、最新の値。保存は少し遅れるので、速く続けて打っても、前の値に戻らないように、
     * 保存済みの値ではなく、これを土台にして更新する。
     */
    private val pending = mutableMapOf<String, Baseline>()

    /** 保存は、打った順に 1 つずつ行う。 */
    private val saveQueue = Channel<Baseline>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (baseline in saveQueue) baselines.save(baseline)
        }
    }

    fun onWeight(exerciseId: String, text: String) = edit(exerciseId) { base, exercise, unit ->
        base.copy(weightKg = MigrationInput.weightKg(text, unit, exercise))
    }

    /** 回数。時間の種目では、秒数。 */
    fun onReps(exerciseId: String, text: String) = edit(exerciseId) { base, exercise, _ ->
        val value = MigrationInput.count(text)
        if (exercise.recordType == RecordType.Duration) base.copy(durationSec = value) else base.copy(reps = value)
    }

    /** セット数。空欄・0 は「入れていない」。 */
    fun onSets(exerciseId: String, text: String) = edit(exerciseId) { base, _, _ ->
        base.copy(sets = MigrationInput.setCount(text))
    }

    fun onBest(exerciseId: String, text: String) = edit(exerciseId) { base, exercise, unit ->
        base.copy(best = MigrationInput.best(text, unit, exercise))
    }

    /** 一覧から外す。 */
    fun remove(exerciseId: String) {
        pending.remove(exerciseId)
        viewModelScope.launch { baselines.remove(exerciseId) }
    }

    private fun edit(exerciseId: String, transform: (Baseline, Exercise, WeightUnit) -> Baseline) {
        val current = source.value
        val detail = current.rows.firstOrNull { it.exercise.id == exerciseId } ?: return
        val next = transform(pending[exerciseId] ?: detail.baseline, detail.exercise, current.unit)
        pending[exerciseId] = next
        saveQueue.trySend(next)
    }

    private fun row(detail: BaselineDetail, unit: WeightUnit): MigrationRowUi {
        val exercise = detail.exercise
        val baseline = detail.baseline
        val unitLabel = WeightUnits.label(unit)
        val weightKeyboard = if (exercise.recordType == RecordType.Bodyweight && exercise.useAddedWeight) {
            CellKeyboard.SignedDecimal
        } else {
            CellKeyboard.Decimal
        }
        fun weightText(kg: Double?) = kg?.let { WeightUnits.formatKg(it, unit) }.orEmpty()

        val weight = CellUi(
            initialText = weightText(baseline.weightKg),
            enabled = exercise.usesWeight,
            keyboard = weightKeyboard,
            description = "${exercise.name}の重量（$unitLabel）",
        )
        val reps = if (exercise.recordType == RecordType.Duration) {
            CellUi(baseline.durationSec?.toString().orEmpty(), true, CellKeyboard.Integer, "${exercise.name}の秒数")
        } else {
            CellUi(baseline.reps?.toString().orEmpty(), true, CellKeyboard.Integer, "${exercise.name}の回数")
        }
        val sets = CellUi(baseline.sets?.toString().orEmpty(), true, CellKeyboard.Integer, "${exercise.name}のセット数")
        val best = when {
            exercise.recordType == RecordType.WeightReps ||
                (exercise.recordType == RecordType.Bodyweight && exercise.useAddedWeight) ->
                CellUi(weightText(baseline.best), true, weightKeyboard, "${exercise.name}の自己ベスト（$unitLabel）")
            exercise.recordType == RecordType.Bodyweight ->
                CellUi(baseline.best?.toInt()?.toString().orEmpty(), true, CellKeyboard.Integer, "${exercise.name}の自己ベスト（回）")
            else ->
                CellUi(baseline.best?.toInt()?.toString().orEmpty(), true, CellKeyboard.Integer, "${exercise.name}の自己ベスト（秒）")
        }
        return MigrationRowUi(
            exerciseId = exercise.id,
            name = exercise.name,
            meta = meta(exercise),
            weight = weight,
            reps = reps,
            sets = sets,
            best = best,
        )
    }

    private fun meta(exercise: Exercise): String = when (exercise.recordType) {
        RecordType.WeightReps -> listOfNotNull(
            exercise.equipment.label(),
            exercise.weightMode.tag(exercise.equipment),
            exercise.repMode.tag(),
        )
        RecordType.Bodyweight -> listOfNotNull(
            "自重",
            if (exercise.useAddedWeight) "加重あり" else "回数のみ",
            exercise.repMode.tag(),
        )
        RecordType.Duration -> listOf("自重", "時間（秒）")
        RecordType.DistanceTime -> listOf(exercise.equipment.label())
    }.joinToString(" · ")
}

/** 画面で打った文字を、保存する値に直す（純粋な関数）。空欄・読めない文字は null（その欄は空）。 */
internal object MigrationInput {
    /** 重量。表示している単位（kg / lb）で打った値を kg に直す。マイナス（補助）は、加重のある自重の種目だけ。 */
    fun weightKg(text: String, unit: WeightUnit, exercise: Exercise): Double? {
        val value = text.trim().toDoubleOrNull() ?: return null
        val kg = WeightUnits.fromDisplay(value, unit)
        val allowsNegative = exercise.recordType == RecordType.Bodyweight && exercise.useAddedWeight
        return if (allowsNegative) kg else kg.coerceAtLeast(0.0)
    }

    /** 回数・秒数。 */
    fun count(text: String): Int? = text.trim().toIntOrNull()?.coerceAtLeast(0)

    /** セット数。1 以上だけ（空欄・0 は「入れていない」）。上限を超えたら上限にする。 */
    fun setCount(text: String): Int? = count(text)?.takeIf { it >= 1 }?.coerceAtMost(Baselines.MAX_SETS)

    /** 自己ベスト。重量の種目は kg、加重のない自重は回数、時間は秒（PersonalBests の尺度）。 */
    fun best(text: String, unit: WeightUnit, exercise: Exercise): Double? = when {
        exercise.recordType == RecordType.WeightReps ||
            (exercise.recordType == RecordType.Bodyweight && exercise.useAddedWeight) -> weightKg(text, unit, exercise)
        else -> count(text)?.toDouble()
    }
}
