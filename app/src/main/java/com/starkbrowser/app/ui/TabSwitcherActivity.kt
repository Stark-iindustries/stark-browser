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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.starkbrowser.app.R
import com.starkbrowser.app.databinding.ActivityTabSwitcherBinding

class TabSwitcherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTabSwitcherBinding
    private lateinit var adapter: TabAdapter

    private val tabIds get() = intent.getStringArrayListExtra(EXTRA_TAB_IDS) ?: arrayListOf()
    private val tabTitles get() = intent.getStringArrayListExtra(EXTRA_TAB_TITLES) ?: arrayListOf()
    private val tabUrls get() = intent.getStringArrayListExtra(EXTRA_TAB_URLS) ?: arrayListOf()
    private val tabIncognitos get() = intent.getBooleanArrayExtra(EXTRA_TAB_INCOGNITOS) ?: BooleanArray(0)
    private val activeTabId get() = intent.getStringExtra(EXTRA_ACTIVE_TAB_ID)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTabSwitcherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Close all option in toolbar
        binding.toolbar.inflateMenu(R.menu.browser_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_new_tab) {
                returnNewTab(false)
                true
            } else false
        }

        val tabs = tabIds.mapIndexed { i, id ->
            TabItem(
                id = id,
                title = tabTitles.getOrNull(i) ?: "Tab",
                url = tabUrls.getOrNull(i) ?: "",
                isIncognito = tabIncognitos.getOrElse(i) { false },
                isActive = id == activeTabId
            )
        }.toMutableList()

        adapter = TabAdapter(
            tabs = tabs,
            onTabClick = { item -> returnTabId(item.id) },
            onTabClose = { item ->
                if (tabs.size == 1) {
                    // Last tab — just return new tab
                    returnNewTab(false)
                } else {
                    val idx = tabs.indexOfFirst { it.id == item.id }
                    if (idx != -1) {
                        tabs.removeAt(idx)
                        adapter.notifyItemRemoved(idx)
                        // If we closed active tab, switch to last
                        if (item.isActive && tabs.isNotEmpty()) {
                            val result = Intent().apply {
                                putExtra(EXTRA_TAB_ID, tabs.last().id)
                                putExtra(EXTRA_CLOSE_TAB_ID, item.id)
                            }
                            setResult(RESULT_OK, result)
                        } else {
                            val result = Intent().apply {
                                putExtra(EXTRA_CLOSE_TAB_ID, item.id)
                            }
                            setResult(RESULT_OK, result)
                        }
                    }
                }
            }
        )

        binding.tabRecycler.apply {
            layoutManager = GridLayoutManager(this@TabSwitcherActivity, 2)
            this.adapter = this@TabSwitcherActivity.adapter
        }

        binding.btnNewTab.setOnClickListener { returnNewTab(false) }
        binding.btnNewIncognito.setOnClickListener { returnNewTab(true) }

        // Long press toolbar for close all
        binding.toolbar.setOnLongClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.close_all_tabs)
                .setMessage("Close all ${tabs.size} tabs?")
                .setPositiveButton(R.string.close_all_tabs) { _, _ ->
                    val result = Intent().apply {
                        putExtra(EXTRA_CLOSE_ALL, true)
                        putExtra(EXTRA_NEW_TAB, true)
                    }
                    setResult(RESULT_OK, result)
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            true
        }
    }

    private fun returnTabId(tabId: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_TAB_ID, tabId))
        finish()
    }

    private fun returnNewTab(incognito: Boolean) {
        setResult(RESULT_OK, Intent().apply {
            putExtra(EXTRA_NEW_TAB, true)
            putExtra(EXTRA_INCOGNITO, incognito)
        })
        finish()
    }

    data class TabItem(
        val id: String,
        val title: String,
        val url: String,
        val isIncognito: Boolean,
        val isActive: Boolean
    )

    companion object {
        const val EXTRA_TAB_IDS = "tab_ids"
        const val EXTRA_TAB_TITLES = "tab_titles"
        const val EXTRA_TAB_URLS = "tab_urls"
        const val EXTRA_TAB_INCOGNITOS = "tab_incognitos"
        const val EXTRA_ACTIVE_TAB_ID = "active_tab_id"
        const val EXTRA_TAB_ID = "tab_id"
        const val EXTRA_NEW_TAB = "new_tab"
        const val EXTRA_INCOGNITO = "incognito"
        const val EXTRA_CLOSE_TAB_ID = "close_tab_id"
        const val EXTRA_CLOSE_ALL = "close_all"
    }
}

class TabAdapter(
    private val tabs: MutableList<TabSwitcherActivity.TabItem>,
    private val onTabClick: (TabSwitcherActivity.TabItem) -> Unit,
    private val onTabClose: (TabSwitcherActivity.TabItem) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    class TabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val favicon: ImageView = view.findViewById(R.id.tabFavicon)
        val title: TextView = view.findViewById(R.id.tabTitle)
        val closeBtn: ImageButton = view.findViewById(R.id.btnCloseTab)
        val incognitoBadge: TextView = view.findViewById(R.id.incognitoBadge)
        val activeIndicator: View = view.findViewById(R.id.activeIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TabViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_tab, parent, false)
    )

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.title.text = tab.title.ifBlank { tab.url.ifBlank { "New Tab" } }
        holder.incognitoBadge.isVisible = tab.isIncognito
        holder.activeIndicator.isVisible = tab.isActive
        holder.itemView.setOnClickListener { onTabClick(tab) }
        holder.closeBtn.setOnClickListener { onTabClose(tab) }
        if (tab.isActive) {
            holder.itemView.alpha = 1f
            holder.itemView.scaleX = 1f
            holder.itemView.scaleY = 1f
        } else {
            holder.itemView.alpha = 0.85f
        }
    }

    override fun getItemCount() = tabs.size

    private val View.isVisible: Boolean
        get() = visibility == View.VISIBLE
    private var View.isVisible: Boolean
        get() = visibility == View.VISIBLE
        set(v) { visibility = if (v) View.VISIBLE else View.GONE }
}
