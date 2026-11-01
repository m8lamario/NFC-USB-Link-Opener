package it.nfcusb.linkopener.session

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class FeedbackPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val vibrator: Vibrator? = createVibrator()

    fun success(vibration: Boolean, sound: Boolean) {
        if (vibration) vibrate(longArrayOf(0, 45))
        if (sound) tone(ToneGenerator.TONE_PROP_ACK, 140)
    }

    fun failure(vibration: Boolean, sound: Boolean) {
        if (vibration) vibrate(longArrayOf(0, 35, 45, 70))
        if (sound) tone(ToneGenerator.TONE_PROP_NACK, 220)
    }

    private fun vibrate(pattern: LongArray) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        device.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun tone(kind: Int, durationMs: Int) {
        val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
        generator.startTone(kind, durationMs)
        handler.postDelayed({ generator.release() }, durationMs + 80L)
    }

    private fun createVibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
