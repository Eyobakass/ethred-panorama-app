package com.ethred.panorama.sensors

data class TargetDot(
    val id: Int,
    val targetYawDeg: Float,
    val targetPitchDeg: Float,
    val state: State = State.UNCAPTURED
) {
    enum class State {
        UNCAPTURED,
        IN_ALIGNMENT,
        CAPTURED
    }
}
