package com.diarynote.data.repository

import com.diarynote.data.db.AppDatabase
import com.diarynote.data.model.*

class NoteRepository(private val db: AppDatabase) {

    // Notebooks
    suspend fun getAllNotebooks() = db.notebookDao().getAllNotebooks()
    suspend fun insertNotebook(notebook: Notebook) = db.notebookDao().insertNotebook(notebook)
    suspend fun updateNotebook(notebook: Notebook) = db.notebookDao().updateNotebook(notebook)
    suspend fun deleteNotebook(notebook: Notebook) = db.notebookDao().deleteNotebook(notebook)
    suspend fun getNotebookById(id: Long) = db.notebookDao().getNotebookById(id)

    // Pages
    suspend fun getPagesForNotebook(notebookId: Long) = db.pageDao().getPagesForNotebook(notebookId)
    suspend fun insertPage(page: Page) = db.pageDao().insertPage(page)
    suspend fun updatePage(page: Page) = db.pageDao().updatePage(page)
    suspend fun deletePage(page: Page) {
        db.pageDao().deletePage(page)
        db.pageDao().reorderAfterDelete(page.notebookId, page.pageNumber)
    }
    suspend fun getPageById(id: Long) = db.pageDao().getPageById(id)

    suspend fun addNewPage(notebookId: Long, pageType: PageType): Long {
        val maxPage = db.pageDao().getMaxPageNumber(notebookId) ?: 0
        val newPage = Page(
            notebookId = notebookId,
            pageNumber = maxPage + 1,
            pageType = pageType
        )
        return db.pageDao().insertPage(newPage)
    }
}
