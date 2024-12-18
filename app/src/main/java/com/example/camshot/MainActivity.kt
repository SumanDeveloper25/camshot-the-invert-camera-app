package com.example.camshot

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.TextureView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camshot.databinding.ActivityMainBinding
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isMirrored: Boolean = false
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1001
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable Edge-to-Edge
        enableEdgeToEdge()

        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = Locale.getDefault()
            }
        }

        // Initialize Camera Executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check Camera Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission()
        }

        // Set up toggle button listener
        binding.btnToggle.setOnClickListener {
            togglePreviewMode()
        }

        // Set up snapshot button listener
        binding.btnSnapshot.setOnClickListener {
            takeSnapshot()
        }
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to use this app", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera(retryCount: Int = 3) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Camera preview setup
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder().build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera use cases: ${e.message}")
                if (retryCount > 0) {
                    Toast.makeText(this, "Retrying camera initialization...", Toast.LENGTH_SHORT).show()
                    startCamera(retryCount - 1)
                } else {
                    Toast.makeText(this, "Failed to start camera. Please try again later.", Toast.LENGTH_LONG).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun togglePreviewMode() {
        isMirrored = !isMirrored
        val textureView = binding.previewView.getChildAt(0) as? TextureView
        textureView?.let {
            val matrix = Matrix()
            if (isMirrored) {
                matrix.preScale(-1f, 1f, it.width / 2f, it.height / 2f)
                textToSpeech.speak("Mirrored mode", TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                matrix.reset()
                textToSpeech.speak("Normal mode", TextToSpeech.QUEUE_FLUSH, null, null)
            }
            it.setTransform(matrix)
        } ?: run {
            Toast.makeText(this, "Preview view transformation not supported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun takeSnapshot() {
        val imageCapture = imageCapture ?: return

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Snapshot-${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/InvertedCameraApp")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    textToSpeech.speak("Snapshot saved", TextToSpeech.QUEUE_FLUSH, null, null)
                    Toast.makeText(this@MainActivity, "Snapshot saved to gallery", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}")
                    textToSpeech.speak("Snapshot failed", TextToSpeech.QUEUE_FLUSH, null, null)
                    Toast.makeText(this@MainActivity, "Snapshot failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.shutdown()
        cameraExecutor.shutdown()
    }
}
