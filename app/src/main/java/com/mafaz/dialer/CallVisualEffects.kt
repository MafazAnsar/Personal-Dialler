package com.mafaz.dialer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.animation.ValueAnimator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

/**
 * A practical deep-zoom rendering of the Mandelbrot boundary.
 * Frames are calculated on a worker thread at low resolution, then scaled by Canvas.
 * The zoom periodically restarts after reaching double-precision limits.
 */
class MandelbrotZoomView(ctx: Context) : View(ctx) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var frame: Bitmap? = null
    private var worker: Thread? = null
    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val rect = android.graphics.Rect()
    private val centerX = -0.743643887037151
    private val centerY = 0.13182590420533

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == View.VISIBLE) startRenderer()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && isAttachedToWindow) startRenderer()
        else stopRenderer()
    }

    private fun startRenderer() {
        if (running) return
        running = true
        worker = Thread({
            var zoom = 2.8
            while (running) {
                try {
                    val w = 120
                    val h = 213
                    val pixels = IntArray(w * h)
                    val scale = zoom
                    for (py in 0 until h) {
                        val ci = centerY + ((py - h / 2.0) / h) * scale * 1.65
                        for (px in 0 until w) {
                            val cr = centerX + ((px - w / 2.0) / w) * scale
                            var zr = 0.0
                            var zi = 0.0
                            var n = 0
                            val maxIter = 34
                            while (zr * zr + zi * zi <= 256.0 && n < maxIter) {
                                val nextR = zr * zr - zi * zi + cr
                                zi = 2.0 * zr * zi + ci
                                zr = nextR
                                n++
                            }
                            val color = if (n >= maxIter) {
                                Color.argb(0, 13, 17, 22)
                            } else {
                                val glow = (n * 3 + 18).coerceIn(18, 105)
                                if (n % 7 == 0) Color.argb(90, glow / 2, glow, glow)
                                else Color.argb(65, glow, (glow * 1.04).toInt().coerceAtMost(120), (glow * 1.08).toInt().coerceAtMost(125))
                            }
                            pixels[py * w + px] = color
                        }
                    }
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
                    main.post {
                        if (running) {
                            val old = frame
                            frame = bitmap
                            invalidate()
                            old?.recycle()
                        } else {
                            bitmap.recycle()
                        }
                    }
                    zoom *= 0.985
                    if (zoom < 0.00000025) zoom = 2.8
                    Thread.sleep(12)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    Thread.sleep(100)
                }
            }
        }, "MandelbrotRenderer").apply { isDaemon = true; start() }
    }

    private fun stopRenderer() {
        running = false
        worker?.interrupt()
        worker = null
        frame?.recycle()
        frame = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = frame ?: return
        rect.set(0, 0, width, height)
        canvas.drawBitmap(bitmap, null, rect, imagePaint)
    }

    override fun onDetachedFromWindow() {
        stopRenderer()
        super.onDetachedFromWindow()
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
