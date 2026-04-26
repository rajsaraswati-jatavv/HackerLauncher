package com.hackerlauncher.launcher

import com.hackerlauncher.R
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

// ─── Data Models ──────────────────────────────────────────────────────────────

enum class Priority(val label: String, val color: Int) {
    HIGH("High", Color.parseColor("#FF4444")),
    MEDIUM("Med", Color.parseColor("#FFAA00")),
    LOW("Low", Color.parseColor("#00FF00"))
}

data class SubTask(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var done: Boolean = false
)

data class TodoTask(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var done: Boolean = false,
    var priority: Priority = Priority.MEDIUM,
    var dueDate: Long? = null,
    var category: String = "General",
    val subtasks: MutableList<SubTask> = mutableListOf()
)

// ─── JSON Serialization ──────────────────────────────────────────────────────

fun TodoTask.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("done", done)
    put("priority", priority.name)
    put("dueDate", dueDate)
    put("category", category)
    val stArr = JSONArray()
    subtasks.forEach { st ->
        stArr.put(JSONObject().apply {
            put("id", st.id)
            put("title", st.title)
            put("done", st.done)
        })
    }
    put("subtasks", stArr)
}

fun JSONObject.toTodoTask(): TodoTask {
    val subtaskArr = optJSONArray("subtasks") ?: JSONArray()
    val subs = mutableListOf<SubTask>()
    for (i in 0 until subtaskArr.length()) {
        val st = subtaskArr.getJSONObject(i)
        subs.add(SubTask(st.getString("id"), st.getString("title"), st.getBoolean("done")))
    }
    return TodoTask(
        id = getString("id"),
        title = getString("title"),
        done = getBoolean("done"),
        priority = try { Priority.valueOf(getString("priority")) } catch (_: Exception) { Priority.MEDIUM },
        dueDate = if (has("dueDate") && !isNull("dueDate")) getLong("dueDate") else null,
        category = optString("category", "General"),
        subtasks = subs
    )
}

// ─── Storage ─────────────────────────────────────────────────────────────────

class TodoStorage(private val prefs: android.content.SharedPreferences) {
    companion object {
        private const val KEY_TASKS = "todo_tasks"
    }

    fun loadTasks(): MutableList<TodoTask> {
        val json = prefs.getString(KEY_TASKS, null) ?: return mutableListOf()
        val arr = JSONArray(json)
        return (0 until arr.length()).mapTo(mutableListOf()) { arr.getJSONObject(it).toTodoTask() }
    }

    fun saveTasks(tasks: List<TodoTask>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_TASKS, arr.toString()).apply()
    }
}

// ─── Filter & Sort ───────────────────────────────────────────────────────────

enum class FilterMode { ALL, ACTIVE, COMPLETED }
enum class SortMode { PRIORITY, DATE, NAME }

// ─── ViewHolder ──────────────────────────────────────────────────────────────

class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val checkbox: CheckBox = itemView.findViewById(R.id.task_checkbox)
    val titleText: TextView = itemView.findViewById(R.id.task_title)
    val priorityDot: ImageView = itemView.findViewById(R.id.priority_dot)
    val dueDateText: TextView = itemView.findViewById(R.id.task_due_date)
    val categoryChip: TextView = itemView.findViewById(R.id.task_category)
    val expandButton: ImageView = itemView.findViewById(R.id.expand_button)
    val subtaskContainer: LinearLayout = itemView.findViewById(R.id.subtask_container)
}

// ─── Adapter ─────────────────────────────────────────────────────────────────

class TodoAdapter(
    var tasks: List<TodoTask>,
    private val onDoneChanged: (TodoTask, Boolean) -> Unit,
    private val onSubtaskChanged: (TodoTask, SubTask, Boolean) -> Unit,
    private val onLongClick: (TodoTask) -> Unit,
    private val onAddSubtask: (TodoTask) -> Unit
) : RecyclerView.Adapter<TaskViewHolder>() {

    private val expandedIds = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_todo_task, parent, false
        )
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        val ctx = holder.itemView.context

        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = task.done
        holder.titleText.text = task.title
        holder.titleText.setTextColor(if (task.done) Color.GRAY else Color.parseColor("#00FF00"))
        if (task.done) {
            holder.titleText.paintFlags = holder.titleText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.titleText.paintFlags = holder.titleText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        // Priority dot
        val dotSize = (12 * ctx.resources.displayMetrics.density).toInt()
        val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(task.priority.color)
            setSize(dotSize, dotSize)
        }
        holder.priorityDot.setImageDrawable(dotDrawable)

        // Due date
        if (task.dueDate != null) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            holder.dueDateText.text = sdf.format(Date(task.dueDate!!))
            holder.dueDateText.visibility = View.VISIBLE
            val now = System.currentTimeMillis()
            holder.dueDateText.setTextColor(
                when {
                    task.done -> Color.GRAY
                    task.dueDate!! < now -> Color.RED
                    task.dueDate!! - now < 86400000L -> Color.parseColor("#FFAA00")
                    else -> Color.parseColor("#00FF00")
                }
            )
        } else {
            holder.dueDateText.visibility = View.GONE
        }

        // Category
        holder.categoryChip.text = task.category
        holder.categoryChip.setTextColor(Color.parseColor("#00FF00"))

        // Subtasks
        val isExpanded = expandedIds.contains(task.id)
        holder.expandButton.visibility = if (task.subtasks.isNotEmpty()) View.VISIBLE else View.INVISIBLE
        holder.expandButton.rotation = if (isExpanded) 180f else 0f
        holder.subtaskContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.expandButton.setOnClickListener {
            if (expandedIds.contains(task.id)) {
                expandedIds.remove(task.id)
            } else {
                expandedIds.add(task.id)
            }
            notifyItemChanged(holder.adapterPosition)
        }

        holder.subtaskContainer.removeAllViews()
        if (isExpanded) {
            task.subtasks.forEach { subtask ->
                val subCheck = CheckBox(ctx).apply {
                    text = subtask.title
                    isChecked = subtask.done
                    setTextColor(Color.parseColor("#00FF00"))
                    textSize = 12f
                    setOnCheckedChangeListener { _, isChecked ->
                        onSubtaskChanged(task, subtask, isChecked)
                    }
                }
                holder.subtaskContainer.addView(subCheck)
            }
            // Add subtask button
            val addSubBtn = TextView(ctx).apply {
                text = "+ Add Subtask"
                setTextColor(Color.parseColor("#00AA00"))
                textSize = 12f
                setPadding(32, 8, 0, 8)
                setOnClickListener { onAddSubtask(task) }
            }
            holder.subtaskContainer.addView(addSubBtn)
        }

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            onDoneChanged(task, isChecked)
        }

        holder.itemView.setOnLongClickListener {
            onLongClick(task)
            true
        }
    }

    override fun getItemCount() = tasks.size

    fun updateTasks(newTasks: List<TodoTask>) {
        val diff = androidx.recyclerview.widget.DiffUtil.calculateDiff(object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = tasks.size
            override fun getNewListSize() = newTasks.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                tasks[oldPos].id == newTasks[newPos].id
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                tasks[oldPos] == newTasks[newPos]
        })
        tasks = newTasks
        diff.dispatchUpdatesTo(this)
    }
}

// ─── Fragment ────────────────────────────────────────────────────────────────

class TodoFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TodoAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var sortButton: ImageView
    private lateinit var fab: FloatingActionButton
    private lateinit var emptyView: TextView

    private lateinit var storage: TodoStorage
    private var allTasks = mutableListOf<TodoTask>()
    private var filterMode = FilterMode.ALL
    private var sortMode = SortMode.PRIORITY

    private val categories = listOf("General", "Hacking", "Dev", "Personal", "Work", "Study")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        // Header
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 8)
            val headerText = TextView(requireContext()).apply {
                text = "> TASK_MANAGER"
                setTextColor(Color.parseColor("#00FF00"))
                textSize = 18f
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(headerText)
            sortButton = ImageView(requireContext()).apply {
                setImageResource(android.R.drawable.ic_menu_sort_by_size)
                setColorFilter(Color.parseColor("#00FF00"))
                setPadding(8, 8, 8, 8)
                setOnClickListener { showSortDialog() }
            }
            addView(sortButton)
        }
        root.addView(header)

        // Tab layout
        tabLayout = TabLayout(requireContext()).apply {
            setBackgroundColor(Color.BLACK)
            setTabTextColors(Color.GRAY, Color.parseColor("#00FF00"))
            setSelectedTabIndicatorColor(Color.parseColor("#00FF00"))
            addTab(newTab().setText("ALL"))
            addTab(newTab().setText("ACTIVE"))
            addTab(newTab().setText("DONE"))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    filterMode = when (tab.position) {
                        1 -> FilterMode.ACTIVE
                        2 -> FilterMode.COMPLETED
                        else -> FilterMode.ALL
                    }
                    applyFilterAndSort()
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
        root.addView(tabLayout)

        // RecyclerView
        recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(recyclerView)

        // Empty view
        emptyView = TextView(requireContext()).apply {
            text = "[ NO_TASKS_FOUND ]"
            setTextColor(Color.parseColor("#00FF00"))
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(32, 64, 32, 32)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(emptyView)

        // FAB
        fab = FloatingActionButton(requireContext()).apply {
            setImageResource(android.R.drawable.ic_input_add)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00FF00")))
            imageTintList = android.content.res.ColorStateList.valueOf(Color.BLACK)
            setOnClickListener { showAddTaskDialog() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.END or android.view.Gravity.BOTTOM
                marginEnd = 32
                bottomMargin = 32
            }
        }

        storage = TodoStorage(
            requireContext().getSharedPreferences("hacker_todo", android.content.Context.MODE_PRIVATE)
        )
        allTasks = storage.loadTasks()

        adapter = TodoAdapter(
            tasks = emptyList(),
            onDoneChanged = { task, done ->
                task.done = done
                saveAndRefresh()
            },
            onSubtaskChanged = { task, subtask, done ->
                subtask.done = done
                saveAndRefresh()
            },
            onLongClick = { task -> showTaskOptionsDialog(task) },
            onAddSubtask = { task -> showAddSubtaskDialog(task) }
        )
        recyclerView.adapter = adapter

        // Swipe to delete
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val pos = vh.adapterPosition
                val task = adapter.tasks[pos]
                allTasks.removeAll { it.id == task.id }
                saveAndRefresh()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recyclerView)

        applyFilterAndSort()
        return root
    }

    // ── Filter & Sort ──────────────────────────────────────────────────────

    private fun applyFilterAndSort() {
        val filtered = when (filterMode) {
            FilterMode.ALL -> allTasks
            FilterMode.ACTIVE -> allTasks.filter { !it.done }
            FilterMode.COMPLETED -> allTasks.filter { it.done }
        }

        val sorted = when (sortMode) {
            SortMode.PRIORITY -> filtered.sortedBy {
                when (it.priority) { Priority.HIGH -> 0; Priority.MEDIUM -> 1; Priority.LOW -> 2 }
            }
            SortMode.DATE -> filtered.sortedBy { it.dueDate ?: Long.MAX_VALUE }
            SortMode.NAME -> filtered.sortedBy { it.title.lowercase() }
        }

        adapter.updateTasks(sorted)
        emptyView.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (sorted.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun saveAndRefresh() {
        storage.saveTasks(allTasks)
        applyFilterAndSort()
    }

    // ── Add Task Dialog ────────────────────────────────────────────────────

    private fun showAddTaskDialog() {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val titleInput = EditText(ctx).apply {
            hint = "Task title..."
            setTextColor(Color.parseColor("#00FF00"))
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            typeface = android.graphics.Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT
        }
        container.addView(titleInput)

        // Due date button
        var selectedDueDate: Long? = null
        val dueDateBtn = android.widget.Button(ctx).apply {
            text = "[ SET_DUE_DATE ]"
            setTextColor(Color.parseColor("#00FF00"))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(ctx, { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedDueDate = cal.timeInMillis
                    text = "$y-${String.format("%02d", m + 1)}-${String.format("%02d", d)}"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        container.addView(dueDateBtn)

        // Priority radio
        val priorityLabel = TextView(ctx).apply {
            text = "PRIORITY:"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
        }
        container.addView(priorityLabel)

        val priorityGroup = RadioGroup(ctx).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        Priority.values().forEach { p ->
            RadioButton(ctx).apply {
                text = p.label
                setTextColor(p.color)
                typeface = android.graphics.Typeface.MONOSPACE
                id = View.generateViewId()
                tag = p
                if (p == Priority.MEDIUM) isChecked = true
            }.also { priorityGroup.addView(it) }
        }
        container.addView(priorityGroup)

        // Category spinner
        val catLabel = TextView(ctx).apply {
            text = "CATEGORY:"
            setTextColor(Color.parseColor("#00FF00"))
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
        }
        container.addView(catLabel)

        val catSpinner = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, categories)
        }
        container.addView(catSpinner)

        AlertDialog.Builder(ctx)
            .setTitle("> NEW_TASK")
            .setView(container)
            .setPositiveButton("ADD") { _, _ ->
                val title = titleInput.text.toString().trim()
                if (title.isNotEmpty()) {
                    val priority = priorityGroup.findViewById<RadioButton>(priorityGroup.checkedRadioButtonId)
                        ?.tag as? Priority ?: Priority.MEDIUM
                    val category = catSpinner.selectedItem as? String ?: "General"
                    allTasks.add(TodoTask(
                        title = title,
                        priority = priority,
                        dueDate = selectedDueDate,
                        category = category
                    ))
                    saveAndRefresh()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // ── Add Subtask Dialog ─────────────────────────────────────────────────

    private fun showAddSubtaskDialog(task: TodoTask) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            hint = "Subtask title..."
            setTextColor(Color.parseColor("#00FF00"))
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            typeface = android.graphics.Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(48, 24, 48, 16)
        }

        AlertDialog.Builder(ctx)
            .setTitle("> ADD_SUBTASK")
            .setView(input)
            .setPositiveButton("ADD") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    val target = allTasks.find { it.id == task.id }
                    target?.subtasks?.add(SubTask(title = title))
                    saveAndRefresh()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // ── Task Options Dialog ────────────────────────────────────────────────

    private fun showTaskOptionsDialog(task: TodoTask) {
        val ctx = requireContext()
        val options = arrayOf("Edit", "Delete", "Add Subtask", "Change Priority")
        AlertDialog.Builder(ctx)
            .setTitle("> TASK_OPTIONS")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditTaskDialog(task)
                    1 -> {
                        allTasks.removeAll { it.id == task.id }
                        saveAndRefresh()
                    }
                    2 -> showAddSubtaskDialog(task)
                    3 -> showPriorityDialog(task)
                }
            }
            .show()
    }

    private fun showEditTaskDialog(task: TodoTask) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            setText(task.title)
            setTextColor(Color.parseColor("#00FF00"))
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
            typeface = android.graphics.Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(48, 24, 48, 16)
        }

        AlertDialog.Builder(ctx)
            .setTitle("> EDIT_TASK")
            .setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    val target = allTasks.find { it.id == task.id }
                    target?.title = newTitle
                    saveAndRefresh()
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showPriorityDialog(task: TodoTask) {
        val ctx = requireContext()
        val labels = Priority.values().map { it.label }.toTypedArray()
        AlertDialog.Builder(ctx)
            .setTitle("> SET_PRIORITY")
            .setItems(labels) { _, which ->
                val target = allTasks.find { it.id == task.id }
                target?.priority = Priority.values()[which]
                saveAndRefresh()
            }
            .show()
    }

    // ── Sort Dialog ────────────────────────────────────────────────────────

    private fun showSortDialog() {
        val ctx = requireContext()
        val options = arrayOf("By Priority", "By Date", "By Name")
        AlertDialog.Builder(ctx)
            .setTitle("> SORT_MODE")
            .setItems(options) { _, which ->
                sortMode = when (which) {
                    0 -> SortMode.PRIORITY
                    1 -> SortMode.DATE
                    else -> SortMode.NAME
                }
                applyFilterAndSort()
            }
            .show()
    }
}
