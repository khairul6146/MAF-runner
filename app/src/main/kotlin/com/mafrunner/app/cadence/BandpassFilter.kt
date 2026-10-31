package com.mafrunner.app.cadence

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class BandpassFilter(
    sampleRate: Int = 16000,
    centerFreq: Float = 100f,
    qFactor: Float = 1.0f
) {
    // 2nd-order IIR biquad bandpass filter coefficients
    private val b0: Float
    private val b1: Float
    private val b2: Float
    private val a1: Float
    private val a2: Float

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    init {
        val omega = (2.0 * PI * centerFreq / sampleRate).toFloat()
        val alpha = (sin(omega) / (2.0 * qFactor)).toFloat()
        val cosW = cos(omega)

        val a0 = 1.0f + alpha
        b0 = alpha / a0
        b1 = 0f
        b2 = -alpha / a0
        a1 = (-2.0f * cosW) / a0
        a2 = (1.0f - alpha) / a0
    }

    fun filter(sample: Float): Float {
        val out = b0 * sample + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = sample
        y2 = y1
        y1 = out
        return out
    }

    fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
    }
}
