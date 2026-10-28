package com.aos.agent.system.capture

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内截图。
 *
 * 这台车机的显示带 FLAG_SECURE，`adb shell screencap` 拿到的可能是全黑图；应用把自身
 * View 树画进 Bitmap 不经过系统合成器，因此不受该标记影响——只要像素是我们自己画的就能截到。
 * 代价是 SurfaceView / TextureView 里的内容不会出现在图里（当前界面没有这类控件）。
 *
 * 落盘到应用专属外部目录，adb 可直接拉取：
 * `/sdcard/Android/data/<applicationId>/files/screenshots/`。
 */
class ScreenCapture(private val activity: Activity) {

    fun capture(): Result<File> = runCatching {
        val root = activity.window.decorView
        // 布局没完成时宽高是 0，画出来会是一张空图——如实报错而不是存个坏文件
        check(root.width > 0 && root.height > 0) { "界面尚未完成布局，等一帧再试" }

        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        try {
            root.draw(Canvas(bitmap))
            val file = nextFile()
            FileOutputStream(file).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG 编码失败" }
            }
            file
        } finally {
            bitmap.recycle()
        }
    }.onFailure { Log.w(TAG, "截图失败", it) }

    private fun nextFile(): File {
        val base = activity.getExternalFilesDir(null) ?: activity.filesDir
        val dir = File(base, DIR_NAME).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        var candidate = File(dir, "aos-$stamp.png")
        // 同一秒连按两次不覆盖前一张
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, "aos-$stamp-$index.png")
            index++
        }
        return candidate
    }

    private companion object {
        const val TAG = "AOSAgent.Capture"
        const val DIR_NAME = "screenshots"
    }
}
