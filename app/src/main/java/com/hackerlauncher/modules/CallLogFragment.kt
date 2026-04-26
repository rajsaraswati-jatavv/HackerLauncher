package com.hackerlauncher.modules

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
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
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class CallLogFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var tvStats: TextView
    private lateinit var searchInput: EditText

    private val allCalls = mutableListOf<CallEntry>()
    private val filteredCalls = mutableListOf<CallEntry>()
    private lateinit var callLogAdapter: CallLogAdapter

    private var currentFilter = FILTER_ALL

    companion object {
        private const val FILTER_ALL = 0
        private const val FILTER_INCOMING = 1
        private const val FILTER_OUTGOING = 2
        private const val FILTER_MISSED = 3
    }

    private val callLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadCallLog() else showToast("READ_CALL_LOG permission required")
    }

    data class CallEntry(
        val id: Long,
        val number: String,
        val contactName: String?,
        val type: Int,
        val date: Long,
        val duration: Long,
        val isNew: Boolean,
        val simSlot: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_call_log, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById(R.id.tabLayout)
        recyclerView = view.findViewById(R.id.callLogRecycler)
        emptyState = view.findViewById(R.id.emptyState)
        tvStats = view.findViewById(R.id.tvStats)
        searchInput = view.findViewById(R.id.searchInput)

        callLogAdapter = CallLogAdapter(filteredCalls) { call ->
            showCallDetail(call)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = callLogAdapter

        tabLayout.addTab(tabLayout.newTab().setText("All"))
        tabLayout.addTab(tabLayout.newTab().setText("Incoming"))
        tabLayout.addTab(tabLayout.newTab().setText("Outgoing"))
        tabLayout.addTab(tabLayout.newTab().setText("Missed"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentFilter = tab.position
                applyFilter()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadCallLog()
        } else {
            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }

    private fun loadCallLog() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val calls = mutableListOf<CallEntry>()

                val projection = arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.NEW,
                    CallLog.Calls.PHONE_ACCOUNT_ID
                )

                val cursor = requireContext().contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )

                cursor?.use {
                    val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                    val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                    val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                    val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                    val newIdx = it.getColumnIndex(CallLog.Calls.NEW)
                    val simIdx = it.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                    while (it.moveToNext()) {
                        calls.add(
                            CallEntry(
                                id = it.getLong(idIdx),
                                number = it.getString(numIdx) ?: "Unknown",
                                contactName = it.getString(nameIdx),
                                type = it.getInt(typeIdx),
                                date = it.getLong(dateIdx),
                                duration = it.getLong(durIdx),
                                isNew = it.getInt(newIdx) == 1,
                                simSlot = try { it.getInt(simIdx) } catch (e: Exception) { 0 }
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    allCalls.clear()
                    allCalls.addAll(calls)
                    applyFilter()
                    updateStats()
                }
            }
        }
    }

    private fun applyFilter() {
        val query = searchInput.text.toString().lowercase(Locale.getDefault())
        filteredCalls.clear()

        val typeFiltered = when (currentFilter) {
            FILTER_INCOMING -> allCalls.filter { it.type == CallLog.Calls.INCOMING_TYPE }
            FILTER_OUTGOING -> allCalls.filter { it.type == CallLog.Calls.OUTGOING_TYPE }
            FILTER_MISSED -> allCalls.filter { it.type == CallLog.Calls.MISSED_TYPE }
            else -> allCalls
        }

        val searchFiltered = if (query.isEmpty()) typeFiltered else {
            typeFiltered.filter { call ->
                call.number.lowercase(Locale.getDefault()).contains(query) ||
                call.contactName?.lowercase(Locale.getDefault())?.contains(query) == true
            }
        }

        filteredCalls.addAll(searchFiltered)
        callLogAdapter.notifyDataSetChanged()
        emptyState.visibility = if (filteredCalls.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateStats() {
        val totalCalls = allCalls.size
        val incoming = allCalls.count { it.type == CallLog.Calls.INCOMING_TYPE }
        val outgoing = allCalls.count { it.type == CallLog.Calls.OUTGOING_TYPE }
        val missed = allCalls.count { it.type == CallLog.Calls.MISSED_TYPE }
        val rejected = allCalls.count { it.type == CallLog.Calls.REJECTED_TYPE }

        val totalDuration = allCalls.sumOf { it.duration }
        val avgDuration = if (totalCalls > 0) totalDuration / totalCalls else 0L

        val incomingDuration = allCalls
            .filter { it.type == CallLog.Calls.INCOMING_TYPE }
            .sumOf { it.duration }
        val outgoingDuration = allCalls
            .filter { it.type == CallLog.Calls.OUTGOING_TYPE }
            .sumOf { it.duration }

        val statsText = buildString {
            append("═══ CALL STATISTICS ═══\n")
            append("Total Calls: $totalCalls\n")
            append("├─ Incoming: $incoming\n")
            append("├─ Outgoing: $outgoing\n")
            append("├─ Missed: $missed\n")
            append("└─ Rejected: $rejected\n\n")
            append("Total Duration: ${formatDuration(totalDuration)}\n")
            append("├─ Incoming: ${formatDuration(incomingDuration)}\n")
            append("├─ Outgoing: ${formatDuration(outgoingDuration)}\n")
            append("└─ Average: ${formatDuration(avgDuration)}\n")
        }

        tvStats.text = statsText
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val hrs = TimeUnit.SECONDS.toHours(seconds)
        val mins = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val secs = seconds % 60
        return when {
            hrs > 0 -> String.format("%dh %dm %ds", hrs, mins, secs)
            mins > 0 -> String.format("%dm %ds", mins, secs)
            else -> String.format("%ds", secs)
        }
    }

    private fun formatCallType(type: Int): String {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> "◀ INCOMING"
            CallLog.Calls.OUTGOING_TYPE -> "▶ OUTGOING"
            CallLog.Calls.MISSED_TYPE -> "✕ MISSED"
            CallLog.Calls.REJECTED_TYPE -> "⊘ REJECTED"
            CallLog.Calls.BLOCKED_TYPE -> "■ BLOCKED"
            else -> "? UNKNOWN"
        }
    }

    private fun getCallTypeColor(type: Int): Int {
        return when (type) {
            CallLog.Calls.INCOMING_TYPE -> android.graphics.Color.parseColor("#00FF00")
            CallLog.Calls.OUTGOING_TYPE -> android.graphics.Color.parseColor("#00AA00")
            CallLog.Calls.MISSED_TYPE -> android.graphics.Color.parseColor("#FF4444")
            CallLog.Calls.REJECTED_TYPE -> android.graphics.Color.parseColor("#FF8800")
            else -> android.graphics.Color.parseColor("#888888")
        }
    }

    private fun showCallDetail(call: CallEntry) {
        val dialog = BottomSheetDialog(requireContext())
        val sheetView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_call_detail, null)

        val tvName: TextView = sheetView.findViewById(R.id.tvCallName)
        val tvNumber: TextView = sheetView.findViewById(R.id.tvCallNumber)
        val tvType: TextView = sheetView.findViewById(R.id.tvCallType)
        val tvDate: TextView = sheetView.findViewById(R.id.tvCallDate)
        val tvDuration: TextView = sheetView.findViewById(R.id.tvCallDuration)
        val chipCall: Chip = sheetView.findViewById(R.id.chipCallBack)
        val chipSms: Chip = sheetView.findViewById(R.id.chipSms)
        val chipDial: Chip = sheetView.findViewById(R.id.chipDial)

        tvName.text = call.contactName ?: "Unknown"
        tvNumber.text = call.number
        tvType.text = formatCallType(call.type)
        tvType.setTextColor(getCallTypeColor(call.type))
        tvDate.text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(call.date))
        tvDuration.text = "Duration: ${formatDuration(call.duration)}"

        chipCall.setOnClickListener {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${call.number}")))
        }
        chipDial.setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${call.number}")))
        }
        chipSms.setOnClickListener {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${call.number}")))
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    // --- Inner Adapter ---

    inner class CallLogAdapter(
        private val items: List<CallEntry>,
        private val onClick: (CallEntry) -> Unit
    ) : RecyclerView.Adapter<CallLogAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvCallName)
            val tvNumber: TextView = view.findViewById(R.id.tvCallNumber)
            val tvType: TextView = view.findViewById(R.id.tvCallType)
            val tvTime: TextView = view.findViewById(R.id.tvCallTime)
            val tvDuration: TextView = view.findViewById(R.id.tvCallDuration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_call_log, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvName.text = item.contactName ?: "Unknown"
            holder.tvNumber.text = item.number
            holder.tvType.text = formatCallType(item.type)
            holder.tvType.setTextColor(getCallTypeColor(item.type))
            holder.tvTime.text = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                .format(Date(item.date))
            holder.tvDuration.text = formatDuration(item.duration)

            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
