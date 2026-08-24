package com.jawtrack.corelogic.dsp

/**
 * In-place iterative radix-2 Cooley-Tukey FFT. [real]/[imag] length must be a power of two;
 * pure math, no Android dependency, so it's unit-testable on any JVM. Used by
 * [BandEnergyAnalyzer] for calibration-night noise floor measurement (JawTrackSpec §4.5).
 */
object Fft {

    fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    fun transform(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        require(imag.size == n) { "real and imag must be the same length" }
        require(isPowerOfTwo(n)) { "length must be a power of two, was $n" }
        if (n == 1) return

        bitReversalPermute(real, imag)

        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val angleStep = -2.0 * Math.PI / size
            var start = 0
            while (start < n) {
                for (k in 0 until halfSize) {
                    val angle = angleStep * k
                    val wr = Math.cos(angle)
                    val wi = Math.sin(angle)

                    val evenIndex = start + k
                    val oddIndex = start + k + halfSize

                    val oddRe = real[oddIndex] * wr - imag[oddIndex] * wi
                    val oddIm = real[oddIndex] * wi + imag[oddIndex] * wr

                    real[oddIndex] = real[evenIndex] - oddRe
                    imag[oddIndex] = imag[evenIndex] - oddIm
                    real[evenIndex] += oddRe
                    imag[evenIndex] += oddIm
                }
                start += size
            }
            size *= 2
        }
    }

    private fun bitReversalPermute(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                real[i] = real[j].also { real[j] = real[i] }
                imag[i] = imag[j].also { imag[j] = imag[i] }
            }
            var m = n shr 1
            while (m in 1..j) {
                j -= m
                m = m shr 1
            }
            j += m
        }
    }
}
