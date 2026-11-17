package io.github.otato.traininglog.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 部位。用意した部位（腕は、二頭・三頭・前腕に分ける）と、利用者が足した部位（F-EX-03）。 */
class BodyPartTest {
    @Test
    fun `保存された名前から、部位を求める`() {
        assertEquals(BodyPart.Chest, BodyPart.fromName("Chest"))
        assertEquals(BodyPart.Biceps, BodyPart.fromName("Biceps"))
        assertEquals(BodyPart.Triceps, BodyPart.fromName("Triceps"))
        assertEquals(BodyPart.Forearms, BodyPart.fromName("Forearms"))
    }

    @Test
    fun `昔の「腕」は、二頭として読む`() {
        assertEquals(BodyPart.Biceps, BodyPart.fromName("Arms"))
    }

    @Test
    fun `用意した部位にない名前は、利用者が足した部位として、名前のまま持つ。空なら、その他`() {
        val calves = BodyPart.fromName("ふくらはぎ")
        assertEquals("ふくらはぎ", calves.name)
        assertFalse(calves.isPreset)
        assertTrue(BodyPart.Chest.isPreset)
        // 同じ名前なら、同じ部位
        assertEquals(calves, BodyPart.fromName(" ふくらはぎ "))
        assertEquals(BodyPart.Other, BodyPart.fromName(""))
        assertEquals(BodyPart.Other, BodyPart.fromName("   "))
    }

    @Test
    fun `利用者が付ける名前は、前後の空白を除き、長すぎれば切る`() {
        assertEquals("お尻", BodyPart.custom("  お尻 ").name)
        assertEquals(BodyPart.MAX_CUSTOM_LENGTH, BodyPart.custom("あ".repeat(30)).name.length)
        assertEquals(BodyPart.Other, BodyPart.custom(" "))
        // 用意した部位の名前（保存に使う名前）と同じなら、その部位になる
        assertEquals(BodyPart.Chest, BodyPart.custom("Chest"))
    }

    @Test
    fun `選択肢は、用意した部位、利用者が足した部位（使っている順）、その他、の順に並ぶ`() {
        val calves = BodyPart.custom("ふくらはぎ")
        val glutes = BodyPart.custom("お尻")

        val parts = BodyPart.available(listOf(BodyPart.Chest, glutes, BodyPart.Other, calves, glutes))

        assertEquals(BodyPart.entries.dropLast(1) + listOf(glutes, calves) + BodyPart.Other, parts)
        // 使っている種目がなければ、用意した部位だけ
        assertEquals(BodyPart.entries, BodyPart.available(emptyList()))
    }

    @Test
    fun `腕は、二頭・三頭・前腕に分かれて、昔の「腕」という部位は残っていない`() {
        val names = BodyPart.entries.map { it.name }
        assertEquals(listOf("Biceps", "Triceps", "Forearms"), names.filter { it == "Biceps" || it == "Triceps" || it == "Forearms" })
        assertEquals(false, "Arms" in names)
        assertEquals(BodyPart.Other, BodyPart.entries.last())
    }
}
