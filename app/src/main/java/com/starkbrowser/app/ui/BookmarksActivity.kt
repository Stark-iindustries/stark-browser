package com.starkbrowser.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.starkbrowser.app.MainActivity
import com.starkbrowser.app.R
import com.starkbrowser.app.data.AppDatabase
import com.starkbrowser.app.data.Bookmark
import com.starkbrowser.app.databinding.ActivityBookmarksBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BookmarksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookmarksBinding
    private val db by lazy { AppDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val adapter = BookmarkAdapter(
            onItemClick = { bookmark ->
                setResult(RESULT_OK, Intent().putExtra(MainActivity.EXTRA_OPEN_URL, bookmark.url))
                finish()
            },
            onDeleteClick = { bookmark ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.bookmarkDao().delete(bookmark)
                }
            }
        )

        binding.bookmarkRecycler.layoutManager = LinearLayoutManager(this)
        binding.bookmarkRecycler.adapter = adapter

        lifecycleScope.launch {
            db.bookmarkDao().getAllFlow().collectLatest { list ->
                adapter.submitList(list)
                binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}

class BookmarkAdapter(
    private val onItemClick: (Bookmark) -> Unit,
    private val onDeleteClick: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.VH>() {

    private var items = emptyList<Bookmark>()

    fun submitList(list: List<Bookmark>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.bookmarkTitle)
        val url: TextView = view.findViewById(R.id.bookmarkUrl)
        val favicon: ImageView = view.findViewById(R.id.favicon)
        val deleteBtn: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.url.text = item.url
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.deleteBtn.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount() = items.size
}
