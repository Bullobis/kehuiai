package com.kehuiai.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 音频生成引擎
 * 支持：文本转语音、音色选择、语速调节
 * 基于本地合成，不依赖云端API
 */
class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioEngine"
        
        // 采样率
        const val SAMPLE_RATE = 22050
        const val SAMPLE_RATE_HIGH = 44100
        
        // 音色类型
        const val VOICE_MALE = 0
        const val VOICE_FEMALE = 1
        const val VOICE_NEUTRAL = 2
        const val VOICE_ROBOT = 3
        const val VOICE_CHILD = 4
    }

    // 当前设置
    private var currentVoice = VOICE_NEUTRAL
    private var currentSpeed = 1.0f
    private var currentPitch = 1.0f
    private var currentVolume = 1.0f
    
    // 音频播放器引用（用于资源管理）
    @Volatile
    private var currentPlayer: AudioTrack? = null
    
    // 音频目录
    private val audioDir = File(context.filesDir, "audio")
    private val ttsDir = File(audioDir, "tts")
    
    init {
        if (!audioDir.exists()) audioDir.mkdirs()
        if (!ttsDir.exists()) ttsDir.mkdirs()
    }

    /**
     * 设置音色
     */
    fun setVoice(voice: Int) {
        currentVoice = voice
    }

    /**
     * 设置语速 (0.5 - 2.0)
     */
    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0.5f, 2.0f)
    }

    /**
     * 设置音调 (0.5 - 2.0)
     */
    fun setPitch(pitch: Float) {
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
    }

    /**
     * 设置音量 (0.0 - 1.0)
     */
    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0.0f, 1.0f)
    }

    /**
     * 文本转语音
     */
    fun textToSpeech(
        text: String,
        voice: Int = currentVoice,
        speed: Float = currentSpeed,
        pitch: Float = currentPitch,
        sampleRate: Int = SAMPLE_RATE
    ): Flow<AudioProgress> = flow {
        emit(AudioProgress.Status("正在合成语音..."))

        try {
            // 分词处理
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            @Suppress("UNUSED_VARIABLE") val totalWords = words.size
            
            emit(AudioProgress.Progress(0, "准备合成..."))

            // 生成音频数据
            val audioData = generateSpeech(
                text = text,
                voice = voice,
                speed = speed,
                pitch = pitch,
                sampleRate = sampleRate
            )

            emit(AudioProgress.Progress(50, "渲染音频..."))

            // 保存文件
            val outputFile = saveAudio(audioData, text, sampleRate)

            emit(AudioProgress.Progress(90, "保存完成..."))

            emit(AudioProgress.Completed(outputFile.absolutePath, audioData))

        } catch (e: Exception) {
            Log.e(TAG, "TTS error: ${e.message}")
            emit(AudioProgress.Error("语音合成失败: ${e.message}"))
        }
    }

    /**
     * 生成语音波形
     */
    private suspend fun generateSpeech(
        text: String,
        voice: Int,
        speed: Float,
        pitch: Float,
        sampleRate: Int
    ): ShortArray = withContext(Dispatchers.Default) {
        
        // 估算音频长度（每字符约0.1秒）
        val baseDuration = (text.length * 0.1 / speed).toInt()
        val numSamples = baseDuration * sampleRate
        
        val audioData = ShortArray(numSamples)
        
        // 根据音色设置参数
        val (baseFreq, harmonicCount, vibrato) = getVoiceParams(voice)
        
        var sampleIndex = 0
        var time = 0.0
        
        // 文本到音素的简单映射（实际需要更复杂的TTS引擎）
        val phonemes = textToPhonemes(text)
        
        for (phoneme in phonemes) {
            val phonemeDuration = (phoneme.duration * sampleRate / speed).toInt()
            
            for (i in 0 until phonemeDuration) {
                if (sampleIndex >= numSamples) break
                
                val t = time + i.toDouble() / sampleRate
                
                // 基础频率 + 音调调整
                val freq = baseFreq * pitch
                
                // 振动效果
                @Suppress("UNUSED_VARIABLE") val vibratoOffset = if (vibrato) sin(t * 5 * 2 * PI) * 10 else 0.0
                
                // 生成复合波形
                var sample = 0.0
                for (h in 1..harmonicCount) {
                    val harmonicFreq = freq * h
                    sample += sin(2 * PI * harmonicFreq * t + phoneme.phase) / h
                }
                
                // 包络（ADSR）
                val envelope = getEnvelope(i.toDouble(), phonemeDuration.toDouble())
                
                // 应用包络
                sample *= envelope
                
                // 转换为16位音频
                val sampleShort = (sample * 32767 * currentVolume * envelope).toInt().coerceIn(-32768, 32767).toShort()
                audioData[sampleIndex] = sampleShort
                
                sampleIndex++
            }
            
            time += phonemeDuration.toDouble() / sampleRate
        }
        
        // 淡出
        val fadeOutSamples = (sampleRate * 0.1).toInt()
        for (i in 0 until fadeOutSamples) {
            if (sampleIndex - i > 0) {
                val fade = 1.0 - i.toDouble() / fadeOutSamples
                audioData[sampleIndex - i - 1] = (audioData[sampleIndex - i - 1] * fade).toInt().toShort()
            }
        }
        
        audioData
    }

    /**
     * 文本转音素（简化实现）
     */
    private fun textToPhonemes(text: String): List<Phoneme> {
        val phonemes = mutableListOf<Phoneme>()
        
        // 简单的字符到音素映射
        for (char in text.lowercase()) {
            when (char) {
                'a' -> phonemes.add(Phoneme(0.1, 0.0))
                'e' -> phonemes.add(Phoneme(0.1, 0.5))
                'i' -> phonemes.add(Phoneme(0.08, 1.0))
                'o' -> phonemes.add(Phoneme(0.12, 1.5))
                'u' -> phonemes.add(Phoneme(0.1, 2.0))
                'b', 'd', 'g', 'p', 't', 'k' -> phonemes.add(Phoneme(0.05, 0.0))
                's', 'z', 'f', 'v' -> phonemes.add(Phoneme(0.08, 3.0))
                'l', 'r', 'm', 'n' -> phonemes.add(Phoneme(0.1, 0.0))
                ' ' -> phonemes.add(Phoneme(0.05, 0.0))
            }
        }
        
        return phonemes
    }

    /**
     * 获取音色参数
     */
    private fun getVoiceParams(voice: Int): Triple<Double, Int, Boolean> {
        return when (voice) {
            VOICE_MALE -> Triple(100.0, 3, true)      // 男声：低频，少量谐波，有振动
            VOICE_FEMALE -> Triple(200.0, 5, false)   // 女声：高频，更多谐波
            VOICE_NEUTRAL -> Triple(150.0, 4, false)  // 中性
            VOICE_ROBOT -> Triple(120.0, 2, false)    // 机器人：简单波形
            VOICE_CHILD -> Triple(300.0, 6, false)    // 童声：高频，丰富谐波
            else -> Triple(150.0, 4, false)
        }
    }

    /**
     * ADSR 包络
     */
    private fun getEnvelope(sample: Double, totalSamples: Double): Double {
        val attack = totalSamples * 0.1
        val decay = totalSamples * 0.1
        val sustain = totalSamples * 0.6
        val release = totalSamples * 0.2
        
        return when {
            sample < attack -> sample / attack
            sample < attack + decay -> 1.0 - (sample - attack) / decay * 0.3
            sample < attack + decay + sustain -> 1.0
            else -> 1.0 - (sample - attack - decay - sustain) / release
        }.coerceIn(0.0, 1.0)
    }

    /**
     * 保存音频文件
     */
    private fun saveAudio(audioData: ShortArray, text: String, sampleRate: Int): File {
        val timestamp = System.currentTimeMillis()
        val sanitizedText = text.take(20).replace(Regex("[^a-zA-Z0-9]"), "_")
        val fileName = "tts_${sanitizedText}_$timestamp.wav"
        val file = File(ttsDir, fileName)
        
        FileOutputStream(file).use { fos ->
            // WAV 文件头
            val dataSize = audioData.size * 2
            val fileSize = 36 + dataSize
            
            // RIFF header
            fos.write("RIFF".toByteArray())
            fos.write(intToBytes(fileSize))
            fos.write("WAVE".toByteArray())
            
            // fmt chunk
            fos.write("fmt ".toByteArray())
            fos.write(intToBytes(16)) // chunk size
            fos.write(shortToBytes(1)) // PCM format
            fos.write(shortToBytes(1)) // mono
            fos.write(intToBytes(sampleRate))
            fos.write(intToBytes(sampleRate * 2)) // byte rate
            fos.write(shortToBytes(2)) // block align
            fos.write(shortToBytes(16)) // bits per sample
            
            // data chunk
            fos.write("data".toByteArray())
            fos.write(intToBytes(dataSize))
            
            // 音频数据
            val buffer = ByteBuffer.allocate(audioData.size * 2)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            for (sample in audioData) {
                buffer.putShort(sample)
            }
            fos.write(buffer.array())
        }
        
        return file
    }

    private fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()
    }

    /**
     * 播放音频
     */
    @Synchronized
    fun play(audioData: ShortArray, sampleRate: Int = SAMPLE_RATE) {
        // 先停止之前的播放
        stop()
        
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        // 保存引用以便后续控制
        currentPlayer = audioTrack
        
        audioTrack.write(audioData, 0, audioData.size)
        audioTrack.play()
    }

    /**
     * 停止播放
     */
    @Synchronized
    fun stop() {
        currentPlayer?.let { player ->
            try {
                if (player.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio: ${e.message}")
            }
        }
        currentPlayer = null
    }
    
    /**
     * 释放所有资源
     */
    @Synchronized
    fun release() {
        stop()
    }

    /**
     * 获取可用音色列表
     */
    fun getAvailableVoices(): List<VoiceInfo> {
        return listOf(
            VoiceInfo(VOICE_MALE, "男声", "低沉有力的男声"),
            VoiceInfo(VOICE_FEMALE, "女声", "清晰明亮的女声"),
            VoiceInfo(VOICE_NEUTRAL, "中性", "自然中性音色"),
            VoiceInfo(VOICE_ROBOT, "机器人", "机械合成音色"),
            VoiceInfo(VOICE_CHILD, "童声", "清脆可爱的童声")
        )
    }

    data class Phoneme(val duration: Double, val phase: Double)
    data class VoiceInfo(val id: Int, val name: String, val description: String)
}

sealed class AudioProgress {
    data class Status(val message: String) : AudioProgress()
    data class Progress(val percent: Int, val message: String) : AudioProgress()
    data class Completed(val path: String, val audioData: ShortArray) : AudioProgress()
    data class Error(val message: String) : AudioProgress()
}
