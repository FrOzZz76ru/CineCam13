package com.cinecam13

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var frontCamera = false
    private var videoMode = false
    private var recordingActive = false

    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var selectedStyle = LutProcessor.Style.NAT
    private var filterPanelVisible = false

    private lateinit var flashButton: TextView
    private lateinit var modeButton: TextView
    private lateinit var captureButton: TextView
    private lateinit var filterButton: TextView
    private lateinit var filterPanel: LinearLayout
    private lateinit var styleContainer: LinearLayout
    private lateinit var statusText: TextView

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            if (hasCameraPermission()) {
                startCamera()
            } else {
                toast("Для работы CineCam13 нужен доступ к камере")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        buildInterface()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    // ---------------------------------------------------------
    // ИНТЕРФЕЙС
    // ---------------------------------------------------------
    private fun buildInterface() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        setContentView(root)

        previewView = PreviewView(this).apply {
            implementationMode =
                PreviewView.ImplementationMode.COMPATIBLE

            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Нажатие на изображение запускает фокусировку.
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                focusAt(event.x, event.y)
            }
            true
        }

        // Верхняя панель.
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(12), dp(8))
            setBackgroundColor(0x66000000)
        }

        val title = TextView(this).apply {
            text = "CINECAM"
            textSize = 19f
            letterSpacing = 0.18f
            setTextColor(Color.WHITE)
        }

        val spacer = View(this)

        flashButton = TextView(this).apply {
            text = "⚡ OFF"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(8), dp(10), dp(8))

            setOnClickListener {
                cycleFlash()
            }
        }

        val switchButton = TextView(this).apply {
            text = "↻"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(4), 0)

            setOnClickListener {
                if (!recordingActive) {
                    frontCamera = !frontCamera

                    if (frontCamera) {
                        flashMode = ImageCapture.FLASH_MODE_OFF
                    }

                    updateFlashButton()
                    startCamera()
                }
            }
        }

        topBar.addView(title)

        topBar.addView(
            spacer,
            LinearLayout.LayoutParams(0, dp(1), 1f)
        )

        topBar.addView(flashButton)
        topBar.addView(switchButton)

        root.addView(
            topBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58),
                Gravity.TOP
            )
        )

        // Нижняя панель управления.
        val bottomPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(0xE6000000.toInt())
        }

        // Переключение фото и видео.
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val photoModeButton = makeTextButton("ФОТО") {
            if (!recordingActive) {
                videoMode = false
                updateModeUI()
            }
        }

        modeButton = makeTextButton("ВИДЕО") {
            if (!recordingActive) {
                videoMode = true
                updateModeUI()
            }
        }

        modeRow.addView(
            photoModeButton,
            LinearLayout.LayoutParams(dp(110), dp(42))
        )

        modeRow.addView(
            modeButton,
            LinearLayout.LayoutParams(dp(110), dp(42))
        )

        bottomPanel.addView(
            modeRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )

        // Одна отдельная кнопка выбора фильтров.
        filterButton = TextView(this).apply {
            text = "◈  NATURAL  ▾"
            textSize = 12f
            letterSpacing = 0.06f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFD27A.toInt())
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setBackgroundColor(0xFF252525.toInt())

            setOnClickListener {
                toggleFilterPanel()
            }
        }

        val filterButtonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))

            addView(
                filterButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(42)
                )
            )
        }

        bottomPanel.addView(
            filterButtonContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            )
        )

        // Отдельная панель фильтров.
        filterPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(8))
            setBackgroundColor(0xFF171717.toInt())
            visibility = View.GONE
        }

        val filterHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val filterTitle = TextView(this).apply {
            text = "ЦВЕТОВЫЕ ПРОФИЛИ"
            textSize = 11f
            letterSpacing = 0.10f
            setTextColor(0xFFAAAAAA.toInt())
        }

        val closeFilters = TextView(this).apply {
            text = "✕"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(4), dp(8), dp(4))

            setOnClickListener {
                hideFilterPanel()
            }
        }

        filterHeader.addView(
            filterTitle,
            LinearLayout.LayoutParams(0, dp(32), 1f)
        )

        filterHeader.addView(
            closeFilters,
            LinearLayout.LayoutParams(dp(40), dp(32))
        )

        filterPanel.addView(filterHeader)

        val filterScroll =
            android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
            }

        styleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        filterScroll.addView(styleContainer)

        filterPanel.addView(
            filterScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        buildStyleButtons()

        bottomPanel.addView(
            filterPanel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(88)
            )
        )
        // Галерея и кнопка съёмки.
        val controls = FrameLayout(this).apply {
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }

        val galleryButton = TextView(this).apply {
            text = "▧"
            textSize = 29f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            setOnClickListener {
                openGallery()
            }
        }

        captureButton = TextView(this).apply {
            text = "●"
            textSize = 56f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            setOnClickListener {
                if (videoMode) {
                    toggleRecording()
                } else {
                    takePhoto()
                }
            }
        }

        controls.addView(
            galleryButton,
            FrameLayout.LayoutParams(
                dp(60),
                dp(64),
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )

        controls.addView(
            captureButton,
            FrameLayout.LayoutParams(
                dp(90),
                dp(68),
                Gravity.CENTER
            )
        )

        bottomPanel.addView(
            controls,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(70)
            )
        )

        statusText = TextView(this).apply {
            text = "ГОТОВ К СЪЁМКЕ"
            textSize = 10f
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            setTextColor(0xFFCCCCCC.toInt())
        }

        bottomPanel.addView(
            statusText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(20)
            )
        )

        root.addView(
            bottomPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        updateModeUI()
        updateFlashButton()
    }

    // ---------------------------------------------------------
    // ФИЛЬТРЫ И ПАНЕЛЬ
    // ---------------------------------------------------------

    private fun buildStyleButtons() {
        styleContainer.removeAllViews()

        val styles = listOf(
            LutProcessor.Style.NAT to "NATURAL",
            LutProcessor.Style.CINE to "CINEMA",
            LutProcessor.Style.KODACHROME_64 to "KODAK 64",
            LutProcessor.Style.PORTRA_400 to "PORTRA 400",
            LutProcessor.Style.PORTRA_800 to "PORTRA 800",
            LutProcessor.Style.FUJI_FILM to "FUJI FILM",
            LutProcessor.Style.FUJI_PORTRAIT to "FUJI PORTRAIT",
            LutProcessor.Style.TEAL_ORANGE to "TEAL ORANGE",
            LutProcessor.Style.MOODY_AQUA to "MOODY AQUA",
            LutProcessor.Style.WARM to "WARM",
            LutProcessor.Style.COOL to "COOL"
        )

        styles.forEach { (style, label) ->
            val selected = style == selectedStyle

            val button = TextView(this).apply {
                text = if (selected) "✓  $label" else label
                textSize = 11f
                gravity = Gravity.CENTER
                letterSpacing = 0.02f
                setPadding(dp(13), 0, dp(13), 0)

                setTextColor(
                    if (selected) 0xFFFFD27A.toInt()
                    else 0xFFE0E0E0.toInt()
                )

                setBackgroundColor(
                    if (selected) 0xFF353027.toInt()
                    else 0xFF222222.toInt()
                )

                setOnClickListener {
                    selectedStyle = style

                    filterButton.text = "◈  $label  ▾"

                    buildStyleButtons()
                    hideFilterPanel()

                    statusText.text = "ПРОФИЛЬ: $label"
                }
            }

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(36)
            ).apply {
                marginEnd = dp(7)
            }

            styleContainer.addView(button, params)
        }
    }

    private fun toggleFilterPanel() {
        if (filterPanelVisible) {
            hideFilterPanel()
        } else {
            filterPanelVisible = true
            filterPanel.visibility = View.VISIBLE
            updateFilterButtonArrow()
        }
    }

    private fun hideFilterPanel() {
        filterPanelVisible = false
        filterPanel.visibility = View.GONE
        updateFilterButtonArrow()
    }

    private fun updateFilterButtonArrow() {
        val label = styleName(selectedStyle)
        val arrow = if (filterPanelVisible) "▴" else "▾"

        filterButton.text = "◈  $label  $arrow"
    }

    private fun styleName(style: LutProcessor.Style): String {
        return when (style) {
            LutProcessor.Style.NAT -> "NATURAL"
            LutProcessor.Style.CINE -> "CINEMA"
            LutProcessor.Style.KODACHROME_64 -> "KODAK 64"
            LutProcessor.Style.PORTRA_400 -> "PORTRA 400"
            LutProcessor.Style.PORTRA_800 -> "PORTRA 800"
            LutProcessor.Style.FUJI_FILM -> "FUJI FILM"
            LutProcessor.Style.FUJI_PORTRAIT -> "FUJI PORTRAIT"
            LutProcessor.Style.TEAL_ORANGE -> "TEAL ORANGE"
            LutProcessor.Style.MOODY_AQUA -> "MOODY AQUA"
            LutProcessor.Style.WARM -> "WARM"
            LutProcessor.Style.COOL -> "COOL"
        }
    }

    // ---------------------------------------------------------
    // РЕЖИМЫ
    // ---------------------------------------------------------

    private fun updateModeUI() {
        modeButton.text = if (videoMode) "● ВИДЕО" else "ВИДЕО"
        captureButton.text = if (recordingActive) "■"
        else if (videoMode) "◉" else "●"

        statusText.text = when {
            recordingActive -> "ИДЁТ ЗАПИСЬ"
            videoMode -> "РЕЖИМ ВИДЕО"
            else -> "РЕЖИМ ФОТО"
        }
    }

    // ---------------------------------------------------------
    // ЗАПУСК КАМЕРЫ
    // ---------------------------------------------------------
    private fun startCamera() {
        val providerFuture =
            ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()

                val selector = if (frontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                if (!provider.hasCamera(selector)) {
                    toast("Эта камера недоступна")
                    return@addListener
                }

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .setFlashMode(flashMode)
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(Quality.FHD)
                    )
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)

                provider.unbindAll()

                camera = provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageCapture,
                    videoCapture
                )

                updateFlashButton()
                statusText.text = "КАМЕРА ГОТОВА"

            } catch (e: Exception) {
                toast(
                    "Не удалось запустить камеру: " +
                        (e.localizedMessage ?: "ошибка")
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ---------------------------------------------------------
    // ВСПЫШКА
    // ---------------------------------------------------------

    private fun cycleFlash() {
        if (frontCamera) {
            toast("Вспышка доступна только на задней камере")
            return
        }

        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF ->
                ImageCapture.FLASH_MODE_AUTO

            ImageCapture.FLASH_MODE_AUTO ->
                ImageCapture.FLASH_MODE_ON

            else ->
                ImageCapture.FLASH_MODE_OFF
        }

        imageCapture?.flashMode = flashMode
        updateFlashButton()
    }

    private fun updateFlashButton() {
        flashButton.text = when {
            frontCamera -> "⚡ OFF"

            flashMode == ImageCapture.FLASH_MODE_AUTO ->
                "⚡ AUTO"

            flashMode == ImageCapture.FLASH_MODE_ON ->
                "⚡ ON"

            else -> "⚡ OFF"
        }
    }

    // ---------------------------------------------------------
    // ФОКУСИРОВКА
    // ---------------------------------------------------------

    private fun focusAt(x: Float, y: Float) {
        val activeCamera = camera ?: return

        if (previewView.width <= 0 || previewView.height <= 0) {
            return
        }

        try {
            val point: MeteringPoint =
                previewView.meteringPointFactory.createPoint(x, y)

            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or
                    FocusMeteringAction.FLAG_AE or
                    FocusMeteringAction.FLAG_AWB
            )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()

            activeCamera.cameraControl
                .startFocusAndMetering(action)
                .addListener({
                    runOnUiThread {
                        statusText.text = "ФОКУСИРОВКА"
                    }
                }, ContextCompat.getMainExecutor(this))

        } catch (_: Exception) {
            statusText.text = "НЕ УДАЛОСЬ СФОКУСИРОВАТЬСЯ"
        }
    }

    // ---------------------------------------------------------
    // ФОТОГРАФИЯ
    // ---------------------------------------------------------

    private fun takePhoto() {
        val capture = imageCapture ?: run {
            toast("Камера ещё запускается")
            return
        }

        if (recordingActive) return

        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                "CineCam_${System.currentTimeMillis()}.jpg"
            )
            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/CineCam13"
                )
            }
        }

        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )

        if (uri == null) {
            toast("Не удалось создать файл фотографии")
            return
        }

        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            uri,
            values
        ).build()

        statusText.text = "СЪЁМКА..."

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    output: ImageCapture.OutputFileResults
                ) {
                    if (selectedStyle == LutProcessor.Style.NAT) {
                        statusText.text = "ФОТО СОХРАНЕНО"
                        toast("Фото сохранено")
                    } else {
                        statusText.text = "ПРИМЕНЕНИЕ ФИЛЬТРА..."

                        applyStyleToPhoto(uri, selectedStyle)
                    }
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    contentResolver.delete(uri, null, null)

                    statusText.text = "ОШИБКА СЪЁМКИ"
                    toast("Ошибка фото: ${exception.message}")
                }
            }
        )
    }

    private fun applyStyleToPhoto(
        uri: Uri,
        style: LutProcessor.Style
    ) {
        cameraExecutor.execute {
            var original: Bitmap? = null
            var filtered: Bitmap? = null

            try {
                original = contentResolver
                    .openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException(
                        "Не удалось прочитать фото"
                    )

                val lut = LutProcessor.createStyle(style)
                filtered = LutProcessor.apply(original, lut)

                contentResolver.openOutputStream(uri, "wt")
                    ?.use { output ->
                        if (!filtered.compress(
                                Bitmap.CompressFormat.JPEG,
                                95,
                                output
                            )
                        ) {
                            throw IllegalStateException(
                                "Не удалось сохранить фильтр"
                            )
                        }
                    }
                    ?: throw IllegalStateException(
                        "Не удалось открыть фото"
                    )

                runOnUiThread {
                    statusText.text = "ФОТО СОХРАНЕНО"
                    toast("Фото сохранено с фильтром")
                }

            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "ФОТО СОХРАНЕНО"
                    toast("Фото сохранено, но фильтр не применён")
                }
            } finally {
                original?.recycle()
                filtered?.recycle()
            }
        }
    }

    // ---------------------------------------------------------
    // ВИДЕО
    // ---------------------------------------------------------

    private fun toggleRecording() {
        if (recordingActive) {
            recording?.stop()
            statusText.text = "ЗАВЕРШЕНИЕ ЗАПИСИ..."
            return
        }

        val video = videoCapture ?: run {
            toast("Камера ещё запускается")
            return
        }

        val values = ContentValues().apply {
            put(
                MediaStore.Video.Media.DISPLAY_NAME,
                "CineCam_${System.currentTimeMillis()}.mp4"
            )
            put(
                MediaStore.Video.Media.MIME_TYPE,
                "video/mp4"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/CineCam13"
                )
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(values)
            .build()

        var pending = video.output.prepareRecording(
            this,
            outputOptions
        )

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }

        recording = pending.start(
            ContextCompat.getMainExecutor(this)
        ) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recordingActive = true
                    updateModeUI()
                    statusText.text = "ИДЁТ ЗАПИСЬ"
                }

                is VideoRecordEvent.Finalize -> {
                    recordingActive = false
                    recording = null
                    updateModeUI()

                    if (event.hasError()) {
                        statusText.text = "ОШИБКА ВИДЕО"
                        toast("Ошибка записи видео")
                    } else {
                        statusText.text = "ВИДЕО СОХРАНЕНО"
                        toast("Видео сохранено")
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // ГАЛЕРЕЯ
    // ---------------------------------------------------------
    private fun openGallery() {
        val intent = Intent(
            Intent.ACTION_VIEW
        ).apply {
            setDataAndType(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "image/*"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("Не удалось открыть галерею")
        }
    }

    // ---------------------------------------------------------
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ---------------------------------------------------------

    private fun makeTextButton(
        text: String,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            setOnClickListener {
                action()
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        recording?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
