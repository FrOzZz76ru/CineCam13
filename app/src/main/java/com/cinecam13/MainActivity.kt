package com.cinecam13

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
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

    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    private var frontCamera = false
    private var zoom = 1f
    private var minSupportedZoom = 1f
    private var maxSupportedZoom = 1f

    private var currentMode = "PHOTO"
    private var currentStyle = "NATURAL"

    private val zoomButtons = mutableListOf<TextView>()

    private val bg = Color.rgb(9, 9, 11)
    private val white = Color.WHITE
    private val muted = Color.rgb(145, 145, 155)
    private val gold = Color.rgb(222, 183, 116)

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Разреши доступ к камере",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        buildInterface()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun text(
        value: String,
        size: Float,
        color: Int
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
    }

    // ───── ZOOM BUTTONS ─────

    private fun updateZoomButtons() {
        zoomButtons.forEach { button ->
            val factor = button.tag as Float
            val selected = kotlin.math.abs(factor - zoom) < 0.01f

            button.setTextColor(
                if (selected) gold else white
            )

            button.setBackgroundColor(
                if (selected) {
                    Color.rgb(40, 35, 27)
                } else {
                    Color.TRANSPARENT
                }
            )
        }
    }

    private fun rebuildZoomButtons() {
        if (!::zoomRow.isInitialized) return

        zoomRow.removeAllViews()
        zoomButtons.clear()

        val presets = if (frontCamera) {
            listOf(0.8f, 1f, 1.5f, 2f, 3f)
        } else {
            listOf(0.6f, 1f, 1.5f, 2f, 3f)
        }

        val availablePresets = presets.filter {
            it >= minSupportedZoom - 0.001f &&
                it <= maxSupportedZoom + 0.001f
        }

        // 1× показываем как основной вариант,
        // если камера сообщает обычный диапазон зума.
        if (availablePresets.isEmpty()) {
            val fallback = zoom.coerceIn(
                minSupportedZoom,
                maxSupportedZoom
            )

            addZoomButton(fallback)
        } else {
            availablePresets.forEach { factor ->
                addZoomButton(factor)
            }
        }

        updateZoomButtons()
    }

    private fun addZoomButton(factor: Float) {
        val button = text(
            "${factor}×",
            13f,
            if (kotlin.math.abs(factor - zoom) < 0.01f) {
                gold
            } else {
                white
            }
        ).apply {
            tag = factor

            setOnClickListener {
                val state = camera?.cameraInfo?.zoomState?.value

                val minZoom = state?.minZoomRatio
                    ?: minSupportedZoom

                val maxZoom = state?.maxZoomRatio
                    ?: maxSupportedZoom

                val actualZoom = factor.coerceIn(
                    minZoom,
                    maxZoom
                )

                zoom = actualZoom

                camera?.cameraControl?.setZoomRatio(actualZoom)

                updateZoomButtons()
            }
        }

        zoomButtons.add(button)

        zoomRow.addView(
            button,
            LinearLayout.LayoutParams(
                0,
                dp(38),
                1f
            ).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
        )
    }

    // ───── INTERFACE ─────

    private fun buildInterface() {

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }

        // ───── HEADER ─────

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = text("CINECAM 13", 18f, white).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            letterSpacing = 0.12f
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        val cinematic = text("CINEMATIC", 11f, gold).apply {
            letterSpacing = 0.12f
        }

        header.addView(
            cinematic,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(42)
            )
        )

        root.addView(header)

        // ───── CAMERA PREVIEW ─────

        val previewFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(24, 24, 27))
        }

        previewView = PreviewView(this).apply {
            implementationMode =
                PreviewView.ImplementationMode.COMPATIBLE

            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        previewFrame.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val live = text("● LIVE", 11f, white).apply {
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackgroundColor(Color.argb(150, 0, 0, 0))
        }

        previewFrame.addView(
            live,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                setMargins(dp(10), dp(10), 0, 0)
            }
        )

        root.addView(
            previewFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        )

        // ───── ZOOM ─────

        zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        root.addView(zoomRow)
        rebuildZoomButtons()

        // ───── CINEMATIC LOOK ─────

        val lookTitle = text(
            "CINEMATIC LOOK",
            10f,
            muted
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            letterSpacing = 0.16f
        }

        root.addView(
            lookTitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(26)
            )
        )

        val styles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val styleButtons = mutableListOf<TextView>()

        fun updateStyles() {
            styleButtons.forEach { button ->
                val selected =
                    button.text.toString() == currentStyle

                button.setTextColor(
                    if (selected) gold else muted
                )
            }
        }

        listOf(
            "NATURAL",
            "CINEMA",
            "WARM",
            "COOL"
        ).forEach { style ->

            val button = text(
                style,
                10f,
                if (style == currentStyle) gold else muted
            ).apply {
                letterSpacing = 0.02f

                setOnClickListener {
                    currentStyle = style
                    updateStyles()

                    Toast.makeText(
                        this@MainActivity,
                        "$style выбран",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            styleButtons.add(button)

            styles.addView(
                button,
                LinearLayout.LayoutParams(0, dp(34), 1f)
            )
        }

        root.addView(styles)
        updateStyles()

        // ───── SHOOTING MODE ─────

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val videoButton = text("VIDEO", 13f, muted)
        val photoButton = text("PHOTO", 13f, gold)

        fun updateModes() {
            videoButton.setTextColor(
                if (currentMode == "VIDEO") gold else muted
            )

            photoButton.setTextColor(
                if (currentMode == "PHOTO") gold else muted
            )
        }

        videoButton.setOnClickListener {
            currentMode = "VIDEO"
            updateModes()

            Toast.makeText(
                this,
                "Видеозапись добавим следующим этапом",
                Toast.LENGTH_SHORT
            ).show()
        }

        photoButton.setOnClickListener {
            currentMode = "PHOTO"
            updateModes()
        }

        modeRow.addView(
            videoButton,
            LinearLayout.LayoutParams(dp(82), dp(36))
        )

        modeRow.addView(
            photoButton,
            LinearLayout.LayoutParams(dp(82), dp(36))
        )

        root.addView(modeRow)

        // ───── BOTTOM CONTROLS ─────

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val gallery = text("▧", 27f, white)

        gallery.setOnClickListener {
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
                Toast.makeText(
                    this,
                    "Не удалось открыть галерею",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        bottom.addView(
            gallery,
            LinearLayout.LayoutParams(0, dp(66), 1f)
        )

        val shutter = FrameLayout(this).apply {
            foregroundGravity = Gravity.CENTER
        }

        val outerCircle = View(this).apply {
            background =
                android.graphics.drawable.GradientDrawable().apply {
                    shape =
                        android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(3), white)
                }
        }

        val innerCircle = View(this).apply {
            background =
                android.graphics.drawable.GradientDrawable().apply {
                    shape =
                        android.graphics.drawable.GradientDrawable.OVAL
                    setColor(white)
                }
        }

        shutter.addView(
            outerCircle,
            FrameLayout.LayoutParams(
                dp(58),
                dp(58),
                Gravity.CENTER
            )
        )

        shutter.addView(
            innerCircle,
            FrameLayout.LayoutParams(
                dp(46),
                dp(46),
                Gravity.CENTER
            )
        )

        shutter.setOnClickListener {
            if (currentMode == "PHOTO") {
                takePhoto()
            } else {
                Toast.makeText(
                    this,
                    "Запись видео пока не подключена",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        bottom.addView(
            shutter,
            LinearLayout.LayoutParams(0, dp(66), 1.2f)
        )

        val switchCamera = text("↻", 31f, white)

        switchCamera.setOnClickListener {
            frontCamera = !frontCamera
            startCamera()
        }

        bottom.addView(
            switchCamera,
            LinearLayout.LayoutParams(0, dp(66), 1f)
        )

        root.addView(bottom)

        setContentView(root)
    }

    // ───── CAMERA START ─────

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
                    Toast.makeText(
                        this,
                        "Эта камера недоступна",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addListener
                }

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    )
                    .build()

                provider.unbindAll()

                camera = provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageCapture
                )

                val state = camera?.cameraInfo?.zoomState?.value

                minSupportedZoom = state?.minZoomRatio ?: 1f
                maxSupportedZoom = state?.maxZoomRatio ?: 1f

                zoom = zoom.coerceIn(
                    minSupportedZoom,
                    maxSupportedZoom
                )

                camera?.cameraControl?.setZoomRatio(zoom)

                rebuildZoomButtons()

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Ошибка запуска камеры: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ───── TAKE PHOTO ─────

    private fun takePhoto() {
        val capture = imageCapture ?: run {
            Toast.makeText(
                this,
                "Камера ещё запускается",
                Toast.LENGTH_SHORT
            ).show()
            return
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

        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        ).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    output: ImageCapture.OutputFileResults
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "Фото сохранено в Pictures/CineCam13",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "Ошибка фото: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density + 0.5f
        ).toInt()
    }
}
