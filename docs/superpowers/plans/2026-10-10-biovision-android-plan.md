# BioVision Android App — Plano de Implementação

> **Para agentes de execução:** HABILIDADE OBRIGATÓRIA: Utilize superpowers:subagent-driven-development ou superpowers:executing-plans para implementar este plano passo a passo. Os passos utilizam a sintaxe de checkbox (`- [ ]`) para acompanhamento.

**Meta:** Construir uma aplicação nativa Android em Kotlin com layout XML tradicional para executar classificação offline de folhas (saudável, oídio, queimada) utilizando o modelo `modelo.tflite` com MobileNetV2 e limiar de corte de 70%.

**Arquitetura:** Projeto Android padrão com módulo `app`, tela única `MainActivity` baseada em `CardView` e classe de inferência autocontida `LeafClassifier`. As chamadas ao modelo rodam em background através de corrotinas (`Dispatchers.Default`), atualizando a interface de forma reativa.

**Stack Tecnológica:** Android SDK (minSdk 26, targetSdk 34), Kotlin, Android View Binding, Material Components, TensorFlow Lite (`org.tensorflow:tensorflow-lite:2.14.0`).

## Restrições Globais
- Formato de entrada do modelo: $224 \times 224$ pixels, RGB, float32 na faixa de 0 a 255.
- Ordem exata dos rótulos em `labels.txt`: `oidio`, `queimada`, `saudavel`.
- Limiar de corte de confiança: 0.70 (valores abaixo retornam "Inconclusivo").
- Regra de iluminação: média de luminância $L = 0.299R + 0.587G + 0.114B$. Alerta "Muito escuro" se $L < 60$ e "Muito claro" se $L > 200$.
- Sem permissões legadas de escrita em disco: uso estrito de `ActivityResultContracts`.

---

### Estrutura de Arquivos a Ser Criada

```
C:\Users\kaycs\Dev\lab-biovision\
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── app\
│   ├── build.gradle.kts
│   ├── src\main\
│   │   ├── AndroidManifest.xml
│   │   ├── assets\
│   │   │   ├── modelo.tflite
│   │   │   └── labels.txt
│   │   ├── java\com\biovision\app\
│   │   │   ├── MainActivity.kt
│   │   │   └── LeafClassifier.kt
│   │   └── res\
│   │       ├── layout\
│   │       │   └── activity_main.xml
│   │       └── values\
│   │           ├── colors.xml
│   │           ├── strings.xml
│   │           └── themes.xml
```

---

### Tarefa 1: Configuração do Projeto Android e Gradle Build Scripts

**Arquivos:**
- Criar: `settings.gradle.kts`
- Criar: `build.gradle.kts`
- Criar: `gradle.properties`
- Criar: `app/build.gradle.kts`

**Interfaces:**
- Produz: Ambiente Gradle funcional com dependência do TensorFlow Lite e suporte a View Binding.

- [ ] **Passo 1: Criar `settings.gradle.kts`**
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BioVision"
include(":app")
```

- [ ] **Passo 2: Criar `build.gradle.kts` raiz**
```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

- [ ] **Passo 3: Criar `gradle.properties`**
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

- [ ] **Passo 4: Criar `app/build.gradle.kts` com suporte a TFLite e View Binding**
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.biovision.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.biovision.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        viewBinding = true
    }

    aaptOptions {
        noCompress("tflite")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // TensorFlow Lite Runtime oficial
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
}
```

- [ ] **Passo 5: Commit do setup Gradle**
```bash
git add settings.gradle.kts build.gradle.kts gradle.properties app/build.gradle.kts
git commit -m "chore: configurações de build gradle com suporte a TFLite e View Binding"
```

---

### Tarefa 2: Cópia dos Artefatos de IA para os Assets do Android

**Arquivos:**
- Criar: `app/src/main/assets/modelo.tflite` (cópia de `model/modelo.tflite`)
- Criar: `app/src/main/assets/labels.txt` (cópia de `model/labels.txt`)

- [ ] **Passo 1: Copiar os arquivos para a pasta assets**
```powershell
New-Item -ItemType Directory -Force -Path "app/src/main/assets"
Copy-Item "model/modelo.tflite" "app/src/main/assets/modelo.tflite"
Copy-Item "model/labels.txt" "app/src/main/assets/labels.txt"
```

- [ ] **Passo 2: Verificar a integridade dos arquivos em assets**
Verificar se `modelo.tflite` possui ~2.5 MB e `labels.txt` contém `oidio`, `queimada`, `saudavel`.

- [ ] **Passo 3: Commit dos assets**
```bash
git add app/src/main/assets
git commit -m "feat: adiciona modelo.tflite e labels.txt aos assets do app Android"
```

---

### Tarefa 3: Implementação do Mecanismo de Inferência (`LeafClassifier.kt`)

**Arquivos:**
- Criar: `app/src/main/java/com/biovision/app/LeafClassifier.kt`

**Interfaces:**
- Consome: `context.assets`, Bitmap capturado.
- Produz: `data class ClassificationResult(val label: String, val confidence: Float, val lightStatus: String, val careAdvice: String)`

- [ ] **Passo 1: Escrever `LeafClassifier.kt` com pré-processamento, checagem de luz e inferência**
```kotlin
package com.biovision.app

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
    val careAdvice: String
)

class LeafClassifier(context: Context) {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize = 224
    private val threshold = 0.70f

    init {
        val modelBuffer = loadModelFile(context, "modelo.tflite")
        interpreter = Interpreter(modelBuffer)
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
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            totalBrightness += luminance
        }

        val avgBrightness = totalBrightness / pixels.size
        return when {
            avgBrightness < 60 -> "Aviso: Imagem muito escura"
            avgBrightness > 200 -> "Aviso: Imagem muito clara (excesso de luz)"
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

        val predictedLabel = labels.getOrElse(maxIndex) { "desconhecido" }

        return if (maxConfidence < threshold) {
            ClassificationResult(
                label = "Inconclusivo",
                confidence = maxConfidence,
                lightStatus = lightStatus,
                careAdvice = "A confiança ficou abaixo de 70%. Tire outra foto aproximada com iluminação natural e foco nítido na folha."
            )
        } else {
            val advice = when (predictedLabel.lowercase()) {
                "saudavel" -> "Folha saudável! Mantenha os cuidados habituais de rega e exposição à luz."
                "oidio" -> "Suspeita de oídio (fungo). Isole a planta, remova folhas gravemente afetadas e melhore a ventilação do ambiente."
                "queimada" -> "Suspeita de queimadura foliar. Verifique exposição solar direta excessiva ou desidratação por falta de rega."
                else -> "Mantenha a planta em observação."
            }
            ClassificationResult(
                label = predictedLabel.replaceFirstChar { it.uppercase() },
                confidence = maxConfidence,
                lightStatus = lightStatus,
                careAdvice = advice
            )
        }
    }

    fun close() {
        interpreter.close()
    }
}
```

- [ ] **Passo 2: Commit da classe `LeafClassifier`**
```bash
git add app/src/main/java/com/biovision/app/LeafClassifier.kt
git commit -m "feat: motor de inferência LeafClassifier com TFLite, verificação de luz e limiar de 70%"
```

---

### Tarefa 4: Layout XML Tradicional e Recursos de Interface

**Arquivos:**
- Criar: `app/src/main/res/values/colors.xml`
- Criar: `app/src/main/res/values/strings.xml`
- Criar: `app/src/main/res/values/themes.xml`
- Criar: `app/src/main/res/layout/activity_main.xml`

- [ ] **Passo 1: Criar `colors.xml` e `strings.xml`**
```xml
<!-- colors.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#2E7D32</color>
    <color name="primary_variant">#1B5E20</color>
    <color name="secondary">#81C784</color>
    <color name="background">#F1F8E9</color>
    <color name="surface">#FFFFFF</color>
    <color name="text_primary">#212121</color>
    <color name="text_secondary">#757575</color>
</resources>
```

```xml
<!-- strings.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">BioVision</string>
    <string name="title_app">BioVision</string>
    <string name="subtitle_app">Triagem Visual de Saúde de Folhas</string>
    <string name="btn_camera">Câmera</string>
    <string name="btn_gallery">Galeria</string>
    <string name="placeholder_ready">Selecione ou fotografe uma folha para analisar</string>
</resources>
```

- [ ] **Passo 2: Criar `themes.xml`**
```xml
<!-- themes.xml -->
<resources>
    <style name="Theme.BioVision" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryVariant">@color/primary_variant</item>
        <item name="colorSecondary">@color/secondary</item>
        <item name="android:statusBarColor">@color/primary_variant</item>
    </style>
</resources>
```

- [ ] **Passo 3: Criar `activity_main.xml` (CardView Centralizado)**
```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:orientation="vertical"
        android:padding="20dp">

        <com.google.android.material.card.MaterialCardView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:cardCornerRadius="16dp"
            app:cardElevation="6dp"
            app:cardBackgroundColor="@color/surface">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="20dp"
                android:gravity="center_horizontal">

                <TextView
                    android:id="@+id/tvTitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/title_app"
                    android:textColor="@color/primary"
                    android:textSize="24sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tvSubtitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/subtitle_app"
                    android:textColor="@color/text_secondary"
                    android:textSize="14sp" />

                <ImageView
                    android:id="@+id/ivLeaf"
                    android:layout_width="260dp"
                    android:layout_height="260dp"
                    android:layout_marginTop="16dp"
                    android:background="#E0E0E0"
                    android:scaleType="centerCrop"
                    android:contentDescription="Visualização da Folha" />

                <TextView
                    android:id="@+id/tvLightStatus"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:textColor="@color/text_secondary"
                    android:textSize="13sp" />

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="16dp"
                    android:orientation="horizontal">

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnCamera"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginEnd="8dp"
                        android:text="@string/btn_camera"
                        app:icon="@android:drawable/ic_menu_camera"
                        app:cornerRadius="12dp" />

                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/btnGallery"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:layout_marginStart="8dp"
                        android:text="@string/btn_gallery"
                        app:icon="@android:drawable/ic_menu_gallery"
                        app:cornerRadius="12dp" />
                </LinearLayout>

                <TextView
                    android:id="@+id/tvResult"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="20dp"
                    android:text="@string/placeholder_ready"
                    android:textAlignment="center"
                    android:textColor="@color/text_primary"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tvAdvice"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:background="#F9FBE7"
                    android:padding="12dp"
                    android:textColor="@color/text_primary"
                    android:textSize="14sp"
                    android:visibility="gone" />

            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>

    </LinearLayout>
</ScrollView>
```

- [ ] **Passo 4: Commit dos recursos e layout XML**
```bash
git add app/src/main/res
git commit -m "feat: layout XML da MainActivity baseado no card de referência com Material Design"
```

---

### Tarefa 5: Implementação da `MainActivity.kt` e `AndroidManifest.xml`

**Arquivos:**
- Criar: `app/src/main/AndroidManifest.xml`
- Criar: `app/src/main/java/com/biovision/app/MainActivity.kt`

- [ ] **Passo 1: Criar `AndroidManifest.xml`**
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/ic_menu_camera"
        android:label="@string/app_name"
        android:roundIcon="@android:drawable/ic_menu_camera"
        android:supportsRtl="true"
        android:theme="@style/Theme.BioVision">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Passo 2: Implementar `MainActivity.kt` com View Binding e Corrotinas**
```kotlin
package com.biovision.app

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.biovision.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var classifier: LeafClassifier

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { processImage(it) }
    }

    private val pickMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = loadBitmapFromUri(it)
            bitmap?.let { b -> processImage(b) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classifier = LeafClassifier(this)

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
        binding.tvResult.text = "Analisando folha..."
        binding.tvLightStatus.text = ""
        binding.tvAdvice.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.Default) {
            val result = classifier.classify(bitmap)

            withContext(Dispatchers.Main) {
                binding.tvLightStatus.text = result.lightStatus
                val percentText = String.format("%.1f%%", result.confidence * 100)
                binding.tvResult.text = "${result.label} ($percentText)"
                binding.tvAdvice.text = result.careAdvice
                binding.tvAdvice.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
    }
}
```

- [ ] **Passo 3: Commit da `MainActivity` e do manifesto**
```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/biovision/app/MainActivity.kt
git commit -m "feat: MainActivity conectando captura de câmera, galeria e inferência assíncrona"
```

---

### Tarefa 6: Validação Final e Merge

- [ ] **Passo 1: Verificação de consistência de arquivos**
Verificar se todas as classes, layouts, assets e scripts de build estão no lugar correto.

- [ ] **Passo 2: Merge da branch no `main`**
```bash
git switch main
git merge --no-ff feature/android-plan -m "merge: implementação do aplicativo Android BioVision em Kotlin"
```
