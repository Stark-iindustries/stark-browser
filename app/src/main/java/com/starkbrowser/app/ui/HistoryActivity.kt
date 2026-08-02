package com.starkbrowser.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.starkbrowser.app.MainActivity
import com.starkbrowser.app.R
import com.starkbrowser.app.data.AppDatabase
import com.starkbrowser.app.data.HistoryItem
import com.starkbrowser.app.databinding.ActivityHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val db by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = HistoryAdapter(
            onItemClick = { item ->
                setResult(RESULT_OK, Intent().putExtra(MainActivity.EXTRA_OPEN_URL, item.url))
                finish()
            },
            onItemLongClick = { item ->
                lifecycleScope.launch(Dispatchers.IO) { db.historyDao().delete(item) }
                true
            }
        )

        binding.historyRecycler.layoutManager = LinearLayoutManager(this)
        binding.historyRecycler.adapter = adapter

        binding.btnClearHistory.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) { db.historyDao().deleteAll() }
        }

        lifecycleScope.launch {
            db.historyDao().getAllFlow().collectLatest { list ->
                adapter.submitList(list)
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}

class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit,
    private val onItemLongClick: (HistoryItem) -> Boolean
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private var items = emptyList<HistoryItem>()
    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    fun submitList(list: List<HistoryItem>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.historyTitle)
        val url: TextView = view.findViewById(R.id.historyUrl)
        val time: TextView = view.findViewById(R.id.historyTime)
        val favicon: ImageView = view.findViewById(R.id.favicon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title.ifBlank { item.url }
        holder.url.text = item.url
        holder.time.text = dateFormat.format(Date(item.visitedAt))
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { onItemLongClick(item) }
    }

    override fun getItemCount() = items.size
}
