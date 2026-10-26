package com.cinecam13

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
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
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView

    private lateinit var topInfo: TextView
    private lateinit var flashButton: TextView
    private lateinit var settingsButton: TextView
    private lateinit var shutterButton: FrameLayout
    private lateinit var shutterInner: View
    private lateinit var modePhoto: TextView
    private lateinit var modeVideo: TextView
    private lateinit var cameraButton: TextView
    private lateinit var galleryButton: ImageView
    private lateinit var filterButton: TextView
    private lateinit var filterPanel: LinearLayout
    private lateinit var focusRing: View
    private lateinit var recordingIndicator: TextView

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var frontCamera = false
    private var videoMode = false
    private var filterPanelVisible = false
    private var recordingActive = false
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var selectedStyle = LutProcessor.Style.NAT
    private var videoQuality = Quality.FHD

    private var focusHideRunnable: Runnable? = null

    private val cameraExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val permissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val cameraGranted =
                results[Manifest.permission.CAMERA] == true

            if (cameraGranted) {
                startCamera()
            } else {
                toast("Для работы CineCam13 нужен доступ к камере")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )

        buildInterface()
        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionsLauncher.launch(permissions.toTypedArray())
        }
    }

    // ---------------------------------------------------------
    // INTERFACE
    // ---------------------------------------------------------

    private fun buildInterface() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Tap to focus.
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                focusAt(event.x, event.y)
                true
            } else {
                true
            }
        }

        buildFocusRing()
        buildTopControls()
        buildBottomControls()
        buildFilterPanel()

        setContentView(root)
    }

    private fun buildTopControls() {
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }

        val title = TextView(this).apply {
            text = "CINECAM"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(
                "sans-serif-medium",
                Typeface.BOLD
            )
            letterSpacing = 0.18f
        }

        topInfo = TextView(this).apply {
            text = "13 PRO"
            textSize = 10f
            setTextColor(Color.WHITE)
            alpha = 0.65f
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
        }

        val titleGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title)
            addView(
                topInfo,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(2)
                }
            )
        }

        val spacer = View(this)

        flashButton = makePill("⚡ OFF") {
            cycleFlash()
        }

        settingsButton = makeCircleButton("⚙", 46) {
            showSettings()
        }

        top.addView(
            titleGroup,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        top.addView(
            spacer,
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        // Flash remains accessible but is not a tiny icon.
        top.addView(
            flashButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44)
            ).apply {
                marginEnd = dp(8)
            }
        )

        top.addView(
            settingsButton,
            LinearLayout.LayoutParams(dp(46), dp(46))
        )

        root.addView(
            top,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(76),
                Gravity.TOP
            )
        )
    }

    private fun buildBottomControls() {
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(12), dp(18), dp(18))
            background = roundedBackground(
                Color.argb(175, 8, 9, 12),
                30
            )
        }

        // Filter control.
        val filterRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }

        filterButton = makePill("✧  ${selectedStyle.displayName()}") {
            toggleFilterPanel()
        }

        filterRow.addView(
            filterButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44)
            )
        )

        bottom.addView(
            filterRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        // Photo / video selector.
        val modeRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            background = roundedBackground(
                Color.argb(100, 255, 255, 255),
                24
            )
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        modePhoto = makeModeButton("ФОТО")
        modeVideo = makeModeButton("ВИДЕО")

        modePhoto.setOnClickListener { setVideoMode(false) }
        modeVideo.setOnClickListener { setVideoMode(true) }

        modeRow.addView(
            modePhoto,
            LinearLayout.LayoutParams(dp(110), dp(42))
        )

        modeRow.addView(
            modeVideo,
            LinearLayout.LayoutParams(dp(110), dp(42))
        )

        bottom.addView(
            modeRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(50)
            ).apply {
                topMargin = dp(8)
            }
        )

        // Main camera controls.
        val controls = FrameLayout(this)

        galleryButton = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_gallery)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = roundedBackground(
                Color.argb(90, 255, 255, 255),
                50
            )
            contentDescription = "Открыть галерею"
            setOnClickListener { openGallery() }
        }

        shutterButton = FrameLayout(this).apply {
            background = roundedBackground(
                Color.TRANSPARENT,
                100,
                Color.WHITE,
                4
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onShutterPressed() }
        }

        shutterInner = View(this).apply {
            background = roundedBackground(
                Color.WHITE,
                100
            )
        }

        shutterButton.addView(
            shutterInner,
            FrameLayout.LayoutParams(
                dp(68),
                dp(68),
                Gravity.CENTER
            )
        )

        cameraButton = makeCircleButton("↻", 58) {
            switchCamera()
        }

        controls.addView(
            galleryButton,
            FrameLayout.LayoutParams(
                dp(56),
                dp(56),
                Gravity.CENTER_VERTICAL or Gravity.START
            )
        )

        controls.addView(
            shutterButton,
            FrameLayout.LayoutParams(
                dp(88),
                dp(88),
                Gravity.CENTER
            )
        )

        controls.addView(
            cameraButton,
            FrameLayout.LayoutParams(
                dp(58),
                dp(58),
                Gravity.CENTER_VERTICAL or Gravity.END
            )
        )

        bottom.addView(
            controls,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(100)
            ).apply {
                topMargin = dp(8)
            }
        )

        recordingIndicator = TextView(this).apply {
            text = "●  ЗАПИСЬ"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.RED)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        bottom.addView(
            recordingIndicator,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(22)
            )
        )

        root.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(10)
                rightMargin = dp(10)
                bottomMargin = dp(8)
            }
        )

        updateModeButtons()
    }

    private fun buildFocusRing() {
        focusRing = View(this).apply {
            background = roundedBackground(
                Color.TRANSPARENT,
                6,
                Color.rgb(255, 211, 65),
                2
            )
            visibility = View.GONE
            elevation = dp(20).toFloat()
        }

        root.addView(
            focusRing,
            FrameLayout.LayoutParams(dp(68), dp(68))
        )
    }

    // ---------------------------------------------------------
    // FILTER PANEL
    // ---------------------------------------------------------
    private fun buildFilterPanel() {
        filterPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = roundedBackground(
                Color.argb(238, 20, 21, 25),
                26
            )
            visibility = View.GONE
            elevation = dp(18).toFloat()
        }

        val heading = TextView(this).apply {
            text = "СТИЛЬ ИЗОБРАЖЕНИЯ"
            textSize = 12f
            letterSpacing = 0.12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        filterPanel.addView(
            heading,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(34)
            )
        )

        val styles = LutProcessor.Style.values()

        styles.forEach { style ->
            val item = TextView(this).apply {
                text = if (style == selectedStyle) {
                    "✓   ${style.displayName()}"
                } else {
                    style.displayName()
                }

                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)

                background = roundedBackground(
                    if (style == selectedStyle) {
                        Color.argb(80, 255, 255, 255)
                    } else {
                        Color.TRANSPARENT
                    },
                    14
                )

                setOnClickListener {
                    selectedStyle = style
                    updateFilterButton()
                    refreshFilterPanel()
                }
            }

            filterPanel.addView(
                item,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(44)
                ).apply {
                    topMargin = dp(3)
                }
            )
        }

        val close = makePill("ГОТОВО") {
            toggleFilterPanel()
        }

        filterPanel.addView(
            close,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                topMargin = dp(10)
            }
        )

        root.addView(
            filterPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(20)
                rightMargin = dp(20)
                bottomMargin = dp(300)
            }
        )
    }

    private fun refreshFilterPanel() {
        filterPanel.removeAllViews()

        val heading = TextView(this).apply {
            text = "ВЫБЕРИ СТИЛЬ"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        filterPanel.addView(
            heading,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(36)
            )
        )

        LutProcessor.Style.values().forEach { style ->
            val item = TextView(this).apply {
                text = (if (style == selectedStyle) "✓   " else "     ") +
                    style.displayName()

                textSize = 15f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                background = roundedBackground(
                    if (style == selectedStyle) {
                        Color.argb(80, 255, 255, 255)
                    } else {
                        Color.TRANSPARENT
                    },
                    14
                )

                setOnClickListener {
                    selectedStyle = style
                    updateFilterButton()
                    refreshFilterPanel()
                }
            }

            filterPanel.addView(
                item,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(42)
                ).apply {
                    topMargin = dp(2)
                }
            )
        }

        val close = makePill("ГОТОВО") {
            toggleFilterPanel()
        }

        filterPanel.addView(
            close,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                topMargin = dp(8)
            }
        )
    }

    private fun toggleFilterPanel() {
        filterPanelVisible = !filterPanelVisible

        filterPanel.visibility =
            if (filterPanelVisible) View.VISIBLE else View.GONE

        if (filterPanelVisible) {
            refreshFilterPanel()
        }
    }

    private fun updateFilterButton() {
        filterButton.text = "✧  ${selectedStyle.displayName()}"
    }

    private fun LutProcessor.Style.displayName(): String {
        return when (this) {
            LutProcessor.Style.NAT -> "Natural"
            LutProcessor.Style.KODACHROME_64 -> "Kodachrome 64"
            LutProcessor.Style.PORTRA_400 -> "Portra 400"
            LutProcessor.Style.PORTRA_800 -> "Portra 800"
            LutProcessor.Style.FUJI_FILM -> "Fuji Film"
            LutProcessor.Style.FUJI_PORTRAIT -> "Fuji Portrait"
            LutProcessor.Style.CINE -> "Cinematic"
            LutProcessor.Style.TEAL_ORANGE -> "Teal & Orange"
            LutProcessor.Style.MOODY_AQUA -> "Moody Aqua"
            LutProcessor.Style.WARM -> "Warm"
            LutProcessor.Style.COOL -> "Cool"
        }
    }

    // ---------------------------------------------------------
    // CAMERA
    // ---------------------------------------------------------
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                bindCamera(provider)
            } catch (e: Exception) {
                toast("Не удалось запустить камеру: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        val selector = if (frontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            provider.unbindAll()

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
                    QualitySelector.from(videoQuality)
                )
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            camera = provider.bindToLifecycle(
                this,
                selector,
                preview,
                imageCapture,
                videoCapture
            )

            updateFlashButton()
            updateZoomInfo()
        } catch (e: Exception) {
            toast("Ошибка запуска камеры: ${e.message}")
        }
    }

    private fun switchCamera() {
        if (recordingActive) {
            toast("Сначала останови запись")
            return
        }

        frontCamera = !frontCamera
        flashMode = ImageCapture.FLASH_MODE_OFF

        startCamera()
    }

    private fun cycleFlash() {
        if (frontCamera) {
            toast("Вспышка доступна на основной камере")
            return
        }

        flashMode = when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
            ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
            else -> ImageCapture.FLASH_MODE_OFF
        }

        imageCapture?.flashMode = flashMode
        updateFlashButton()
    }

    private fun updateFlashButton() {
        flashButton.text = when {
            frontCamera -> "⚡ —"
            flashMode == ImageCapture.FLASH_MODE_OFF -> "⚡ OFF"
            flashMode == ImageCapture.FLASH_MODE_AUTO -> "⚡ AUTO"
            else -> "⚡ ON"
        }
    }

    private fun updateZoomInfo() {
        val zoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
        topInfo.text = String.format(
            Locale.US,
            "13 PRO  ·  %.1fx",
            zoom
        )
    }

    // ---------------------------------------------------------
    // FOCUS
    // ---------------------------------------------------------

    private fun focusAt(x: Float, y: Float) {
        val activeCamera = camera ?: return

        val factory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(),
            previewView.height.toFloat()
        )

        val point = factory.createPoint(x, y)

        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or
                FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        activeCamera.cameraControl.startFocusAndMetering(action)

        focusRing.x = x - dp(34)
        focusRing.y = y - dp(34)
        focusRing.visibility = View.VISIBLE
        focusRing.alpha = 1f

        focusHideRunnable?.let { root.removeCallbacks(it) }

        val hide = Runnable {
            focusRing.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction {
                    focusRing.visibility = View.GONE
                    focusRing.alpha = 1f
                }
                .start()
        }

        focusHideRunnable = hide
        root.postDelayed(hide, 1600)
    }

    // ---------------------------------------------------------
    // PHOTO
    // ---------------------------------------------------------

    private fun onShutterPressed() {
        if (recordingActive) {
            stopRecording()
            return
        }

        if (videoMode) {
            startRecording()
        } else {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: run {
            toast("Камера ещё запускается")
            return
        }

        shutterButton.isEnabled = false

        val filename = "CineCam_${timestamp()}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/CineCam13"
                )
            }
        }

        val output = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {
                    shutterButton.isEnabled = true

                    val uri = result.savedUri

                    if (uri == null) {
                        toast("Фото сохранено, но URI недоступен")
                        refreshGalleryThumbnail()
                        return
                    }

                    refreshGalleryThumbnail()

                    if (selectedStyle == LutProcessor.Style.NAT) {
                        toast("Фото сохранено")
                    } else {
                        applyFilterToPhoto(uri, selectedStyle)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    shutterButton.isEnabled = true
                    toast("Ошибка фото: ${exception.message}")
                }
            }
        )
    }

    private fun applyFilterToPhoto(
        uri: Uri,
        style: LutProcessor.Style
    ) {
        toast("Применяю стиль: ${style.displayName()}")

        cameraExecutor.execute {
            try {
                val source = contentResolver.openInputStream(uri)
                    ?.use { BitmapFactory.decodeStream(it) }
                    ?: throw IllegalStateException("Не удалось прочитать фото")

                val lut = LutProcessor.createStyle(style)
                val filtered = LutProcessor.apply(source, lut)

                source.recycle()

                contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    if (!filtered.compress(
                            android.graphics.Bitmap.CompressFormat.JPEG,
                            95,
                            stream
                        )
                    ) {
                        throw IllegalStateException("Не удалось записать фото")
                    }
                } ?: throw IllegalStateException("Не удалось открыть файл")

                filtered.recycle()

                runOnUiThread {
                    toast("Фото сохранено с фильтром")
                    refreshGalleryThumbnail()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    toast(
                        "Фото сохранено, но фильтр не применён: " +
                            (e.message ?: "ошибка")
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // VIDEO
    // ---------------------------------------------------------
    
private fun startRecording() {
        val capture = videoCapture ?: run {
            toast("Камера ещё запускается")
            return
        }

        val filename = "CineCam_${timestamp()}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/CineCam13"
                )
            }
        }

        val output = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(values)
            .build()

        var pending: PendingRecording =
            capture.output.prepareRecording(this, output)

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
                    recordingIndicator.visibility = View.VISIBLE
                    shutterInner.background = roundedBackground(
                        Color.RED,
                        12
                    )
                    toast("Запись началась")
                }

                is VideoRecordEvent.Finalize -> {
                    recordingActive = false
                    recording = null

                    recordingIndicator.visibility = View.GONE

                    shutterInner.background = roundedBackground(
                        Color.WHITE,
                        100
                    )

                    if (event.hasError()) {
                        toast("Ошибка записи видео")
                    } else {
                        toast("Видео сохранено")
                    }

                    refreshGalleryThumbnail()
                }
            }
        }
    }

    private fun stopRecording() {
        recording?.stop()
    }

    private fun setVideoMode(enabled: Boolean) {
        if (recordingActive) return

        videoMode = enabled
        updateModeButtons()

        shutterInner.background = roundedBackground(
            Color.WHITE,
            if (videoMode) 12 else 100
        )
    }

    private fun updateModeButtons() {
        modePhoto.background = roundedBackground(
            if (!videoMode) Color.WHITE else Color.TRANSPARENT,
            22
        )

        modePhoto.setTextColor(
            if (!videoMode) Color.BLACK else Color.WHITE
        )

        modeVideo.background = roundedBackground(
            if (videoMode) Color.WHITE else Color.TRANSPARENT,
            22
        )

        modeVideo.setTextColor(
            if (videoMode) Color.BLACK else Color.WHITE
        )
    }

    // ---------------------------------------------------------
    // SETTINGS
    // ---------------------------------------------------------

    private fun showSettings() {
        val options = arrayOf(
            "Качество видео: ${videoQualityLabel()}",
            "Вспышка: ${flashLabel()}",
            "Увеличить масштаб",
            "Уменьшить масштаб",
            "Сбросить масштаб",
            "Переключить камеру",
            "Выбрать стиль",
            "Закрыть"
        )

        AlertDialog.Builder(this)
            .setTitle("CineCam13")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> chooseVideoQuality()
                    1 -> cycleFlash()
                    2 -> changeZoom(0.5f)
                    3 -> changeZoom(-0.5f)
                    4 -> resetZoom()
                    5 -> switchCamera()
                    6 -> {
                        if (!filterPanelVisible) {
                            toggleFilterPanel()
                        }
                    }
                    else -> Unit
                }
            }
            .show()
    }

    private fun chooseVideoQuality() {
        val labels = arrayOf(
            "Full HD (1080p)",
            "HD (720p)",
            "SD (480p)"
        )

        AlertDialog.Builder(this)
            .setTitle("Качество видео")
            .setItems(labels) { _, which ->
                videoQuality = when (which) {
                    0 -> Quality.FHD
                    1 -> Quality.HD
                    else -> Quality.SD
                }

                if (cameraProvider != null) {
                    startCamera()
                }

                toast("Качество: ${labels[which]}")
            }
            .show()
    }

    private fun videoQualityLabel(): String {
        return when (videoQuality) {
            Quality.FHD -> "1080p"
            Quality.HD -> "720p"
            Quality.SD -> "480p"
            Quality.UHD -> "4K"
            else -> "Авто"
        }
    }

    private fun flashLabel(): String {
        return when (flashMode) {
            ImageCapture.FLASH_MODE_OFF -> "Выкл."
            ImageCapture.FLASH_MODE_AUTO -> "Авто"
            else -> "Вкл."
        }
    }

    private fun changeZoom(delta: Float) {
        val activeCamera = camera ?: return

        val state = activeCamera.cameraInfo.zoomState.value ?: return

        val newZoom = (state.zoomRatio + delta)
            .coerceIn(state.minZoomRatio, state.maxZoomRatio)

        activeCamera.cameraControl.setZoomRatio(newZoom)
        updateZoomInfo()
    }

    private fun resetZoom() {
        camera?.cameraControl?.setZoomRatio(1f)
        updateZoomInfo()
    }

    // ---------------------------------------------------------
    // GALLERY
    // ---------------------------------------------------------
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            type = "image/*"
            action = Intent.ACTION_VIEW
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            toast("Не удалось открыть галерею")
        }
    }

    private fun refreshGalleryThumbnail() {
        // Здесь пока используется универсальная иконка галереи.
        // Позже можно заменить её последним сохранённым снимком.
        galleryButton.invalidate()
    }

    // ---------------------------------------------------------
    // UI HELPERS
    // ---------------------------------------------------------

    private fun makePill(
        label: String,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(16), 0, dp(16), 0)

            background = roundedBackground(
                Color.argb(100, 255, 255, 255),
                30
            )

            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    }

    private fun makeCircleButton(
        label: String,
        size: Int,
        action: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)

            background = roundedBackground(
                Color.argb(100, 255, 255, 255),
                100
            )

            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { action() }
        }
    }

    private fun makeModeButton(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
        strokeWidthDp: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()

            if (strokeColor != null) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun timestamp(): String {
        return SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())
    }

    private fun toast(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        recording?.stop()
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
