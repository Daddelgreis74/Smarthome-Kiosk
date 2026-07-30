package com.example.smarthomekiosk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import java.nio.ByteBuffer

class MotionDetector(
    private val context: Context,
    private val onMotionDetected: () -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var lastGrid: IntArray? = null
    
    // Configurable parameters
    var sensitivity: Int = 50
    var isDebugEnabled: Boolean = false
        set(value) {
            val changed = field != value
            field = value
            if (changed && isRunning) {
                // Restart camera session with/without debug view
                restartSession()
            }
        }

    private var isRunning = false
    private var frameCount = 0
    private val skipFrames = 3 // Only process 1 in 3 frames (approx. 2-3 fps)

    // Floating Debug Preview View
    private var debugOverlayView: TextureView? = null
    private var windowManager: WindowManager? = null
    private var debugSurface: Surface? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true
        startBackgroundThread()

        val cameraId = getFrontCameraId()
        if (cameraId == null) {
            Log.e("MotionDetector", "No front camera found!")
            stop()
            return
        }

        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    setupCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    stop()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e("MotionDetector", "Camera error: $error")
                    stop()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("MotionDetector", "Failed to open camera", e)
            stop()
        }
    }

    fun stop() {
        isRunning = false
        removeDebugOverlay()
        
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: Exception) {
            // Ignore
        }
        
        stopBackgroundThread()
        lastGrid = null
        Log.i("MotionDetector", "Stopped")
    }

    private fun getFrontCameraId(): String? {
        for (id in cameraManager.cameraIdList) {
            val chars = cameraManager.getCameraCharacteristics(id)
            val facing = chars.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraMetadata.LENS_FACING_FRONT) {
                return id
            }
        }
        return cameraManager.cameraIdList.firstOrNull() // Fallback to primary camera
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackgroundThread").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (e: Exception) {
            // Ignore
        }
        backgroundThread = null
        backgroundHandler = null
    }

    private fun setupCaptureSession() {
        val device = cameraDevice ?: return
        
        // Setup ImageReader for YUV processing (small resolution for fast analysis)
        imageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2)
        imageReader?.setOnImageAvailableListener({ reader ->
            val img = reader.acquireNextImage()
            if (img != null) {
                try {
                    if (isRunning && frameCount++ % skipFrames == 0) {
                        processImage(img)
                    }
                } finally {
                    img.close()
                }
            }
        }, backgroundHandler)

        val targets = mutableListOf<Surface>()
        targets.add(imageReader!!.surface)

        if (isDebugEnabled) {
            createDebugOverlay { surface ->
                debugSurface = surface
                if (surface != null) {
                    targets.add(surface)
                }
                createSession(device, targets)
            }
        } else {
            createSession(device, targets)
        }
    }

    private fun createSession(device: CameraDevice, targets: List<Surface>) {
        try {
            device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    startRepeatingRequest()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e("MotionDetector", "Failed to configure capture session")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e("MotionDetector", "Error creating session", e)
        }
    }

    private fun startRepeatingRequest() {
        val device = cameraDevice ?: return
        val session = captureSession ?: return
        val reader = imageReader ?: return

        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(reader.surface)
                if (isDebugEnabled && debugSurface != null) {
                    addTarget(debugSurface!!)
                }
            }
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e("MotionDetector", "Error starting repeating request", e)
        }
    }

    private fun restartSession() {
        backgroundHandler?.post {
            try {
                captureSession?.stopRepeating()
                captureSession?.close()
                captureSession = null
                removeDebugOverlay()
                setupCaptureSession()
            } catch (e: Exception) {
                Log.e("MotionDetector", "Error restarting session", e)
            }
        }
    }

    private fun processImage(image: android.media.Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        // Downsample to a 16x12 grid to average out pixel noise and run super fast
        val gridW = 16
        val gridH = 12
        val cellW = width / gridW
        val cellH = height / gridH
        val currentGrid = IntArray(gridW * gridH)

        for (gy in 0 until gridH) {
            for (gx in 0 until gridW) {
                var sum = 0
                var count = 0
                val startX = gx * cellW
                val startY = gy * cellH
                for (dy in 0 until cellH step 4) {
                    val py = startY + dy
                    val rowOffset = py * rowStride
                    for (dx in 0 until cellW step 4) {
                        val px = startX + dx
                        val offset = rowOffset + px * pixelStride
                        if (offset < buffer.remaining()) {
                            sum += buffer.get(offset).toInt() and 0xFF
                            count++
                        }
                    }
                }
                currentGrid[gy * gridW + gx] = if (count > 0) sum / count else 0
            }
        }

        if (lastGrid != null) {
            var totalDiff = 0
            for (i in currentGrid.indices) {
                totalDiff += Math.abs(currentGrid[i] - lastGrid!![i])
            }
            val avgDiff = totalDiff.toFloat() / currentGrid.size
            
            // Scaled threshold: sensitivity 100 -> threshold ~2 (extremely sensitive)
            // sensitivity 0 -> threshold ~52 (hardly sensitive)
            val threshold = (105 - sensitivity).coerceInBound(5, 100) / 2
            
            if (avgDiff > threshold) {
                Log.d("MotionDetector", "Motion detected! Diff: $avgDiff (Threshold: $threshold)")
                onMotionDetected()
            }
        }
        lastGrid = currentGrid
    }

    private fun Int.coerceInBound(min: Int, max: Int): Int {
        return if (this < min) min else if (this > max) max else this
    }

    private fun createDebugOverlay(onSurfaceReady: (Surface?) -> Unit) {
        // Run on UI thread since we are creating a view and adding it to WindowManager
        Handler(context.mainLooper).post {
            try {
                if (debugOverlayView != null) {
                    removeDebugOverlay()
                }

                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                debugOverlayView = TextureView(context)
                
                val params = WindowManager.LayoutParams(
                    320, 240,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    x = 20
                    y = 20
                }

                debugOverlayView?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        onSurfaceReady(Surface(surface))
                    }

                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                        onSurfaceReady(null)
                        return true
                    }
                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                }

                windowManager?.addView(debugOverlayView, params)
            } catch (e: Exception) {
                Log.e("MotionDetector", "Failed to create floating debug overlay (no overlay permission?)", e)
                onSurfaceReady(null)
            }
        }
    }

    private fun removeDebugOverlay() {
        if (debugOverlayView != null) {
            Handler(context.mainLooper).post {
                try {
                    windowManager?.removeView(debugOverlayView)
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    debugOverlayView = null
                    debugSurface = null
                    windowManager = null
                }
            }
        }
    }
}
