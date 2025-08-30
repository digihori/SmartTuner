package tk.horiuchi.smarttuner.core

enum class InstrumentMode(val stringsHz: List<Double>) {
    GUITAR(listOf(82.41, 110.00, 146.83, 196.00, 246.94, 329.63)),
    BASS(listOf(41.20, 55.00, 73.42, 98.00)),
    UKULELE(listOf(392.00, 261.63, 329.63, 440.00)),
    VIOLIN(listOf(196.00, 293.66, 440.00, 659.25));
    fun closestStringIndex(freq: Double): Int? {
        if (freq <= 0) return null
        return stringsHz.indices.minByOrNull { i -> kotlin.math.abs(stringsHz[i] - freq) }
    }
}
