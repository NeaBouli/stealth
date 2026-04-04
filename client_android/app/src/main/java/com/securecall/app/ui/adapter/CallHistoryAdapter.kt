package com.securecall.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.securecall.app.R
import com.securecall.app.data.CallRecord
import com.securecall.app.data.CallType
import java.text.SimpleDateFormat
import java.util.*

class CallHistoryAdapter(
    records: List<CallRecord>,
    private val onItemClick: ((CallRecord) -> Unit)? = null,
    private val onItemLongClick: ((CallRecord) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = buildGroupedList(records)

    sealed class ListItem {
        data class Header(val label: String) : ListItem()
        data class Record(val record: CallRecord) : ListItem()
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_RECORD = 1
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ListItem.Header -> TYPE_HEADER
        is ListItem.Record -> TYPE_RECORD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(inflater.inflate(R.layout.item_call_history_header, parent, false))
        } else {
            RecordVH(inflater.inflate(R.layout.item_call_history, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.Header -> (holder as HeaderVH).bind(item.label)
            is ListItem.Record -> {
                (holder as RecordVH).bind(item.record)
                holder.itemView.setOnClickListener {
                    onItemClick?.invoke(item.record)
                }
                holder.itemView.setOnLongClickListener {
                    onItemLongClick?.invoke(item.record)
                    true
                }
            }
        }
    }

    override fun getItemCount() = items.size

    class HeaderVH(view: View) : RecyclerView.ViewHolder(view) {
        private val txt: TextView = view.findViewById(R.id.txtHeader)
        fun bind(label: String) { txt.text = label }
    }

    class RecordVH(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.iconCallType)
        private val name: TextView = view.findViewById(R.id.txtContactName)
        private val time: TextView = view.findViewById(R.id.txtCallTime)
        private val duration: TextView = view.findViewById(R.id.txtDuration)

        fun bind(record: CallRecord) {
            name.text = record.contactName
            time.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.timestamp))

            val mins = record.durationSeconds / 60
            val secs = record.durationSeconds % 60
            duration.text = String.format(Locale.US, "%d:%02d", mins, secs)

            val iconRes = when (record.type) {
                CallType.INCOMING -> R.drawable.ic_call_incoming
                CallType.OUTGOING -> R.drawable.ic_call_outgoing
                CallType.MISSED -> R.drawable.ic_call_missed
            }
            icon.setImageResource(iconRes)

            val cd = when (record.type) {
                CallType.INCOMING -> itemView.context.getString(R.string.cd_call_incoming)
                CallType.OUTGOING -> itemView.context.getString(R.string.cd_call_outgoing)
                CallType.MISSED -> itemView.context.getString(R.string.cd_call_missed)
            }
            icon.contentDescription = cd
        }
    }

    private fun buildGroupedList(records: List<CallRecord>): List<ListItem> {
        val result = mutableListOf<ListItem>()
        val cal = Calendar.getInstance()
        val today = calDay(cal)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = calDay(cal)

        var currentGroup = ""
        for (record in records) {
            val recCal = Calendar.getInstance().apply { timeInMillis = record.timestamp }
            val recDay = calDay(recCal)
            val group = when {
                recDay == today -> "Today"
                recDay == yesterday -> "Yesterday"
                else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(record.timestamp))
            }
            if (group != currentGroup) {
                result.add(ListItem.Header(group))
                currentGroup = group
            }
            result.add(ListItem.Record(record))
        }
        return result
    }

    private fun calDay(cal: Calendar): String =
        "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}
