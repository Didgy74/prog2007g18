package prog2007.group18.todolist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// This is the data-adapter that our RecyclerView uses
// to construct its Views. It just points to our list of Tasks.
class ListRecyclerAdapter(private val mainActivity: MainActivity) :
    RecyclerView.Adapter<ListRecyclerAdapter.ViewHolder>() {

    override fun getItemCount() = mainActivity.taskListSize()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {}

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.task_item, viewGroup, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        // Grab the Entry object from our data-set.
        val task = mainActivity.taskListGet(position)
        val view = viewHolder.itemView

        val title = view.findViewById<TextView>(R.id.taskItemTitle)
        title.text = task.title

        val deadlineLabel = view.findViewById<TextView>(R.id.taskItemDeadline)
        deadlineLabel.text = task.formattedDeadline()

        val checkbox = view.findViewById<CheckBox>(R.id.taskItemDoneCheckbox)
        checkbox.isChecked = task.done
        checkbox.setOnCheckedChangeListener { _, value ->
            mainActivity.taskListSet(
                position,
                task.copy( done = value),
                updateRecyclerAdapter = false)
        }
    }
}