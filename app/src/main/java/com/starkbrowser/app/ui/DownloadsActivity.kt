package com.starkbrowser.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.starkbrowser.app.R
import com.starkbrowser.app.data.AppDatabase
import com.starkbrowser.app.data.DownloadItem
import com.starkbrowser.app.databinding.ActivityDownloadsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class DownloadsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadsBinding
    private val db by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = DownloadAdapter(
            onOpenClick = { item -> openFile(item) },
            onDeleteClick = { item ->
                lifecycleScope.launch(Dispatchers.IO) { db.downloadDao().deleteById(item.id) }
            }
        )

        binding.downloadRecycler.layoutManager = LinearLayoutManager(this)
        binding.downloadRecycler.adapter = adapter

        lifecycleScope.launch {
            db.downloadDao().getAllFlow().collectLatest { list ->
                adapter.submitList(list)
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun openFile(item: DownloadItem) {
        val path = item.filePath.removePrefix("file://")
        val file = File(path)
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, item.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: open URL
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
        }
    }
}

class DownloadAdapter(
    private val onOpenClick: (DownloadItem) -> Unit,
    private val onDeleteClick: (DownloadItem) -> Unit
) : RecyclerView.Adapter<DownloadAdapter.VH>() {

    private var items = emptyList<DownloadItem>()

    fun submitList(list: List<DownloadItem>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.downloadIcon)
        val filename: TextView = view.findViewById(R.id.downloadFilename)
        val size: TextView = view.findViewById(R.id.downloadSize)
        val openBtn: ImageButton = view.findViewById(R.id.btnOpen)
        val deleteBtn: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.filename.text = item.filename
        holder.size.text = when (item.status) {
            DownloadItem.STATUS_COMPLETE -> formatSize(item.fileSize)
            DownloadItem.STATUS_RUNNING -> "Downloading…"
            DownloadItem.STATUS_FAILED -> "Failed"
            else -> "Pending"
        }
        holder.openBtn.setOnClickListener { onOpenClick(item) }
        holder.deleteBtn.setOnClickListener { onDeleteClick(item) }
        holder.itemView.setOnClickListener { onOpenClick(item) }
    }

    override fun getItemCount() = items.size

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
