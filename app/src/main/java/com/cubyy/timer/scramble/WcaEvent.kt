package com.cubyy.timer.scramble

import org.worldcubeassociation.tnoodle.scrambles.PuzzleRegistry

/** WCA events supported by TNoodle's official scramble library. */
enum class WcaEvent(
    val id: String,
    val label: String,
    val tnoodle: PuzzleRegistry,
    val csTimerType: String,
    val threeByThreeHardware: Boolean = false
) {
    CUBE_3("333", "3x3x3 Cube", PuzzleRegistry.THREE, "333", true),
    CUBE_2("222", "2x2x2 Cube", PuzzleRegistry.TWO, "222so"),
    CUBE_4("444", "4x4x4 Cube", PuzzleRegistry.FOUR, "444wca"),
    CUBE_5("555", "5x5x5 Cube", PuzzleRegistry.FIVE, "555wca"),
    CUBE_6("666", "6x6x6 Cube", PuzzleRegistry.SIX, "666wca"),
    CUBE_7("777", "7x7x7 Cube", PuzzleRegistry.SEVEN, "777wca"),
    CUBE_3_BLIND("333bf", "3x3x3 Blindfolded", PuzzleRegistry.THREE_NI, "333ni"),
    CUBE_4_BLIND("444bf", "4x4x4 Blindfolded", PuzzleRegistry.FOUR_NI, "444bld"),
    CUBE_5_BLIND("555bf", "5x5x5 Blindfolded", PuzzleRegistry.FIVE_NI, "555bld"),
    CUBE_3_OH("333oh", "3x3x3 One-Handed", PuzzleRegistry.THREE, "333oh"),
    CLOCK("clock", "Clock", PuzzleRegistry.CLOCK, "clkwca"),
    MEGAMINX("megaminx", "Megaminx", PuzzleRegistry.MEGA, "mgmp"),
    PYRAMINX("pyraminx", "Pyraminx", PuzzleRegistry.PYRA, "pyrso"),
    SKEWB("skewb", "Skewb", PuzzleRegistry.SKEWB, "skbso"),
    SQUARE_1("square1", "Square-1", PuzzleRegistry.SQ1, "sqrs"),
    CUBE_3_FMC("333fm", "3x3x3 Fewest Moves", PuzzleRegistry.THREE_FM, "333fm"),
    CUBE_3_MULTI("333mbf", "3x3x3 Multi-Blind", PuzzleRegistry.THREE_NI, "r3ni"),
    FTO("fto", "Face-Turning Octahedron", PuzzleRegistry.FTO, "ftoso");

    fun generateScramble(): String = tnoodle.scrambler.generateScramble()

    fun drawScrambleSvg(scramble: String): String {
        val puzzle = tnoodle.scrambler
        return puzzle.drawScramble(scramble, puzzle.defaultColorScheme).toString()
    }

    companion object {
        val all: List<WcaEvent> = entries
        fun fromId(id: String?): WcaEvent = entries.firstOrNull { it.id == id } ?: CUBE_3
        fun fromCsTimerType(type: String?): WcaEvent = entries.firstOrNull { it.csTimerType == type }
            ?: when (type) {
                // Accept identifiers written by earlier Cubyy builds as well.
                "222" -> CUBE_2
                "444" -> CUBE_4
                "555" -> CUBE_5
                "666" -> CUBE_6
                "777" -> CUBE_7
                "444ni" -> CUBE_4_BLIND
                "555ni" -> CUBE_5_BLIND
                "clock" -> CLOCK
                "pyram" -> PYRAMINX
                "skewb" -> SKEWB
                "sq1" -> SQUARE_1
                "fto" -> FTO
                else -> CUBE_3
            }
    }
}
