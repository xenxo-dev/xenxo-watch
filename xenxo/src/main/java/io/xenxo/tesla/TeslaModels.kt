package io.xenxo.tesla

data class TeslaState(
    val modelName: String = "Tesla Model 3/Y",
    val batteryPercent: Int = 78,
    val rangeKm: Int = 385,
    val isLocked: Boolean = true,
    val isClimateOn: Boolean = false,
    val targetTemp: Float = 21.0f,
    val insideTemp: Float = 20.5f,
    val isCharging: Boolean = false,
    val chargePortOpen: Boolean = false,
    val keyFingerprint: String = "",
    val lastTapTime: Long = 0L
)
