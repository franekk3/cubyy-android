package com.cubyy.timer.scramble

object ScrambleGenerator {
    fun generate(eventId: String): String = WcaEvent.fromId(eventId).generateScramble()

    fun generateMultiBlind(count: Int): String = (1..count)
        .joinToString("\n") { WcaEvent.CUBE_3.generateScramble() }

    fun generate3x3(): String = generate(WcaEvent.CUBE_3.id)
}
