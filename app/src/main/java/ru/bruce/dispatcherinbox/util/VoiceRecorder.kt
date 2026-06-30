package ru.bruce.dispatcherinbox.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(file: File) {
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
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            outputFile = file
        } catch (error: Exception) {
            mediaRecorder.release()
            file.delete()
            throw error
        }
    }

    fun stop(): File {
        val activeRecorder = recorder ?: error("Запись не запущена")
        val file = outputFile ?: error("Файл записи не создан")
        recorder = null
        outputFile = null
        try {
            activeRecorder.stop()
        } catch (error: RuntimeException) {
            file.delete()
            throw IllegalStateException("Не удалось сохранить аудиозапись", error)
        } finally {
            activeRecorder.release()
        }
        return file
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
        file?.delete()
    }
}
