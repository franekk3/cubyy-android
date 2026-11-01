package com.cubyy.timer.scramble

import com.cubyy.timer.smartcube.model.CubeFace
import kotlin.random.Random

object ScrambleGenerator {

    private val FACES = arrayOf(
        CubeFace.U,
        CubeFace.D,
        CubeFace.L,
        CubeFace.R,
        CubeFace.F,
        CubeFace.B
    )

    private val SUFFIXES = arrayOf("", "'", "2")

    // Axis mapping: 0 = U/D, 1 = L/R, 2 = F/B
    private fun getAxis(face: CubeFace): Int = when (face) {
        CubeFace.U, CubeFace.D -> 0
        CubeFace.L, CubeFace.R -> 1
        CubeFace.F, CubeFace.B -> 2
    }

    /**
     * Generates a valid WCA-compliant 3x3 scramble (standard length 21 moves).
     */
    fun generate3x3(length: Int = 21): String {
        val moves = ArrayList<String>(length)
        var lastFace: CubeFace? = null
        var secondLastFace: CubeFace? = null

        for (i in 0 until length) {
            val validFaces = FACES.filter { face ->
                // Condition 1: Can never pick the same face consecutively
                if (face == lastFace) return@filter false

                // Condition 2: If previous face is on the same axis (opposite face),
                // cannot pick another face on that axis if the face before that was also on that axis
                if (lastFace != null && secondLastFace != null) {
                    val currentAxis = getAxis(face)
                    val lastAxis = getAxis(lastFace!!)
                    val secondLastAxis = getAxis(secondLastFace!!)
                    if (currentAxis == lastAxis && lastAxis == secondLastAxis) {
                        return@filter false
                    }
                }
                true
            }

            val chosenFace = validFaces[Random.nextInt(validFaces.size)]
            val suffix = SUFFIXES[Random.nextInt(SUFFIXES.size)]

            moves.add("${chosenFace.symbol}$suffix")
            secondLastFace = lastFace
            lastFace = chosenFace
        }

        return moves.joinToString(" ")
    }
}

