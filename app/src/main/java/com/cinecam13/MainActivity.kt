package com.cinecam13

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
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
    private lateinit var cameraProvider: ProcessCameraProvider
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var frontCamera = false
    private var zoom = 1f
    private var currentMode = "PHOTO"
    private var currentStyle = "NATURAL"

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                Toast.makeText(this, "Нужен доступ к камере", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        buildInterface()

        val permissions = arrayOf(Manifest.permission.CAMERA)
        if (permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            startCamera()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    private fun buildInterface() {
        val background = Color.rgb(8, 8, 10)
        val white = Color.WHITE
        val muted = Color.rgb(145, 145, 155)
        val accent = Color.rgb(240, 190, 105)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
            setPadding(18, 12, 18, 12)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = TextView(this).apply {
            text = "CINECAM 13"
            textSize = 18f
            setTextColor(white)
            letterSpacing = 0.12f
        }
        top.addView(brand, LinearLayout.LayoutParams(0, 54, 1f))

        val badge = TextView(this).apply {
            text = "CINEMATIC"
            textSize = 10f
            setTextColor(accent)
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
        }
        top.addView(badge)
        root.addView(top)

        val previewContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(20, 20, 23))
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        previewContainer.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val liveLabel = TextView(this).apply {
            text = "● LIVE"
            textSize = 11f
            setTextColor(white)
            setPadding(12, 8, 12, 8)
            setBackgroundColor(Color.argb(130, 0, 0, 0))
        }
        previewContainer.addView(
            liveLabel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                setMargins(12, 12, 0, 0)
            }
        )

        root.addView(
            previewContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 10
                bottomMargin = 14
            }
        )

        val zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        listOf(1f, 1.5f, 2f, 3f).forEach { factor ->
            val button = Button(this).apply {
                text = "${factor}×"
                textSize = 13f
                isAllCaps = false
                setTextColor(if (factor == zoom) accent else white)
                setBackgroundColor(Color.TRANSPARENT)
                setOnClickListener {
                    zoom = factor
                    camera?.cameraControl?.setZoomRatio(factor)
                    for (i in 0 until zoomRow.childCount) {
                        (zoomRow.getChildAt(i) as? Button)?.setTextColor(
                            if (zoomRow.getChildAt(i).tag == factor) accent else white
                        )
                    }
                }
                tag = factor
            }
            zoomRow.addView(button, LinearLayout.LayoutParams(0, 48, 1f))
        }
        root.addView(zoomRow)

        val styleTitle = TextView(this).apply {
            text = "CINEMATIC LOOK"
            textSize = 10f
            letterSpacing = 0.16f
            setTextColor(muted)
            setPadding(2, 8, 2, 8)
        }
        root.addView(styleTitle)

        val styles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        listOf("NATURAL", "CINEMA", "WARM", "COOL").forEach { style ->
            val button = TextView(this).apply {
                text = style
                textSize = 10f
                gravity = Gravity.CENTER
                setPadding(5, 12, 5, 12)
                setTextColor(if (style == currentStyle) accent else white)
                setOnClickListener {
                    currentStyle = style
                    for (i in 0 until styles.childCount) {
                        val child = styles.getChildAt(i) as TextView
                        child.setTextColor(if (child.text == style) accent else white)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        "$style: стиль выбран",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            styles.addView(button, LinearLayout.LayoutParams(0, 42, 1f))
        }
        root.addView(styles)

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 4)
        }

        listOf("VIDEO", "PHOTO").forEach { mode ->
            val button = TextView(this).apply {
                text = mode
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(24, 8, 24, 8)
                setTextColor(if (mode == currentMode) accent else muted)
                setOnClickListener {
                    currentMode = mode
                    for (i in 0 until modeRow.childCount) {
                        val child = modeRow.getChildAt(i) as TextView
                        child.setTextColor(if (child.text == mode) accent else muted)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        if (mode == "VIDEO") "Видеосъёмка пока не подключена" else "Режим фото",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            modeRow.addView(button)
        }
        root.addView(modeRow)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 8)
        }

        val galleryButton = TextView(this).apply {
            text = "▧"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(white)
            setOnClickListener {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                ).apply { type = "image/*" }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Галерея не найдена", Toast.LENGTH_SHORT).show()
                }
            }
        }
        bottom.addView(galleryButton, LinearLayout.LayoutParams(0, 64, 1f))

        val shutter = TextView(this).apply {
            text = "●"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(white)
            setOnClickListener {
                if (currentMode == "VIDEO") {
                    Toast.makeText(
                        this@MainActivity,
                        "Запись видео добавим следующим шагом",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    takePhoto()
                }
            }
        }
        bottom.addView(shutter, LinearLayout.LayoutParams(0, 70, 1.5f))

        val switchButton = TextView(this).apply {
            text = "↻"
            textSize = 32f
            gravity = Gravity.CENTER
            setTextColor(white)
            setOnClickListener {
                frontCamera = !frontCamera
                startCamera()
            }
        }
        bottom.addView(switchButton, LinearLayout.LayoutParams(0, 64, 1f))

        root.addView(bottom)
        setContentView(root)
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                val selector = if (frontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageCapture
                )
                camera?.cameraControl?.setZoomRatio(zoom)
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
            Toast.makeText(this, "Камера ещё запускается", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(
                        this@MainActivity,
                        "Фото сохранено: Pictures/CineCam13",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onError(exception: ImageCaptureException) {
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
