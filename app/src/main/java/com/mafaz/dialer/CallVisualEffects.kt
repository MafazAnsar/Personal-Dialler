package com.mafaz.dialer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.animation.ValueAnimator
import android.view.View
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

/** Lightweight ambient ribbons and drifting particles, tinted from the caller's identity colour. */
class AuroraFlowView(ctx: Context) : View(ctx) {
    private data class Particle(val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float)
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var progress = 0f
    private var tint = 0xFF55C7D8.toInt()
    private val particles = List(34) { i ->
        val r = Random(i * 7919 + 37)
        Particle(r.nextFloat(), r.nextFloat(), 0.025f + r.nextFloat() * 0.055f,
            1.0f + r.nextFloat() * 1.8f, r.nextFloat() * 6.28f)
    }
    private val motion = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 28000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { progress = it.animatedValue as Float; invalidate() }
    }

    fun setCaller(name: String, number: String, color: Int) {
        tint = color
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == View.VISIBLE) motion.start()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && isAttachedToWindow) motion.start() else motion.cancel()
    }

    override fun onDetachedFromWindow() {
        motion.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val t = progress * 2f * Math.PI.toFloat()
        val red = Color.red(tint)
        val green = Color.green(tint)
        val blue = Color.blue(tint)
        for (i in 0..2) {
            val phase = t + i * 2.05f
            val baseY = height * (0.30f + i * 0.19f)
            path.reset()
            path.moveTo(-width * 0.12f, baseY + sin(phase) * height * 0.10f)
            path.cubicTo(
                width * 0.22f, baseY + cos(phase * 0.83f + 0.8f) * height * 0.22f,
                width * 0.68f, baseY + sin(phase * 0.71f + 2.1f) * height * 0.20f,
                width * 1.12f, baseY + cos(phase + 1.7f) * height * 0.12f
            )
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = Color.argb(15, red, green, blue)
            paint.strokeWidth = 24f * density
            canvas.drawPath(path, paint)
            paint.color = Color.argb(30 + i * 5, red, green, blue)
            paint.strokeWidth = (2.2f + i * 0.45f) * density
            canvas.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        for (p in particles) {
            val x = ((p.x + progress * p.speed * 3f) % 1.08f) * width - width * 0.04f
            val y = (p.y + sin(t + p.phase) * 0.035f) * height
            val alpha = (22 + (0.5f + 0.5f * sin(t * 1.2f + p.phase)) * 30).toInt()
            paint.color = Color.argb(alpha, red, green, blue)
            canvas.drawCircle(x, y, p.size * density, paint)
        }
    }
}


/** Animated caller-coloured neural network with drifting nodes and travelling light pulses. */
class NeuralNetworkView(ctx: Context) : View(ctx) {
    private data class Node(val x: Float, val y: Float, val phase: Float, val speed: Float)
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodes = List(44) { i ->
        val r = Random(9109 + i * 3571)
        Node(r.nextFloat(), r.nextFloat(), r.nextFloat() * 6.28f, 0.20f + r.nextFloat() * 0.26f)
    }
    private var progress = 0f
    private var tint = 0xFF55C7D8.toInt()
    private val motion = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 24000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { progress = it.animatedValue as Float; invalidate() }
    }

    fun setCaller(color: Int) { tint = color; invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == View.VISIBLE) motion.start()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && isAttachedToWindow) motion.start() else motion.cancel()
    }
    override fun onDetachedFromWindow() { motion.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        val t = progress * 2f * Math.PI.toFloat()
        val red = Color.red(tint)
        val green = Color.green(tint)
        val blue = Color.blue(tint)
        val xs = FloatArray(nodes.size)
        val ys = FloatArray(nodes.size)
        for (i in nodes.indices) {
            val n = nodes[i]
            xs[i] = (n.x + sin(t * n.speed + n.phase) * 0.075f) * width
            ys[i] = (n.y + cos(t * n.speed * 0.83f + n.phase) * 0.075f) * height
        }

        val maxDist = 172f * density
        val maxDist2 = maxDist * maxDist
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.0f * density
        for (i in nodes.indices) for (j in i + 1 until nodes.size) {
            val dx = xs[i] - xs[j]
            val dy = ys[i] - ys[j]
            val d2 = dx * dx + dy * dy
            if (d2 < maxDist2) {
                val proximity = 1f - d2 / maxDist2
                val shimmer = 0.5f + 0.5f * sin(t * 1.8f + nodes[i].phase + nodes[j].phase)
                val alpha = (9f + proximity * 25f + shimmer * 12f).toInt().coerceIn(8, 46)
                paint.color = Color.argb(alpha, red, green, blue)
                canvas.drawLine(xs[i], ys[i], xs[j], ys[j], paint)

                // A small bright point travels along selected live connections.
                if (j == (i + 5) % nodes.size || j == (i + 11) % nodes.size) {
                    val phase = (progress * 2.8f + i * 0.137f + j * 0.031f) % 1f
                    val px = xs[i] + (xs[j] - xs[i]) * phase
                    val py = ys[i] + (ys[j] - ys[i]) * phase
                    paint.style = Paint.Style.FILL
                    paint.color = Color.argb(48, red, green, blue)
                    canvas.drawCircle(px, py, 6.0f * density, paint)
                    paint.color = Color.argb(185, red, green, blue)
                    canvas.drawCircle(px, py, 2.1f * density, paint)
                    paint.color = Color.argb(150, 225, 240, 248)
                    canvas.drawCircle(px, py, 0.9f * density, paint)
                    paint.style = Paint.Style.STROKE
                }
            }
        }

        paint.style = Paint.Style.FILL
        for (i in nodes.indices) {
            val pulse = 0.5f + 0.5f * sin(t * 2.0f + nodes[i].phase)
            val alpha = (52f + pulse * 75f).toInt()
            paint.color = Color.argb(alpha, red, green, blue)
            canvas.drawCircle(xs[i], ys[i], (1.8f + pulse * 1.7f) * density, paint)
            if (i % 5 == 0) {
                paint.color = Color.argb((alpha * 0.34f).toInt(), 225, 240, 248)
                canvas.drawCircle(xs[i], ys[i], (4f + pulse * 2.5f) * density, paint)
            }
        }
    }
}


/** Softly drifting glyphs derived only from the current caller's name and number. */
class CallerMatrixView(ctx: Context) : View(ctx) {
    private data class Glyph(val char: Char, val x: Float, val y: Float, val speed: Float, val size: Float, val phase: Float)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
    private var glyphs: List<Glyph> = emptyList()
    private var progress = 0f
    private val density = resources.displayMetrics.density
    private val motion = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 24000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { progress = it.animatedValue as Float; invalidate() }
    }

    fun setCaller(name: String, number: String) {
        val source = (name + "   " + number).filter { it.isLetterOrDigit() || it == '+' || it == '*' || it == '#' }
        val chars = source.ifBlank { "CALL" }
        val random = Random(chars.hashCode())
        glyphs = List(66) { i ->
            Glyph(chars[i % chars.length], random.nextFloat(), random.nextFloat(),
                0.10f + random.nextFloat() * 0.28f, 22f + random.nextFloat() * 18f,
                random.nextFloat() * 6.28f)
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == View.VISIBLE) motion.start()
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && isAttachedToWindow) motion.start() else motion.cancel()
    }
    override fun onDetachedFromWindow() { motion.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val t = progress * 2f * Math.PI.toFloat()
        for ((i, g) in glyphs.withIndex()) {
            val y = ((g.y + progress * g.speed * 2.5f) % 1.12f) * height - height * 0.06f
            val x = (g.x + sin(t + g.phase) * 0.025f).coerceIn(0f, 1f) * width
            val wave = (0.5f + 0.5f * sin(t * 1.3f + g.phase))
            val alpha = (42 + wave * 52).toInt().coerceIn(42, 94)
            paint.color = if (i % 7 == 0) Color.argb(alpha, 112, 145, 145)
                else Color.argb(alpha, 190, 198, 204)
            paint.textSize = g.size * density
            canvas.drawText(g.char.toString(), x, y, paint)
        }
    }
}
