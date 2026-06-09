package com.diarynote.util

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Environment
import com.diarynote.data.model.Page
import com.diarynote.data.model.PageType
import com.diarynote.data.model.StrokeData
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.sqrt

object FontManager {
    private var customTypeface: Typeface? = null
    private const val FONT_PREF_KEY = "custom_font_path"

    fun loadCustomFont(context: Context, uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val fontFile = File(context.filesDir, "custom_font.ttf")
            fontFile.outputStream().use { out -> inputStream.copyTo(out) }
            customTypeface = Typeface.createFromFile(fontFile)
            context.getSharedPreferences("diarynote", Context.MODE_PRIVATE)
                .edit().putString(FONT_PREF_KEY, fontFile.absolutePath).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCustomTypeface(context: Context): Typeface? {
        if (customTypeface != null) return customTypeface
        val path = context.getSharedPreferences("diarynote", Context.MODE_PRIVATE)
            .getString(FONT_PREF_KEY, null) ?: return null
        return try {
            Typeface.createFromFile(File(path)).also { customTypeface = it }
        } catch (e: Exception) { null }
    }
}

object OCRHelper {
    suspend fun recognizeHandwriting(context: Context, bitmap: Bitmap): String {
        return suspendCoroutine { cont ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    cont.resume("")
                }
        }
    }
}

object PdfExporter {
    fun export(
        context: Context,
        title: String,
        pages: List<Page>,
        callback: (Boolean, String?) -> Unit
    ) {
        try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageWidth = 1240
            val pageHeight = 1754

            for ((index, page) in pages.withIndex()) {
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                    pageWidth, pageHeight, index + 1
                ).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // White background
                canvas.drawColor(Color.WHITE)

                // Draw page lines
                drawPageLines(canvas, page.pageType, pageWidth.toFloat(), pageHeight.toFloat())

                // Draw strokes
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                for (stroke in page.strokes) {
                    if (stroke.points.size < 2) continue
                    strokePaint.color = stroke.color
                    strokePaint.strokeWidth = stroke.strokeWidth
                    val path = Path()
                    path.moveTo(stroke.points[0].x, stroke.points[0].y)
                    for (i in 1 until stroke.points.size) {
                        val prev = stroke.points[i - 1]
                        val curr = stroke.points[i]
                        val midX = (prev.x + curr.x) / 2f
                        val midY = (prev.y + curr.y) / 2f
                        path.quadTo(prev.x, prev.y, midX, midY)
                    }
                    canvas.drawPath(path, strokePaint)
                }

                pdfDocument.finishPage(pdfPage)
            }

            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: context.filesDir
            val file = File(dir, "${title.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            callback(true, file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            callback(false, null)
        }
    }

    private fun drawPageLines(canvas: Canvas, pageType: PageType, w: Float, h: Float) {
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C8D4E0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val marginPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFAAAA")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val lineSpacing = 80f
        when (pageType) {
            PageType.RULED -> {
                canvas.drawLine(120f, 0f, 120f, h, marginPaint)
                var y = lineSpacing
                while (y < h) { canvas.drawLine(0f, y, w, y, rulePaint); y += lineSpacing }
            }
            PageType.GRID -> {
                var x = 0f
                while (x < w) { canvas.drawLine(x, 0f, x, h, rulePaint); x += lineSpacing }
                var y = 0f
                while (y < h) { canvas.drawLine(0f, y, w, y, rulePaint); y += lineSpacing }
            }
            PageType.BLANK -> {}
        }
    }
}
