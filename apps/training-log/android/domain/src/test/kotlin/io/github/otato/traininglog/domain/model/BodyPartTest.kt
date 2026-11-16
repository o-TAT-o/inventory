package io.github.otato.traininglog.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** 部位の読み替え。腕は、二頭と三頭に分けた（F-EX-03）。 */
class BodyPartTest {
    @Test
    fun `保存された名前から、部位を求める`() {
        assertEquals(BodyPart.Chest, BodyPart.fromName("Chest"))
        assertEquals(BodyPart.Biceps, BodyPart.fromName("Biceps"))
        assertEquals(BodyPart.Triceps, BodyPart.fromName("Triceps"))
    }

    @Test
    fun `昔の「腕」は、二頭として読む`() {
        assertEquals(BodyPart.Biceps, BodyPart.fromName("Arms"))
    }

    @Test
    fun `知らない名前は、その他にする（読めなくなって落ちないように）`() {
        assertEquals(BodyPart.Other, BodyPart.fromName("Forearms"))
        assertEquals(BodyPart.Other, BodyPart.fromName(""))
    }

    @Test
    fun `腕は、二頭と三頭の 2 つに分かれて、昔の「腕」という部位は残っていない`() {
        val names = BodyPart.entries.map { it.name }
        assertEquals(listOf("Biceps", "Triceps"), names.filter { it == "Biceps" || it == "Triceps" })
        assertEquals(false, "Arms" in names)
    }
}
