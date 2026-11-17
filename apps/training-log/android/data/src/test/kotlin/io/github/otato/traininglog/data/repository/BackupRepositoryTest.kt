package io.github.otato.traininglog.data.repository

import io.github.otato.traininglog.data.DataModule
import io.github.otato.traininglog.data.backup.BackupJson
import io.github.otato.traininglog.data.testing.TestDatabase
import io.github.otato.traininglog.domain.logic.CsvExport
import io.github.otato.traininglog.domain.logic.WorkoutPlanner
import io.github.otato.traininglog.domain.model.AdjustmentField
import io.github.otato.traininglog.domain.model.AdjustmentValueType
import io.github.otato.traininglog.domain.model.Baseline
import io.github.otato.traininglog.domain.model.BackupFormatException
import io.github.otato.traininglog.domain.model.BodyPart
import io.github.otato.traininglog.domain.model.Equipment
import io.github.otato.traininglog.domain.model.Exercise
import io.github.otato.traininglog.domain.model.Gear
import io.github.otato.traininglog.domain.model.Routine
import io.github.otato.traininglog.domain.model.RoutineItem
import io.github.otato.traininglog.domain.model.SetValues
import io.github.otato.traininglog.domain.model.Workout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 全データの書き出し・読み込み（F-DA-02、F-DA-03）と、CSV（F-DA-04）。 */
class BackupRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
    private val data: DataModule = TestDatabase.create(clock = clock)

    /** 種目・ギア・調整項目・ルーティン・終えたワークアウト（調整項目の値つき）・初期設定・設定が入った状態にする。 */
    private suspend fun fill(target: DataModule = data) {
        target.exercises.save(Exercise(id = "bench", name = "ベンチプレス", bodyPart = BodyPart.Chest, equipment = Equipment.Barbell))
        target.exercises.save(Exercise(id = "bench-narrow", parentId = "bench", name = "ナローグリップ", bodyPart = BodyPart.Triceps))
        target.exercises.saveAdjustmentFields(
            "bench",
            listOf(AdjustmentField("angle", "bench", "ベンチ角度", AdjustmentValueType.Number, "°")),
        )
        target.gear.save(Gear("belt", "ベルト", 0))
        target.routines.saveRoutine(Routine("push", "Push", "ベンチが空いていなければダンベル"))
        target.routines.saveItem(RoutineItem("i1", "push", "bench", targetSets = 3, targetWeightKg = 60.0, targetReps = 10))

        val planned = WorkoutPlanner(TestDatabase.sequentialIds("p")).plan("w1", 0, Exercise(id = "bench", name = "ベンチプレス"))
            .copy(gearIds = setOf("belt"), adjustments = mapOf("angle" to "30"))
        target.workouts.createWorkout(
            Workout("w1", LocalDate.of(2026, 11, 14), "push", Instant.parse("2026-11-14T10:00:00Z"), quickEntry = true),
            listOf(planned),
        )
        planned.sets.forEachIndexed { index, set ->
            target.workouts.updateSetValues(SetValues(set.id, 62.5, 10 - index))
            if (index < 2) target.workouts.setCompleted(set.id, true, Instant.parse("2026-11-14T10:30:00Z"))
        }
        target.workouts.finishWorkout("w1", Instant.parse("2026-11-14T11:00:00Z"))
        target.baselines.save(Baseline("bench", weightKg = 60.0, reps = 8, best = 70.0, sets = 4))
        target.settings.update { it.copy(restBetweenSetsSec = 120, lastExportAtMillis = 111L) }
    }

    @Test
    fun `書き出したデータを、別の端末に読み込むと、同じ内容になる`() = runTest {
        fill()
        val text = BackupJson.encode(data.backup.snapshot())

        val other = TestDatabase.create(clock = clock)
        other.backup.restore(BackupJson.decode(text))

        assertEquals(data.backup.snapshot().copy(settings = emptyMap()), other.backup.snapshot().copy(settings = emptyMap()))
        assertTrue(other.backup.snapshot().workouts.single().quickEntry, "「あとから入力」の印も移る")
        // 設定も移る（休憩の長さ）。端末の状態（最後に書き出した日）は移らない
        val settings = other.settings.get()
        assertEquals(120, settings.restBetweenSetsSec)
        assertEquals(null, settings.lastExportAtMillis)
    }

    @Test
    fun `読み込むと、いまのデータは置き換わり、端末の状態は残る`() = runTest {
        fill()
        val text = BackupJson.encode(data.backup.snapshot())

        val other = TestDatabase.create(clock = clock)
        other.exercises.save(Exercise(id = "old", name = "古い種目"))
        other.settings.update { it.copy(lastExportAtMillis = 999L, adsRemoved = true) }
        other.backup.restore(BackupJson.decode(text))

        val exerciseNames = other.exercises.observeExercises(includeHidden = true).first().map { it.name }
        assertTrue("古い種目" !in exerciseNames, "読み込む前のデータは、残らない")
        assertTrue("ベンチプレス" in exerciseNames)
        val settings = other.settings.get()
        assertEquals(999L, settings.lastExportAtMillis, "最後に書き出した日は、この端末のものを残す")
        assertTrue(settings.adsRemoved, "広告を消す購入も、この端末のものを残す")
    }

    @Test
    fun `新しすぎる版と、別のファイルは、断る`() {
        val tooNew = """{"app":"training-log","formatVersion":999,"exportedAt":0}"""
        assertFailsWith<BackupFormatException> { BackupJson.decode(tooNew) }
        assertFailsWith<BackupFormatException> { BackupJson.decode("""{"app":"other","formatVersion":1,"exportedAt":0}""") }
        assertFailsWith<BackupFormatException> { BackupJson.decode("これは JSON ではありません") }
    }

    @Test
    fun `知らない項目があっても読める（古い版や、あとで項目が増えた版のファイル）`() = runTest {
        val minimal = """
            {"app":"training-log","formatVersion":1,"exportedAt":0,"futureField":123,
             "exercises":[{"id":"e1","name":"スクワット","bodyPart":"Arms","equipment":"Unknown"}]}
        """.trimIndent()
        val snapshot = BackupJson.decode(minimal)
        assertEquals("スクワット", snapshot.exercises.single().name)
        assertEquals(BodyPart.Biceps, snapshot.exercises.single().bodyPart, "昔の「腕」は二頭として読む")
        assertEquals(Equipment.Other, snapshot.exercises.single().equipment, "読めない名前は、初期値")
    }

    @Test
    fun `CSV は、完了したセットだけを、1 行 1 セットで出す`() = runTest {
        fill()
        val csv = CsvExport.build(data.backup.snapshot())
        val lines = csv.removePrefix("﻿").trim().split("\r\n")
        assertEquals("日付,ルーティン,種目,部位,器具,セット,重量(kg),重量の数え方,回数,時間(秒),距離(m),ギア,調整項目", lines.first())
        // 3 セットのうち、完了した 2 セットだけ
        assertEquals(2, lines.size - 1)
        assertEquals("2026-11-14,Push,ベンチプレス,胸,バーベル,1,62.5,合計,10,,,ベルト,ベンチ角度 30°", lines[1])
        assertEquals("2026-11-14,Push,ベンチプレス,胸,バーベル,2,62.5,合計,9,,,ベルト,ベンチ角度 30°", lines[2])
    }

    @Test
    fun `CSV は、カンマや引用符を含む値を、引用符で囲む`() {
        assertEquals("\"a,b\"", CsvExport.escape("a,b"))
        assertEquals("\"他の\"\"人\"\"\"", CsvExport.escape("他の\"人\""))
        assertEquals("普通", CsvExport.escape("普通"))
    }

    @Test
    fun `利用者が足した部位は、保存しても、書き出して別の端末に読み込んでも、名前のまま残る`() = runTest {
        val calves = BodyPart.custom("ふくらはぎ")
        data.exercises.save(Exercise(id = "calf", name = "カーフレイズ", bodyPart = calves))
        assertEquals(calves, data.exercises.getExercise("calf")?.bodyPart)

        val text = BackupJson.encode(data.backup.snapshot())
        val other = TestDatabase.create(clock = clock)
        other.backup.restore(BackupJson.decode(text))

        assertEquals(calves, other.exercises.getExercise("calf")?.bodyPart)
        assertEquals("ふくらはぎ", other.exercises.getExercise("calf")?.bodyPart?.name)
    }
}
