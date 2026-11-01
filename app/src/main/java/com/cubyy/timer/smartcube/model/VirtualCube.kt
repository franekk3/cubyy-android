package com.cubyy.timer.smartcube.model

class VirtualCube {

    // 6 faces with 9 stickers each (0..8, row-major order: top-left to bottom-right)
    private val faces: MutableMap<CubeFace, Array<CubeColor>> = mutableMapOf()

    init {
        resetToSolved()
    }

    fun resetToSolved() {
        faces[CubeFace.U] = Array(9) { CubeColor.WHITE }
        faces[CubeFace.D] = Array(9) { CubeColor.YELLOW }
        faces[CubeFace.L] = Array(9) { CubeColor.ORANGE }
        faces[CubeFace.R] = Array(9) { CubeColor.RED }
        faces[CubeFace.F] = Array(9) { CubeColor.GREEN }
        faces[CubeFace.B] = Array(9) { CubeColor.BLUE }
    }

    fun getFace(face: CubeFace): Array<CubeColor> = faces[face]!!.copyOf()

    fun getSticker(face: CubeFace, index: Int): CubeColor = faces[face]!![index]

    fun isSolved(): Boolean {
        for ((_, stickers) in faces) {
            val centerColor = stickers[4]
            if (stickers.any { it != centerColor }) {
                return false
            }
        }
        return true
    }

    fun hasSameStateAs(other: VirtualCube): Boolean {
        return CubeFace.entries.all { face -> faces[face]!!.contentEquals(other.faces[face]!!) }
    }

    fun applyMove(move: CubeMove) {
        val quarterTurns = when (move.amount) {
            1 -> 1
            2 -> 2
            -1, 3 -> 3
            else -> (move.amount % 4 + 4) % 4
        }
        repeat(quarterTurns) {
            rotateClockwise(move.face)
        }
    }

    fun applyScramble(scramble: String) {
        val moves = scramble.trim().split("\\s+".toRegex())
        for (m in moves) {
            CubeMove.fromNotation(m)?.let { applyMove(it) }
        }
    }

    private fun rotateClockwise(face: CubeFace) {
        // Rotate the 9 stickers on the face itself
        val f = faces[face]!!
        val oldF = f.copyOf()
        f[0] = oldF[6]; f[1] = oldF[3]; f[2] = oldF[0]
        f[3] = oldF[7]; f[4] = oldF[4]; f[5] = oldF[1]
        f[6] = oldF[8]; f[7] = oldF[5]; f[8] = oldF[2]

        // Rotate adjacent edge stripes
        val u = faces[CubeFace.U]!!
        val d = faces[CubeFace.D]!!
        val l = faces[CubeFace.L]!!
        val r = faces[CubeFace.R]!!
        val front = faces[CubeFace.F]!!
        val b = faces[CubeFace.B]!!

        when (face) {
            CubeFace.U -> {
                val temp = arrayOf(front[0], front[1], front[2])
                front[0] = r[0]; front[1] = r[1]; front[2] = r[2]
                r[0] = b[0]; r[1] = b[1]; r[2] = b[2]
                b[0] = l[0]; b[1] = l[1]; b[2] = l[2]
                l[0] = temp[0]; l[1] = temp[1]; l[2] = temp[2]
            }
            CubeFace.D -> {
                val temp = arrayOf(front[6], front[7], front[8])
                front[6] = l[6]; front[7] = l[7]; front[8] = l[8]
                l[6] = b[6]; l[7] = b[7]; l[8] = b[8]
                b[6] = r[6]; b[7] = r[7]; b[8] = r[8]
                r[6] = temp[0]; r[7] = temp[1]; r[8] = temp[2]
            }
            CubeFace.R -> {
                val temp = arrayOf(u[2], u[5], u[8])
                u[2] = front[2]; u[5] = front[5]; u[8] = front[8]
                front[2] = d[2]; front[5] = d[5]; front[8] = d[8]
                d[2] = b[6]; d[5] = b[3]; d[8] = b[0]
                b[6] = temp[0]; b[3] = temp[1]; b[0] = temp[2]
            }
            CubeFace.L -> {
                val temp = arrayOf(u[0], u[3], u[6])
                u[0] = b[8]; u[3] = b[5]; u[6] = b[2]
                b[8] = d[0]; b[5] = d[3]; b[2] = d[6]
                d[0] = front[0]; d[3] = front[3]; d[6] = front[6]
                front[0] = temp[0]; front[3] = temp[1]; front[6] = temp[2]
            }
            CubeFace.F -> {
                val temp = arrayOf(u[6], u[7], u[8])
                u[6] = l[8]; u[7] = l[5]; u[8] = l[2]
                l[2] = d[0]; l[5] = d[1]; l[8] = d[2]
                d[0] = r[6]; d[1] = r[3]; d[2] = r[0]
                r[0] = temp[0]; r[3] = temp[1]; r[6] = temp[2]
            }
            CubeFace.B -> {
                val temp = arrayOf(u[0], u[1], u[2])
                u[0] = r[2]; u[1] = r[5]; u[2] = r[8]
                r[2] = d[8]; r[5] = d[7]; r[8] = d[6]
                d[8] = l[6]; d[7] = l[3]; d[6] = l[0]
                l[0] = temp[2]; l[3] = temp[1]; l[6] = temp[0]
            }
        }
    }

    fun clone(): VirtualCube {
        val copy = VirtualCube()
        for (face in CubeFace.entries) {
            copy.faces[face] = this.faces[face]!!.copyOf()
        }
        return copy
    }
}

