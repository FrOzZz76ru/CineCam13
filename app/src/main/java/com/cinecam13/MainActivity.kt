package com.cinecam13

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraStarting = false

    private val photoExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val backgroundColor = Color.BLACK
    private val white = Color.WHITE
    private val secondary = Color.rgb(160, 160, 165)
    private val gold = Color.rgb(225, 184, 120)
    private val selectedBackground = Color.rgb(35, 32, 27)
    private val glass = Color.argb(190, 24, 24, 27)

    private val cameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startCamera()
            } else {
                toast("Разреши доступ к камере")
            }
        }

    private val audioPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            startVideoRecording(withAudio = granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        buildInterface()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun circle(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private fun label(
        text: String,
        size: Float,
        color: Int = white
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(16), dp(8), dp(16), dp(10))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = label("CineCam", 25f, white).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
        }

        val version = label("13", 22f, gold).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), 0, 0, 0)
        }

        header.addView(
            title,
            LinearLayout.LayoutParams(-2, dp(52))
        )
        header.addView(
            version,
            LinearLayout.LayoutParams(-2, dp(52))
        )

        root.addView(
            header,
            LinearLayout.LayoutParams(-1, dp(52))
        )

        val previewFrame = FrameLayout(this).apply {
            background = rounded(Color.rgb(15, 15, 17), 20)
            clipToOutline = true
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        previewFrame.addView(
            previewView,
            FrameLayout.LayoutParams(-1, -1)
        )

        cameraLabel = label("КАМЕРА", 11f, white).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(glass, 20)
        }

        previewFrame.addView(
            cameraLabel,
            FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(dp(12), dp(12), 0, 0)
            }
        )

        recordLabel = label("● REC 00:00", 14f, Color.RED).apply {
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = rounded(glass, 18)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            visibility = View.GONE
        }

        previewFrame.addView(
            recordLabel,
            FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(0, dp(12), dp(12), 0)
            }
        )

        root.addView(
            previewFrame,
            LinearLayout.LayoutParams(-1, 0, 1f).apply {
                topMargin = dp(8)
                bottomMargin = dp(12)
            }
        )

        val zoomHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        zoomHeader.addView(
            label("ЗУМ", 11f, secondary),
            LinearLayout.LayoutParams(0, dp(28), 1f)
        )

        zoomValueLabel = label("1.0×", 13f, gold)
        zoomHeader.addView(
            zoomValueLabel,
            LinearLayout.LayoutParams(-2, dp(28))
        )

        root.addView(
            zoomHeader,
            LinearLayout.LayoutParams(-1, dp(28))
        )

        zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(22, 22, 24), 14)
            setPadding(dp(12), 0, dp(12), 0)
        }

        val zoomHint = label("Проведи пальцем ← →", 12f, secondary)
        zoomRow.addView(
            zoomHint,
            LinearLayout.LayoutParams(-1, dp(42))
        )

        var startX = 0f
        var startZoom = 1f

        zoomRow.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startZoom = zoom
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val difference = event.x - startX
                    val factor = difference / dp(180).toFloat()
                    applyZoom(startZoom + factor * 3f)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    zoomRow.performClick()
                    true
                }

                else -> true
            }
        }

        root.addView(
            zoomRow,
            LinearLayout.LayoutParams(-1, dp(42))
        )

        root.addView(
            label("ЦВЕТОВОЙ ПРОФИЛЬ", 10f, secondary).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            },
            LinearLayout.LayoutParams(-1, dp(32)).apply {
                topMargin = dp(6)
            }
        )

        styleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        root.addView(
            styleRow,
            LinearLayout.LayoutParams(-1, dp(42))
        )

        renderStyles()

        modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }

        root.addView(
            modeRow,
            LinearLayout.LayoutParams(-1, dp(48)).apply {
                topMargin = dp(4)
            }
        )

        renderModes()

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(2))
        }

        galleryButton = label("▧", 28f, white).apply {
            background = rounded(Color.rgb(30, 30, 32), 18)
            isClickable = true
            setOnClickListener { openGallery() }
        }

        controls.addView(
            galleryButton,
            LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                rightMargin = dp(24)
            }
        )

        shutterOuter = FrameLayout(this).apply {
            background = circle(white)
            isClickable = true
            isFocusable = true
        }

        shutterInner = View(this).apply {
            background = circle(Color.BLACK)
        }

        shutterOuter.addView(
            shutterInner,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER)
        )

        shutterOuter.setOnClickListener {
            when {
                currentMode == "ФОТО" -> takePhoto()

                activeRecording != null -> stopVideoRecording()

                else -> requestVideoRecording()
            }
        }

        controls.addView(
            shutterOuter,
            LinearLayout.LayoutParams(dp(68), dp(68))
        )

        switchButton = label("↻", 32f, white).apply {
            background = rounded(Color.rgb(30, 30, 32), 18)
            isClickable = true
            setOnClickListener {
                if (activeRecording != null) {
                    toast("Останови запись перед сменой камеры")
                    return@setOnClickListener
                }

                frontCamera = !frontCamera
                zoom = 1f
                updateZoomText()
                startCamera()
            }
        }

        controls.addView(
            switchButton,
            LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                leftMargin = dp(24)
            }
        )

        root.addView(
            controls,
            LinearLayout.LayoutParams(-1, dp(78))
        )

        setContentView(root)

        updateCameraLabel()
        updateShutterAppearance()
    }

    private fun renderStyles() {
        styleRow.removeAllViews()

        val styles = listOf(
            "NATURAL" to "NAT",
            "CINEMA" to "CINE",
            "TEAL" to "T&O",
            "WARM" to "WARM",
            "COOL" to "COOL"
        )

        styles.forEach { (style, title) ->
            val item = label(
                title,
                10f,
                if (currentStyle == style) gold else secondary
            ).apply {
                background = rounded(
                    if (currentStyle == style) {
                        selectedBackground
                    } else {
                        Color.TRANSPARENT
                    },
                    10
                )

                isClickable = true

                setOnClickListener {
                    currentStyle = style
                    renderStyles()
                    toast("Профиль $currentStyle выбран")
                }
            }

            styleRow.addView(
                item,
                LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                }
            )
        }
    }

    private fun renderModes() {
        modeRow.removeAllViews()

        val modes = listOf("КИНО", "ВИДЕО", "ФОТО", "ПОРТРЕТ")

        modes.forEach { mode ->
            val item = label(
                mode,
                11f,
                if (currentMode == mode) gold else secondary
            ).apply {
                background = rounded(
                    if (currentMode == mode) selectedBackground
                    else Color.TRANSPARENT,
                    12
                )

                isClickable = true

                setOnClickListener {
                    when (mode) {
                        "ФОТО", "ВИДЕО" -> {
                            if (activeRecording != null && mode != currentMode) {
                                toast("Сначала останови запись")
                                return@setOnClickListener
                            }

                            currentMode = mode
                            renderModes()
                            updateShutterAppearance()
                        }

                        else -> {
                            toast("Режим $mode пока не реализован")
                        }
                    }
                }
            }

            modeRow.addView(
                item,
                LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                }
            )
        }
    }

    private fun updateShutterAppearance() {
        if (!::shutterOuter.isInitialized) return

        if (activeRecording != null) {
            shutterOuter.background = circle(Color.RED)
            shutterInner.background = rounded(Color.WHITE, 6)
            val params = shutterInner.layoutParams
            params.width = dp(23)
            params.height = dp(23)
            shutterInner.layoutParams = params
        } else if (currentMode == "ВИДЕО") {
            shutterOuter.background = circle(Color.RED)
            shutterInner.background = circle(Color.RED)
            val params = shutterInner.layoutParams
            params.width = dp(48)
            params.height = dp(48)
            shutterInner.layoutParams = params
        } else {
            shutterOuter.background = circle(Color.WHITE)
            shutterInner.background = circle(Color.BLACK)
            val params = shutterInner.layoutParams
            params.width = dp(48)
            params.height = dp(48)
            shutterInner.layoutParams = params
        }
    }

    private fun updateCameraLabel() {
        if (!::cameraLabel.isInitialized) return

        cameraLabel.text = if (frontCamera) {
            "FRONT CAMERA"
        } else {
            "BACK CAMERA"
        }
    }

    private fun updateZoomText() {
        if (::zoomValueLabel.isInitialized) {
            zoomValueLabel.text = String.format(
                Locale.US,
                "%.1f×",
                zoom
            )
        }
    }

    private fun startCamera() {
        if (cameraStarting) return

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermission.launch(Manifest.permission.CAMERA)
            return
        }

        cameraStarting = true

        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

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

                val capture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .setTargetRotation(previewView.display.rotation)
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.FHD,
                            FallbackStrategy.lowerQualityOrHigherThan(
                                Quality.SD
                            )
                        )
                    )
                    .build()

                val video = VideoCapture.withOutput(recorder)

                provider.unbindAll()

                camera = provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    capture,
                    video
                )

                imageCapture = capture
                videoCapture = video

                val minZoom = camera!!.cameraInfo.zoomState.value
                    ?.minZoomRatio ?: 1f

                val maxZoom = camera!!.cameraInfo.zoomState.value
                    ?.maxZoomRatio ?: 10f

                zoom = zoom.coerceIn(minZoom, maxZoom)

                camera!!.cameraControl.setZoomRatio(zoom)

                updateZoomText()
                updateCameraLabel()

            } catch (e: Exception) {
                toast("Ошибка запуска камеры: ${e.message}")
            } finally {
                cameraStarting = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyZoom(value: Float) {
        val state = camera?.cameraInfo?.zoomState?.value

        val minZoom = state?.minZoomRatio ?: 1f
        val maxZoom = state?.maxZoomRatio ?: 10f

        zoom = value.coerceIn(minZoom, maxZoom)

        camera?.cameraControl?.setZoomRatio(zoom)

        updateZoomText()
    }

    private fun takePhoto() {
        val capture = imageCapture

        if (capture == null) {
            toast("Камера ещё запускается")
            return
        }

        val outputFile = File(
            cacheDir,
            "cinecam_temp_${System.currentTimeMillis()}.jpg"
        )

        val options = ImageCapture.OutputFileOptions.Builder(
            outputFile
        ).build()

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
            photoExecutor,
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    output: ImageCapture.OutputFileResults
                ) {
                    val selectedStyle = currentStyle

                    try {
                        var original: Bitmap? = null
                        var oriented: Bitmap? = null
                        var filtered: Bitmap? = null

                        try {
                            original = BitmapFactory.decodeFile(
                                outputFile.absolutePath
                            ) ?: throw Exception(
                                "Не удалось прочитать снимок"
                            )

                            val exif = ExifInterface(
                                outputFile.absolutePath
                            )

                            val orientation = exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )

                            val matrix = Matrix()

                            when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 ->
                                    matrix.postRotate(90f)

                                ExifInterface.ORIENTATION_ROTATE_180 ->
                                    matrix.postRotate(180f)

                                ExifInterface.ORIENTATION_ROTATE_270 ->
                                    matrix.postRotate(270f)

                                ExifInterface.ORIENTATION_FLIP_HORIZONTAL ->
                                    matrix.preScale(-1f, 1f)

                                ExifInterface.ORIENTATION_FLIP_VERTICAL ->
                                    matrix.preScale(1f, -1f)

                                ExifInterface.ORIENTATION_TRANSPOSE -> {
                                    matrix.postRotate(90f)
                                    matrix.postScale(-1f, 1f)
                                }

                                ExifInterface.ORIENTATION_TRANSVERSE -> {
                                    matrix.postRotate(270f)
                                    matrix.postScale(-1f, 1f)
                                }
                            }

                            oriented = if (matrix.isIdentity) {
                                original
                            } else {
                                Bitmap.createBitmap(
                                    original,
                                    0,
                                    0,
                                    original.width,
                                    original.height,
                                    matrix,
                                    true
                                )
                            }

                            val lutStyle = when (selectedStyle) {
                                "CINEMA" -> LutProcessor.Style.CINE
                                "TEAL" -> LutProcessor.Style.TEAL_ORANGE
                                "WARM" -> LutProcessor.Style.WARM
                                "COOL" -> LutProcessor.Style.COOL
                                else -> LutProcessor.Style.NAT
                            }

                            filtered = if (
                                lutStyle == LutProcessor.Style.NAT
                            ) {
                                oriented
                            } else {
                                LutProcessor.apply(
                                    oriented!!,
                                    LutProcessor.createStyle(lutStyle)
                                )
                            }

                            val values = ContentValues().apply {
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
                                values
                            ) ?: throw Exception(
                                "Не удалось создать файл фото"
                            )

                            try {
                                val stream = contentResolver
                                    .openOutputStream(uri)
                                    ?: throw Exception(
                                        "Не удалось открыть файл фото"
                                    )

                                stream.use {
                                    if (
                                        !filtered!!.compress(
                                            Bitmap.CompressFormat.JPEG,
                                            95,
                                            it
                                        )
                                    ) {
                                        throw Exception(
                                            "Не удалось сохранить фото"
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                contentResolver.delete(uri, null, null)
                                throw e
                            }

                            runOnUiThread {
                                toast(
                                    "Фото с профилем $selectedStyle сохранено"
                                )
                            }

                        } finally {
                            if (
                                filtered != null &&
                                filtered !== oriented &&
                                filtered !== original
                            ) {
                                filtered?.recycle()
                            }

                            if (
                                oriented != null &&
                                oriented !== original
                            ) {
                                oriented?.recycle()
                            }

                            original?.recycle()
                        }

                    } catch (e: Exception) {
                        runOnUiThread {
                            toast("Ошибка обработки фото: ${e.message}")
                        }
                    } finally {
                        outputFile.delete()
                    }
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    outputFile.delete()

                    runOnUiThread {
                        toast("Ошибка фото: ${exception.message}")
                    }
                }
            }
        )
    }

    private fun requestVideoRecording() {
        if (videoCapture == null) {
            toast("Видеокамера ещё запускается")
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
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

        if (activeRecording != null) return

        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "CineCam_${System.currentTimeMillis()}.mp4"
            )

            put(
                MediaStore.MediaColumns.MIME_TYPE,
                "video/mp4"
            )

            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "Movies/CineCam13"
            )
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(values)
            .build()

        try {
            var pending = capture.output.prepareRecording(
                this,
                outputOptions
            )

            if (withAudio) {
                pending = pending.withAudioEnabled()
            }

            activeRecording = pending.start(
                ContextCompat.getMainExecutor(this)
            ) { event ->

                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordingStartedAt = System.currentTimeMillis()

                        recordLabel.visibility = View.VISIBLE
                        recordLabel.text = "● REC 00:00"

                        updateShutterAppearance()
                        toast("Запись началась")
                        updateRecordClock()
                    }

                    is VideoRecordEvent.Status -> {
                        val durationMs =
                            event.recordingStats.recordedDurationNanos /
                                1_000_000

                        val totalSeconds = durationMs / 1000
                        val minutes = totalSeconds / 60
                        val seconds = totalSeconds % 60

                        recordLabel.text = String.format(
                            Locale.US,
                            "● REC %02d:%02d",
                            minutes,
                            seconds
                        )
                    }

                    is VideoRecordEvent.Finalize -> {
                        activeRecording = null
                        recordLabel.visibility = View.GONE

                        updateShutterAppearance()

                        if (event.hasError()) {
                            toast("Ошибка записи: ${event.error}")
                        } else {
                            toast(
                                "Видео сохранено в Movies/CineCam13"
                            )
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
        if (
            activeRecording == null ||
            recordingStartedAt == 0L ||
            isFinishing
        ) {
            return
        }

        val elapsed =
            (System.currentTimeMillis() - recordingStartedAt) / 1000

        recordLabel.text = String.format(
            Locale.US,
            "● REC %02d:%02d",
            elapsed / 60,
            elapsed % 60
        )

        recordLabel.postDelayed({
            updateRecordClock()
        }, 1000)
    }

    private fun stopVideoRecording() {
        val recording = activeRecording

        if (recording != null) {
            recording.stop()
            toast("Останавливаю запись…")
        }
    }

    private fun openGallery() {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ).apply {
                type = "image/*"
            }

            startActivity(
                Intent.createChooser(
                    intent,
                    "Открыть галерею"
                )
            )

        } catch (e: Exception) {
            try {
                val fallback = Intent(
                    Intent.ACTION_GET_CONTENT
                ).apply {
                    type = "image/*"
                }

                startActivity(
                    Intent.createChooser(
                        fallback,
                        "Выбрать фотографию"
                    )
                )
            } catch (_: Exception) {
                toast("Не удалось открыть галерею")
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onStop() {
        if (activeRecording != null) {
            stopVideoRecording()
        }

        super.onStop()
    }

    override fun onDestroy() {
        photoExecutor.shutdown()
        super.onDestroy()
    }
}
