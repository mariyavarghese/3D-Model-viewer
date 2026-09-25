package com.mariya.modelviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * High-performance 2D overlay view for drawing part labels and connector lines
 * anchored to 3D projected coordinates.
 *
 * Implements zero-allocation rendering in onDraw() for 60fps performance on low-end devices.
 */
class LabelOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class ProjectedLabel(
        val text: String,
        var anchorX: Float = 0f,
        var anchorY: Float = 0f,
        var isVisible: Boolean = false
    )

    private val labels = mutableListOf<ProjectedLabel>()
    var isOverlayEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // Pre-allocated Paint and geometry objects to avoid GC churn in render loop
    private val density = context.resources.displayMetrics.density

    private val dotOuterRadius = 6f * density
    private val dotInnerRadius = 3.5f * density
    private val dotCoreRadius = 1.5f * density
    private val badgeCornerRadius = 6f * density
    private val badgePaddingH = 7f * density
    private val badgePaddingV = 4f * density
    private val connectorOffset = 22f * density

    private val dotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#6638BDF8") // Semi-transparent Cyan
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38BDF8") // Vibrant Cyan
    }

    private val dotCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.parseColor("#38BDF8")
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E60F172A") // Deep Slate Glassmorphism
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.parseColor("#38BDF8") // Cyan border
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8FAFC")
        textSize = 10.5f * density * context.resources.configuration.fontScale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val badgeRect = RectF()
    private val linePath = Path()

    init {
        // Overlay should not handle touch events directly
        isClickable = false
        isFocusable = false
    }

    fun setLabels(newLabels: List<ProjectedLabel>) {
        labels.clear()
        labels.addAll(newLabels)
        invalidate()
    }

    fun hideAll() {
        for (label in labels) {
            label.isVisible = false
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isOverlayEnabled || labels.isEmpty()) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0 || viewH <= 0) return

        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.descent - fontMetrics.ascent

        for (i in labels.indices) {
            val item = labels[i]
            if (!item.isVisible) continue

            val ax = item.anchorX
            val ay = item.anchorY

            // 1. Draw Anchor Pin (Glow + Solid Dot + Core)
            canvas.drawCircle(ax, ay, dotOuterRadius, dotGlowPaint)
            canvas.drawCircle(ax, ay, dotInnerRadius, dotPaint)
            canvas.drawCircle(ax, ay, dotCoreRadius, dotCorePaint)

            // Measure text for badge dimensions
            val textWidth = textPaint.measureText(item.text)
            val badgeW = textWidth + (badgePaddingH * 2)
            val badgeH = textHeight + (badgePaddingV * 2)

            // Determine badge placement: offset to right or left depending on anchor position
            val isLeftHalf = ax <= (viewW * 0.5f)
            val targetX = if (isLeftHalf) ax + connectorOffset else ax - connectorOffset - badgeW
            val targetY = ay - (badgeH * 0.7f) - (8f * density)

            // Clamp badge inside container bounds
            val left = targetX.coerceIn(4f * density, viewW - badgeW - 4f * density)
            val top = targetY.coerceIn(4f * density, viewH - badgeH - 4f * density)
            val right = left + badgeW
            val bottom = top + badgeH

            badgeRect.set(left, top, right, bottom)

            // 2. Draw Connector Line
            val lineTargetX = if (isLeftHalf) left else right
            val lineTargetY = (top + bottom) * 0.5f

            linePath.reset()
            linePath.moveTo(ax, ay)
            // Midpoint dogleg for modern blueprint look
            val midX = (ax + lineTargetX) * 0.5f
            linePath.lineTo(midX, lineTargetY)
            linePath.lineTo(lineTargetX, lineTargetY)
            canvas.drawPath(linePath, linePaint)

            // 3. Draw Badge Background and Border
            canvas.drawRoundRect(badgeRect, badgeCornerRadius, badgeCornerRadius, badgeBgPaint)
            canvas.drawRoundRect(badgeRect, badgeCornerRadius, badgeCornerRadius, badgeBorderPaint)

            // 4. Draw Label Text
            val textX = left + badgePaddingH
            val textY = top + badgePaddingV - fontMetrics.ascent
            canvas.drawText(item.text, textX, textY, textPaint)
        }
    }
}
