package com.example

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume

object StealthMediaCapture {
    suspend fun captureAudio(context: Context, durationMillis: Long = 1500): String? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return@withContext null
        }
        val outputFile = File(context.cacheDir, "sos_audio.mp4")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()
            kotlinx.coroutines.delay(durationMillis)
            recorder.stop()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            try { recorder.release() } catch (e: Exception) {}
        }
        
        if (outputFile.exists()) {
            try {
                val bytes = outputFile.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                outputFile.delete()
                return@withContext "data:audio/mp4;base64,$base64"
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext null
    }

    suspend fun capturePhotos(context: Context): List<String> = withContext(Dispatchers.Main) {
        val photos = mutableListOf<String>()
        val backPhoto = captureSinglePhoto(context, CameraSelector.DEFAULT_BACK_CAMERA)
        if (backPhoto != null) photos.add(backPhoto)
        val frontPhoto = captureSinglePhoto(context, CameraSelector.DEFAULT_FRONT_CAMERA)
        if (frontPhoto != null) photos.add(frontPhoto)
        photos
    }

    private suspend fun captureSinglePhoto(context: Context, cameraSelector: CameraSelector): String? = suspendCancellableCoroutine { cont ->
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                
                val fakeLifecycleOwner = object : androidx.lifecycle.LifecycleOwner {
                    val registry = androidx.lifecycle.LifecycleRegistry(this).apply {
                        currentState = androidx.lifecycle.Lifecycle.State.RESUMED
                    }
                    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
                }
                
                cameraProvider.bindToLifecycle(fakeLifecycleOwner, cameraSelector, imageCapture)

                imageCapture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.capacity())
                                buffer.get(bytes)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
                                
                                val matrix = Matrix()
                                matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                                
                                val scale = minOf(400f / bitmap.width, 400f / bitmap.height)
                                if (scale < 1f) {
                                    matrix.postScale(scale, scale)
                                }
                                
                                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                                val stream = ByteArrayOutputStream()
                                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 30, stream)
                                val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                                
                                image.close()
                                cameraProvider.unbindAll()
                                fakeLifecycleOwner.registry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
                                cont.resume("data:image/jpeg;base64,$base64")
                            } catch (e: Exception) {
                                image.close()
                                cameraProvider.unbindAll()
                                fakeLifecycleOwner.registry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
                                cont.resume(null)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            cameraProvider.unbindAll()
                            fakeLifecycleOwner.registry.currentState = androidx.lifecycle.Lifecycle.State.DESTROYED
                            cont.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                cont.resume(null)
            }
        }, ContextCompat.getMainExecutor(context))
    }
}
