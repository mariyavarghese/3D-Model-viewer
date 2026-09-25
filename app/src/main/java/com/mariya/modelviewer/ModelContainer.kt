package com.mariya.modelviewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.filament.Colors
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import java.nio.ByteBuffer

@SuppressLint("ViewConstructor")
class ModelContainer(
    context: Context,
    val modelItem: ModelItem,
    val modelBuffer: ByteBuffer,
    val onClose: (ModelContainer) -> Unit
) : FrameLayout(context) {

    var isInteractionMode = false
        private set
    var showLabels = false
        private set

    private val density = context.resources.displayMetrics.density

    var currentX = 20f * density
    var currentY = 80f * density
    var currentWidth = (300 * density).toInt()
    var currentHeight = (330 * density).toInt()

    // Touch handling state
    private var activePointerId = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Parsed label metadata from glTF
    private val parsedLabels: List<GltfParser.LabelInfo> = GltfParser.parseLabels(modelBuffer)
    private val projectedLabels = parsedLabels.map {
        LabelOverlayView.ProjectedLabel(it.text)
    }

    // Pre-allocated projection buffers for zero-allocation per frame
    private val viewMatrix = FloatArray(16)
    private val projMatrixD = DoubleArray(16)
    private val projMatrix = FloatArray(16)
    private val viewProjMatrix = FloatArray(16)
    private val worldMatrix = FloatArray(16)
    private val clipPosBuffer = FloatArray(4)
    private val worldPosBuffer = FloatArray(4)
    private val outScreenBuffer = FloatArray(2)

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!isInteractionMode) {
                    val factor = detector.scaleFactor
                    val minW = (220 * density).toInt()
                    val maxW = (650 * density).toInt()
                    val minH = (240 * density).toInt()
                    val maxH = (700 * density).toInt()

                    val newW = (currentWidth * factor).toInt().coerceIn(minW, maxW)
                    val newH = (currentHeight * factor).toInt().coerceIn(minH, maxH)

                    if (newW != currentWidth || newH != currentHeight) {
                        currentWidth = newW
                        currentHeight = newH
                        layoutParams?.let {
                            it.width = currentWidth
                            it.height = currentHeight
                            requestLayout()
                        }
                    }
                    return true
                }
                return false
            }
        }
    )

    private val textureView = TextureView(context)
    private val labelOverlayView = LabelOverlayView(context)

    private var _modelViewer: ModelViewer? = null
    val modelViewer: ModelViewer?
        get() = _modelViewer

    // Additional Filament lighting and skybox entities
    private var skybox: Skybox? = null
    private var indirectLight: IndirectLight? = null
    private var fillLightEntity: Int = 0
    private var rimLightEntity: Int = 0

    // UI Controls
    private val btnInteract: TextView
    private val btnLabels: TextView
    private val btnClose: TextView
    private val tvTitle: TextView
    private val topBar: LinearLayout

    private val normalBackground: GradientDrawable
    private val activeBackground: GradientDrawable

    init {
        // Prepare Card Backgrounds
        val cornerRadius = 18f * density
        normalBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setCornerRadius(cornerRadius)
            setColor(Color.parseColor("#0F172A")) // Solid dark slate background
            setStroke((1.5f * density).toInt(), Color.parseColor("#4D94A3B8")) // Subtle slate border
        }

        activeBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setCornerRadius(cornerRadius)
            setColor(Color.parseColor("#0F172A"))
            setStroke((2.5f * density).toInt(), Color.parseColor("#38BDF8")) // Glowing Cyan border in interaction mode
        }

        background = normalBackground
        clipToOutline = true
        elevation = 8f * density

        // 1. TextureView for 3D Filament Rendering
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 2. 2D Label Overlay on top of 3D TextureView
        labelOverlayView.isOverlayEnabled = false
        labelOverlayView.setLabels(projectedLabels)
        addView(labelOverlayView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 3. ModelViewer setup
        val mv = ModelViewer(textureView)
        _modelViewer = mv

        setupLightingAndSkybox(mv)

        // Load 3D model
        mv.loadModelGlb(modelBuffer)
        mv.transformToUnitCube()

        // 4. Always-Visible Two-Row Top Controls Bar
        tvTitle = TextView(context).apply {
            text = "${modelItem.iconEmoji} ${modelItem.title}"
            setTextColor(Color.parseColor("#F8FAFC"))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setSingleLine(true)
            setPadding((10 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (6 * density).toInt())
        }

        btnClose = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            background = createButtonDrawable(Color.parseColor("#B3DC2626"), Color.parseColor("#EF4444"), 8f)
            setOnClickListener {
                onClose(this@ModelContainer)
            }
        }

        btnInteract = TextView(context).apply {
            text = "Normal"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 11.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (7 * density).toInt(), (10 * density).toInt(), (7 * density).toInt())
            background = createButtonDrawable(Color.parseColor("#801E293B"), Color.parseColor("#64748B"), 8f)
            setOnClickListener {
                toggleInteractionMode()
            }
        }

        btnLabels = TextView(context).apply {
            text = "Labels: OFF"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 11.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding((10 * density).toInt(), (7 * density).toInt(), (10 * density).toInt(), (7 * density).toInt())
            background = createButtonDrawable(Color.parseColor("#801E293B"), Color.parseColor("#64748B"), 8f)
            setOnClickListener {
                toggleLabelMode()
            }
        }

        // Top Row: Title + Close Button
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val titleLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(tvTitle, titleLp)
            addView(btnClose, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        // Bottom Controls Row: Interaction Toggle + Label Toggle
        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val btnLp1 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (6 * density).toInt()
            }
            val btnLp2 = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(btnInteract, btnLp1)
            addView(btnLabels, btnLp2)
        }

        topBar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (6 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(14f * density)
                setColor(Color.parseColor("#CC0F172A")) // Translucent dark header overlay
            }

            addView(topRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            val bRowLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (4 * density).toInt()
            }
            addView(buttonsRow, bRowLp)
        }

        val topBarLp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), 0)
        }
        addView(topBar, topBarLp)

        updateLayoutPosition()
    }

    private fun setupLightingAndSkybox(mv: ModelViewer) {
        val engine = mv.engine
        val scene = mv.scene
        val em = EntityManager.get()

        // 1. Skybox with dark background (matches #0F172A slate theme for clean artifact-free rendering)
        val sb = Skybox.Builder()
            .color(0.06f, 0.09f, 0.16f, 1.0f)
            .build(engine)
        scene.skybox = sb
        skybox = sb

        // 2. Ambient Spherical Harmonics (IBL) for bright, clear diffuse & specular shading on all 3D materials
        val sh = floatArrayOf(
            0.75f, 0.75f, 0.78f,
            0.15f, 0.15f, 0.18f,
            0.20f, 0.20f, 0.22f,
            0.15f, 0.15f, 0.18f,
            0.05f, 0.05f, 0.06f,
            0.05f, 0.05f, 0.06f,
            0.08f, 0.08f, 0.10f,
            0.05f, 0.05f, 0.06f,
            0.05f, 0.05f, 0.06f
        )
        val ibl = IndirectLight.Builder()
            .irradiance(3, sh)
            .intensity(45_000.0f)
            .build(engine)
        scene.indirectLight = ibl
        indirectLight = ibl

        // 3. Adjust primary directional light to point from top-front-right
        val lm = engine.lightManager
        val lightInst = lm.getInstance(mv.light)
        if (lightInst != 0) {
            lm.setDirection(lightInst, 0.5f, -0.7f, -0.6f)
            lm.setIntensity(lightInst, 120_000.0f)
            val (r, g, b) = Colors.cct(6_500.0f)
            lm.setColor(lightInst, r, g, b)
        }

        // 4. Add Fill Light from bottom-front-left to illuminate dark underside shadows
        fillLightEntity = em.create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.9f, 0.95f, 1.0f)
            .intensity(65_000.0f)
            .direction(-0.5f, 0.5f, -0.6f)
            .castShadows(false)
            .build(engine, fillLightEntity)
        scene.addEntity(fillLightEntity)

        // 5. Add Rim Light from back to highlight contours
        rimLightEntity = em.create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(50_000.0f)
            .direction(0.0f, 0.3f, 1.0f)
            .castShadows(false)
            .build(engine, rimLightEntity)
        scene.addEntity(rimLightEntity)
    }

    private fun createButtonDrawable(bgColor: Int, strokeColor: Int, radiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setCornerRadius(radiusDp * density)
            setColor(bgColor)
            setStroke((1f * density).toInt(), strokeColor)
        }
    }

    private fun toggleInteractionMode() {
        isInteractionMode = !isInteractionMode
        if (isInteractionMode) {
            btnInteract.text = "Interact"
            btnInteract.setTextColor(Color.WHITE)
            btnInteract.background = createButtonDrawable(Color.parseColor("#0284C7"), Color.parseColor("#38BDF8"), 8f)
            background = activeBackground
        } else {
            btnInteract.text = "Normal"
            btnInteract.setTextColor(Color.parseColor("#E2E8F0"))
            btnInteract.background = createButtonDrawable(Color.parseColor("#801E293B"), Color.parseColor("#64748B"), 8f)
            background = normalBackground
        }
    }

    private fun toggleLabelMode() {
        showLabels = !showLabels
        labelOverlayView.isOverlayEnabled = showLabels
        if (showLabels) {
            btnLabels.text = "Labels: ON"
            btnLabels.setTextColor(Color.WHITE)
            btnLabels.background = createButtonDrawable(Color.parseColor("#059669"), Color.parseColor("#10B981"), 8f)
            updateLabels()
        } else {
            btnLabels.text = "Labels: OFF"
            btnLabels.setTextColor(Color.parseColor("#E2E8F0"))
            btnLabels.background = createButtonDrawable(Color.parseColor("#801E293B"), Color.parseColor("#64748B"), 8f)
            labelOverlayView.hideAll()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val topBarL = topBar.left
        val topBarT = topBar.top
        val topBarR = topBar.right
        val topBarB = topBar.bottom

        if (ev.x >= topBarL && ev.x <= topBarR && ev.y >= topBarT && ev.y <= topBarB) {
            return false
        }
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val topBarL = topBar.left
        val topBarT = topBar.top
        val topBarR = topBar.right
        val topBarB = topBar.bottom

        if (event.x >= topBarL && event.x <= topBarR && event.y >= topBarT && event.y <= topBarB && event.actionMasked == MotionEvent.ACTION_DOWN) {
            return false
        }

        if (isInteractionMode) {
            // Mode 2: Interaction Mode -> 1-finger drag orbits 3D model, 2-finger pinch zooms 3D model
            // Container position and size stay strictly unchanged
            _modelViewer?.onTouchEvent(event)
            return true
        }

        // Mode 1: Normal Mode -> 1-finger drag moves container, 2-finger pinch resizes container
        // 3D model does NOT rotate or zoom
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                bringToFront()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val rx = event.rawX
                        val ry = event.rawY
                        val dx = rx - lastTouchX
                        val dy = ry - lastTouchY

                        currentX += dx
                        currentY += dy
                        lastTouchX = rx
                        lastTouchY = ry

                        updateLayoutPosition()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    if (newPointerIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(newPointerIndex)
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
            }
        }
        return true
    }

    /**
     * Renders a 3D frame and updates the projected 2D part labels.
     */
    fun renderFrame(frameTimeNanos: Long) {
        val mv = _modelViewer ?: return
        mv.render(frameTimeNanos)

        if (showLabels) {
            updateLabels()
        }
    }

    /**
     * Projects 3D node coordinates through the camera every frame to keep 2D labels glued to parts.
     */
    fun updateLabels() {
        val mv = _modelViewer ?: return
        val asset = mv.asset ?: return
        if (!showLabels || parsedLabels.isEmpty()) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        val tm = mv.engine.transformManager
        val camera = mv.camera

        // 1. Get Camera Matrices
        camera.getViewMatrix(viewMatrix)
        camera.getProjectionMatrix(projMatrixD)
        for (i in 0 until 16) {
            projMatrix[i] = projMatrixD[i].toFloat()
        }

        // 2. Compute View-Projection Matrix: VP = P * V
        MatrixUtils.multiplyMM(viewProjMatrix, projMatrix, viewMatrix)

        val entities = asset.entities

        // 3. Project Each Label Node
        for (i in parsedLabels.indices) {
            val label = parsedLabels[i]
            val projected = projectedLabels[i]

            val entity = if (label.nodeIndex in entities.indices) {
                entities[label.nodeIndex]
            } else {
                asset.getFirstEntityByName(label.name)
            }

            var hasWorldPos = false
            var worldX = 0f
            var worldY = 0f
            var worldZ = 0f

            if (entity != 0) {
                val instance = tm.getInstance(entity)
                if (instance != 0) {
                    tm.getWorldTransform(instance, worldMatrix)
                    worldX = worldMatrix[12]
                    worldY = worldMatrix[13]
                    worldZ = worldMatrix[14]
                    hasWorldPos = true
                }
            }

            if (!hasWorldPos) {
                projected.isVisible = false
                continue
            }

            // 4. Project 3D World Position to 2D Container Screen Coordinates
            val isVisible = MatrixUtils.projectWorldToScreen(
                worldX,
                worldY,
                worldZ,
                viewProjMatrix,
                viewW,
                viewH,
                clipPosBuffer,
                worldPosBuffer,
                outScreenBuffer
            )

            if (isVisible) {
                projected.anchorX = outScreenBuffer[0]
                projected.anchorY = outScreenBuffer[1]
                projected.isVisible = true
            } else {
                projected.isVisible = false
            }
        }

        labelOverlayView.postInvalidateOnAnimation()
    }

    fun destroy() {
        labelOverlayView.hideAll()
        val mv = _modelViewer
        if (mv != null) {
            try {
                val sb = skybox
                if (sb != null) {
                    mv.engine.destroySkybox(sb)
                    skybox = null
                }
                val ibl = indirectLight
                if (ibl != null) {
                    mv.engine.destroyIndirectLight(ibl)
                    indirectLight = null
                }
                if (fillLightEntity != 0) {
                    mv.scene.removeEntity(fillLightEntity)
                    mv.engine.destroyEntity(fillLightEntity)
                    EntityManager.get().destroy(fillLightEntity)
                    fillLightEntity = 0
                }
                if (rimLightEntity != 0) {
                    mv.scene.removeEntity(rimLightEntity)
                    mv.engine.destroyEntity(rimLightEntity)
                    EntityManager.get().destroy(rimLightEntity)
                    rimLightEntity = 0
                }
            } catch (e: Exception) {
                // Ignore cleanup errors on engine destroy
            }
            _modelViewer = null
        }
    }

    private fun updateLayoutPosition() {
        x = currentX
        y = currentY
    }
}
