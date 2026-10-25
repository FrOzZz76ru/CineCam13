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
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var recordingActive = false

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                toast("Разреши доступ к камере в настройках телефона")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buildUi()

        if (checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101114.toInt())
        }

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        root.addView(previewView)

        val title = TextView(this).apply {
            text = "CINECAM 13  •  CAMERA"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(8, 16, 8, 12)
        }

        root.addView(title)

        val filterRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(6, 4, 6, 4)
        }

        listOf("Natural", "Cinema", "Warm", "Cool", "Mono")
            .forEach { filter ->
                filterRow.addView(Button(this).apply {
                    text = filter
                    textSize = 10f
                    setOnClickListener {
                        toast(
                            "$filter выбрано. Эффект пока не реализован."
                        )
                    }
                })
            }

        root.addView(filterRow)

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 24)
        }

        controls.addView(Button(this).apply {
            text = "ФОТО"
            setOnClickListener { takePhoto() }
        })

        controls.addView(Button(this).apply {
            text = "ВИДЕО ●"
            setOnClickListener { toggleVideo() }
        })

        root.addView(controls)
        setContentView(root)
    }

    private fun startCamera() {
        val providerFuture =
            ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            imageCapture = ImageCapture.Builder().build()

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.FHD)
                )
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            try {
                provider.unbindAll()

                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    videoCapture
                )
            } catch (e: Exception) {
                toast("Не удалось запустить камеру")
            }
        }, mainExecutor)
    }

    private fun takePhoto() {
        val capture = imageCapture
            ?: return toast("Камера ещё запускается")

        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())

        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "CineCam13_$timestamp.jpg"
            )
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                "image/jpeg"
            )
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/CineCam13"
            )
        }

        val output =
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()

        capture.takePicture(
            output,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {
                    toast("Фото сохранено")
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    toast("Ошибка фото: ${exception.message}")
                }
            }
        )
    }

    private fun toggleVideo() {
        val capture = videoCapture
            ?: return toast("Камера ещё запускается")

        if (recordingActive) {
            recording?.stop()
            return
        }

        val timestamp = SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            Locale.US
        ).format(Date())

        val values = ContentValues().apply {
            put(
                MediaStore.MediaColumns.DISPLAY_NAME,
                "CineCam13_$timestamp.mp4"
            )
            put(
                MediaStore.MediaColumns.MIME_TYPE,
                "video/mp4"
            )
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "Movies/CineCam13"
            )
        }

        val output = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(values)
            .build()

        var pending =
            capture.output.prepareRecording(this, output)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }

        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recordingActive = true
                    toast("Идёт запись видео")
                }

                is VideoRecordEvent.Finalize -> {
                    recordingActive = false
                    recording = null

                    if (event.hasError()) {
                        toast("Ошибка записи: ${event.error}")
                    } else {
                        toast("Видео сохранено")
                    }
                }
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
}
