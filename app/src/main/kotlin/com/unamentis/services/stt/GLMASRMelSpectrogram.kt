package com.unamentis.services.stt

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Mel Spectrogram computation for GLM-ASR audio preprocessing.
 *
 * Converts raw PCM audio samples to mel-frequency spectrograms suitable
 * for input to the Whisper encoder neural network.
 *
 * Pipeline:
 * 1. Apply pre-emphasis filter
 * 2. Frame the signal with overlapping windows
 * 3. Apply Hann window to each frame
 * 4. Compute FFT for each frame
 * 5. Compute power spectrum
 * 6. Apply mel filterbank
 * 7. Apply log transformation
 *
 * Configuration matches GLM-ASR-Nano requirements:
 * - Sample rate: 16000 Hz
 * - N_FFT: 400 (25ms window)
 * - Hop length: 160 (10ms hop)
 * - N_MELS: 128 mel bands
 */
class GLMASRMelSpectrogram(
    private val sampleRate: Int = GLMASROnDeviceConfig.SAMPLE_RATE,
    private val nFFT: Int = GLMASROnDeviceConfig.N_FFT,
    private val hopLength: Int = GLMASROnDeviceConfig.HOP_LENGTH,
    private val nMels: Int = GLMASROnDeviceConfig.N_MELS,
) {
    companion object {
        private const val PRE_EMPHASIS = 0.97f
        private const val MEL_FLOOR = 1e-10f
        private const val F_MIN = 0.0
    }

    // Nyquist frequency derived from the actual sample rate
    private val fMax: Double = sampleRate / 2.0

    // Precomputed Hann window
    private val hannWindow: FloatArray =
        FloatArray(nFFT) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / (nFFT - 1)))).toFloat()
        }

    // Actual FFT size (next power of 2 of nFFT)
    private val fftSize: Int = nextPowerOf2(nFFT)

    // Precomputed mel filterbank
    private val melFilterbank: Array<FloatArray> = createMelFilterbank()

    /**
     * Compute mel spectrogram from audio samples.
     *
     * @param samples Audio samples (16kHz mono, normalized -1.0 to 1.0)
     * @param maxFrames Maximum number of frames to compute (default: 3000 for 30s)
     * @return Mel spectrogram as 2D array [nMels, nFrames]
     */
    fun compute(
        samples: FloatArray,
        maxFrames: Int = 3000,
    ): Array<FloatArray> {
        if (samples.isEmpty()) {
            return Array(nMels) { FloatArray(0) }
        }

        // Apply pre-emphasis
        val emphasized = applyPreEmphasis(samples)

        // Calculate number of frames
        val numFrames = min(maxFrames, (emphasized.size - nFFT) / hopLength + 1)

        if (numFrames <= 0) {
            return Array(nMels) { FloatArray(0) }
        }

        // Compute spectrogram
        val melSpectrogram = Array(nMels) { FloatArray(numFrames) }

        for (frame in 0 until numFrames) {
            val start = frame * hopLength
            val end = min(start + nFFT, emphasized.size)

            // Extract and window the frame
            val windowedFrame = FloatArray(nFFT)
            for (i in 0 until (end - start)) {
                windowedFrame[i] = emphasized[start + i] * hannWindow[i]
            }

            // Compute FFT
            val fftResult = computeRealFFT(windowedFrame)

            // Compute power spectrum (magnitude squared)
            val powerSpectrum =
                FloatArray(fftSize / 2 + 1) { i ->
                    val real = fftResult[i * 2]
                    val imag = fftResult[i * 2 + 1]
                    real * real + imag * imag
                }

            // Apply mel filterbank and log transform
            for (mel in 0 until nMels) {
                var melEnergy = 0f
                for (k in 0 until powerSpectrum.size) {
                    melEnergy += powerSpectrum[k] * melFilterbank[mel][k]
                }
                // Log transform with floor to avoid log(0)
                melSpectrogram[mel][frame] = ln(max(melEnergy, MEL_FLOOR))
            }
        }

        return melSpectrogram
    }

    /**
     * Compute mel spectrogram and flatten to 1D array for ONNX input.
     *
     * @param samples Audio samples
     * @param maxFrames Maximum frames
     * @return Flattened array in [nMels * nFrames] order
     */
    fun computeFlat(
        samples: FloatArray,
        maxFrames: Int = 3000,
    ): FloatArray {
        val melSpec = compute(samples, maxFrames)

        if (melSpec[0].isEmpty()) {
            return FloatArray(0)
        }

        val numFrames = melSpec[0].size
        val result = FloatArray(nMels * numFrames)

        for (mel in 0 until nMels) {
            for (frame in 0 until numFrames) {
                result[mel * numFrames + frame] = melSpec[mel][frame]
            }
        }

        return result
    }

    /**
     * Apply pre-emphasis filter to boost high frequencies.
     */
    private fun applyPreEmphasis(samples: FloatArray): FloatArray {
        if (samples.size < 2) return samples.copyOf()

        val result = FloatArray(samples.size)
        result[0] = samples[0]
        for (i in 1 until samples.size) {
            result[i] = samples[i] - PRE_EMPHASIS * samples[i - 1]
        }
        return result
    }

    /**
     * Compute real FFT using Cooley-Tukey algorithm.
     *
     * Pads input to next power of 2 if necessary.
     *
     * @param input Real-valued input array
     * @return Complex output as interleaved [real0, imag0, real1, imag1, ...]
     */
    private fun computeRealFFT(input: FloatArray): FloatArray {
        // Pad to next power of 2 for Cooley-Tukey algorithm
        val n = nextPowerOf2(input.size)

        // Convert to complex with zero-padding
        val complex = FloatArray(n * 2)
        for (i in input.indices) {
            complex[i * 2] = input[i]
            complex[i * 2 + 1] = 0f
        }
        // Rest is already 0 (zero-padded)

        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n) {
            if (j > i) {
                val tempReal = complex[i * 2]
                val tempImag = complex[i * 2 + 1]
                complex[i * 2] = complex[j * 2]
                complex[i * 2 + 1] = complex[j * 2 + 1]
                complex[j * 2] = tempReal
                complex[j * 2 + 1] = tempImag
            }
            var m = n / 2
            while (m >= 1 && j >= m) {
                j -= m
                m /= 2
            }
            j += m
        }

        // Cooley-Tukey FFT
        var mmax = 1
        while (n > mmax) {
            val istep = mmax * 2
            val theta = -PI / mmax
            val wpr = cos(theta).toFloat()
            val wpi = sin(theta).toFloat()
            var wr = 1f
            var wi = 0f

            for (m in 0 until mmax) {
                for (i in m until n step istep) {
                    val jIdx = i + mmax
                    val tempReal = wr * complex[jIdx * 2] - wi * complex[jIdx * 2 + 1]
                    val tempImag = wr * complex[jIdx * 2 + 1] + wi * complex[jIdx * 2]

                    complex[jIdx * 2] = complex[i * 2] - tempReal
                    complex[jIdx * 2 + 1] = complex[i * 2 + 1] - tempImag
                    complex[i * 2] += tempReal
                    complex[i * 2 + 1] += tempImag
                }
                val wtemp = wr
                wr = wr * wpr - wi * wpi
                wi = wi * wpr + wtemp * wpi
            }
            mmax = istep
        }

        return complex
    }

    /**
     * Create mel filterbank matrix.
     *
     * @return Array of mel filters, each filter has nFFT/2+1 coefficients
     */
    private fun createMelFilterbank(): Array<FloatArray> {
        val nFreqs = fftSize / 2 + 1

        // Mel scale conversion functions
        fun hzToMel(hz: Double): Double = 2595.0 * kotlin.math.log10(1.0 + hz / 700.0)

        fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)

        // Mel points
        val melMin = hzToMel(F_MIN)
        val melMax = hzToMel(fMax)
        val melPoints =
            DoubleArray(nMels + 2) { i ->
                melMin + i * (melMax - melMin) / (nMels + 1)
            }

        // Convert mel points to Hz
        val hzPoints = melPoints.map { melToHz(it) }

        // Convert Hz to FFT bin
        val binPoints =
            hzPoints.map { hz ->
                ((fftSize + 1) * hz / sampleRate).toInt()
            }

        // Create filterbank
        val filterbank = Array(nMels) { FloatArray(nFreqs) }

        for (m in 0 until nMels) {
            val binLeft = binPoints[m]
            val binCenter = binPoints[m + 1]
            val binRight = binPoints[m + 2]

            // Handle edge cases where bins might be the same
            val leftWidth = maxOf(1, binCenter - binLeft)
            val rightWidth = maxOf(1, binRight - binCenter)

            for (k in 0 until nFreqs) {
                filterbank[m][k] =
                    when {
                        k < binLeft -> 0f
                        k < binCenter -> ((k - binLeft).toFloat() / leftWidth).coerceIn(0f, 1f)
                        k < binRight -> ((binRight - k).toFloat() / rightWidth).coerceIn(0f, 1f)
                        else -> 0f
                    }
            }
        }

        return filterbank
    }

    /**
     * Get the expected output shape for given input length.
     *
     * @param numSamples Number of audio samples
     * @return Pair of (nMels, nFrames)
     */
    fun getOutputShape(
        numSamples: Int,
        maxFrames: Int = 3000,
    ): Pair<Int, Int> {
        val numFrames = min(maxFrames, max(0, (numSamples - nFFT) / hopLength + 1))
        return Pair(nMels, numFrames)
    }

    /**
     * Get the number of audio samples needed for given number of frames.
     *
     * @param numFrames Desired number of frames
     * @return Number of audio samples required, or 0 if numFrames <= 0
     */
    fun getSamplesForFrames(numFrames: Int): Int {
        if (numFrames <= 0) return 0
        return (numFrames - 1) * hopLength + nFFT
    }

    /**
     * Find the next power of 2 greater than or equal to n.
     *
     * Required for Cooley-Tukey FFT algorithm which only works with power-of-2 sizes.
     */
    private fun nextPowerOf2(n: Int): Int {
        var power = 1
        while (power < n) {
            power *= 2
        }
        return power
    }
}
