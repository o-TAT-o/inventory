package io.github.otato.traininglog.feature.exercise

import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.logic.ChartMetric
import io.github.otato.traininglog.domain.logic.ChartPeriod
import io.github.otato.traininglog.domain.model.AdjustmentValueType
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.usecase.AddExerciseToWorkout
import io.github.otato.traininglog.domain.usecase.StartWorkout
import io.github.otato.traininglog.feature.gear.GearViewModel
import io.github.otato.traininglog.feature.picker.ExercisePickerViewModel
import io.github.otato.traininglog.feature.picker.PickerMode
import io.github.otato.traininglog.testing.ViewModelTestHarness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 種目の編集（調整項目・バリエーション）、種目の詳細（グラフ・フォームの切り替え）、種目選択の折りたたみ、ギアの管理。 */
class ExerciseScreensViewModelTest {
    private val harness = ViewModelTestHarness()
    private val data = TestDatabase.create()
    private val ids = TestDatabase.sequentialIds("x")
    private val clock = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 16)

    private val row = Exercise(id = "row", name = "ワンハンドダンベルロウ")

    @BeforeTest
    fun setUp() = harness.setUp()

    @AfterTest
    fun tearDown() = harness.tearDown()

    private fun editViewModel(id: String) = harness.track(ExerciseEditViewModel(id, data.exercises, ids))

    /** [exerciseId] を、[date] に、[weight] kg × 10 回で 1 セットやって終える。 */
    private suspend fun record(exerciseId: String, date: LocalDate, weight: Double) {
        val workoutId = StartWorkout(data.workouts, data.routines, data.exercises, clock)(date, null)
        AddExerciseToWorkout(data.workouts, data.exercises)(workoutId, exerciseId, 0)
        val set = assertNotNull(data.workouts.observeWorkout(workoutId).first()).exercises.single().sets.first()
        data.workouts.updateSetValues(SetValues(set.id, weight, 10))
        data.workouts.setCompleted(set.id, true, clock.instant())
        data.workouts.finishWorkout(workoutId, clock.instant())
    }

    // ---- 種目の編集 ----

    @Test
    fun `調整項目は、保存したときに入り、名前のない項目は入らない`() = harness.run {
        data.exercises.save(row)
        val viewModel = editViewModel("row")
        val state = viewModel.state.first { it.loaded }
        assertFalse(state.isNew)

        viewModel.addField()
        viewModel.addField()
        val first = viewModel.state.value.fields.first()
        viewModel.editField(first.id) { it.copy(name = " ベンチ角度 ", unit = "°") }
        // まだ保存していないので、データには入っていない
        assertEquals(emptyList(), data.exercises.getAdjustmentFields("row"))

        val event = CompletableDeferred<ExerciseEditEvent>()
        background { event.complete(viewModel.events.first()) }
        viewModel.save()
        assertEquals(ExerciseEditEvent.Done, event.await())
        val saved = data.exercises.getAdjustmentFields("row")
        assertEquals(listOf("ベンチ角度"), saved.map { it.name })
        assertEquals("°", saved.single().unit)
        assertEquals(AdjustmentValueType.Number, saved.single().valueType)
    }

    @Test
    fun `バリエーションを足すと、いまの入力を保存してから作り、調整項目を引き継いで、その編集を開く`() = harness.run {
        data.exercises.save(row)
        val viewModel = editViewModel("row")
        viewModel.state.first { it.loaded }
        viewModel.addField()
        viewModel.editField(viewModel.state.value.fields.single().id) { it.copy(name = "ベンチ角度") }

        val opened = CompletableDeferred<ExerciseEditEvent>()
        background { opened.complete(viewModel.events.first()) }
        viewModel.addVariation("膝をベンチにつく")
        val event = opened.await() as ExerciseEditEvent.OpenVariation

        val child = assertNotNull(data.exercises.getExercise(event.exerciseId))
        assertEquals("row", child.parentId)
        assertEquals("膝をベンチにつく", child.name)
        assertEquals(listOf("ベンチ角度"), data.exercises.getAdjustmentFields(child.id).map { it.name })
        // 一覧にも出る
        val rows = viewModel.state.first { it.variations.isNotEmpty() }.variations
        assertEquals(listOf("膝をベンチにつく" to "記録なし"), rows.map { it.name to it.detail })
    }

    @Test
    fun `新しい種目と、バリエーション自身には、バリエーションを足せない`() = harness.run {
        data.exercises.save(row)
        data.exercises.save(Exercise(id = "knee", parentId = "row", name = "膝をベンチにつく"))

        assertFalse(editViewModel("new").state.first { it.loaded }.canHaveVariations)
        val child = editViewModel("knee").state.first { it.loaded }
        assertFalse(child.canHaveVariations)
        assertEquals("ワンハンドダンベルロウ", child.parentName)
        assertTrue(editViewModel("row").state.first { it.loaded }.canHaveVariations)
    }

    @Test
    fun `親の種目の記録を、バリエーションへまとめて移せる`() = harness.run {
        data.exercises.save(row)
        data.exercises.save(Exercise(id = "knee", parentId = "row", name = "膝をベンチにつく"))
        record("row", today.minusDays(7), 24.0)
        val viewModel = editViewModel("row")
        assertEquals(1, viewModel.state.first { it.ownRecordCount > 0 }.ownRecordCount)

        viewModel.moveRecordsTo("knee")

        val state = viewModel.state.first { it.ownRecordCount == 0 && it.variations.isNotEmpty() }
        assertEquals("記録 1 回", state.variations.single().detail)
    }

    // ---- 種目の詳細 ----

    private fun detailViewModel(id: String) = harness.track(
        ExerciseDetailViewModel(id, data.exercises, data.workouts, data.gear, data.settings, clock),
    )

    @Test
    fun `種目の詳細は、フォームをまとめて出し、フォームを選ぶと、その記録だけになる`() = harness.run {
        data.exercises.save(row)
        data.exercises.save(Exercise(id = "knee", parentId = "row", name = "膝をベンチにつく", sortOrder = 1))
        data.exercises.save(Exercise(id = "stand", parentId = "row", name = "立って手をつく", sortOrder = 2))
        record("knee", today.minusDays(14), 24.0)
        record("stand", today.minusDays(7), 20.0)
        record("knee", today.minusDays(1), 26.0)
        val viewModel = detailViewModel("row")

        val all = viewModel.state.first { it.sessions.size == 3 }
        assertEquals(listOf<String?>(null, "knee", "stand"), all.variations.map { it.first })
        assertEquals(listOf("膝をベンチにつく", "立って手をつく", "膝をベンチにつく"), all.sessions.map { it.formName })
        assertEquals(listOf(24.0, 20.0, 26.0), all.chart.points.map { it.value })
        // それまでの最高（24）を超えた点に、印が付く
        assertEquals(listOf(false, false, true), all.chart.points.map { it.isPersonalBest })
        assertEquals("26 kg", all.best?.line?.weightText)

        viewModel.selectVariation("stand")
        val stand = viewModel.state.first { it.selectedVariationId == "stand" }
        assertEquals(1, stand.sessions.size)
        assertNull(stand.sessions.single().formName)
        assertEquals(listOf(20.0), stand.chart.points.map { it.value })
    }

    @Test
    fun `グラフは、期間と種類を切り替えられる`() = harness.run {
        data.exercises.save(row)
        record("row", today.minusMonths(6), 20.0)
        record("row", today.minusDays(3), 30.0)
        val viewModel = detailViewModel("row")

        // 初めは 3 か月・最大重量
        val first = viewModel.state.first { it.sessions.size == 2 }
        assertEquals(ChartPeriod.ThreeMonths, first.period)
        assertEquals(listOf(30.0), first.chart.points.map { it.value })
        assertEquals(listOf(ChartMetric.Best, ChartMetric.OneRepMax, ChartMetric.Volume), first.metrics)

        viewModel.selectPeriod(ChartPeriod.All)
        assertEquals(listOf(20.0, 30.0), viewModel.state.first { it.period == ChartPeriod.All }.chart.points.map { it.value })

        viewModel.selectMetric(ChartMetric.Volume)
        assertEquals(listOf(200.0, 300.0), viewModel.state.first { it.metric == ChartMetric.Volume }.chart.points.map { it.value })

        viewModel.selectMetric(ChartMetric.OneRepMax)
        assertEquals(40.0, viewModel.state.first { it.metric == ChartMetric.OneRepMax }.chart.points.last().value)
    }

    // ---- 種目選択の折りたたみ ----

    @Test
    fun `種目選択では、バリエーションは親の下にたたまれ、開くか、名前で探すと出る`() = harness.run {
        data.exercises.save(row)
        data.exercises.save(Exercise(id = "knee", parentId = "row", name = "膝をベンチにつく"))
        val workoutId = StartWorkout(data.workouts, data.routines, data.exercises, clock)(today, null)
        val viewModel = harness.track(
            ExercisePickerViewModel(
                mode = PickerMode.Workout,
                targetId = workoutId,
                exercises = data.exercises,
                routines = data.routines,
                workouts = data.workouts,
                baselines = data.baselines,
                addExerciseToWorkout = AddExerciseToWorkout(data.workouts, data.exercises),
            ),
        )

        val folded = viewModel.state.first { it.loaded }
        assertEquals(listOf("row"), folded.rows.map { it.exercise.id })
        assertEquals(1, folded.rows.single().childCount)

        viewModel.toggleExpanded("row")
        assertEquals(listOf("row", "knee"), viewModel.state.first { it.rows.size == 2 }.rows.map { it.exercise.id })

        viewModel.toggleExpanded("row")
        viewModel.setQuery("膝")
        assertEquals(listOf("knee"), viewModel.state.first { it.query == "膝" }.rows.map { it.exercise.id })
    }

    // ---- ギアの管理 ----

    @Test
    fun `ギアは、足す・名前を変える・非表示にする・並べ替える、ができる`() = harness.run {
        data.exercises.seedPresetsIfEmpty()
        val viewModel = harness.track(GearViewModel(data.gear, ids))
        val before = viewModel.rows.first { it.isNotEmpty() }
        assertEquals("ベルト", before.first().name)
        assertEquals("使用 0 回", before.first().detail)

        viewModel.add(" チョーク ")
        val added = viewModel.rows.first { rows -> rows.any { it.name == "チョーク" } }.last()
        assertEquals("自分で追加 · 使用 0 回", added.detail)

        viewModel.rename(added.id, "液体チョーク")
        viewModel.setVisible(added.id, false)
        val hidden = viewModel.rows.first { rows -> rows.any { it.name == "液体チョーク" && !it.visible } }.last()
        assertEquals("非表示 · 自分で追加 · 使用 0 回", hidden.detail)
        // 非表示のギアは、記録画面の一覧には出ない
        assertFalse(data.gear.observeGear().first().any { it.id == added.id })

        viewModel.reorder(listOf(added.id) + before.map { it.id })
        assertEquals("液体チョーク", viewModel.rows.first { it.first().id == added.id }.first().name)
    }
}
