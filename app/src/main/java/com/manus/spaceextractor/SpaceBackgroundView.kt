package com.manus.spaceextractor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class SpaceBackgroundView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = List(110) {
        Star(
            Random.nextFloat(),
            Random.nextFloat(),
            Random.nextFloat() * 2.2f + .5f,
            Random.nextFloat() * 6.28f
        )
    }
    private var phase = 0f
    private val nebula = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderRect = RectF()

    data class Star(val x: Float, val y: Float, val radius: Float, val seed: Float)

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawColor(Color.rgb(3, 7, 22))
        nebula.shader = RadialGradient(
            w * .74f,
            h * .18f,
            w * .7f,
            intArrayOf(0x66492d9c, 0x33202e72, 0x0010163b),
            floatArrayOf(0f, .48f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, nebula)
        nebula.shader = RadialGradient(
            w * .12f,
            h * .72f,
            w * .56f,
            intArrayOf(0x44204280, 0x22106e8d, 0x00000000),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, nebula)

        stars.forEach { star ->
            val twinkle = .55f + .45f * sin(phase * 1.4f + star.seed)
            paint.color = Color.argb(
                (150 + 100 * twinkle).toInt().coerceIn(0, 255),
                170,
                225,
                255
            )
            canvas.drawCircle(
                star.x * w,
                star.y * h,
                star.radius * twinkle,
                paint
            )
        }

        paint.color = 0x5537c8ff
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        canvas.drawOval(w * .38f, h * .08f, w * .94f, h * .32f, paint)
        paint.style = Paint.Style.FILL

        drawRgbBorder(canvas, w, h)

        phase += .035f
        postInvalidateOnAnimation()
    }

    private fun drawRgbBorder(canvas: Canvas, width: Float, height: Float) {
        val inset = 7f
        borderRect.set(inset, inset, width - inset, height - inset)
        val angle = (phase * 70f) % 360f
        val radians = Math.toRadians(angle.toDouble())
        val startX = width / 2f - width * .65f * kotlin.math.cos(radians).toFloat()
        val startY = height / 2f - height * .65f * kotlin.math.sin(radians).toFloat()
        val endX = width / 2f + width * .65f * kotlin.math.cos(radians).toFloat()
        val endY = height / 2f + height * .65f * kotlin.math.sin(radians).toFloat()
        val colors = intArrayOf(
            0xFF00E5FF.toInt(),
            0xFF526BFF.toInt(),
            0xFFE340FF.toInt(),
            0xFFFF2F7D.toInt(),
            0xFF00E5FF.toInt()
        )

        borderPaint.shader = LinearGradient(
            startX,
            startY,
            endX,
            endY,
            colors,
            null,
            Shader.TileMode.CLAMP
        )
        borderPaint.strokeWidth = 3.5f
        borderPaint.alpha = 235
        borderPaint.setShadowLayer(18f, 0f, 0f, 0xFF00DFFF.toInt())
        canvas.drawRoundRect(borderRect, 18f, 18f, borderPaint)
        borderPaint.clearShadowLayer()
        borderPaint.shader = null
    }
}
