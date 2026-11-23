package com.framecontrol.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: CompositionOverlayView? = null
    private lateinit var overlayParams: WindowManager.LayoutParams
    private var floatingPill: View? = null

    private var currentPresetName: String = "4:3 Camera Viewfinder"
    private var isEditMode = false

    companion object {
        const val ACTION_UPDATE = "com.framecontrol.overlay.ACTION_UPDATE"
        const val ACTION_APPLY_PRESET = "com.framecontrol.overlay.ACTION_APPLY_PRESET"
        const val ACTION_TOGGLE_RESIZE = "com.framecontrol.overlay.ACTION_TOGGLE_RESIZE"
        const val ACTION_STOP = "com.framecontrol.overlay.ACTION_STOP"

        const val EXTRA_GRID_ORDINAL = "extra_grid_ordinal"
        const val EXTRA_COLOR = "extra_color"
        const val EXTRA_ALPHA = "extra_alpha"
        const val EXTRA_STROKE = "extra_stroke"
        const val EXTRA_FLIP = "extra_flip"
        const val EXTRA_PRESET_NAME = "extra_preset_name"

        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()

        val initialState = PresetRepository.loadActiveState(this)
        currentPresetName = initialState.name
        createResizableOverlay(initialState)
        createFloatingControlPill()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APPLY_PRESET -> {
                val active = PresetRepository.loadActiveState(this)
                applyPresetToWindow(active)
            }
            ACTION_TOGGLE_RESIZE -> {
                toggleResizeMode()
            }
            ACTION_UPDATE -> {
                overlayView?.let { view ->
                    val ord = intent.getIntExtra(EXTRA_GRID_ORDINAL, view.gridType.ordinal)
                    view.gridType = GridType.values()[ord]
                    view.lineColor = intent.getIntExtra(EXTRA_COLOR, view.lineColor)
                    view.lineAlpha = intent.getIntExtra(EXTRA_ALPHA, view.lineAlpha)
                    view.strokeWidthDp = intent.getFloatExtra(EXTRA_STROKE, view.strokeWidthDp)
                    view.flipSpiral = intent.getBooleanExtra(EXTRA_FLIP, view.flipSpiral)
                    persistCurrentState()
                }
            }
        }
        return START_STICKY
    }

    private fun createResizableOverlay(preset: OverlayPreset) {
        val view = CompositionOverlayView(this).apply {
            gridType = GridType.values()[preset.gridOrdinal.coerceIn(0, GridType.values().size - 1)]
            lineColor = preset.color
            lineAlpha = preset.alpha
            strokeWidthDp = preset.strokeDp
            flipSpiral = preset.flip
        }

        overlayParams = WindowManager.LayoutParams(
            preset.width,
            preset.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preset.x
            y = preset.y
        }

        setupResizeTouchListener(view)
        overlayView = view
        windowManager.addView(view, overlayParams)
    }

    private fun setupResizeTouchListener(view: CompositionOverlayView) {
        var startX = 0
        var startY = 0
        var startW = 0
        var startH = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var dragMode = 0 // 0=move, 1=TL, 2=TR, 3=BL, 4=BR

        view.setOnTouchListener { _, event ->
            if (!isEditMode) return@setOnTouchListener false
            val density = resources.displayMetrics.density
            val cornerZone = 64f * density
            val minSize = (140 * density).toInt()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = overlayParams.x
                    startY = overlayParams.y
                    startW = overlayParams.width
                    startH = overlayParams.height
                    touchStartX = event.rawX
                    touchStartY = event.rawY

                    val lx = event.x
                    val ly = event.y
                    dragMode = when {
                        lx < cornerZone && ly < cornerZone -> 1 // Top-Left
                        lx > startW - cornerZone && ly < cornerZone -> 2 // Top-Right
                        lx < cornerZone && ly > startH - cornerZone -> 3 // Bottom-Left
                        lx > startW - cornerZone && ly > startH - cornerZone -> 4 // Bottom-Right
                        else -> 0 // Move whole frame
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()

                    when (dragMode) {
                        0 -> {
                            overlayParams.x = startX + dx
                            overlayParams.y = startY + dy
                        }
                        1 -> {
                            val newW = (startW - dx).coerceAtLeast(minSize)
                            val newH = (startH - dy).coerceAtLeast(minSize)
                            overlayParams.x = startX + (startW - newW)
                            overlayParams.y = startY + (startH - newH)
                            overlayParams.width = newW
                            overlayParams.height = newH
                        }
                        2 -> {
                            val newW = (startW + dx).coerceAtLeast(minSize)
                            val newH = (startH - dy).coerceAtLeast(minSize)
                            overlayParams.y = startY + (startH - newH)
                            overlayParams.width = newW
                            overlayParams.height = newH
                        }
                        3 -> {
                            val newW = (startW - dx).coerceAtLeast(minSize)
                            val newH = (startH + dy).coerceAtLeast(minSize)
                            overlayParams.x = startX + (startW - newW)
                            overlayParams.width = newW
                            overlayParams.height = newH
                        }
                        4 -> {
                            overlayParams.width = (startW + dx).coerceAtLeast(minSize)
                            overlayParams.height = (startH + dy).coerceAtLeast(minSize)
                        }
                    }
                    windowManager.updateViewLayout(view, overlayParams)
                    view.invalidate()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    persistCurrentState()
                    true
                }
                else -> false
            }
        }
    }

    private var resizeBtnView: TextView? = null
    private var presetBtnView: TextView? = null
    private var gridBtnView: TextView? = null

    private fun createFloatingControlPill() {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((10 * density).toInt(), (6 * density).toInt(), (10 * density).toInt(), (6 * density).toInt())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DD10141E"))
                cornerRadius = 24 * density
                setStroke((1.2f * density).toInt(), Color.parseColor("#00E5FF"))
            }
        }

        val gridBtn = TextView(this).apply {
            text = "⊞ Grid"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            setPadding(0, 0, (10 * density).toInt(), 0)
        }
        gridBtnView = gridBtn

        val presetBtn = TextView(this).apply {
            text = "★ ${currentPresetName.take(10)}"
            setTextColor(Color.parseColor("#FFD600"))
            textSize = 11.5f
            setPadding(0, 0, (10 * density).toInt(), 0)
            setOnClickListener {
                cycleNextPreset()
            }
        }
        presetBtnView = presetBtn

        val resizeBtn = TextView(this).apply {
            text = "⤢ Resize"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 11.5f
            setPadding(0, 0, (10 * density).toInt(), 0)
            setOnClickListener {
                toggleResizeMode()
            }
        }
        resizeBtnView = resizeBtn

        val saveQuickBtn = TextView(this).apply {
            text = "💾"
            textSize = 12f
            setPadding(0, 0, (10 * density).toInt(), 0)
            setOnClickListener {
                saveCurrentFrameToActivePreset()
            }
        }

        val flipBtn = TextView(this).apply {
            text = "⇄"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, (10 * density).toInt(), 0)
            setOnClickListener {
                overlayView?.let {
                    it.flipSpiral = !it.flipSpiral
                    persistCurrentState()
                }
            }
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 13f
            setOnClickListener { stopSelf() }
        }

        container.addView(gridBtn)
        container.addView(presetBtn)
        container.addView(resizeBtn)
        container.addView(saveQuickBtn)
        container.addView(flipBtn)
        container.addView(closeBtn)

        val pillParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (56 * density).toInt()
        }

        // Drag floating pill or tap "⊞ Grid" to cycle through the 10 FrameControl grids
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        gridBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = pillParams.x
                    initialY = pillParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (dx * dx + dy * dy > 25) isClick = false
                    pillParams.x = initialX + dx
                    pillParams.y = initialY + dy
                    windowManager.updateViewLayout(container, pillParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        overlayView?.let { view ->
                            val nextIdx = (view.gridType.ordinal + 1) % GridType.values().size
                            view.gridType = GridType.values()[nextIdx]
                            gridBtn.text = "⊞ ${view.gridType.title.take(8)}"
                            persistCurrentState()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        floatingPill = container
        windowManager.addView(container, pillParams)
    }

    private fun toggleResizeMode() {
        val view = overlayView ?: return
        isEditMode = !isEditMode
        view.isResizeEditMode = isEditMode

        if (isEditMode) {
            // Make overlay touchable so user can drag corners & reposition over their Camera viewfinder
            overlayParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            resizeBtnView?.text = "🔒 Lock"
            resizeBtnView?.setTextColor(Color.parseColor("#00E676"))
            Toast.makeText(this, "Resize Mode ON: Drag corners to match your camera viewfinder, then tap Lock", Toast.LENGTH_SHORT).show()
        } else {
            // Lock & restore 100% touch passthrough to Camera app underneath
            overlayParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            resizeBtnView?.text = "⤢ Resize"
            resizeBtnView?.setTextColor(Color.parseColor("#00E5FF"))
            persistCurrentState()
            Toast.makeText(this, "Frame Locked! Camera touches pass through.", Toast.LENGTH_SHORT).show()
        }
        windowManager.updateViewLayout(view, overlayParams)
    }

    private fun cycleNextPreset() {
        val presets = PresetRepository.getPresets(this)
        if (presets.isEmpty()) return
        val currentIdx = presets.indexOfFirst { it.name == currentPresetName }
        val nextPreset = presets[(currentIdx + 1) % presets.size]
        PresetRepository.saveActiveState(this, nextPreset)
        applyPresetToWindow(nextPreset)
        Toast.makeText(this, "Preset: ${nextPreset.name}", Toast.LENGTH_SHORT).show()
    }

    private fun applyPresetToWindow(preset: OverlayPreset) {
        val view = overlayView ?: return
        currentPresetName = preset.name
        presetBtnView?.text = "★ ${preset.name.take(10)}"

        view.gridType = GridType.values()[preset.gridOrdinal.coerceIn(0, GridType.values().size - 1)]
        view.lineColor = preset.color
        view.lineAlpha = preset.alpha
        view.strokeWidthDp = preset.strokeDp
        view.flipSpiral = preset.flip

        overlayParams.x = preset.x
        overlayParams.y = preset.y
        overlayParams.width = preset.width
        overlayParams.height = preset.height
        windowManager.updateViewLayout(view, overlayParams)
        view.invalidate()
    }

    private fun persistCurrentState() {
        val view = overlayView ?: return
        val updated = OverlayPreset(
            name = currentPresetName,
            x = overlayParams.x,
            y = overlayParams.y,
            width = overlayParams.width,
            height = overlayParams.height,
            gridOrdinal = view.gridType.ordinal,
            color = view.lineColor,
            alpha = view.lineAlpha,
            strokeDp = view.strokeWidthDp,
            flip = view.flipSpiral
        )
        PresetRepository.saveActiveState(this, updated)
    }

    private fun saveCurrentFrameToActivePreset() {
        val view = overlayView ?: return
        val updated = OverlayPreset(
            name = currentPresetName,
            x = overlayParams.x,
            y = overlayParams.y,
            width = overlayParams.width,
            height = overlayParams.height,
            gridOrdinal = view.gridType.ordinal,
            color = view.lineColor,
            alpha = view.lineAlpha,
            strokeDp = view.strokeWidthDp,
            flip = view.flipSpiral
        )
        PresetRepository.savePreset(this, updated)
        Toast.makeText(this, "Saved size & grid to preset '$currentPresetName'", Toast.LENGTH_SHORT).show()
    }

    private fun startForegroundNotification() {
        val channelId = "framecontrol_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FrameControl Camera Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("FrameControl Overlay v2 Active")
            .setContentText("Tap ⤢ Resize on pill to fit your camera viewfinder")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Overlay", stopPending)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        isRunning = false
        overlayView?.let { windowManager.removeView(it) }
        floatingPill?.let { windowManager.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
