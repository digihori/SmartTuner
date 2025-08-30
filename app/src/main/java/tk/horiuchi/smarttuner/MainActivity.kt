package tk.horiuchi.smarttuner

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import tk.horiuchi.smarttuner.audio.AudioEngine
import tk.horiuchi.smarttuner.core.InstrumentMode
import tk.horiuchi.smarttuner.core.NoteMapper
import tk.horiuchi.smarttuner.ui.NeedleView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var needle: NeedleView
    private lateinit var textNote: TextView
    private lateinit var textFreq: TextView
    private lateinit var modeChips: ChipGroup
    private lateinit var textA4: TextView
    private lateinit var seekA4: SeekBar
    //private lateinit var stringsBar: androidx.appcompat.widget.LinearLayoutCompat
    private lateinit var stringsBar: android.widget.LinearLayout
    private lateinit var seekToneVol: SeekBar

    private var mode = InstrumentMode.GUITAR
    private var engine: AudioEngine? = null

    // 平滑化
    private val medianBuf = ArrayDeque<Double>()
    private val medianWin = 5
    private var lpfFreq = 0.0
    private val alpha = 0.25

    // Tone
    private var toneTrack: AudioTrack? = null
    private var toneVol = 0.5f

    private val permReq = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startEngine()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        needle = findViewById(R.id.needle)
        textNote = findViewById(R.id.textNote)
        textFreq = findViewById(R.id.textFreq)
        modeChips = findViewById(R.id.modeChips)
        textA4 = findViewById(R.id.textA4)
        seekA4 = findViewById(R.id.seekA4)
        stringsBar = findViewById(R.id.stringButtons)
        seekToneVol = findViewById(R.id.seekToneVol)

        setupModeChips()
        setupA4()
        setupStrings()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permReq.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startEngine()
        }

        seekToneVol.progress = (toneVol*100).toInt()
        seekToneVol.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { toneVol = p/100f }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEngine()
        stopTone()
    }

    private fun setupModeChips() {
        findViewById<Chip>(R.id.chipGuitar).isChecked = true
        modeChips.setOnCheckedStateChangeListener { _, ids ->
            mode = when (ids.firstOrNull()) {
                R.id.chipBass -> InstrumentMode.BASS
                R.id.chipUkulele -> InstrumentMode.UKULELE
                R.id.chipViolin -> InstrumentMode.VIOLIN
                else -> InstrumentMode.GUITAR
            }
            setupStrings()
        }
    }

    private fun setupA4() {
        // 435–445Hz を 0..100 にマッピング
        fun a4FromProgress(p: Int) = 435.0 + (p/100.0)*10.0
        seekA4.progress = 50 // 440Hz
        textA4.text = "440Hz"
        seekA4.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val a4 = a4FromProgress(p)
                NoteMapper.a4 = a4
                textA4.text = "${"%.1f".format(a4)}Hz"
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }

    private fun setupStrings() {
        stringsBar.removeAllViews()
        mode.stringsHz.forEachIndexed { idx, hz ->
            val b = Button(this).apply {
                text = "${idx+1}弦 ${"%.2f".format(hz)}Hz"
                setOnClickListener { playTone(hz) }
            }
            stringsBar.addView(b)
        }
    }

    private fun startEngine() {
        engine = AudioEngine(48000) { freqRaw, _ ->
            val freq = smooth(freqRaw)
            runOnUiThread {
                if (freq <= 0.0) {
                    textNote.text = "--"
                    textFreq.text = "0.00 Hz"
                    needle.cents = 0f
                    needle.inTune = false
                } else {
                    val np = tk.horiuchi.smarttuner.core.NoteMapper.map(freq)
                    textNote.text = np.name
                    textFreq.text = "%.2f Hz".format(freq)
                    needle.cents = np.cents.toFloat().coerceIn(-50f, 50f)
                    needle.inTune = abs(np.cents) <= 5.0
                }
            }
        }.also { it.start() }
    }

    private fun stopEngine() { engine?.stop(); engine = null }

    private fun smooth(freq: Double): Double {
        if (freq <= 0.0) { medianBuf.clear(); lpfFreq = 0.0; return 0.0 }
        medianBuf.addLast(freq)
        if (medianBuf.size > medianWin) medianBuf.removeFirst()
        val med = medianBuf.sorted()[medianBuf.size/2]
        lpfFreq = if (lpfFreq==0.0) med else (1-alpha)*lpfFreq + alpha*med
        return lpfFreq
    }

    private fun playTone(f: Double) {
        stopTone()
        val sr = 48000
        val seconds = 2.0
        val len = (sr*seconds).toInt()
        val buf = ShortArray(len)
        val twoPiF = 2*Math.PI*f
        var phase = 0.0
        val step = twoPiF/sr
        for (i in 0 until len) {
            val s = kotlin.math.sin(phase)
            buf[i] = (s * Short.MAX_VALUE * toneVol).toInt().toShort()
            phase += step
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sr)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buf.size*2)
            .build()
        track.write(buf, 0, buf.size)
        track.play()
        toneTrack = track
    }

    private fun stopTone() { toneTrack?.stop(); toneTrack?.release(); toneTrack = null }
}
