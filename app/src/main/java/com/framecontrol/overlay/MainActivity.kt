package com.framecontrol.overlay

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var selectedGrid = GridType.RULE_OF_THIRDS
    private var selectedColor = Color.parseColor("#00E5FF")
    private var selectedAlpha = 200
    private var selectedStroke = 1.8f
    private var flipSpiral = false

    private lateinit var presetsContainer: LinearLayout
    private lateinit var presetNameInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val active = PresetRepository.loadActiveState(this)
        selectedGrid = GridType.values()[active.gridOrdinal.coerceIn(0, GridType.values().size - 1)]
        selectedColor = active.color
        selectedAlpha = active.alpha
        selectedStroke = active.strokeDp
        flipSpiral = active.flip

        val density = resources.displayMetrics.density
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D1117"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (18 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "FrameControl Overlay v2"
            textSize = 23f
            setTextColor(Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "Resizable Viewfinder Overlay + Custom Presets for any Android Camera App"
            textSize = 13f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, (4 * density).toInt(), 0, (16 * density).toInt())
        }

        // Launch / Stop + Live Resize Mode row
        val topActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val toggleBtn = Button(this).apply {
            text = if (OverlayService.isRunning) "STOP OVERLAY" else "START OVERLAY"
            setBackgroundColor(Color.parseColor("#00E5FF"))
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (6 * density).toInt()
            }
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(
                        this@MainActivity,
                        "Please grant 'Appear on top' permission first",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                    )
                } else {
                    if (OverlayService.isRunning) {
                        stopService(Intent(this@MainActivity, OverlayService::class.java))
                        text = "START OVERLAY"
                    } else {
                        pushOverlayUpdate(startIfNotRunning = true)
                        text = "STOP OVERLAY"
                    }
                }
            }
        }

        val resizeModeBtn = Button(this).apply {
            text = "⤢ RESIZE ON SCREEN"
            setBackgroundColor(Color.parseColor("#FFD600"))
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (!OverlayService.isRunning) {
                    pushOverlayUpdate(startIfNotRunning = true)
                    toggleBtn.text = "STOP OVERLAY"
                }
                val intent = Intent(this@MainActivity, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_TOGGLE_RESIZE
                }
                startService(intent)
            }
        }

        topActionRow.addView(toggleBtn)
        topActionRow.addView(resizeModeBtn)

        // SECTION 1: SAVED PRESETS & CUSTOM PRESET CREATOR
        val presetHeader = TextView(this).apply {
            text = "1. Saved Viewfinder & Grid Presets"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, (20 * density).toInt(), 0, (6 * density).toInt())
        }

        val presetHint = TextView(this).apply {
            text = "Resize the overlay over your camera's viewfinder using '⤢ Resize', then save it as a preset below so you can recall that exact frame size & grid in 1 tap."
            textSize = 12f
            setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 0, 0, (10 * density).toInt())
        }

        val saveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        presetNameInput = EditText(this).apply {
            hint = "Preset name (e.g., S25 4:3 Photo, Pro Video)"
            setHintTextColor(Color.parseColor("#6E7681"))
            setTextColor(Color.WHITE)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = (8 * density).toInt()
            }
        }

        val savePresetBtn = Button(this).apply {
            text = "SAVE PRESET"
            setBackgroundColor(Color.parseColor("#238636"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val rawName = presetNameInput.text.toString().trim()
                val name = if (rawName.isEmpty()) "Custom ${System.currentTimeMillis() % 1000}" else rawName
                val currentActive = PresetRepository.loadActiveState(this@MainActivity)
                val newPreset = currentActive.copy(
                    name = name,
                    gridOrdinal = selectedGrid.ordinal,
                    color = selectedColor,
                    alpha = selectedAlpha,
                    strokeDp = selectedStroke,
                    flip = flipSpiral
                )
                PresetRepository.savePreset(this@MainActivity, newPreset)
                presetNameInput.setText("")
                refreshPresetsList()
                Toast.makeText(this@MainActivity, "Saved preset '$name'!", Toast.LENGTH_SHORT).show()
            }
        }

        saveRow.addView(presetNameInput)
        saveRow.addView(savePresetBtn)

        presetsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (8 * density).toInt(), 0, 0)
        }
        refreshPresetsList()

        // SECTION 2: SELECT COMPOSITION GUIDE
        val gridSectionTitle = TextView(this).apply {
            text = "2. Select FrameControl Composition Guide"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, (18 * density).toInt(), 0, (8 * density).toInt())
        }

        val radioGroup = RadioGroup(this)
        GridType.values().forEachIndexed { index, grid ->
            val rb = RadioButton(this).apply {
                id = index
                text = "${grid.title}  (${grid.category})"
                setTextColor(Color.parseColor("#E6EDF3"))
                isChecked = (grid == selectedGrid)
                setOnClickListener {
                    selectedGrid = grid
                    pushOverlayUpdate()
                }
            }
            radioGroup.addView(rb)
        }

        val flipCheck = CheckBox(this).apply {
            text = "Flip / Mirror Orientation (Spiral, S-Curve, Dutch Tilt)"
            setTextColor(Color.parseColor("#8B949E"))
            isChecked = flipSpiral
            setOnCheckedChangeListener { _, isChecked ->
                flipSpiral = isChecked
                pushOverlayUpdate()
            }
        }

        // SECTION 3: COLOR & OPACITY
        val colorTitle = TextView(this).apply {
            text = "3. Overlay Line Color & Opacity"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, (16 * density).toInt(), 0, (8 * density).toInt())
        }

        val colorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val colors = listOf(
            "Cyan" to "#00E5FF",
            "Gold" to "#FFD600",
            "Green" to "#00E676",
            "Red" to "#FF5252",
            "White" to "#FFFFFF"
        )

        colors.forEach { (name, hex) ->
            val btn = Button(this).apply {
                text = name
                textSize = 11f
                setBackgroundColor(Color.parseColor(hex))
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    rightMargin = (4 * density).toInt()
                }
                setOnClickListener {
                    selectedColor = Color.parseColor(hex)
                    pushOverlayUpdate()
                }
            }
            colorRow.addView(btn)
        }

        val alphaSeek = SeekBar(this).apply {
            max = 255
            progress = selectedAlpha
            setPadding(0, (12 * density).toInt(), 0, (24 * density).toInt())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedAlpha = progress.coerceAtLeast(30)
                    pushOverlayUpdate()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        container.addView(title)
        container.addView(subtitle)
        container.addView(topActionRow)
        container.addView(presetHeader)
        container.addView(presetHint)
        container.addView(saveRow)
        container.addView(presetsContainer)
        container.addView(gridSectionTitle)
        container.addView(radioGroup)
        container.addView(flipCheck)
        container.addView(colorTitle)
        container.addView(colorRow)
        container.addView(alphaSeek)

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    override fun onResume() {
        super.onResume()
        refreshPresetsList()
    }

    private fun refreshPresetsList() {
        presetsContainer.removeAllViews()
        val density = resources.displayMetrics.density
        val presets = PresetRepository.getPresets(this)

        presets.forEach { preset ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = (10 * density).toInt()
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#161B22"))
                    cornerRadius = 10 * density
                    setStroke((1 * density).toInt(), Color.parseColor("#30363D"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (8 * density).toInt()
                }
            }

            val gridName = GridType.values()[preset.gridOrdinal.coerceIn(0, GridType.values().size - 1)].title
            val infoText = TextView(this).apply {
                text = "${preset.name}
${preset.width}x${preset.height}px • $gridName"
                setTextColor(Color.WHITE)
                textSize = 12.5f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val applyBtn = Button(this).apply {
                text = "LOAD"
                textSize = 11f
                setBackgroundColor(Color.parseColor("#1F6FEB"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    PresetRepository.saveActiveState(this@MainActivity, preset)
                    selectedGrid = GridType.values()[preset.gridOrdinal.coerceIn(0, GridType.values().size - 1)]
                    selectedColor = preset.color
                    selectedAlpha = preset.alpha
                    selectedStroke = preset.strokeDp
                    flipSpiral = preset.flip

                    val intent = Intent(this@MainActivity, OverlayService::class.java).apply {
                        action = OverlayService.ACTION_APPLY_PRESET
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    Toast.makeText(this@MainActivity, "Loaded preset: ${preset.name}", Toast.LENGTH_SHORT).show()
                }
            }

            val delBtn = Button(this).apply {
                text = "✕"
                textSize = 11f
                setBackgroundColor(Color.parseColor("#30363D"))
                setTextColor(Color.parseColor("#FF7B72"))
                layoutParams = LinearLayout.LayoutParams(
                    (44 * density).toInt(),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = (6 * density).toInt()
                }
                setOnClickListener {
                    PresetRepository.deletePreset(this@MainActivity, preset.name)
                    refreshPresetsList()
                }
            }

            row.addView(infoText)
            row.addView(applyBtn)
            row.addView(delBtn)
            presetsContainer.addView(row)
        }
    }

    private fun pushOverlayUpdate(startIfNotRunning: Boolean = false) {
        if (!OverlayService.isRunning && !startIfNotRunning) return
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_UPDATE
            putExtra(OverlayService.EXTRA_GRID_ORDINAL, selectedGrid.ordinal)
            putExtra(OverlayService.EXTRA_COLOR, selectedColor)
            putExtra(OverlayService.EXTRA_ALPHA, selectedAlpha)
            putExtra(OverlayService.EXTRA_STROKE, selectedStroke)
            putExtra(OverlayService.EXTRA_FLIP, flipSpiral)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
