package com.diarynote.data.db

import android.content.Context
import androidx.room.*
import com.diarynote.data.model.*

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    suspend fun getAllNotebooks(): List<Notebook>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: Notebook): Long

    @Update
    suspend fun updateNotebook(notebook: Notebook)

    @Delete
    suspend fun deleteNotebook(notebook: Notebook)

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun getNotebookById(id: Long): Notebook?
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE notebookId = :notebookId ORDER BY pageNumber ASC")
    suspend fun getPagesForNotebook(notebookId: Long): List<Page>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page): Long

    @Update
    suspend fun updatePage(page: Page)

    @Delete
    suspend fun deletePage(page: Page)

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPageById(id: Long): Page?

    @Query("SELECT MAX(pageNumber) FROM pages WHERE notebookId = :notebookId")
    suspend fun getMaxPageNumber(notebookId: Long): Int?

    @Query("UPDATE pages SET pageNumber = pageNumber - 1 WHERE notebookId = :notebookId AND pageNumber > :deletedPageNumber")
    suspend fun reorderAfterDelete(notebookId: Long, deletedPageNumber: Int)
}

@Database(
    entities = [Notebook::class, Page::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(StrokeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun pageDao(): PageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "diarynote.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
