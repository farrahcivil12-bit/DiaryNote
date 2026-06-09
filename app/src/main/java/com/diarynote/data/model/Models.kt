package com.diarynote.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "notebooks")
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val coverColor: Int = 0xFF4A90D9.toInt()
)

@Entity(tableName = "pages")
@TypeConverters(StrokeConverter::class)
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val pageNumber: Int,
    val pageType: PageType = PageType.RULED,
    val strokes: List<StrokeData> = emptyList(),
    val imageItems: List<ImageItem> = emptyList(),
    val textItems: List<TextItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class PageType { RULED, GRID, BLANK }

data class StrokeData(
    val points: List<StrokePoint>,
    val color: Int,
    val strokeWidth: Float,
    val brushType: BrushType,
    val alpha: Int = 255
)

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val timestamp: Long
)

enum class BrushType { PEN, ERASER }

data class ImageItem(
    val id: String,
    val uri: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class TextItem(
    val id: String,
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float,
    val color: Int,
    val useCustomFont: Boolean = false
)

class StrokeConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromStrokeList(strokes: List<StrokeData>): String = gson.toJson(strokes)

    @TypeConverter
    fun toStrokeList(json: String): List<StrokeData> {
        val type = object : TypeToken<List<StrokeData>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    @TypeConverter
    fun fromImageList(items: List<ImageItem>): String = gson.toJson(items)

    @TypeConverter
    fun toImageList(json: String): List<ImageItem> {
        val type = object : TypeToken<List<ImageItem>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    @TypeConverter
    fun fromTextList(items: List<TextItem>): String = gson.toJson(items)

    @TypeConverter
    fun toTextList(json: String): List<TextItem> {
        val type = object : TypeToken<List<TextItem>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
