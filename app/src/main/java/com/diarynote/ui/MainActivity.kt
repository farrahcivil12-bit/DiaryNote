package com.diarynote.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.diarynote.R
import com.diarynote.data.db.AppDatabase
import com.diarynote.data.model.Notebook
import com.diarynote.data.repository.NoteRepository
import com.diarynote.databinding.ActivityMainBinding
import com.diarynote.ui.canvas.CanvasActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: NoteRepository
    private lateinit var adapter: NotebookAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = NoteRepository(AppDatabase.getInstance(this))
        adapter = NotebookAdapter(
            onOpen = { notebook -> openNotebook(notebook) },
            onDelete = { notebook -> deleteNotebook(notebook) }
        )

        binding.rvNotebooks.layoutManager = GridLayoutManager(this, 2)
        binding.rvNotebooks.adapter = adapter

        binding.fabNewNotebook.setOnClickListener { showCreateNotebookDialog() }
        loadNotebooks()
    }

    override fun onResume() {
        super.onResume()
        loadNotebooks()
    }

    private fun loadNotebooks() {
        lifecycleScope.launch {
            val notebooks = repository.getAllNotebooks()
            adapter.submitList(notebooks)
            binding.tvEmpty.visibility = if (notebooks.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showCreateNotebookDialog() {
        val input = EditText(this).apply { hint = "Notebook name" }
        AlertDialog.Builder(this)
            .setTitle("New Notebook")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        repository.insertNotebook(Notebook(title = name))
                        loadNotebooks()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openNotebook(notebook: Notebook) {
        val intent = Intent(this, CanvasActivity::class.java).apply {
            putExtra("notebookId", notebook.id)
        }
        startActivity(intent)
    }

    private fun deleteNotebook(notebook: Notebook) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"${notebook.title}\"?")
            .setMessage("All pages will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteNotebook(notebook)
                    loadNotebooks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class NotebookAdapter(
    private val onOpen: (Notebook) -> Unit,
    private val onDelete: (Notebook) -> Unit
) : RecyclerView.Adapter<NotebookAdapter.VH>() {

    private var items = listOf<Notebook>()

    fun submitList(list: List<Notebook>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvNotebookTitle)
        val cover: View = view.findViewById(R.id.notebookCover)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notebook, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val nb = items[position]
        holder.tvTitle.text = nb.title
        holder.cover.setBackgroundColor(nb.coverColor)
        holder.itemView.setOnClickListener { onOpen(nb) }
        holder.itemView.setOnLongClickListener { onDelete(nb); true }
    }

    override fun getItemCount() = items.size
}
