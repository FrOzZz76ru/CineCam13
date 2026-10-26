package com.cinecam13

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var zoomRow: LinearLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var galleryButton: TextView
    private lateinit var switchButton: TextView
    private lateinit var shutterOuter: FrameLayout
    private lateinit var shutterInner: View
    private lateinit var cameraLabel: TextView
    private lateinit var recordLabel: TextView
    private lateinit var styleRow: LinearLayout
    private lateinit var zoomValueLabel: TextView

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private var frontCamera = false
    private var zoom = 1f
    private var currentMode = "ФОТО"
    private var currentStyle = "NATURAL"
    private var recordingStartedAt = 0L

    private val backgroundColor = Color.BLACK
    private val white = Color.WHITE
    private val secondary = Color.rgb(160, 160, 165)
    private val gold = Color.rgb(225, 184, 120)
    private val selectedBackground = Color.rgb(35, 32, 27)
    private val glass = Color.argb(190, 24, 24, 27)

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else toast("Разреши доступ к камере")
        }

    private val audioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startVideoRecording(withAudio = true)
            else startVideoRecording(withAudio = false)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor

        buildInterface()

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }

    private fun circle(
        color: Int,
        strokeColor: Int? = null,
        strokeWidth: Int = 0
    ) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        if (strokeColor != null) setStroke(dp(strokeWidth), strokeColor)
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(12), dp(5), dp(12), dp(7))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(8))
        }

        header.addView(
            label("CineCam 13", 19f, white, true).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(0, dp(36), 1f)
        )

        header.addView(
            label("···", 26f, white, true).apply {
                background = rounded(glass, 22)
                setOnClickListener {
                    toast("Кинематографические профили доступны ниже")
                }
            },
            LinearLayout.LayoutParams(dp(42), dp(36))
        )
        root.addView(header)

        val previewFrame = FrameLayout(this).apply {
            background = rounded(Color.rgb(13, 13, 15), 22)
            clipToOutline = true
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.rgb(13, 13, 15))
        }

        previewFrame.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        cameraLabel = label("●  ОСНОВНАЯ", 10f, white, true).apply {
            background = rounded(Color.argb(155, 0, 0, 0), 18)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }

        previewFrame.addView(
            cameraLabel,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(10), dp(10), 0, 0)
            }
        )

        recordLabel = label("●  REC 00:00", 11f, Color.RED, true).apply {
            background = rounded(Color.argb(175, 0, 0, 0), 16)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            visibility = View.GONE
        }

        previewFrame.addView(
            recordLabel,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                setMargins(0, dp(10), dp(10), 0)
            }
        )

        // Компактная полоса зума. Свайп влево/вправо меняет увеличение.
        zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.argb(145, 0, 0, 0), 18)
            setPadding(dp(9), dp(5), dp(9), dp(5))
        }

        zoomValueLabel = label("1.0×", 11f, gold, true)
        zoomRow.addView(
            zoomValueLabel,
            LinearLayout.LayoutParams(dp(43), dp(24))
        )

        val zoomTrack = View(this).apply {
            background = rounded(Color.argb(150, 180, 180, 180), 2)
        }

        zoomRow.addView(
            zoomTrack,
            LinearLayout.LayoutParams(dp(105), dp(3)).apply {
                marginStart = dp(5)
                marginEnd = dp(3)
            }
        )

        val zoomHint = label("↔", 13f, white, true)
        zoomRow.addView(zoomHint, LinearLayout.LayoutParams(dp(20), dp(24)))

        var downX = 0f
        var startingZoom = 1f

        zoomRow.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    startingZoom = zoom
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val delta = (event.rawX - downX) / dp(100).toFloat()
                    val state = camera?.cameraInfo?.zoomState?.value
                    if (state != null) {
                        val minZoom = state.minZoomRatio
                        val maxZoom = state.maxZoomRatio
                        val next = (startingZoom * Math.pow(2.0, delta.toDouble()).toFloat())
                            .coerceIn(minZoom, maxZoom)
                        zoom = next
                        applyZoom(next)
                        updateZoomText()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }

        previewFrame.addView(
            zoomRow,
            FrameLayout.LayoutParams(-2, dp(36), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                bottomMargin = dp(10)
            }
        )

        root.addView(
            previewFrame,
            LinearLayout.LayoutParams(-1, 0, 1f).apply {
                bottomMargin = dp(9)
            }
        )

        // Кинематографические стили. На этом этапе это выбор профиля интерфейса;
        // настоящий LUT-фильтр требует отдельного GPU-обработчика.
        styleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = rounded(Color.rgb(20, 20, 22), 18)
        }

        renderStyles()

        root.addView(
            styleRow,
            LinearLayout.LayoutParams(-1, dp(35)).apply {
                bottomMargin = dp(7)
            }
        )

        modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(3), dp(3), dp(3))
            background = rounded(Color.rgb(24, 24, 26), 22)
        }

        renderModes()

        root.addView(
            modeRow,
            LinearLayout.LayoutParams(-1, dp(42)).apply {
                bottomMargin = dp(10)
            }
        )

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), dp(2))
        }

        galleryButton = label("▧", 26f, white).apply {
            background = rounded(glass, 18)
            contentDescription = "Открыть галерею"
            setOnClickListener { openGallery() }
        }

        shutterOuter = FrameLayout(this).apply {
            background = circle(Color.TRANSPARENT, white, 3)
            contentDescription = "Сделать фото или начать запись"
            isClickable = true
            isFocusable = true
        }

        shutterInner = View(this).apply {
            background = circle(white)
        }

        shutterOuter.addView(
            shutterInner,
            FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER)
        )

        shutterOuter.setOnClickListener {
            if (currentMode == "ФОТО") {
                takePhoto()
            } else if (activeRecording != null) {
                stopVideoRecording()
            } else {
                requestVideoRecording()
            }
        }

        switchButton = label("↻", 28f, white).apply {
            background = rounded(glass, 18)
            contentDescription = "Переключить камеру"
            setOnClickListener {
                if (activeRecording != null) {
                    toast("Сначала останови запись")
                    return@setOnClickListener
                }
                frontCamera = !frontCamera
                zoom = 1f
                startCamera()
                updateCameraLabel()
                updateZoomText()
            }
        }

        val sideSize = dp(52)

        controls.addView(galleryButton, LinearLayout.LayoutParams(sideSize, sideSize))
        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(shutterOuter, LinearLayout.LayoutParams(dp(74), dp(74)))
        controls.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        controls.addView(switchButton, LinearLayout.LayoutParams(sideSize, sideSize))

        root.addView(controls)
        setContentView(root)
        updateShutterAppearance()
    }

    private fun renderStyles() {
        styleRow.removeAllViews()

        val styles = listOf(
            "NATURAL",
            "CINEMA",
            "TEAL",
            "WARM",
            "COOL"
        )

        styles.forEach { style ->
            val title = when (style) {
                "NATURAL" -> "NAT"
                "CINEMA" -> "CINE"
                "TEAL" -> "T&O"
                "WARM" -> "WARM"
                else -> "COOL"
            }

            val item = label(
                title,
                9f,
                if (style == currentStyle) gold else secondary,
                style == currentStyle
            ).apply {
                background = rounded(
                    if (style == currentStyle) selectedBackground else Color.TRANSPARENT,
                    12
                )
                setOnClickListener {
                    currentStyle = style
                    renderStyles()
                    toast(
                        when (style) {
                            "NATURAL" -> "Профиль Natural выбран"
                            "CINEMA" -> "Профиль Cinema выбран"
                            "TEAL" -> "Профиль Teal & Orange выбран"
                            "WARM" -> "Профиль Warm Film выбран"
                            else -> "Профиль Cool Film выбран"
                        } + ". LUT-обработка ещё не подключена"
                    )
                }
            }

            styleRow.addView(
                item,
                LinearLayout.LayoutParams(0, -1, 1f).apply {
                    marginEnd = dp(2)
                }
            )
        }
    }

    private fun renderModes() {
        modeRow.removeAllViews()

        listOf("КИНО", "ВИДЕО", "ФОТО", "ПОРТРЕТ").forEach { mode ->
            val item = label(
                mode,
                10f,
                if (currentMode == mode) white else secondary,
                currentMode == mode
            ).apply {
                background = rounded(
                    if (currentMode == mode) selectedBackground else Color.TRANSPARENT,
                    18
                )

                setOnClickListener {
                    if (mode == "ФОТО" || mode == "ВИДЕО") {
                        if (activeRecording != null && mode != currentMode) {
                            toast("Сначала останови запись")
                            return@setOnClickListener
                        }

                        currentMode = mode
                        renderModes()
                        updateShutterAppearance()
                    } else {
                        toast("$mode пока не реализован")
                    }
                }
            }

            modeRow.addView(
                item,
                LinearLayout.LayoutParams(0, -1, 1f).apply {
                    marginEnd = dp(2)
                }
            )
        }
    }

    private fun updateShutterAppearance() {
        val recording = activeRecording != null
        shutterInner.background = if (recording) {
            rounded(Color.RED, 7)
        } else if (currentMode == "ВИДЕО") {
            rounded(Color.RED, 22)
        } else {
            circle(white)
        }

        shutterOuter.contentDescription = when {
            recording -> "Остановить видеозапись"
            currentMode == "ВИДЕО" -> "Начать видеозапись"
            else -> "Сделать фото"
        }
    }

    private fun updateCameraLabel() {
        cameraLabel.text = if (frontCamera) "●  ФРОНТАЛЬНАЯ" else "●  ОСНОВНАЯ"
    }

    private fun updateZoomText() {
        zoomValueLabel.text = String.format(Locale.US, "%.1f×", zoom)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val provider = future.get()
                val selector = if (frontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                if (!provider.hasCamera(selector)) {
                    toast("Камера недоступна")
                    return@addListener
                }

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.FHD,
                            androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(
                                Quality.SD
                            )
                        )
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

                updateCameraLabel()
                updateZoomText()

                val state = camera?.cameraInfo?.zoomState?.value
                if (state != null) {
                    zoom = zoom.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                    applyZoom(zoom)
                    updateZoomText()
                }
            } catch (e: Exception) {
                toast("Ошибка запуска камеры: ${e.message ?: "неизвестная ошибка"}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyZoom(value: Float) {
        val activeCamera = camera ?: return
        val state = activeCamera.cameraInfo.zoomState.value ?: return
        val safeValue = value.coerceIn(state.minZoomRatio, state.maxZoomRatio)
        activeCamera.cameraControl.setZoomRatio(safeValue)
    }

    private fun requestVideoRecording() {
        if (videoCapture == null) {
            toast("Видеокамера ещё запускается")
            return
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startVideoRecording(withAudio = true)
        }
    }

    private fun startVideoRecording(withAudio: Boolean) {
        val capture = videoCapture
        if (capture == null) {
            toast("Видеокамера ещё запускается")
            return
        }

        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "CineCam_${System.currentTimeMillis()}.mp4"
            )
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/CineCam13")
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        try {
            var pending: PendingRecording = capture.output.prepareRecording(
                this, outputOptions
            )

            if (withAudio) pending = pending.withAudioEnabled()

            activeRecording = pending.start(
                ContextCompat.getMainExecutor(this)
            ) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordingStartedAt = System.currentTimeMillis()
                        recordLabel.visibility = View.VISIBLE
                        recordLabel.text = "●  REC 00:00"
                        shutterInner.background = rounded(Color.RED, 7)
                        updateShutterAppearance()
                        toast("Запись началась")
                        updateRecordClock()
                    }

                    is VideoRecordEvent.Status -> {
                        val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                        val totalSeconds = durationMs / 1000
                        val minutes = totalSeconds / 60
                        val seconds = totalSeconds % 60
                        recordLabel.text = String.format(
                            Locale.US, "●  REC %02d:%02d", minutes, seconds
                        )
                    }

                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        recordLabel.visibility = View.GONE
                        updateShutterAppearance()

                        if (event.hasError()) {
                            toast("Ошибка записи: ${event.error}")
                        } else {
                            toast("Видео сохранено в Movies/CineCam13")
                        }
                    }
                }
            }

            updateShutterAppearance()
        } catch (e: Exception) {
            activeRecording = null
            updateShutterAppearance()
            toast("Не удалось начать запись: ${e.message}")
        }
    }

    private fun updateRecordClock() {
        val recording = activeRecording ?: return
        if (recordingStartedAt == 0L) return

        val elapsed = (System.currentTimeMillis() - recordingStartedAt) / 1000
        recordLabel.text = String.format(
            Locale.US, "●  REC %02d:%02d", elapsed / 60, elapsed % 60
        )

        recordLabel.postDelayed({
            if (activeRecording != null) updateRecordClock()
        }, 1000)
    }

    private fun stopVideoRecording() {
        activeRecording?.stop()
        toast("Останавливаю запись…")
    }

    private fun takePhoto() {
    val capture = imageCapture
    if (capture == null) {
        toast("Камера ещё запускается")
        return
    }

    val values = ContentValues().apply {
        put(
            MediaStore.Images.Media.DISPLAY_NAME,
            "CineCam_${System.currentTimeMillis()}.jpg"
        )
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "Pictures/CineCam13"
        )
    }

    val outputFile = java.io.File(
        cacheDir,
        "cinecam_temp_${System.currentTimeMillis()}.jpg"
    )

    val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()

    shutterOuter.animate()
        .scaleX(0.93f)
        .scaleY(0.93f)
        .setDuration(80)
        .withEndAction {
            shutterOuter.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(100)
                .start()
        }
        .start()

    capture.takePicture(
        options,
        ContextCompat.getMainExecutor(this),
        object : ImageCapture.OnImageSavedCallback {

            override fun onImageSaved(
                output: ImageCapture.OutputFileResults
            ) {
                Thread {
                    try {
                        val original = android.graphics.BitmapFactory
                            .decodeFile(outputFile.absolutePath)
                            ?: throw Exception("Не удалось прочитать снимок")

                        val style = when (currentStyle) {
                            "CINEMA" ->
                                LutProcessor.Style.CINE
                            "TEAL" ->
                                LutProcessor.Style.TEAL_ORANGE
                            "WARM" ->
                                LutProcessor.Style.WARM
                            "COOL" ->
                                LutProcessor.Style.COOL
                            else ->
                                LutProcessor.Style.NAT
                        }

                        val filtered = if (style == LutProcessor.Style.NAT) {
                            original
                        } else {
                            LutProcessor.apply(
                                original,
                                LutProcessor.createStyle(style)
                            )
                        }

                        val finalValues = ContentValues().apply {
                            put(
                                MediaStore.Images.Media.DISPLAY_NAME,
                                "CineCam_${System.currentTimeMillis()}.jpg"
                            )
                            put(
                                MediaStore.Images.Media.MIME_TYPE,
                                "image/jpeg"
                            )
                            put(
                                MediaStore.Images.Media.RELATIVE_PATH,
                                "Pictures/CineCam13"
                            )
                        }

                        val uri = contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            finalValues
                        ) ?: throw Exception("Не удалось создать файл фото")

                        try {
                            contentResolver.openOutputStream(uri)?.use { stream ->
                                if (!filtered.compress(
                                        android.graphics.Bitmap.CompressFormat.JPEG,
                                        95,
                                        stream
                                    )
                                ) {
                                    throw Exception("Не удалось сохранить фото")
                                }
                            } ?: throw Exception("Не удалось открыть файл фото")
                        } catch (e: Exception) {
                            contentResolver.delete(uri, null, null)
                            throw e
                        }

                        if (filtered !== original) filtered.recycle()
                        original.recycle()
                        outputFile.delete()

                        runOnUiThread {
                            toast("Фото с профилем $currentStyle сохранено")
                        }
                    } catch (e: Exception) {
                        outputFile.delete()
                        runOnUiThread {
                            toast("Ошибка обработки фото: ${e.message}")
                        }
                    }
                }.start()
            }

            override fun onError(exception: ImageCaptureException) {
                outputFile.delete()
                toast("Ошибка фото: ${exception.message}")
            }
        }
    )
}
    private fun openGallery() {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW
            ).apply {
                setDataAndType(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "video/*"
                )
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW
                ).apply {
                    setDataAndType(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        "image/*"
                    )
                }
                startActivity(intent)
            } catch (_: Exception) {
                toast("Не удалось открыть галерею")
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onStop() {
        if (activeRecording != null) stopVideoRecording()
        super.onStop()
    }
}
