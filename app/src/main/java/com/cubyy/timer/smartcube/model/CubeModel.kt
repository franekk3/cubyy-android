package com.cubyy.timer.smartcube.model

enum class CubeFace(val symbol: String) {
    U("U"), // Up (White)
    D("D"), // Down (Yellow)
    L("L"), // Left (Orange)
    R("R"), // Right (Red)
    F("F"), // Front (Green)
    B("B"); // Back (Blue)

    companion object {
        fun fromSymbol(symbol: String): CubeFace? = entries.firstOrNull { it.symbol == symbol }
    }
}

enum class CubeColor(val hex: Long, val displayName: String) {
    WHITE(0xFFFFFFFF, "White"),
    YELLOW(0xFFFFD600, "Yellow"),
    GREEN(0xFF00E676, "Green"),
    BLUE(0xFF2979FF, "Blue"),
    RED(0xFFFF1744, "Red"),
    ORANGE(0xFFFF9100, "Orange");
}

data class CubeMove(
    val face: CubeFace,
    val amount: Int, // 1 = Clockwise (90°), -1 or 3 = Counter-Clockwise (-90°), 2 = 180°
    val timestamp: Long = System.currentTimeMillis()
) {
    fun inverse(): CubeMove = copy(
        amount = when (amount) {
            1 -> -1
            -1, 3 -> 1
            else -> amount
        }
    )

    val notation: String
        get() = when (amount) {
            1 -> face.symbol
            -1, 3 -> "${face.symbol}'"
            2 -> "${face.symbol}2"
            else -> face.symbol
        }

    companion object {
        fun fromNotation(notation: String, timestamp: Long = System.currentTimeMillis()): CubeMove? {
            val trimmed = notation.trim()
            if (trimmed.isEmpty()) return null
            val faceSymbol = trimmed.take(1).uppercase()
            val face = CubeFace.fromSymbol(faceSymbol) ?: return null
            val amount = when {
                trimmed.endsWith("'") -> -1
                trimmed.endsWith("2") -> 2
                else -> 1
            }
            return CubeMove(face, amount, timestamp)
        }
    }
}

