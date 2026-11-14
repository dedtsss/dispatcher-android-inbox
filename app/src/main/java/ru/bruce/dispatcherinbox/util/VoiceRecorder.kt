package ru.bruce.dispatcherinbox.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

data class RecordedVoice(val file: File, val durationMillis: Long)

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMillis: Long? = null

    fun start(file: File, onError: (String) -> Unit = {}) {
        check(recorder == null) { "Запись уже идет" }
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioEncodingBitRate(96_000)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.setOnErrorListener { _, what, extra ->
                onError("Ошибка микрофона ($what/$extra)")
            }
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            outputFile = file
            startedAtMillis = SystemClock.elapsedRealtime()
        } catch (error: Exception) {
            mediaRecorder.release()
            file.delete()
            throw error
        }
    }

    fun stop(): RecordedVoice {
        val activeRecorder = recorder ?: error("Запись не запущена")
        val file = outputFile ?: error("Файл записи не создан")
        recorder = null
        outputFile = null
        val durationMillis = startedAtMillis?.let { SystemClock.elapsedRealtime() - it } ?: 0L
        startedAtMillis = null
        try {
            activeRecorder.stop()
        } catch (error: RuntimeException) {
            file.delete()
            throw IllegalStateException("Не удалось сохранить аудиозапись", error)
        } finally {
            activeRecorder.release()
        }
        return RecordedVoice(file, durationMillis)
    }

    fun currentAmplitude(): Int = runCatching {
        recorder?.maxAmplitude ?: 0
    }.getOrDefault(0)

    fun cancel() {
        val file = outputFile
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        outputFile = null
        startedAtMillis = null
        file?.delete()
    }

    fun isActuallyActive(): Boolean = recorder != null
}
