package io.github.otato.traininglog.domain.logic

import io.github.otato.traininglog.domain.model.Routine

/** 次にやるルーティンの提案（F-RT-07）。ルーティンの並び順で回し、最後にやったものの次を返す。 */
object NextRoutine {
    /**
     * @param routines ルーティン（並び順は [Routine.sortOrder]）
     * @param lastRoutineId 最後にやったルーティンの id。まだなければ null（先頭を返す）
     */
    fun suggest(routines: List<Routine>, lastRoutineId: String?): Routine? {
        if (routines.isEmpty()) return null
        val ordered = routines.sortedBy { it.sortOrder }
        val last = ordered.indexOfFirst { it.id == lastRoutineId }
        return if (last < 0) ordered.first() else ordered[(last + 1) % ordered.size]
    }
}
