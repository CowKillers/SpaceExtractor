package com.manus.spaceextractor

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

class SpaceBackgroundView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stars = List(110) { Star(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2.2f + .5f, Random.nextFloat() * 6.28f) }
    private var phase = 0f
    private val nebula = Paint(Paint.ANTI_ALIAS_FLAG)
    data class Star(val x: Float, val y: Float, val radius: Float, val seed: Float)

    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas); val w = width.toFloat(); val h = height.toFloat()
        canvas.drawColor(Color.rgb(3, 7, 22))
        nebula.shader = RadialGradient(w * .74f, h * .18f, w * .7f, intArrayOf(0x66492d9c, 0x33202e72, 0x0010163b), floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP); canvas.drawRect(0f, 0f, w, h, nebula)
        nebula.shader = RadialGradient(w * .12f, h * .72f, w * .56f, intArrayOf(0x44204280, 0x22106e8d, 0x00000000), null, Shader.TileMode.CLAMP); canvas.drawRect(0f, 0f, w, h, nebula)
        stars.forEach { s -> val twinkle = (.55f + .45f * sin(phase * 1.4f + s.seed)); paint.color = Color.argb((150 + 100 * twinkle).toInt().coerceIn(0, 255), 170, 225, 255); canvas.drawCircle(s.x * w, s.y * h, s.radius * twinkle, paint) }
        paint.color = 0x5537c8ff; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.2f
        canvas.drawOval(w * .38f, h * .08f, w * .94f, h * .32f, paint); paint.style = Paint.Style.FILL
        // Fundo deliberadamente estático: a tela principal não rola nem anima.
    }
}
