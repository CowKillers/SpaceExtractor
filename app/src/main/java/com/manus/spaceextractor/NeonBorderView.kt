package com.manus.spaceextractor

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Camada visual superior; não é clicável e não intercepta os controles. */
class NeonBorderView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var phase = 0f

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val inset = 8f
        rect.set(inset, inset, width - inset, height - inset)
        val radians = Math.toRadians((phase % 360f).toDouble())
        val dx = cos(radians).toFloat()
        val dy = sin(radians).toFloat()
        val colors = intArrayOf(
            0xFF00E5FF.toInt(),
            0xFF4169FF.toInt(),
            0xFFE040FB.toInt(),
            0xFFFF2F7D.toInt(),
            0xFF00E5FF.toInt()
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.alpha = 245
        paint.shader = LinearGradient(
            width / 2f - width * dx,
            height / 2f - height * dy,
            width / 2f + width * dx,
            height / 2f + height * dy,
            colors,
            null,
            Shader.TileMode.MIRROR
        )
        paint.setShadowLayer(30f, 0f, 0f, 0xFF00DFFF.toInt())
        canvas.drawRoundRect(rect, 22f, 22f, paint)
        paint.clearShadowLayer()
        paint.shader = null
        paint.style = Paint.Style.FILL

        phase = (phase + 5.5f) % 360f
        postInvalidateOnAnimation()
    }
}
