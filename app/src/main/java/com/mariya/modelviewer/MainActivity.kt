package com.mariya.modelviewer

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.filament.utils.Utils
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.InputStream
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity() {

    companion object {
        init {
            Utils.init()
        }
    }

    private lateinit var rootContainer: FrameLayout
    private lateinit var modelCanvas: FrameLayout
    private lateinit var choreographer: Choreographer
    private lateinit var tvModelCounter: TextView
    private lateinit var btnClearAll: TextView
    private lateinit var emptyStateView: LinearLayout

    private val containers = mutableListOf<ModelContainer>()
    private val modelCache = mutableMapOf<String, ByteBuffer>()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(currentTime: Long) {
            choreographer.postFrameCallback(this)

            val active = containers.toList()
            for (container in active) {
                try {
                    container.renderFrame(currentTime)
                } catch (e: Exception) {
                    // Suppress transient render errors
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        choreographer = Choreographer.getInstance()

        // Root container with dark futuristic background
        rootContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0B0F19")) // Deep Space Dark
        }
        setContentView(rootContainer)

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Canvas for 3D model containers
        modelCanvas = FrameLayout(this)
        rootContainer.addView(
            modelCanvas,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setupEmptyState()
        setupTopHeader()
        setupBottomControls()

        // Load 1 initial model for a fast, responsive first-run experience
//        rootContainer.post {
//            if (containers.isEmpty()) {
//                addModel(ModelItem.ALL_MODELS[0]) // Bulb
//            }
//        }
    }

    private fun setupEmptyState() {
        val density = resources.displayMetrics.density
        emptyStateView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())


            val tvSub = TextView(this@MainActivity).apply {
                text = "Tap '+ Add 3D Model' below to load interactive models"
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
            }

            addView(tvSub)
        }

        val emptyLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        rootContainer.addView(emptyStateView, emptyLp)
    }

    private fun setupTopHeader() {
        val density = resources.displayMetrics.density

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (10 * density).toInt(), (16 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#E60F172A")) // Translucent dark header
            }

            val titleCol = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val tvAppTitle = TextView(this@MainActivity).apply {
                text = "3D Model Viewer"
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val tvSubTitle = TextView(this@MainActivity).apply {
                text = "Multi-Container 3D Canvas"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
            }

            val titleLayout = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(tvAppTitle)
                addView(tvSubTitle)
            }

            tvModelCounter = TextView(this@MainActivity).apply {
                text = "0 Models"
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 12f * density
                    setColor(Color.parseColor("#2638BDF8"))
                    setStroke((1 * density).toInt(), Color.parseColor("#38BDF8"))
                }
            }

            btnClearAll = TextView(this@MainActivity).apply {
                text = "Clear All"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                visibility = View.GONE
                setPadding((10 * density).toInt(), (4 * density).toInt(), (10 * density).toInt(), (4 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 12f * density
                    setColor(Color.parseColor("#26EF4444"))
                    setStroke((1 * density).toInt(), Color.parseColor("#EF4444"))
                }
                setOnClickListener {
                    clearAllModels()
                }
            }

            val countLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (8 * density).toInt()
            }

            addView(titleLayout, titleCol)
            addView(tvModelCounter, countLp)
            addView(btnClearAll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val headerLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        )
        rootContainer.addView(headerLayout, headerLp)
    }

    private fun setupBottomControls() {
        val density = resources.displayMetrics.density

        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (14 * density).toInt())

            val btnAdd = TextView(this@MainActivity).apply {
                text = "＋ Add 3D Model"
                setTextColor(Color.WHITE)
                textSize = 13.5f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding((18 * density).toInt(), (11 * density).toInt(), (18 * density).toInt(), (11 * density).toInt())
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#4F46E5"), Color.parseColor("#06B6D4"))
                ).apply {
                    cornerRadius = 24f * density
                }
                elevation = 8f * density
                setOnClickListener {
                    showModelSelectionSheet()
                }
            }

            val btnLoadAll = TextView(this@MainActivity).apply {
                text = "Load All Models"
                setTextColor(Color.parseColor("#E2E8F0"))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding((14 * density).toInt(), (11 * density).toInt(), (14 * density).toInt(), (11 * density).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 24f * density
                    setColor(Color.parseColor("#1E293B"))
                    setStroke((1 * density).toInt(), Color.parseColor("#475569"))
                }
                setOnClickListener {
                    loadAllModels()
                }
            }

//

            val btnAddLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (8 * density).toInt()
            }
            val btnLoadAllLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (8 * density).toInt()
            }

            addView(btnAdd, btnAddLp)
            addView(btnLoadAll, btnLoadAllLp)
        }

        val bottomLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        rootContainer.addView(bottomBar, bottomLp)
    }

    private fun showModelSelectionSheet() {
        val density = resources.displayMetrics.density
        val dialog = BottomSheetDialog(this)

        val sheetLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (24 * density).toInt())

            val tvTitle = TextView(this@MainActivity).apply {
                text = "Select a 3D Model"
                setTextColor(Color.WHITE)
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val tvSubtitle = TextView(this@MainActivity).apply {
                text = "Each model supports independent movement, resizing, orbit, and part labels"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                setPadding(0, (2 * density).toInt(), 0, (14 * density).toInt())
            }

            addView(tvTitle)
            addView(tvSubtitle)

            for (model in ModelItem.ALL_MODELS) {
                val isAlreadyLoaded = containers.any { it.modelItem.id == model.id }
                val itemCard = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                    background = GradientDrawable().apply {
                        cornerRadius = 12f * density
                        setColor(Color.parseColor("#1E293B"))
                        setStroke((1 * density).toInt(), Color.parseColor("#334155"))
                    }

                    val infoLayout = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        val nameTv = TextView(this@MainActivity).apply {
                            text = model.title
                            setTextColor(Color.WHITE)
                            textSize = 15f
                            setTypeface(typeface, android.graphics.Typeface.BOLD)
                        }
                        val descTv = TextView(this@MainActivity).apply {
                            text = "${model.subtitle} • ${model.labelCountDescription}"
                            setTextColor(Color.parseColor("#94A3B8"))
                            textSize = 12f
                        }
                        addView(nameTv)
                        addView(descTv)
                    }

                    val infoLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                    val tvAddBtn = TextView(this@MainActivity).apply {
                        text = if (isAlreadyLoaded) "✓ Added" else "＋ Add"
                        val btnColor = if (isAlreadyLoaded) Color.parseColor("#10B981") else Color.parseColor("#38BDF8")
                        setTextColor(btnColor)
                        textSize = 12f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
                        background = GradientDrawable().apply {
                            cornerRadius = 8f * density
                            setColor(if (isAlreadyLoaded) Color.parseColor("#1A10B981") else Color.parseColor("#1A38BDF8"))
                            setStroke((1 * density).toInt(), btnColor)
                        }
                    }

                    addView(infoLayout, infoLp)
                    addView(tvAddBtn)

                    setOnClickListener {
                        dialog.dismiss()
                        if (isAlreadyLoaded) {
                            Toast.makeText(this@MainActivity, "${model.title} is already on screen", Toast.LENGTH_SHORT).show()
                        } else {
                            addModel(model)
                        }
                    }
                }

                val cardLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (10 * density).toInt()
                }
                addView(itemCard, cardLp)
            }
        }

        dialog.setContentView(sheetLayout)
        dialog.show()
    }

    private fun addModel(modelItem: ModelItem) {
        if (containers.any { it.modelItem.id == modelItem.id }) {
            Toast.makeText(this, "${modelItem.title} is already loaded", Toast.LENGTH_SHORT).show()
            return
        }

        val density = resources.displayMetrics.density
        val screenW = modelCanvas.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val screenH = modelCanvas.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val initialW = (300 * density).toInt().coerceAtMost(screenW - (20 * density).toInt())
        val initialH = (330 * density).toInt().coerceAtMost(screenH - (120 * density).toInt())

        val modelBuffer = getModelBuffer(modelItem.fileName)

        val container = ModelContainer(
            context = this,
            modelItem = modelItem,
            modelBuffer = modelBuffer
        ) { closedContainer ->
            removeModelContainer(closedContainer)
        }

        container.currentWidth = initialW
        container.currentHeight = initialH

        // Stagger positions so multiple models don't completely overlap
        val count = containers.size
        val stepX = (30 * density).toInt()
        val stepY = (40 * density).toInt()
        val maxX = (screenW - initialW - (20 * density).toInt()).coerceAtLeast(10)
        val maxY = (screenH - initialH - (160 * density).toInt()).coerceAtLeast(60)

        container.currentX = (20 * density + (count * stepX) % maxX)
        container.currentY = (70 * density + (count * stepY) % maxY)

        val lp = FrameLayout.LayoutParams(initialW, initialH)
        modelCanvas.addView(container, lp)
        containers.add(container)

        updateUiState()
    }

    private fun loadAllModels() {
        val loadedIds = containers.map { it.modelItem.id }.toSet()
        val modelsToAdd = ModelItem.ALL_MODELS.filter { it.id !in loadedIds }
        if (modelsToAdd.isEmpty()) {
            Toast.makeText(this, "All 5 models are already on canvas", Toast.LENGTH_SHORT).show()
            return
        }
        for (model in modelsToAdd) {
            addModel(model)
        }
        Toast.makeText(this, "Loaded all 5 models on canvas", Toast.LENGTH_SHORT).show()
    }

    private fun clearAllModels() {
        for (container in containers.toList()) {
            removeModelContainer(container)
        }
    }

    private fun removeModelContainer(container: ModelContainer) {
        modelCanvas.removeView(container)
        containers.remove(container)
        container.destroy()
        updateUiState()
    }

    private fun updateUiState() {
        val count = containers.size
        tvModelCounter.text = "$count ${if (count == 1) "Model" else "Models"}"
        btnClearAll.visibility = if (count > 0) View.VISIBLE else View.GONE
        emptyStateView.visibility = if (count == 0) View.VISIBLE else View.GONE
    }

    private fun getModelBuffer(fileName: String): ByteBuffer {
        return modelCache.getOrPut(fileName) {
            readAssetToDirectBuffer("models/$fileName")
        }.duplicate().apply { position(0) }
    }

    private fun readAssetToDirectBuffer(assetPath: String): ByteBuffer {
        val inputStream: InputStream = assets.open(assetPath)
        val bytes = inputStream.readBytes()
        inputStream.close()
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes)
        buffer.position(0)
        return buffer
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameCallback)
        for (container in containers) {
            container.destroy()
        }
        containers.clear()
        modelCache.clear()
    }
}
