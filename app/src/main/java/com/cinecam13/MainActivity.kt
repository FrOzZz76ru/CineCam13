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
import android.widget.LinearLayout
import android.widget.SeekBar
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
    private lateinit var modeLabel: TextView
    private lateinit var zoomLabel: TextView
    private lateinit var flashButton: Button
    private lateinit var captureButton: Button
    private lateinit var recordButton: Button
    private lateinit var zoomBar: SeekBar

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var frontCamera = false
    private var flashEnabled = false
    private var videoMode = false
    private var selectedStyle = "Natural"

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.CAMERA] == true) {
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

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0

        buildInterface()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA)
            )
        }
    }

    private fun makeButton(textValue: String): Button {
        return Button(this).apply {
            text = textValue
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                    Color.rgb(45, 45, 45)
                )
            )
        }
    }

    private fun makeLabel(textValue: String, size: Float): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 8)
        }

        flashButton = makeButton("⚡ Off")
        flashButton.setOnClickListener {
            flashEnabled = !flashEnabled
            flashButton.text = if (flashEnabled) "⚡ On" else "⚡ Off"
            imageCapture?.flashMode =
                if (flashEnabled) ImageCapture.FLASH_MODE_ON
                else ImageCapture.FLASH_MODE_OFF
        }

        val title = makeLabel("CINECAM", 18f).apply {
            letterSpacing = 0.15f
        }

        val settingsButton = makeButton("⚙")
        settingsButton.setOnClickListener {
            Toast.makeText(
                this,
                "Расширенные настройки добавим следующим этапом",
                Toast.LENGTH_SHORT
            ).show()
        }

        topBar.addView(
            flashButton,
            LinearLayout.LayoutParams(0, 48, 1f)
        )
        topBar.addView(
            title,
            LinearLayout.LayoutParams(0, 48, 2f)
        )
        topBar.addView(
            settingsButton,
            LinearLayout.LayoutParams(0, 48, 1f)
        )
        root.addView(topBar)

        val cameraArea = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(15, 15, 15))
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }

        cameraArea.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
        }

        zoomLabel = makeLabel("1.0×", 14f).apply {
            gravity = Gravity.END
        }

        overlay.addView(
            zoomLabel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                32
            )
        )

        val spacer = View(this)
        overlay.addView(
            spacer,
            LinearLayout.LayoutParams(1, 0, 1f)
        )

        modeLabel = makeLabel("PHOTO  •  $selectedStyle", 13f).apply {
            gravity = Gravity.START
            setPadding(0, 0, 0, 12)
        }

        overlay.addView(modeLabel)

        cameraArea.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        root.addView(
            cameraArea,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val stylePanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 4)
            setBackgroundColor(Color.rgb(10, 10, 10))
        }

        stylePanel.addView(
            makeLabel("CINECAM STYLES", 11f),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                24
            )
        )

        val styleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }

        val styles = listOf("Natural", "Cinema", "Warm", "Cool", "Mono")

        styles.forEach { style ->
            val button = makeButton(style)
            button.textSize = 11f

            if (style == selectedStyle) {
                button.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                        Color.rgb(85, 85, 85)
                    )
                )
            }

            button.setOnClickListener {
                selectedStyle = style
                modeLabel.text =
                    "${if (videoMode) "VIDEO" else "PHOTO"}  •  $style"

                Toast.makeText(
                    this,
                    "$style выбран. Обработку изображения добавим следующим этапом.",
                    Toast.LENGTH_SHORT
                ).show()

                for (i in 0 until styleRow.childCount) {
                    val child = styleRow.getChildAt(i) as Button
                    child.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                            if (child.text == style) {
                                Color.rgb(85, 85, 85)
                            } else {
                                Color.rgb(45, 45, 45)
                            }
                        )
                    )
                }
            }

            styleRow.addView(
                button,
                LinearLayout.LayoutParams(0, 44, 1f)
            )
        }

        stylePanel.addView(styleRow)
        root.addView(stylePanel)

        zoomBar = SeekBar(this).apply {
            max = 100
            progress = 0
            setPadding(24, 0, 24, 0)
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            val zoom = 1f + progress / 25f
                            camera?.cameraControl?.setZoomRatio(zoom)
                            zoomLabel.text = String.format(
                                java.util.Locale.US,
                                "%.1f×",
                                zoom
                            )
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }

        root.addView(
            zoomBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                36
            )
        )

        val modeRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(16, 0, 16, 0)
        }

        val photoModeButton = makeButton("ФОТО")
        val videoModeButton = makeButton("ВИДЕО")

        photoModeButton.setOnClickListener {
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
