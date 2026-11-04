package com.cubyy.timer.data

import com.cubyy.timer.scramble.WcaEvent
import java.util.UUID

data class PuzzleSession(
    val id: String,
    val name: String,
    val eventId: String
) {
    val event: WcaEvent get() = WcaEvent.fromId(eventId)

    companion object {
        fun create(name: String, eventId: String) = PuzzleSession(UUID.randomUUID().toString(), name, eventId)
        fun default(eventId: String) = PuzzleSession("default:$eventId", "Default", eventId)
        fun legacy(name: String) = PuzzleSession("legacy:333:$name", name, "333")
    }
}
