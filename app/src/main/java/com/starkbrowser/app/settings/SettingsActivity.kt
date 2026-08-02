package com.starkbrowser.app.settings

import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.starkbrowser.app.R
import com.starkbrowser.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var store: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        store = SettingsDataStore(this)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val s = store.settingsFlow.first()

            binding.switchJavascript.isChecked = s.javascriptEnabled
            binding.switchCookies.isChecked = s.cookiesEnabled
            binding.switchContentBlocking.isChecked = s.contentBlockingEnabled
            binding.switchDoNotTrack.isChecked = s.doNotTrack
            binding.switchDataSaver.isChecked = s.dataSaver

            binding.tvSearchEngine.text = SettingsDataStore.SEARCH_ENGINE_NAMES.getOrElse(s.searchEngine) { "Google" }
            binding.tvHomepage.text = s.homepage
            binding.tvTheme.text = when (s.theme) { 1 -> "Light"; 2 -> "Dark"; else -> "System" }

            try {
                val pi = packageManager.getPackageInfo(packageName, 0)
                binding.tvVersion.text = pi.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                binding.tvVersion.text = "1.0.0"
            }
        }
    }

    private fun setupListeners() {
        binding.switchJavascript.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { store.setJavascript(checked) }
        }
        binding.switchCookies.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { store.setCookies(checked) }
        }
        binding.switchContentBlocking.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { store.setContentBlocking(checked) }
        }
        binding.switchDoNotTrack.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { store.setDoNotTrack(checked) }
        }
        binding.switchDataSaver.setOnCheckedChangeListener { _, checked ->
            lifecycleScope.launch { store.setDataSaver(checked) }
        }

        binding.rowSearchEngine.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.search_engine)
                .setItems(SettingsDataStore.SEARCH_ENGINE_NAMES.toTypedArray()) { _, which ->
                    lifecycleScope.launch {
                        store.setSearchEngine(which)
                        binding.tvSearchEngine.text = SettingsDataStore.SEARCH_ENGINE_NAMES[which]
                    }
                }
                .show()
        }

        binding.rowHomepage.setOnClickListener {
            val input = android.widget.EditText(this).apply {
                lifecycleScope.launch {
                    setText(store.settingsFlow.first().homepage)
                }
                hint = "https://example.com or about:home"
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.homepage)
                .setView(input)
                .setPositiveButton(R.string.save) { _, _ ->
                    val hp = input.text.toString().trim().ifBlank { SettingsDataStore.DEFAULT_HOMEPAGE }
                    lifecycleScope.launch {
                        store.setHomepage(hp)
                        binding.tvHomepage.text = hp
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.rowTheme.setOnClickListener {
            val options = arrayOf(
                getString(R.string.system_default),
                getString(R.string.light),
                getString(R.string.dark)
            )
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme)
                .setItems(options) { _, which ->
                    lifecycleScope.launch {
                        store.setTheme(which)
                        binding.tvTheme.text = options[which]
                        val mode = when (which) {
                            1 -> AppCompatDelegate.MODE_NIGHT_NO
                            2 -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                        AppCompatDelegate.setDefaultNightMode(mode)
                    }
                }
                .show()
        }

        binding.rowClearData.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_data)
                .setMessage("Clear history, cookies, and cache?")
                .setPositiveButton(R.string.ok) { _, _ ->
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    cacheDir.deleteRecursively()
                    Toast.makeText(this, R.string.data_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.rowVersion.setOnLongClickListener {
            Toast.makeText(this, "Stark Browser by Stark Industries", Toast.LENGTH_LONG).show()
            true
        }
    }
}
