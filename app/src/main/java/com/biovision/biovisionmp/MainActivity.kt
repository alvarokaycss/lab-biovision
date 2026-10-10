package com.biovision.biovisionmp

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.biovision.biovisionmp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var classifier: LeafClassifier? = null

    // Contrato moderno de câmera (captura direta sem permissão pesada de armazenamento)
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { processImage(it) }
    }

    // Contrato moderno de galeria (Photo Picker do Android)
    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = loadBitmapFromUri(it)
            bitmap?.let { b -> processImage(b) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Habilita Edge-to-Edge nativo: barra de status transparente sem cortes duros
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Aplica insets nas margens para respeitar a área da câmera/entalhe com fluidez
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                systemBars.bottom
            )
            insets
        }

        // Inicializa o classificador com tratamento defensivo
        try {
            classifier = LeafClassifier(this)
        } catch (e: Exception) {
            binding.tvResult.text = "Falha ao inicializar modelo"
            binding.tvSubtitle.text = e.localizedMessage
        }

        binding.btnCamera.setOnClickListener {
            takePictureLauncher.launch(null)
        }

        binding.btnGallery.setOnClickListener {
            pickMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun processImage(bitmap: Bitmap) {
        binding.ivLeaf.setImageBitmap(bitmap)
        binding.tvResult.text = getString(R.string.analyzing_prompt)
        binding.tvLightStatus.visibility = View.GONE
        binding.cardAdvice.visibility = View.GONE

        val currentClassifier = classifier ?: return

        // Execução desacoplada da thread de interface (regra mandatória de Edge AI)
        lifecycleScope.launch(Dispatchers.Default) {
            val result = currentClassifier.classify(bitmap)

            withContext(Dispatchers.Main) {
                binding.tvLightStatus.text = result.lightStatus
                binding.tvLightStatus.visibility = View.VISIBLE

                val percentText = String.format("%.1f%%", result.confidence * 100)
                binding.tvResult.text = "${result.label} ($percentText)"

                val statusColor = when {
                    !result.isConfident -> ContextCompat.getColor(this@MainActivity, R.color.status_inconclusive)
                    result.label.contains("Saudável", ignoreCase = true) -> ContextCompat.getColor(this@MainActivity, R.color.status_healthy)
                    result.label.contains("Oídio", ignoreCase = true) -> ContextCompat.getColor(this@MainActivity, R.color.status_mildew)
                    else -> ContextCompat.getColor(this@MainActivity, R.color.status_scorch)
                }
                binding.tvResult.setTextColor(statusColor)

                binding.tvAdvice.text = result.careAdvice
                binding.cardAdvice.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier?.close()
    }
}