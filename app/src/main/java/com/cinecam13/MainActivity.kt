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
    private lateinit var modeRow: LinearLayout
    private lateinit var galleryButton: TextView
    private lateinit var switchButton: TextView
    private lateinit var shutterOuter: FrameLayout
    private lateinit var cameraLabel: TextView

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var frontCamera = false
    private var zoom = 1f
    private var currentMode = "ФОТО"

    private val backgroundColor = Color.rgb(0, 0, 0)
    private val white = Color.rgb(255, 255, 255)
    private val secondary = Color.rgb(165, 165, 170)
    private val selectedBackground = Color.rgb(35, 32, 27)
    private val glass = Color.argb(175, 25, 25, 28)
    private val gold = Color.rgb(225, 184, 120)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else toast("Разреши доступ к камере")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
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
        if (strokeColor != null) {
            setStroke(dp(strokeWidth), strokeColor)
        }
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

        // Верхняя панель
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(6), dp(5), dp(10))
        }

        val brand = label("CineCam 13", 19f, white, true).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            letterSpacing = -0.02f
        }

        val settings = label("···", 27f, white, true).apply {
            background = rounded(glass, 22)
            setOnClickListener {
                toast("Дополнительные настройки добавим позже")
            }
        }

        header.addView(brand, LinearLayout.LayoutParams(0, dp(38), 1f))
        header.addView(settings, LinearLayout.LayoutParams(dp(42), dp(38)))
        root.addView(header)

        // Предпросмотр камеры
        val previewFrame = FrameLayout(this).apply {
            background = rounded(Color.rgb(13, 13, 15), 22)
            clipToOutline = true
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.rgb(13, 13, 15))
        }

        previewFrame.addView(
            previewView,
            FrameLayout.LayoutParams(-1, -1)
        )

        cameraLabel = label("●  ОСНОВНАЯ", 10f, white, true).apply {
            background = rounded(Color.argb(155, 0, 0, 0), 18)
            setPadding(dp(11), dp(8), dp(11), dp(8))
        }

        previewFrame.addView(
            cameraLabel,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(12), dp(12), 0, 0)
            }
        )

        // Компактный зум поверх изображения
        zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.TRANSPARENT, 20)
            setPadding(0, 0, 0, 0)
        }

        renderZoomButtons()

        previewFrame.addView(
            zoomRow,
            FrameLayout.LayoutParams(
                -1,
                dp(40),
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply {
                setMargins(0, 0, 0, dp(10))
            }
        )

        root.addView(
            previewFrame,
            LinearLayout.LayoutParams(-1, 0, 1f).apply {
                bottomMargin = dp(13)
            }
        )

        // Режимы съёмки
        modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = rounded(Color.rgb(24, 24, 26), 24)
        }

        renderModes()

        root.addView(
            modeRow,
            LinearLayout.LayoutParams(-1, dp(46)).apply {
                bottomMargin = dp(16)
            }
        )

        // Нижняя панель управления
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), dp(3))
        }

        galleryButton = label("▧", 27f, white).apply {
            background = rounded(glass, 19)
            contentDescription = "Открыть галерею"
            setOnClickListener { openGallery() }
        }

        shutterOuter = FrameLayout(this).apply {
            background = circle(Color.TRANSPARENT, white, 3)
            contentDescription = "Сделать фото"
            isClickable = true
            isFocusable = true
        }

        val shutterInner = View(this).apply {
            background = circle(white)
        }

        shutterOuter.addView(
            shutterInner,
            FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER)
        )

        shutterOuter.setOnClickListener {
            if (currentMode == "ФОТО") {
                takePhoto()
            } else {
                toast("Видеозапись пока не реализована")
            }
        }

        switchButton = label("↻", 29f, white).apply {
            background = rounded(glass, 19)
            contentDescription = "Переключить камеру"
            setOnClickListener {
                frontCamera = !frontCamera
                zoom = 1f
                startCamera()
                updateCameraLabel()
                renderZoomButtons()
            }
        }

        val sideSize = dp(53)

        controls.addView(
            galleryButton,
            LinearLayout.LayoutParams(sideSize, sideSize)
        )

        controls.addView(
            View(this),
            LinearLayout.LayoutParams(0, 1, 1f)
        )

        controls.addView(
            shutterOuter,
            LinearLayout.LayoutParams(dp(76), dp(76))
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

    // Компактные круглые кнопки зума с золотистым ободком
    private fun renderZoomButtons() {
        zoomRow.removeAllViews()

        val presets = if (frontCamera) {
            listOf(1f, 1.2f, 1.5f, 2f, 2.5f, 3f)
        } else {
            listOf(0.6f, 0.7f, 0.8f, 1f, 1.2f, 1.5f, 2f, 2.5f, 3f)
        }

        presets.forEach { value ->
            val selected = kotlin.math.abs(zoom - value) < 0.01f

            val title = when (value) {
                0.6f -> "0.6"
                0.7f -> "0.7"
                0.8f -> "0.8"
                1f -> "1×"
                1.2f -> "1.2"
                1.5f -> "1.5"
                2f -> "2"
                2.5f -> "2.5"
                3f -> "3"
                else -> value.toString()
            }

            val button = label(
                title,
                if (selected) 11f else 10f,
                if (selected) gold else white,
                selected
            ).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(
                        if (selected) selectedBackground
                        else Color.argb(70, 30, 30, 33)
                    )

                    if (selected) {
                        setStroke(dp(1), gold)
                    }
                }

                elevation = dp(if (selected) 2 else 0).toFloat()

                setOnClickListener {
                    val state = camera?.cameraInfo?.zoomState?.value

                    if (state == null) {
                        toast("Камера ещё запускается")
                        return@setOnClickListener
                    }

                    if (value < state.minZoomRatio ||
                        value > state.maxZoomRatio
                    ) {
                        if (!frontCamera && value == 0.6f) {
                            toast("Для 0.6× нужен механизм ультраширокой камеры")
                        } else {
                            toast("Зум ${title} недоступен на этой камере")
                        }
                        return@setOnClickListener
                    }

                    zoom = value
                    applyZoom(value)
                    renderZoomButtons()
                }
            }

            zoomRow.addView(
                button,
                LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                }
            )
        }

        zoomRow.background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
        }
        zoomRow.setPadding(0, 0, 0, 0)
        zoomRow.gravity = Gravity.CENTER
    }

    private fun renderModes() {
        modeRow.removeAllViews()

        val modes = listOf("КИНО", "ВИДЕО", "ФОТО", "ПОРТРЕТ")

        modes.forEach { mode ->
            val selected = currentMode == mode

            val button = label(
                mode,
                10f,
                if (selected) white else secondary,
                selected
            ).apply {
                background = rounded(
                    if (selected) selectedBackground else Color.TRANSPARENT,
                    20
                )

                setOnClickListener {
                    if (mode == "ФОТО" || mode == "ВИДЕО") {
                        currentMode = mode
                        renderModes()

                        if (mode == "ВИДЕО") {
                            toast("Видеозапись добавим следующим этапом")
                        }
                    } else {
                        toast("$mode пока не реализован")
                    }
                }
            }

            modeRow.addView(
                button,
                LinearLayout.LayoutParams(0, -1, 1f).apply {
                    marginEnd = dp(2)
                }
            )
        }
    }

    private fun updateCameraLabel() {
        cameraLabel.text = if (frontCamera) {
            "●  ФРОНТАЛЬНАЯ"
        } else {
            "●  ОСНОВНАЯ"
        }
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
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

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

                updateCameraLabel()
                renderZoomButtons()

                val state = camera?.cameraInfo?.zoomState?.value
                if (state != null &&
                    zoom >= state.minZoomRatio &&
                    zoom <= state.maxZoomRatio
                ) {
                    applyZoom(zoom)
                } else {
                    zoom = 1f
                    applyZoom(zoom)
                    renderZoomButtons()
                }

            } catch (e: Exception) {
                toast("Ошибка запуска камеры: ${e.message ?: "неизвестная ошибка"}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyZoom(value: Float) {
        val activeCamera = camera ?: return
        val state = activeCamera.cameraInfo.zoomState.value ?: return

        if (value < state.minZoomRatio || value > state.maxZoomRatio) {
            toast("Этот уровень зума недоступен")
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
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/CineCam13"
            )
        }

        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        shutterOuter.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
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
                    toast("Снимок сохранён в Pictures/CineCam13")
                }

                override fun onError(exception: ImageCaptureException) {
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
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "image/*"
                )
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
