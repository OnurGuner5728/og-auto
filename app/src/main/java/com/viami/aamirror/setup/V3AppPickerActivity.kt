package com.onurguner.ogauto.setup

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.onurguner.ogauto.R
import com.onurguner.ogauto.core.V3SelectedApps

/**
 * Phone-side, opt-in app launcher selector. No all-packages permission:
 * packages are discovered through their launcher/leanback launcher activities.
 */
class V3AppPickerActivity : Activity() {
    private data class Entry(val label: String, val pkg: String) {
        override fun toString() = label
    }
    private val selected = mutableSetOf<String>()
    private var entries = emptyList<Entry>()
    private var visible = emptyList<Entry>()
    private lateinit var search: EditText
    private lateinit var items: ListView
    private lateinit var adapter: ArrayAdapter<Entry>
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(V3SelectedApps.get(this))
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val title = TextView(this).apply {
            text = getString(R.string.v3_picker_title)
            textSize = 21f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = getString(R.string.v3_picker_explain)
            textSize = 13f
        })
        search = EditText(this).apply {
            hint = getString(R.string.v3_picker_search)
            isSingleLine = true
        }
        root.addView(search)
        status = TextView(this).apply { text = getString(R.string.v3_picker_loading) }
        root.addView(status)
        items = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, mutableListOf<Entry>())
        items.adapter = adapter
        root.addView(items, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = getString(R.string.v3_picker_clear)
            setOnClickListener {
                selected.clear()
                save()
                refresh()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        buttons.addView(Button(this).apply {
            text = getString(R.string.v3_picker_done)
            setOnClickListener { save(); finish() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttons)
        setContentView(root)

        items.setOnItemClickListener { _, _, position, _ ->
            val pkg = visible.getOrNull(position)?.pkg ?: return@setOnItemClickListener
            if (!selected.add(pkg)) selected.remove(pkg)
            save()
            updateCount()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refresh()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        Thread({
            val pm = packageManager
            val queries = listOf(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            )
            val result = runCatching {
                queries.flatMap { pm.queryIntentActivities(it, PackageManager.MATCH_ALL) }
                    .map { Entry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                    .filter { it.pkg != packageName }
                    .distinctBy { it.pkg }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
            runOnUiThread { entries = result; refresh() }
        }, "v3-picker-loader").start()
    }

    private fun updateCount() {
        status.text = getString(R.string.v3_picker_count, selected.size, entries.size)
    }

    private fun refresh() {
        if (!::adapter.isInitialized) return
        val q = search.text.toString().trim()
        visible = if (q.isEmpty()) entries else entries.filter {
            it.label.contains(q, ignoreCase = true) || it.pkg.contains(q, ignoreCase = true)
        }
        adapter.clear()
        adapter.addAll(visible)
        adapter.notifyDataSetChanged()
        items.clearChoices()
        visible.forEachIndexed { i, app -> items.setItemChecked(i, app.pkg in selected) }
        updateCount()
    }

    private fun save() = V3SelectedApps.set(this, selected)
}
