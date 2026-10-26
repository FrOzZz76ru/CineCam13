package com.cinecam13

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
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
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var zoomRow: LinearLayout
    private lateinit var modePhoto: TextView
    private lateinit var modeVideo: TextView
    private lateinit var shutter: FrameLayout
    private lateinit var galleryButton: TextView
    private lateinit var switchButton: TextView

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var frontCamera = false
    private var zoom = 1f
    private var currentMode = "PHOTO"
    private var currentStyle = "NATURAL"
    private var cameraProvider: ProcessCameraProvider? = null

    private val bg = Color.rgb(8, 9, 12)
    private val panel = Color.argb(190, 25, 27, 33)
    private val white = Color.rgb(248, 248, 250)
    private val muted = Color.rgb(145, 148, 158)
    private val gold = Color.rgb(231, 190, 122)
    private val darkText = Color.rgb(20, 20, 23)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else toast("Разреши CineCam 13 доступ к камере")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        buildInterface()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            includeFontPadding = false
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun roundedBackground(color: Int, radius: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(14), dp(7), dp(14), dp(8))
        }

        // Верхняя панель бренда
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(7), dp(3), dp(13))
        }

        val brandColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val brand = text("CINECAM 13", 20f, white, true).apply {
            gravity = Gravity.START
            letterSpacing = 0.12f
        }

        val subtitle = text("CINEMATIC CAMERA", 9f, muted, true).apply {
            gravity = Gravity.START
            letterSpacing = 0.2f
            setPadding(0, dp(5), 0, 0)
        }

        brandColumn.addView(brand)
        brandColumn.addView(subtitle)

        val liveBadge = text("●  LIVE", 10f, gold, true).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = roundedBackground(Color.argb(35, 231, 190, 122), 18)
        }

        header.addView(
            brandColumn,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        header.addView(liveBadge)
        root.addView(header)

        // Основная область предпросмотра
        val previewFrame = FrameLayout(this).apply {
            background = roundedBackground(Color.rgb(17, 18, 22), 25)
            clipToOutline = true
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.rgb(17, 18, 22))
        }

        previewFrame.addView(
            previewView,
            FrameLayout.LayoutParams(-1, -1)
        )

        // Тонкая верхняя метка
        val previewLabel = text("CINE  ·  REAL-TIME PREVIEW", 9f, white, true).apply {
            letterSpacing = 0.08f
            setPadding(dp(11), dp(8), dp(11), dp(8))
            background = roundedBackground(Color.argb(150, 10, 10, 13), 15)
        }

        val labelParams = FrameLayout.LayoutParams(
            -2, -2, Gravity.TOP or Gravity.START
        ).apply {
            setMargins(dp(12), dp(12), 0, 0)
        }
        previewFrame.addView(previewLabel, labelParams)

        // Индикатор камеры
        val cameraBadge = text("BACK CAMERA", 9f, white, true).apply {
            letterSpacing = 0.08f
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = roundedBackground(Color.argb(145, 10, 10, 13), 14)
        }

        val cameraBadgeParams = FrameLayout.LayoutParams(
            -2, -2, Gravity.BOTTOM or Gravity.START
        ).apply {
            setMargins(dp(12), 0, 0, dp(12))
        }
        previewFrame.addView(cameraBadge, cameraBadgeParams)

        // Обновляем надпись при переключении камеры
        previewFrame.contentDescription = "camera_preview"
        root.addView(
            previewFrame,
            LinearLayout.LayoutParams(-1, 0, 1f).apply {
                bottomMargin = dp(13)
            }
        )

        // Зум
        zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBackground(panel, 25)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }
        root.addView(
            zoomRow,
            LinearLayout.LayoutParams(-1, dp(48)).apply {
                bottomMargin = dp(14)
            }
        )
        renderZoomButtons()

        // Стили изображения — пока визуальные пресеты интерфейса
        val styleTitle = text("CINEMATIC LOOK", 10f, muted, true).apply {
            gravity = Gravity.START
            letterSpacing = 0.16f
            setPadding(dp(4), 0, 0, dp(9))
        }
        root.addView(styleTitle)

        val styles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        listOf("NATURAL", "CINEMA", "WARM", "COOL").forEach { style ->
            val item = text(
                style,
                9f,
                if (style == currentStyle) darkText else muted,
                style == currentStyle
            ).apply {
                background = roundedBackground(
                    if (style == currentStyle) gold else panel,
                    17
                )
                setPadding(dp(9), dp(10), dp(9), dp(10))
                setOnClickListener {
                    currentStyle = style
                    for (i in 0 until styles.childCount) {
                        val child = styles.getChildAt(i) as TextView
                        val selected = child.text.toString() == currentStyle
                        child.setTextColor(if (selected) darkText else muted)
                        child.setTypeface(
                            child.typeface,
                            if (selected) Typeface.BOLD else Typeface.NORMAL
                        )
                        child.background = roundedBackground(
                            if (selected) gold else panel,
                            17
                        )
                    }
                    toast("$style — оформление выбрано")
                }
            }

            styles.addView(
                item,
                LinearLayout.LayoutParams(0, dp(37), 1f).apply {
                    marginEnd = dp(5)
                }
            )
        }

        root.addView(
            styles,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(14)
            }
        )

        // Переключатель фото/видео
        val modeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBackground(panel, 24)
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }

        modePhoto = text("PHOTO", 11f, darkText, true)
        modeVideo = text("VIDEO", 11f, muted, true)

        modePhoto.background = roundedBackground(gold, 20)
        modeVideo.background = roundedBackground(Color.TRANSPARENT, 20)

        modeContainer.addView(
            modePhoto,
            LinearLayout.LayoutParams(dp(108), dp(39))
        )
        modeContainer.addView(
            modeVideo,
            LinearLayout.LayoutParams(dp(108), dp(39))
        )

        modePhoto.setOnClickListener { setMode("PHOTO") }
        modeVideo.setOnClickListener {
            setMode("VIDEO")
            toast("Видеозапись добавим следующим этапом")
        }

        root.addView(
            modeContainer,
            LinearLayout.LayoutParams(-2, dp(49)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(13)
            }
        )

        // Нижняя панель управления
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(2), dp(8), dp(3))
        }

        galleryButton = text("▧", 29f, white).apply {
            background = roundedBackground(panel, 19)
            setOnClickListener { openGallery() }
        }

        val shutterOuter = FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), white)
            }
            contentDescription = "Take photo"
        }

        val shutterInner = View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(white)
            }
        }

        shutterOuter.addView(
            shutterInner,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER)
        )
        shutter = shutterOuter
        shutter.setOnClickListener {
            if (currentMode == "PHOTO") takePhoto()
            else toast("Видеозапись пока не реализована")
        }

        switchButton = text("↻", 30f, white).apply {
            background = roundedBackground(panel, 19)
            setOnClickListener {
                frontCamera = !frontCamera
                zoom = 1f
                startCamera()
                toast(if (frontCamera) "Фронтальная камера" else "Основная камера")
            }
        }

        val sideSize = dp(52)
        controls.addView(
            galleryButton,
            LinearLayout.LayoutParams(sideSize, sideSize)
        )

        controls.addView(
            View(this),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        controls.addView(
            shutter,
            LinearLayout.LayoutParams(dp(68), dp(68))
        )

        controls.addView(
            View(this),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        controls.addView(
            switchButton,
            LinearLayout.LayoutParams(sideSize, sideSize)
        )

        root.addView(controls)
        setContentView(root)
    }

    private fun renderZoomButtons() {
        zoomRow.removeAllViews()

        val presets = if (frontCamera) {
            listOf(1f, 1.5f, 2f, 3f)
        } else {
            listOf(0.6f, 1f, 1.5f, 2f, 3f)
        }

        presets.forEach { value ->
            val selected = kotlin.math.abs(zoom - value) < 0.01f

            val button = text(
                if (value == value.toInt().toFloat()) "${value.toInt()}×"
                else "${value}×",
                12f,
                if (selected) darkText else white,
                selected
            ).apply {
                background = roundedBackground(
                    if (selected) gold else Color.TRANSPARENT,
                    20
                )
                setOnClickListener {
                    zoom = value
                    applyZoom(value)
                    renderZoomButtons()
                }
            }

            zoomRow.addView(
                button,
                LinearLayout.LayoutParams(0, -1, 1f).apply {
                    marginEnd = dp(2)
                }
            )
        }
    }

    private fun setMode(mode: String) {
        currentMode = mode

        modePhoto.setTextColor(if (mode == "PHOTO") darkText else muted)
        modePhoto.background = roundedBackground(
            if (mode == "PHOTO") gold else Color.TRANSPARENT,
            20
        )

        modeVideo.setTextColor(if (mode == "VIDEO") darkText else muted)
        modeVideo.background = roundedBackground(
            if (mode == "VIDEO") gold else Color.TRANSPARENT,
            20
        )
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                val provider = future.get()
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
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                provider.unbindAll()

                camera = provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageCapture
                )

                // Сохраняем выбранный зум после запуска камеры.
                applyZoom(zoom)
                renderZoomButtons()

            } catch (e: Exception) {
                toast("Ошибка запуска камеры: ${e.message ?: "неизвестная ошибка"}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyZoom(value: Float) {
        val activeCamera = camera ?: return
        val zoomState = activeCamera.cameraInfo.zoomState.value ?: return

        val minZoom = zoomState.minZoomRatio
        val maxZoom = zoomState.maxZoomRatio

        // Не подменяем 0.6× значением 1×: если CameraX не поддерживает
        // 0.6× на выбранной камере, сообщаем об этом явно.
        if (value < minZoom || value > maxZoom) {
            if (!frontCamera && value == 0.6f) {
                toast("0.6× недоступен в текущем режиме камеры")
            } else {
                toast("Этот уровень зума недоступен")
            }
            return
        }

        activeCamera.cameraControl.setZoomRatio(value)
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
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CineCam13")
        }

        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    toast("Фото сохранено в Pictures/CineCam13")
                }

                override fun onError(exception: ImageCaptureException) {
                    toast("Ошибка фото: ${exception.message ?: "неизвестная ошибка"}")
                }
            }
        )
    }

    private fun openGallery() {
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ).apply {
                type = "image/*"
            }
            startActivity(intent)
        } catch (_: Exception) {
            toast("Не удалось открыть галерею")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
