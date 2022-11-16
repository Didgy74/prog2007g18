package prog2007.group18.todolist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// This is the data-adapter that our RecyclerView uses
// to construct its Views. It just points to our list of Tasks.
class ListRecyclerAdapter(
    private val mainActivity: MainActivity,
    showDoneTasks: Boolean) :
    RecyclerView.Adapter<ListRecyclerAdapter.ViewHolder>() {

    init {
        setHasStableIds(true)
    }

    private var showDoneInner = showDoneTasks
    var showDone
        get() = showDoneInner
        set(input) {
            showDoneInner = input
            notifyDataSetChanged()
        }

    private var searchFilter = { _: Task -> true }
    fun setSearchFilter(input: String) {
        searchFilter = { task ->
            task.title.contains(input, ignoreCase = true)
        }
        notifyDataSetChanged()
    }

    private fun buildDisplayList() = mainActivity.taskList
        .withIndex()
        .filter{ searchFilter(it.value) }
        .filter{ if (showDone) { true } else { !it.value.done }}
        .toList()

    override fun getItemCount() = buildDisplayList().size
    override fun getItemId(position: Int): Long = buildDisplayList()[position].index.toLong()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {}

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.task_item, viewGroup, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        // Grab the Entry object from our data-set.
        val displayElement = buildDisplayList()[position]
        val task = displayElement.value
        val view = viewHolder.itemView

        // Setup the the title view
        val title = view.findViewById<TextView>(R.id.taskItemTitle)
        title.text = task.title

        // Setup the the deadline label view
        val deadlineLabel = view.findViewById<TextView>(R.id.taskItemDeadline)
        deadlineLabel.text = task.formattedDeadline()

        // Setup the checkbox
        val checkbox = view.findViewById<CheckBox>(R.id.taskItemDoneCheckbox)
        checkbox.isChecked = task.done
        checkbox.setOnCheckedChangeListener { _, value ->
            mainActivity.taskListSet(displayElement.index, task.copy( done = value))
        }
    }
}