package tk.horiuchi.smarttuner.audio

import android.annotation.SuppressLint
import android.media.*
import android.os.Process
import kotlin.math.log10
import kotlin.math.max

class AudioEngine(
    private val sampleRate: Int = 48000,
    private val onResult: (freq: Double, rmsDb: Double) -> Unit
) {
    private var recording = false
    private var thread: Thread? = null

    fun start() {
        if (recording) return
        recording = true
        thread = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = max(minBuf, 4096)
            @SuppressLint("MissingPermission")
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
            val yin = YinPitchDetector(sampleRate)
            val frame = ShortArray(2048)
            val hop = 1024
            try {
                rec.startRecording()
                var offset = 0
                val ring = ShortArray(frame.size)
                val tmp = ShortArray(hop)
                while (recording) {
                    val read = rec.read(tmp, 0, hop)
                    if (read > 0) {
                        // ring へ
                        for (i in 0 until read) {
                            ring[offset] = tmp[i]
                            offset = (offset + 1) % ring.size
                        }
                        // 解析用コピー
                        for (i in frame.indices) {
                            frame[i] = ring[(offset + i) % ring.size]
                        }
                        val rms = frame.fold(0.0){ acc, s -> acc + (s*s).toDouble() } / frame.size
                        val rmsDb = 10* log10(rms / (Short.MAX_VALUE*Short.MAX_VALUE.toDouble()))
                        if (rmsDb < -45) { onResult(0.0, rmsDb); continue } // ノイズゲート
                        val res = yin.getPitch(frame)
                        val freq = if (res.confidence >= 0.7) res.freqHz else 0.0
                        onResult(freq, rmsDb)
                    }
                }
            } finally {
                try { rec.stop() } catch (_:Exception){}
                rec.release()
            }
        }.also { it.start() }
    }

    fun stop() {
        recording = false
        thread?.join()
    }
}
