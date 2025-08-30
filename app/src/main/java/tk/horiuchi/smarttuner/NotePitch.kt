package tk.horiuchi.smarttuner.core

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

data class NotePitch(val name: String, val freq: Double, val cents: Double)

object NoteMapper {
    // 可変A4
    @Volatile var a4: Double = 440.0

    private val names = arrayOf("C","C#","D","D#","E","F","F#","G","G#","A","A#","B")

    fun map(freq: Double): NotePitch {
        if (freq <= 0.0) return NotePitch("--", 0.0, 0.0)
        val n = 12 * ln(freq / a4) / ln(2.0) + 69 // MIDIノート
        val nRound = round(n).toInt()
        val noteFreq = a4 * 2.0.pow((nRound - 69)/12.0)
        val cents = 1200 * ln(freq / noteFreq) / ln(2.0)
        val name = names[(nRound % 12 + 12)%12] + (nRound/12 - 1) // E2など
        return NotePitch(name, noteFreq, cents)
    }
}
