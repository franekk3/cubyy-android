package com.cubyy.timer.smartcube.model

enum class SmartCubeBrand(val displayName: String) {
    GAN("GAN"),
    MOYU("MoYu"),
    GIIKER("GiiKER"),
    UNKNOWN("Smart Cube")
}

data class SmartCubeDevice(
    val address: String,
    val name: String,
    val brand: SmartCubeBrand,
    val rssi: Int = 0,
    val batteryPercent: Int = -1,
    val isConnected: Boolean = false
) {
    companion object {
        fun identifyBrand(name: String?): SmartCubeBrand {
            if (name == null) return SmartCubeBrand.UNKNOWN
            val upper = name.uppercase()
            return when {
                upper.startsWith("GAN") || upper.startsWith("MG") || upper.startsWith("AICUBE") -> SmartCubeBrand.GAN
                upper.startsWith("MOYU") || upper.startsWith("MY") || upper.contains("WEILONG") -> SmartCubeBrand.MOYU
                upper.startsWith("GIIKER") || upper.startsWith("SUPERCUBE") -> SmartCubeBrand.GIIKER
                else -> SmartCubeBrand.UNKNOWN
            }
        }
    }
}

sealed class SmartCubeEvent {
    data class Move(val move: CubeMove) : SmartCubeEvent()
    data class Battery(val percentage: Int) : SmartCubeEvent()
    data class ConnectionState(val isConnected: Boolean, val device: SmartCubeDevice?) : SmartCubeEvent()
    data class Error(val message: String) : SmartCubeEvent()
}

