package com.wechat.agent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.wechat.agent.data.ScreenCaptureManager
import java.io.File
import java.io.FileOutputStream

/**
 * 一次性屏幕截图 Activity：本身无 UI（透明）。
 *
 * 启动后立即弹系统 MediaProjection 授权；用户同意后截取一帧存成 JPEG，
 * 通过 [ScreenCaptureManager.complete] 回传路径并结束自身。
 * 用户拒绝 / 取消 / 失败则回传 null。
 */
class ScreenCaptureActivity : ComponentActivity() {

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var finished = false

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val ok = captureAndSave(result.resultCode, result.data!!)
                if (!ok) {
                    ScreenCaptureManager.complete(null)
                    finishOnce()
                }
            } else {
                ScreenCaptureManager.complete(null)
                finishOnce()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(mpm.createScreenCaptureIntent())
    }

    /** 同步阶段创建投影与虚拟屏；真正的画面在 ImageReader 回调里异步到达后再回传。 */
    @Suppress("DEPRECATION")
    private fun captureAndSave(resultCode: Int, data: Intent): Boolean {
        return try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection: MediaProjection = mpm.getMediaProjection(resultCode, data)
                ?: return false

            val metrics = DisplayMetrics()
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            val handler = Handler(Looper.getMainLooper())
            reader.setOnImageAvailableListener({ r ->
                r.setOnImageAvailableListener(null, null)
                var image: Image? = null
                try {
                    image = r.acquireLatestImage()
                    val bitmap = image?.let { imageToBitmap(it, width, height) }
                    ScreenCaptureManager.complete(bitmap?.let { saveJpeg(it) })
                } catch (_: Exception) {
                    ScreenCaptureManager.complete(null)
                } finally {
                    image?.close()
                    r.close()
                    runCatching { projection.stop() }
                    finishOnce()
                }
            }, handler)

            // Android 14+（targetSdk 34）必须先 registerCallback 再 createVirtualDisplay
            if (Build.VERSION.SDK_INT >= 34) {
                projection.registerCallback(object : MediaProjection.Callback() {}, handler)
            }

            virtualDisplay = projection.createVirtualDisplay(
                "screen_capture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 将 Image 首平面（RGBA_8888）按 rowStride/pixelStride 正确还原为 Bitmap。 */
    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buffer)
        return if (rowPadding > 0) Bitmap.createBitmap(bmp, 0, 0, width, height) else bmp
    }

    private fun saveJpeg(bitmap: Bitmap): String? {
        return try {
            val file = File(cacheDir, "screen_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            bitmap.recycle()
            file.absolutePath
        } catch (_: Exception) {
            bitmap.recycle()
            null
        }
    }

    private fun finishOnce() {
        if (finished) return
        finished = true
        virtualDisplay?.release()
        imageReader?.close()
        finish()
    }
}