package com.hackerlauncher.modules

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CalendarFragment : Fragment() {

    private lateinit var tvMonthYear: TextView
    private lateinit var btnPrevMonth: MaterialButton
    private lateinit var btnNextMonth: MaterialButton
    private lateinit var btnToday: MaterialButton
    private lateinit var calendarGrid: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var tvEventCount: TextView
    private lateinit var fabAddEvent: com.google.android.material.floatingactionbutton.FloatingActionButton

    private val eventsList = mutableListOf<CalendarEvent>()
    private lateinit var eventsAdapter: EventsAdapter

    private var currentCalendar = Calendar.getInstance()

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadCalendarEvents() else showToast("READ_CALENDAR permission required")
    }

    data class CalendarEvent(
        val id: Long,
        val title: String,
        val description: String?,
        val location: String?,
        val startTime: Long,
        val endTime: Long,
        val allDay: Boolean,
        val calendarName: String?,
        val calendarColor: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvMonthYear = view.findViewById(R.id.tvMonthYear)
        btnPrevMonth = view.findViewById(R.id.btnPrevMonth)
        btnNextMonth = view.findViewById(R.id.btnNextMonth)
        btnToday = view.findViewById(R.id.btnToday)
        calendarGrid = view.findViewById(R.id.calendarGrid)
        recyclerView = view.findViewById(R.id.eventsRecycler)
        emptyState = view.findViewById(R.id.emptyState)
        tvEventCount = view.findViewById(R.id.tvEventCount)
        fabAddEvent = view.findViewById(R.id.fabAddEvent)

        eventsAdapter = EventsAdapter(eventsList) { event ->
            showEventDetail(event)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = eventsAdapter

        btnPrevMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, -1)
            updateMonthView()
            loadCalendarEvents()
        }

        btnNextMonth.setOnClickListener {
            currentCalendar.add(Calendar.MONTH, 1)
            updateMonthView()
            loadCalendarEvents()
        }

        btnToday.setOnClickListener {
            currentCalendar = Calendar.getInstance()
            updateMonthView()
            loadCalendarEvents()
        }

        fabAddEvent.setOnClickListener { createNewEvent() }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR)
            == PackageManager.PERMISSION_GRANTED
        ) {
            updateMonthView()
            loadCalendarEvents()
        } else {
            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    private fun updateMonthView() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        tvMonthYear.text = monthFormat.format(currentCalendar.time)

        drawCalendarGrid()
    }

    private fun drawCalendarGrid() {
        calendarGrid.removeAllViews()

        val year = currentCalendar.get(Calendar.YEAR)
        val month = currentCalendar.get(Calendar.MONTH)

        // First day of month
        val firstDayCal = Calendar.getInstance().apply {
            set(year, month, 1)
        }
        val daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK) // 1=Sunday

        // Get days with events for this month
        val eventDays = getEventDaysForMonth(year, month)

        // Day headers row
        val headerRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val dayNames = arrayOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
        for (dayName in dayNames) {
            val tv = TextView(requireContext()).apply {
                text = dayName
                setTextColor(android.graphics.Color.parseColor("#005500"))
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 11f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            headerRow.addView(tv)
        }
        calendarGrid.addView(headerRow)

        // Weeks rows
        var currentDay = 1
        var dayOfWeekCounter = 1

        var row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Empty cells for offset
        for (i in 1 until firstDayOfWeek) {
            val empty = TextView(requireContext()).apply {
                text = ""
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(empty)
            dayOfWeekCounter++
        }

        // Day cells
        while (currentDay <= daysInMonth) {
            if (dayOfWeekCounter > 7) {
                calendarGrid.addView(row)
                row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                dayOfWeekCounter = 1
            }

            val dayNum = currentDay
            val hasEvent = eventDays.contains(dayNum)
            val isToday = isToday(year, month, dayNum)

            val dayView = TextView(requireContext()).apply {
                text = dayNum.toString()
                gravity = android.view.Gravity.CENTER
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 13f
                setPadding(4, 8, 4, 8)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                when {
                    isToday -> {
                        setTextColor(android.graphics.Color.BLACK)
                        setBackgroundColor(android.graphics.Color.parseColor("#00FF00"))
                    }
                    hasEvent -> {
                        setTextColor(android.graphics.Color.parseColor("#00FF00"))
                        setBackgroundColor(android.graphics.Color.parseColor("#002200"))
                    }
                    else -> {
                        setTextColor(android.graphics.Color.parseColor("#00AA00"))
                    }
                }

                setOnClickListener {
                    currentCalendar.set(Calendar.DAY_OF_MONTH, dayNum)
                    loadCalendarEvents()
                }
            }
            row.addView(dayView)
            currentDay++
            dayOfWeekCounter++
        }

        // Fill remaining empty cells
        while (dayOfWeekCounter <= 7) {
            val empty = TextView(requireContext()).apply {
                text = ""
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(empty)
            dayOfWeekCounter++
        }
        calendarGrid.addView(row)
    }

    private fun getEventDaysForMonth(year: Int, month: Int): Set<Int> {
        val eventDays = mutableSetOf<Int>()
        for (event in eventsList) {
            val eventCal = Calendar.getInstance().apply { timeInMillis = event.startTime }
            if (eventCal.get(Calendar.YEAR) == year && eventCal.get(Calendar.MONTH) == month) {
                eventDays.add(eventCal.get(Calendar.DAY_OF_MONTH))
            }
        }
        return eventDays
    }

    private fun isToday(year: Int, month: Int, day: Int): Boolean {
        val today = Calendar.getInstance()
        return today.get(Calendar.YEAR) == year &&
                today.get(Calendar.MONTH) == month &&
                today.get(Calendar.DAY_OF_MONTH) == day
    }

    private fun loadCalendarEvents() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val events = mutableListOf<CalendarEvent>()

                val year = currentCalendar.get(Calendar.YEAR)
                val month = currentCalendar.get(Calendar.MONTH)

                // Start of month
                val startTime = Calendar.getInstance().apply {
                    set(year, month, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                // End of month
                val endTime = Calendar.getInstance().apply {
                    set(year, month, getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                val projection = arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.CALENDAR_ID
                )

                val selection = "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?)"
                val selectionArgs = arrayOf(startTime.toString(), endTime.toString())

                try {
                    val cursor = requireContext().contentResolver.query(
                        CalendarContract.Events.CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        "${CalendarContract.Events.DTSTART} ASC"
                    )

                    cursor?.use {
                        val idIdx = it.getColumnIndex(CalendarContract.Events._ID)
                        val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
                        val descIdx = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                        val locIdx = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                        val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
                        val endIdx = it.getColumnIndex(CalendarContract.Events.DTEND)
                        val allDayIdx = it.getColumnIndex(CalendarContract.Events.ALL_DAY)
                        val calIdIdx = it.getColumnIndex(CalendarContract.Events.CALENDAR_ID)

                        while (it.moveToNext()) {
                            val calId = it.getLong(calIdIdx)
                            val calInfo = getCalendarInfo(calId)

                            events.add(
                                CalendarEvent(
                                    id = it.getLong(idIdx),
                                    title = it.getString(titleIdx) ?: "Untitled",
                                    description = it.getString(descIdx),
                                    location = it.getString(locIdx),
                                    startTime = it.getLong(startIdx),
                                    endTime = it.getLong(endIdx),
                                    allDay = it.getInt(allDayIdx) == 1,
                                    calendarName = calInfo.first,
                                    calendarColor = calInfo.second
                                )
                            )
                        }
                    }
                } catch (e: SecurityException) {
                    withContext(Dispatchers.Main) {
                        showToast("Calendar access denied: ${e.message}")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Error loading events: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    eventsList.clear()
                    eventsList.addAll(events)
                    eventsAdapter.notifyDataSetChanged()
                    tvEventCount.text = "${events.size} events"
                    emptyState.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
                    drawCalendarGrid() // Refresh grid with event dots
                }
            }
        }
    }

    private fun getCalendarInfo(calendarId: Long): Pair<String?, Int> {
        var name: String? = null
        var color = 0
        try {
            val cursor = requireContext().contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR
                ),
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(calendarId.toString()),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0)
                    color = it.getInt(1)
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return Pair(name, color)
    }

    private fun createNewEvent() {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val etTitle = EditText(requireContext()).apply {
            hint = "Event title"
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
            setHintTextColor(android.graphics.Color.parseColor("#005500"))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val etLocation = EditText(requireContext()).apply {
            hint = "Location (optional)"
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
            setHintTextColor(android.graphics.Color.parseColor("#005500"))
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val etDescription = EditText(requireContext()).apply {
            hint = "Description (optional)"
            setTextColor(android.graphics.Color.parseColor("#00FF00"))
            setHintTextColor(android.graphics.Color.parseColor("#005500"))
            typeface = android.graphics.Typeface.MONOSPACE
            minLines = 2
        }

        layout.addView(etTitle)
        layout.addView(etLocation)
        layout.addView(etDescription)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Event")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isEmpty()) {
                    showToast("Title required")
                    return@setPositiveButton
                }
                createEventViaIntent(
                    title,
                    etLocation.text.toString().trim(),
                    etDescription.text.toString().trim()
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createEventViaIntent(title: String, location: String, description: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = "vnd.android.cursor.item/event"
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            putExtra(CalendarContract.Events.DESCRIPTION, description)

            val startCal = currentCalendar.clone() as Calendar
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startCal.timeInMillis)
            startCal.add(Calendar.HOUR, 1)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startCal.timeInMillis)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            showToast("No calendar app available")
        }
    }

    private fun showEventDetail(event: CalendarEvent) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_event_detail, null)

        val tvTitle: TextView = sheetView.findViewById(R.id.tvEventTitle)
        val tvTime: TextView = sheetView.findViewById(R.id.tvEventTime)
        val tvLocation: TextView = sheetView.findViewById(R.id.tvEventLocation)
        val tvDescription: TextView = sheetView.findViewById(R.id.tvEventDescription)
        val tvCalendar: TextView = sheetView.findViewById(R.id.tvEventCalendar)
        val chipEdit: Chip = sheetView.findViewById(R.id.chipEditEvent)
        val chipDelete: Chip = sheetView.findViewById(R.id.chipDeleteEvent)
        val chipShare: Chip = sheetView.findViewById(R.id.chipShareEvent)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        tvTitle.text = event.title
        tvTime.text = if (event.allDay) {
            "All Day: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(event.startTime))}"
        } else {
            "${dateFormat.format(Date(event.startTime))} → ${dateFormat.format(Date(event.endTime))}"
        }
        tvLocation.text = event.location ?: "No location"
        tvLocation.visibility = if (event.location != null) View.VISIBLE else View.GONE
        tvDescription.text = event.description ?: "No description"
        tvDescription.visibility = if (event.description != null) View.VISIBLE else View.GONE
        tvCalendar.text = event.calendarName ?: "Default Calendar"

        chipEdit.setOnClickListener {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                data = android.content.ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI, event.id
                )
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Cannot edit event")
            }
            dialog.dismiss()
        }

        chipDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Event")
                .setMessage("Delete \"${event.title}\"?")
                .setPositiveButton("Delete") { _, _ ->
                    deleteEvent(event.id)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        chipShare.setOnClickListener {
            val shareText = buildString {
                append("${event.title}\n")
                append("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(event.startTime))}\n")
                event.location?.let { append("Location: $it\n") }
                event.description?.let { append("Details: $it\n") }
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(shareIntent, "Share event"))
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun deleteEvent(eventId: Long) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val uri = android.content.ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_URI, eventId
                    )
                    val deleted = requireContext().contentResolver.delete(uri, null, null)
                    withContext(Dispatchers.Main) {
                        if (deleted > 0) {
                            showToast("Event deleted")
                            loadCalendarEvents()
                        } else {
                            showToast("Failed to delete event")
                        }
                    }
                } catch (e: SecurityException) {
                    withContext(Dispatchers.Main) {
                        showToast("Permission denied: ${e.message}")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showToast("Delete failed: ${e.message}")
                    }
                }
            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    // --- Inner Adapter ---

    inner class EventsAdapter(
        private val items: List<CalendarEvent>,
        private val onClick: (CalendarEvent) -> Unit
    ) : RecyclerView.Adapter<EventsAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvEventTitle)
            val tvTime: TextView = view.findViewById(R.id.tvEventTime)
            val tvLocation: TextView = view.findViewById(R.id.tvEventLocation)
            val tvCalendar: TextView = view.findViewById(R.id.tvEventCalendar)
            val colorIndicator: View = view.findViewById(R.id.colorIndicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_calendar_event, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title

            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.tvTime.text = if (item.allDay) {
                "All Day"
            } else {
                "${dateFormat.format(Date(item.startTime))} - ${dateFormat.format(Date(item.endTime))}"
            }

            holder.tvLocation.text = item.location ?: ""
            holder.tvLocation.visibility = if (item.location != null) View.VISIBLE else View.GONE
            holder.tvCalendar.text = item.calendarName ?: ""

            // Calendar color indicator
            try {
                holder.colorIndicator.setBackgroundColor(item.calendarColor)
            } catch (e: Exception) {
                holder.colorIndicator.setBackgroundColor(android.graphics.Color.parseColor("#00FF00"))
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
