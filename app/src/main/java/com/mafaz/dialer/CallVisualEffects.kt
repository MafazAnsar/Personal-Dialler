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
