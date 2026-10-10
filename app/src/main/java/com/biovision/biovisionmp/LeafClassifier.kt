package com.biovision.biovisionmp

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val lightStatus: String,
    val careAdvice: String,
    val isConfident: Boolean
)

class LeafClassifier(context: Context) {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize = 224
    private val threshold = 0.70f

    init {
        val modelBuffer = loadModelFile(context, "modelo.tflite")
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(modelBuffer, options)
        labels = context.assets.open("labels.txt").bufferedReader().readLines().filter { it.isNotBlank() }
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun checkLight(bitmap: Bitmap): String {
        var totalBrightness = 0.0
        val width = bitmap.width
        val height = bitmap.height
        val sampleStep = 4 // Amostragem rápida a cada 4 pixels para economizar processamento
        var count = 0

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices step sampleStep) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            totalBrightness += luminance
            count++
        }

        val avgBrightness = if (count > 0) totalBrightness / count else 128.0
        return when {
            avgBrightness < 60 -> "Aviso: Ambiente escuro (procure mais luz)"
            avgBrightness > 200 -> "Aviso: Luz excessiva ou reflexo na folha"
            else -> "Iluminação adequada"
        }
    }

    fun classify(bitmap: Bitmap): ClassificationResult {
        val lightStatus = checkLight(bitmap)
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        // Buffer float32 para entrada [1, 224, 224, 3] (4 bytes por float)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in intValues) {
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        // Buffer de saída [1, 3]
        val outputBuffer = Array(1) { FloatArray(labels.size) }
        interpreter.run(inputBuffer, outputBuffer)

        val probabilities = outputBuffer[0]
        var maxIndex = 0
        var maxConfidence = probabilities[0]

        for (i in 1 until probabilities.size) {
            if (probabilities[i] > maxConfidence) {
                maxConfidence = probabilities[i]
                maxIndex = i
            }
        }

        val rawLabel = labels.getOrElse(maxIndex) { "desconhecido" }

        return if (maxConfidence < threshold) {
            ClassificationResult(
                label = "Inconclusivo",
                confidence = maxConfidence,
                lightStatus = lightStatus,
                careAdvice = "A confiança da análise ficou abaixo de 70%. Para maior precisão, tente aproximar a câmera, centralizar a folha e evitar sombras fortes no fundo.",
                isConfident = false
            )
        } else {
            val (displayLabel, advice) = when (rawLabel.lowercase()) {
                "saudavel" -> Pair(
                    "Folha Saudável",
                    "A folha exibe coloração e aspecto uniformes. Mantenha os cuidados habituais de rega e exposição à luz solar."
                )
                "oidio" -> Pair(
                    "Suspeita de Oídio",
                    "Manchas esbranquiçadas sugerem proliferação fúngica. Isole a planta para evitar contágio, remova partes comprometidas e melhore a ventilação do local."
                )
                "queimada" -> Pair(
                    "Queimadura Foliar",
                    "Bordas secas ou escurecidas indicam estresse hídrico ou excesso de sol direto. Ajuste a frequência de rega e proteja a folhagem nas horas mais quentes."
                )
                else -> Pair(
                    rawLabel.replaceFirstChar { it.uppercase() },
                    "Mantenha a planta sob observação nos próximos dias."
                )
            }

            ClassificationResult(
                label = displayLabel,
                confidence = maxConfidence,
                lightStatus = lightStatus,
                careAdvice = advice,
                isConfident = true
            )
        }
    }

    fun close() {
        interpreter.close()
    }
}
