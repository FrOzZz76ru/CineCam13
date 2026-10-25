package com.cinecam13

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var modePhoto: TextView
    private lateinit var modeVideo: TextView
    private lateinit var styleLabel: TextView
    private lateinit var flashButton: TextView
    private lateinit var zoomRow: LinearLayout
    private lateinit var shutter: TextView

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var frontCamera = false
    private var flashEnabled = false
    private var videoMode = false
    private var selectedStyle = "Natural"
    private var currentZoom = 1f

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startCamera()
            else Toast.makeText(
                this,
                "Разреши доступ к камере",
                Toast.LENGTH_LONG
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

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

    private fun label(
        text: String,
        size: Float = 14f,
        color: Int = Color.WHITE
    ) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
    }

    private fun control(
        text: String,
        width: Int = 52,
        onClick: () -> Unit
    ): TextView = label(text, 14f).apply {
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(38, 38, 40))
            cornerRadius = dp(22).toFloat()
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(width), dp(42)).apply {
            setMargins(dp(4), dp(3), dp(4), dp(3))
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = true
        }

        // Compact top bar
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }

        flashButton = label("ϟ", 22f)
        flashButton.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(32, 32, 34))
            cornerRadius = dp(22).toFloat()
        }
        flashButton.setOnClickListener {
            flashEnabled = !flashEnabled
            flashButton.text = if (flashEnabled) "ϟ" else "ϟ̸"
            imageCapture?.flashMode =
                if (flashEnabled) ImageCapture.FLASH_MODE_ON
                else ImageCapture.FLASH_MODE_OFF
        }
        top.addView(
            flashButton,
            LinearLayout.LayoutParams(dp(44), dp(40))
        )

        val title = label("CINECAM", 15f).apply {
            letterSpacing = 0.2f
        }
        top.addView(
            title,
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )

        val settings = control("⚙", 44) {
            Toast.makeText(
                this,
                "Дополнительные настройки добавим позже",
                Toast.LENGTH_SHORT
            ).show()
        }
        top.addView(settings)
        root.addView(top)

        // Camera preview fills remaining space
        val previewFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(12, 12, 12))
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }

        previewFrame.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val zoomOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(12), dp(8), dp(8))
        }

        val zoomScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        zoomRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        listOf(1f, 2f, 3f).forEach { zoom ->
            val button = control("${zoom.toInt()}×", 52) {
                setZoom(zoom)
            }
            zoomRow.addView(button)
        }

        zoomScroll.addView(zoomRow)
        zoomOverlay.addView(zoomScroll)

        val spacer = View(this)
        zoomOverlay.addView(
            spacer,
            LinearLayout.LayoutParams(1, dp(8), 1f)
        )

        val overlayBottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
        }

        styleLabel = label("NATURAL", 12f).apply {
            gravity = Gravity.START
            setPadding(dp(8), dp(6), dp(8), dp(8))
            letterSpacing = 0.12f
        }
        overlayBottom.addView(styleLabel)

        zoomOverlay.addView(overlayBottom)

        previewFrame.addView(
            zoomOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            previewFrame,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        // Scrollable style strip
        val styleScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(Color.rgb(12, 12, 12))
        }

        val styleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(5))
        }

        listOf("Natural", "Cinema", "Warm", "Cool", "Mono").forEach { style ->
            val chip = label(style, 12f).apply {
                setPadding(dp(14), 0, dp(14), 0)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(
                        if (style == selectedStyle) Color.rgb(75, 75, 78)
                        else Color.rgb(32, 32, 34)
                    )
                    cornerRadius = dp(18).toFloat()
                }
                setOnClickListener {
                    selectedStyle = style
                    styleLabel.text = style.uppercase(Locale.ROOT)

                    for (i in 0 until styleRow.childCount) {
                        val child = styleRow.getChildAt(i)
                        if (child is TextView) {
                            child.background =
                                android.graphics.drawable.GradientDrawable().apply {
                                    setColor(
                                        if (child.text == style) {
                                            Color.rgb(75, 75, 78)
                                        } else {
                                            Color.rgb(32, 32, 34)
                                        }
                                    )
                                    cornerRadius = dp(18).toFloat()
                                }
                        }
                    }

                    Toast.makeText(
                        this,
                        "$style выбран. Реальную цветокоррекцию подключим отдельно.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            styleRow.addView(
                chip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(36)
                ).apply {
                    setMargins(dp(4), 0, dp(4), 0)
                }
            )
        }

        styleScroll.addView(styleRow)
        root.addView(
            styleScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
            )
        )

        // Photo/video selector
        val modeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(2))
        }

        modePhoto = label("ФОТО", 13f)
        modeVideo = label("ВИДЕО", 13f)

        fun updateModeAppearance() {
            modePhoto.alpha = if (!videoMode) 1f else 0.55f
            modeVideo.alpha = if (videoMode) 1f else 0.55f
        }

        modePhoto.setOnClickListener {
            videoMode = false
            updateModeAppearance()
        }

        modeVideo.setOnClickListener {
            videoMode = true
            updateModeAppearance()
            Toast.makeText(
                this,
                "Видеозапись подключим следующим этапом",
                Toast.LENGTH_SHORT
            ).show()
        }

        modeBar.addView(
            modePhoto,
            LinearLayout.LayoutParams(dp(90), dp(32))
        )
        modeBar.addView(
            modeVideo,
            LinearLayout.LayoutParams(dp(90), dp(32))
        )
        root.addView(modeBar)

        // Bottom camera controls
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(3), dp(18), dp(8))
        }

        val gallery = label("▧", 25f).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(32, 32, 34))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener {
                try {
                    startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW
                        ).apply {
                            setDataAndType(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                "image/*"
                            )
                        }
                    )
                } catch (_: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Не удалось открыть галерею",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        bottom.addView(
            gallery,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        val shutterArea = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        shutter = label("●", 44f, Color.BLACK).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                setShape(android.graphics.drawable.GradientDrawable.OVAL)
                setStroke(dp(4), Color.rgb(180, 180, 180))
            }
            setOnClickListener {
                if (videoMode) {
                    Toast.makeText(
                        this@MainActivity,
                        "Видеозапись появится в следующем обновлении",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    takePhoto()
                }
            }
        }

        shutterArea.addView(
            shutter,
            LinearLayout.LayoutParams(dp(68), dp(68))
        )

        bottom.addView(
            shutterArea,
            LinearLayout.LayoutParams(0, dp(72), 1f)
        )

        val switch = label("⇄", 28f).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.rgb(32, 32, 34))
                cornerRadius = dp(24).toFloat()
            }
            setOnClickListener {
                frontCamera = !frontCamera
                startCamera()
            }
        }

        bottom.addView(
            switch,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        root.addView(bottom)

        setContentView(root)
    }

    private fun setZoom(zoom: Float) {
        val state = camera?.cameraInfo?.zoomState?.value
        if (state == null) {
            Toast.makeText(
                this,
                "Камера ещё запускается",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        currentZoom = zoom.coerceIn(
            state.minZoomRatio,
            state.maxZoomRatio
        )
        camera?.cameraControl?.setZoomRatio(currentZoom)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            val provider = future.get()

            val selector = if (frontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(
                    if (flashEnabled) ImageCapture.FLASH_MODE_ON
                    else ImageCapture.FLASH_MODE_OFF
                )
                .build()

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageCapture
                )

                currentZoom = 1f
                camera?.cameraControl?.setZoomRatio(1f)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Не удалось запустить камеру: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

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

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(
                    output: ImageCapture.OutputFileResults
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "Фото сохранено",
                        Toast.LENGTH_SHORT
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
}    photoModeButton.setOnClickListener {
            videoMode = false
            modeLabel.text = "PHOTO  •  $selectedStyle"
            Toast.makeText(
                this,
                "Режим фото",
                Toast.LENGTH_SHORT
            ).show()
        }

        videoModeButton.setOnClickListener {
            videoMode = true
            modeLabel.text = "VIDEO  •  $selectedStyle"
            Toast.makeText(
                this,
                "Видеозапись подключим следующим этапом",
                Toast.LENGTH_SHORT
            ).show()
        }

        modeRow.addView(
            photoModeButton,
            LinearLayout.LayoutParams(0, 44, 1f)
        )
        modeRow.addView(
            videoModeButton,
            LinearLayout.LayoutParams(0, 44, 1f)
        )

        root.addView(modeRow)

        val bottomBar = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(12, 4, 12, 12)
        }

        val galleryButton = makeButton("Галерея")
        galleryButton.setOnClickListener {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW
            ).apply {
                setDataAndType(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "image/*"
                )
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Не удалось открыть галерею",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        captureButton = makeButton("●")
        captureButton.textSize = 30f
        captureButton.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(Color.WHITE)
        )
        captureButton.setTextColor(Color.BLACK)
        captureButton.setOnClickListener {
            if (videoMode) {
                Toast.makeText(
                    this,
                    "Запись видео подключим следующим этапом",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                takePhoto()
            }
        }

        val switchButton = makeButton("⇄")
        switchButton.textSize = 24f
        switchButton.setOnClickListener {
            frontCamera = !frontCamera
            startCamera()
        }

        bottomBar.addView(
            galleryButton,
            LinearLayout.LayoutParams(0, 64, 1f)
        )
        bottomBar.addView(
            captureButton,
            LinearLayout.LayoutParams(0, 72, 1.2f)
        )
        bottomBar.addView(
            switchButton,
            LinearLayout.LayoutParams(0, 64, 1f)
        )

        root.addView(bottomBar)
        setContentView(root)
    }

    private fun startCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val selector = if (frontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(
                    if (flashEnabled) ImageCapture.FLASH_MODE_ON
                    else ImageCapture.FLASH_MODE_OFF
                )
                .build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageCapture
                )

                zoomBar.progress = 0
                zoomLabel.text = "1.0×"

            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Ошибка запуска камеры: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

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
}
