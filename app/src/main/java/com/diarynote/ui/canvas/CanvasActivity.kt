package com.diarynote.ui.canvas

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.diarynote.R
import com.diarynote.data.db.AppDatabase
import com.diarynote.data.model.*
import com.diarynote.data.repository.NoteRepository
import com.diarynote.databinding.ActivityCanvasBinding
import com.diarynote.util.FontManager
import com.diarynote.util.OCRHelper
import com.diarynote.util.PdfExporter
import com.google.android.material.slider.Slider
import kotlinx.coroutines.launch

class CanvasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCanvasBinding
    private lateinit var repository: NoteRepository
    private var notebookId: Long = -1
    private var pageId: Long = -1
    private var currentPageNumber: Int = 1
    private var allPages: List<Page> = emptyList()

    private val PICK_IMAGE_REQUEST = 101
    private val PICK_FONT_REQUEST = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCanvasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = NoteRepository(AppDatabase.getInstance(this))
        notebookId = intent.getLongExtra("notebookId", -1)
        pageId = intent.getLongExtra("pageId", -1)

        setupToolbar()
        setupTools()
        loadPage()
    }

    private fun setupToolbar() {
        // Undo / Redo
        binding.btnUndo.setOnClickListener { binding.drawingCanvas.undo() }
        binding.btnRedo.setOnClickListener { binding.drawingCanvas.redo() }

        // Page nav
        binding.btnPrevPage.setOnClickListener { navigatePage(-1) }
        binding.btnNextPage.setOnClickListener { navigatePage(1) }
        binding.btnAddPage.setOnClickListener { addNewPage() }

        // Export
        binding.btnExport.setOnClickListener { exportPdf() }

        // Pen button (stylus hardware button handled via onGenericMotionEvent)
        binding.btnPenOptions.setOnClickListener { showPenOptionsMenu(it) }

        // OCR / font render
        binding.btnOcr.setOnClickListener { runOCRAndRender() }

        // Custom font upload
        binding.btnFont.setOnClickListener { pickFont() }
    }

    private fun setupTools() {
        // Pen tool
        binding.btnToolPen.setOnClickListener {
            binding.drawingCanvas.currentTool = BrushType.PEN
            binding.drawingCanvas.isLassoMode = false
            updateToolUI(binding.btnToolPen)
        }

        // Eraser
        binding.btnToolEraser.setOnClickListener {
            binding.drawingCanvas.currentTool = BrushType.ERASER
            binding.drawingCanvas.isLassoMode = false
            updateToolUI(binding.btnToolEraser)
        }

        // Lasso
        binding.btnToolLasso.setOnClickListener {
            binding.drawingCanvas.isLassoMode = true
            updateToolUI(binding.btnToolLasso)
            binding.lassoActions.visibility = View.VISIBLE
        }

        // Lasso actions
        binding.btnLassoDelete.setOnClickListener {
            binding.drawingCanvas.deleteSelectedStrokes()
            binding.lassoActions.visibility = View.GONE
        }

        // Text insert
        binding.btnInsertText.setOnClickListener { showTextInsertDialog() }

        // Image insert
        binding.btnInsertImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        // Color picker
        binding.btnColor.setOnClickListener { showColorPicker() }

        // Stroke width
        binding.strokeSlider.addOnChangeListener { _, value, _ ->
            binding.drawingCanvas.currentStrokeWidth = value
        }
    }

    private fun updateToolUI(selected: View) {
        listOf(binding.btnToolPen, binding.btnToolEraser, binding.btnToolLasso).forEach {
            it.alpha = if (it == selected) 1f else 0.5f
        }
        if (selected != binding.btnToolLasso) {
            binding.lassoActions.visibility = View.GONE
        }
    }

    private fun showPenOptionsMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menuInflater.inflate(R.menu.pen_options, menu.menu)
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_pen -> {
                    binding.drawingCanvas.currentTool = BrushType.PEN
                    binding.drawingCanvas.currentStrokeWidth = 4f
                }
                R.id.action_marker -> {
                    binding.drawingCanvas.currentTool = BrushType.PEN
                    binding.drawingCanvas.currentStrokeWidth = 12f
                    binding.drawingCanvas.currentColor =
                        binding.drawingCanvas.currentColor and 0x00FFFFFF or (0x80 shl 24)
                }
                R.id.action_eraser -> {
                    binding.drawingCanvas.currentTool = BrushType.ERASER
                    binding.drawingCanvas.currentStrokeWidth = 20f
                }
            }
            true
        }
        menu.show()
    }

    private fun showColorPicker() {
        val colors = intArrayOf(
            Color.BLACK, Color.parseColor("#1565C0"), Color.RED,
            Color.parseColor("#2E7D32"), Color.parseColor("#6A1B9A"),
            Color.parseColor("#E65100"), Color.parseColor("#795548"),
            Color.DKGRAY
        )
        val colorNames = arrayOf("Black", "Blue", "Red", "Green", "Purple", "Orange", "Brown", "Gray")

        AlertDialog.Builder(this)
            .setTitle("Pick Color")
            .setItems(colorNames) { _, which ->
                binding.drawingCanvas.currentColor = colors[which]
                binding.btnColor.setBackgroundColor(colors[which])
            }
            .show()
    }

    private fun showTextInsertDialog() {
        val input = EditText(this)
        input.hint = "Type your text..."
        AlertDialog.Builder(this)
            .setTitle("Insert Text")
            .setView(input)
            .setPositiveButton("Insert") { _, _ ->
                val text = input.text.toString()
                if (text.isNotEmpty()) {
                    binding.textOverlay.addTextItem(
                        text = text,
                        x = 200f,
                        y = 200f,
                        typeface = FontManager.getCustomTypeface(this),
                        color = binding.drawingCanvas.currentColor
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runOCRAndRender() {
        val bitmap = binding.drawingCanvas.getBitmap() ?: return
        lifecycleScope.launch {
            val recognizedText = OCRHelper.recognizeHandwriting(this@CanvasActivity, bitmap)
            if (recognizedText.isNotEmpty()) {
                showOCRResultDialog(recognizedText)
            } else {
                Toast.makeText(this@CanvasActivity, "No handwriting detected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showOCRResultDialog(text: String) {
        val textView = TextView(this).apply {
            this.text = text
            FontManager.getCustomTypeface(this@CanvasActivity)?.let { typeface = it }
            textSize = 18f
            setPadding(32, 32, 32, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Recognized Text")
            .setView(textView)
            .setPositiveButton("Add to Page") { _, _ ->
                binding.textOverlay.addTextItem(
                    text = text,
                    x = 100f,
                    y = 100f,
                    typeface = FontManager.getCustomTypeface(this),
                    color = binding.drawingCanvas.currentColor
                )
            }
            .setNegativeButton("Dismiss", null)
            .show()
    }

    private fun pickFont() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "application/octet-stream"))
        }
        startActivityForResult(intent, PICK_FONT_REQUEST)
    }

    private fun loadPage() {
        lifecycleScope.launch {
            allPages = repository.getPagesForNotebook(notebookId)
            if (allPages.isEmpty()) {
                pageId = repository.addNewPage(notebookId, PageType.RULED)
                allPages = repository.getPagesForNotebook(notebookId)
            }
            val page = if (pageId != -1L) allPages.find { it.id == pageId } ?: allPages.first()
                       else allPages.first()
            displayPage(page)
        }
    }

    private fun displayPage(page: Page) {
        pageId = page.id
        currentPageNumber = page.pageNumber
        binding.drawingCanvas.pageType = page.pageType
        binding.drawingCanvas.loadStrokes(page.strokes)
        binding.tvPageNumber.text = "${page.pageNumber} / ${allPages.size}"

        // Auto-save previous page strokes on switch
        binding.drawingCanvas.onStrokeCompleted = { strokes ->
            lifecycleScope.launch {
                repository.getPageById(pageId)?.let {
                    repository.updatePage(it.copy(strokes = strokes, updatedAt = System.currentTimeMillis()))
                }
            }
        }
    }

    private fun navigatePage(direction: Int) {
        // Save current
        lifecycleScope.launch {
            saveCurrentPage()
            val currentIndex = allPages.indexOfFirst { it.id == pageId }
            val newIndex = (currentIndex + direction).coerceIn(0, allPages.size - 1)
            if (newIndex != currentIndex) displayPage(allPages[newIndex])
        }
    }

    private fun addNewPage() {
        lifecycleScope.launch {
            saveCurrentPage()
            pageId = repository.addNewPage(notebookId, PageType.RULED)
            allPages = repository.getPagesForNotebook(notebookId)
            displayPage(allPages.last())
        }
    }

    private suspend fun saveCurrentPage() {
        repository.getPageById(pageId)?.let {
            repository.updatePage(
                it.copy(
                    strokes = binding.drawingCanvas.getStrokes(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun exportPdf() {
        lifecycleScope.launch {
            saveCurrentPage()
            val pages = repository.getPagesForNotebook(notebookId)
            val notebook = repository.getNotebookById(notebookId)
            PdfExporter.export(this@CanvasActivity, notebook?.title ?: "Notebook", pages) { success, path ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this@CanvasActivity, "PDF saved: $path", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@CanvasActivity, "PDF export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        when (requestCode) {
            PICK_IMAGE_REQUEST -> {
                data?.data?.let { uri ->
                    binding.textOverlay.addImageItem(uri, 100f, 100f, 300f, 300f)
                }
            }
            PICK_FONT_REQUEST -> {
                data?.data?.let { uri ->
                    FontManager.loadCustomFont(this, uri)
                    Toast.makeText(this, "Custom font loaded!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        lifecycleScope.launch { saveCurrentPage() }
    }
}
