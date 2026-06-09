package com.diarynote.ui.canvas

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.diarynote.data.model.ImageItem
import com.diarynote.data.model.TextItem
import java.util.UUID

class TextOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val textItems = mutableListOf<TextItem>()
    private val imageItems = mutableListOf<ImageItem>()
    private val imageBitmaps = mutableMapOf<String, Bitmap>()

    private var draggingTextId: String? = null
    private var draggingImageId: String? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
    }

    fun addTextItem(text: String, x: Float, y: Float, typeface: Typeface?, color: Int) {
        val item = TextItem(
            id = UUID.randomUUID().toString(),
            text = text,
            x = x,
            y = y,
            fontSize = 40f,
            color = color,
            useCustomFont = typeface != null
        )
        textItems.add(item)
        invalidate()
    }

    fun addImageItem(uri: Uri, x: Float, y: Float, w: Float, h: Float) {
        val id = UUID.randomUUID().toString()
        val item = ImageItem(id = id, uri = uri.toString(), x = x, y = y, width = w, height = h)
        imageItems.add(item)

        // Load bitmap
        try {
            val stream = context.contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(stream)
            val scaled = Bitmap.createScaledBitmap(bmp, w.toInt(), h.toInt(), true)
            imageBitmaps[id] = scaled
        } catch (e: Exception) { /* ignore */ }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw images
        for (item in imageItems) {
            imageBitmaps[item.id]?.let { bmp ->
                canvas.drawBitmap(bmp, item.x, item.y, null)
            }
        }

        // Draw text items
        for (item in textItems) {
            textPaint.color = item.color
            textPaint.textSize = item.fontSize
            // Wrap text to page lines
            val lineHeight = item.fontSize * 1.4f
            val lines = item.text.split("\n")
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, item.x, item.y + index * lineHeight, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                // Hit test text items
                for (item in textItems.reversed()) {
                    val bounds = getTextBounds(item)
                    if (bounds.contains(event.x, event.y)) {
                        draggingTextId = item.id
                        return true
                    }
                }
                // Hit test images
                for (item in imageItems.reversed()) {
                    val rect = RectF(item.x, item.y, item.x + item.width, item.y + item.height)
                    if (rect.contains(event.x, event.y)) {
                        draggingImageId = item.id
                        return true
                    }
                }
                return false // Pass to drawing canvas
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                draggingTextId?.let { id ->
                    val idx = textItems.indexOfFirst { it.id == id }
                    if (idx >= 0) textItems[idx] = textItems[idx].copy(
                        x = textItems[idx].x + dx, y = textItems[idx].y + dy
                    )
                    invalidate()
                }
                draggingImageId?.let { id ->
                    val idx = imageItems.indexOfFirst { it.id == id }
                    if (idx >= 0) imageItems[idx] = imageItems[idx].copy(
                        x = imageItems[idx].x + dx, y = imageItems[idx].y + dy
                    )
                    invalidate()
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP -> {
                draggingTextId = null
                draggingImageId = null
            }
        }
        return draggingTextId != null || draggingImageId != null
    }

    private fun getTextBounds(item: TextItem): RectF {
        textPaint.textSize = item.fontSize
        val width = textPaint.measureText(item.text)
        return RectF(item.x - 8, item.y - item.fontSize - 8, item.x + width + 8, item.y + 8)
    }

    fun getTextItems(): List<TextItem> = textItems.toList()
    fun getImageItems(): List<ImageItem> = imageItems.toList()
}
