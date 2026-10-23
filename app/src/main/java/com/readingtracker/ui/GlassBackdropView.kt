package com.readingtracker.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Фон оформления «стекло»: мягкие пятна цветного света медленно плывут,
 * при листании экранов сдвигаются вслед за пальцем; сверху мелкое зерно матового стекла.
 */
class GlassBackdropView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private class Blob(val color: Int, val x: Float, val y: Float, val r: Float,
                       val ax: Float, val ay: Float, val speed: Float, val phase: Float, val depth: Float)

    private val night = RtColors.isNight(context)
    private val motion = RtTheme.motion(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grain = Paint().apply { shader = BitmapShader(noise(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    private val start = SystemClock.uptimeMillis()

    /** Сдвиг при листании: позиция карусели в экранах (дробная). */
    var scroll = 0f
        set(value) { field = value; invalidate() }

    private val blobs: List<Blob> = run {
        val colors = if (night) intArrayOf(0xFF5B3DE0.toInt(), 0xFFE0408A.toInt(), 0xFF1FB6D9.toInt(), 0xFF3D2BA8.toInt(), 0xFFFF8A3D.toInt())
            else intArrayOf(0xFFB9A8FF.toInt(), 0xFFFFB8D6.toInt(), 0xFF9FE8FF.toInt(), 0xFFFFD39A.toInt(), 0xFFB6F5D8.toInt())
        val pos = arrayOf(floatArrayOf(0.15f, 0.12f, 0.75f), floatArrayOf(0.9f, 0.3f, 0.7f), floatArrayOf(0.2f, 0.62f, 0.8f),
            floatArrayOf(0.85f, 0.85f, 0.75f), floatArrayOf(0.5f, 0.45f, 0.55f))
        val rnd = Random(7)
        colors.mapIndexed { i, c ->
            Blob(c, pos[i][0], pos[i][1], pos[i][2], 0.06f + rnd.nextFloat() * 0.06f, 0.05f + rnd.nextFloat() * 0.05f,
                0.05f + rnd.nextFloat() * 0.05f, rnd.nextFloat() * 6.28f, 0.4f + i * 0.18f)
        }
    }

    private fun noise(): Bitmap {
        val n = 96
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val rnd = Random(11)
        val px = IntArray(n * n) {
            val v = rnd.nextInt(256)
            Color.argb(if (night) 14 else 12, v, v, v)
        }
        bmp.setPixels(px, 0, n, 0, 0, n, n)
        return bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        // Основа
        paint.shader = if (night) LinearGradient(0f, 0f, 0f, h, 0xFF0A0818.toInt(), 0xFF120A26.toInt(), Shader.TileMode.CLAMP)
            else LinearGradient(0f, 0f, w, h, 0xFFF3EEFF.toInt(), 0xFFFFF1EA.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)

        val t = if (motion) (SystemClock.uptimeMillis() - start) / 1000f else 0f
        val alpha = if (night) 0.62f else 0.85f
        for (b in blobs) {
            val cx = (b.x + b.ax * sin(t * b.speed + b.phase)) * w - scroll * w * 0.18f * b.depth
            val cy = (b.y + b.ay * cos(t * b.speed * 0.8f + b.phase)) * h
            val r = b.r * maxOf(w, h * 0.6f)
            // Пятна уходят за край при листании и возвращаются с другой стороны
            val span = w * 1.6f
            val x = ((cx % span) + span) % span - w * 0.3f
            paint.shader = RadialGradient(x, cy, r,
                intArrayOf(RtColors.withAlpha(b.color, alpha), RtColors.withAlpha(b.color, alpha * 0.35f), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(x, cy, r, paint)
        }
        paint.shader = null
        canvas.drawRect(0f, 0f, w, h, grain)
        // Медленное движение: 20 кадров в секунду хватает, бережём батарею
        if (motion && isShown) postInvalidateDelayed(50)
    }
}
