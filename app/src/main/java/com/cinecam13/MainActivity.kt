package com.cinecam13

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

private lateinit var previewView: PreviewView
private var imageCapture: ImageCapture? = null

private val permissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            Toast.makeText(this, "Для камеры нужно разрешение", Toast.LENGTH_LONG).show()
        }
    }

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(android.graphics.Color.BLACK)
    }

    val title = TextView(this).apply {
        text = "CineCam 13"
        textSize = 24f
        gravity = Gravity.CENTER
        setTextColor(android.graphics.Color.WHITE)
        setPadding(12, 18, 12, 18)
    }
    root.addView(title)

    previewView = PreviewView(this)
    root.addView(
        previewView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    )

    val filterRow = LinearLayout(this).apply {
        gravity = Gravity.CENTER
    }

    listOf("Natural", "Cinema", "Warm", "Cool", "Mono").forEach { name ->
        filterRow.addView(Button(this).apply {
            text = name
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "$name: фильтр пока не реализован",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
    root.addView(filterRow)

    val photoButton = Button(this).apply {
        text = "Сделать фото"
        setOnClickListener { takePhoto() }
    }
    root.addView(photoButton)

    setContentView(root)

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED
    ) {
        startCamera()
    } else {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }
}

private fun startCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder().build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось запустить камеру: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }, ContextCompat.getMainExecutor(this))
}

private fun takePhoto() {
    val capture = imageCapture ?: run {
        Toast.makeText(this, "Камера ещё запускается", Toast.LENGTH_SHORT).show()
        return
    }

    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "CineCam_${System.currentTimeMillis()}.jpg")
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
                    "Фото сохранено в Pictures/CineCam13",
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
