package tk.horiuchi.smarttuner.audio

import kotlin.math.*

data class PitchResult(val freqHz: Double, val confidence: Double)

/**
 * 精度重視の YIN 実装（CMND + absolute threshold + parabolic interpolation）
 * - 入力: 16bit PCM フレーム（例: 2048サンプル）
 * - 前処理: DC除去 + Hann窓
 * - 出力: 推定周波数(Hz)と信頼度(0..1)  ※信頼度は 1 - CMND(tau_est)
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val threshold: Double = 0.10,     // 絶対閾値（低いほど厳格）
    private val minFreq: Double = 50.0,       // 推定下限
    private val maxFreq: Double = 1500.0      // 推定上限
) {
    private val minTau = (sampleRate / maxFreq).coerceAtLeast(2.0).toInt()
    private val maxTau = (sampleRate / minFreq).toInt()

    fun getPitch(framePcm: ShortArray): PitchResult {
        val n = framePcm.size
        if (n < 512) return PitchResult(0.0, 0.0)

        // === 1) 前処理（DC除去 & Hann窓 & 正規化） ===
        val f = DoubleArray(n)
        var mean = 0.0
        for (s in framePcm) mean += s.toDouble()
        mean /= n
        var energy = 0.0
        for (i in 0 until n) {
            val w = 0.5 * (1.0 - cos(2.0 * Math.PI * i / (n - 1))) // Hann
            val v = (framePcm[i] - mean) * w
            f[i] = v
            energy += v * v
        }
        if (energy <= 1e-9) return PitchResult(0.0, 0.0)

        // === 2) 差分関数 d(tau) ===
        val tauMax = min(maxTau, n / 2)
        val d = DoubleArray(tauMax + 1)
        for (tau in 1..tauMax) {
            var sum = 0.0
            var i = 0
            // O(N * tauMax) 実装（2048程度なら十分速い）
            while (i + tau < n) {
                val diff = f[i] - f[i + tau]
                sum += diff * diff
                i++
            }
            d[tau] = sum
        }

        // === 3) 累積平均正規化差分関数 CMND ===
        val cmnd = DoubleArray(tauMax + 1)
        var running = 0.0
        cmnd[0] = 1.0
        for (tau in 1..tauMax) {
            running += d[tau]
            cmnd[tau] = if (running > 0) d[tau] * tau / running else 1.0
        }

        // === 4) 絶対閾値法で最初の極小を探索 ===
        var tauEstimate = -1
        var tau = minTau
        while (tau <= tauMax) {
            if (cmnd[tau] < threshold) {
                // 局所最小へ前進
                var t = tau
                while (t + 1 <= tauMax && cmnd[t + 1] < cmnd[t]) t++
                tauEstimate = t
                break
            }
            tau++
        }

        // 閾値未達：最小のCMND位置を採用（弱めだが最善推定）
        if (tauEstimate == -1) {
            var best = minTau
            var bestVal = cmnd[best]
            var t = minTau + 1
            while (t <= tauMax) {
                if (cmnd[t] < bestVal) {
                    bestVal = cmnd[t]
                    best = t
                }
                t++
            }
            tauEstimate = best
        }

        // === 5) 放物線補間でサブサンプル精度へ ===
        val t0 = (tauEstimate - 1).coerceAtLeast(minTau)
        val t1 = tauEstimate
        val t2 = (tauEstimate + 1).coerceAtMost(tauMax)
        val y0 = cmnd[t0]
        val y1 = cmnd[t1]
        val y2 = cmnd[t2]
        val denom = 2.0 * (2.0 * y1 - y2 - y0)
        val tauInterp = if (abs(denom) > 1e-12) {
            t1 + (y2 - y0) / denom
        } else t1.toDouble()

        // === 6) オクターブチェック（2倍周期の方がより良ければ置換） ===
        var tauBest = tauInterp
        val t2x = (tauInterp * 2.0).roundToInt()
            .coerceIn(minTau, tauMax)
        if (cmnd[t2x] + 0.01 < cmnd[t1]) { // 少しでも良ければ 2倍周期に寄せる
            tauBest = t2x.toDouble()
        }

        // === 7) 周波数・信頼度 ===
        val freq = sampleRate / tauBest
        if (freq.isNaN() || freq.isInfinite()) return PitchResult(0.0, 0.0)
        if (freq < minFreq || freq > maxFreq) return PitchResult(0.0, 0.0)

        val confidence = (1.0 - cmnd[t1]).coerceIn(0.0, 1.0)
        return PitchResult(freq, confidence)
    }
}
