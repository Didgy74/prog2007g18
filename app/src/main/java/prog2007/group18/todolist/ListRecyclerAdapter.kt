package prog2007.group18.todolist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDateTime
import kotlin.math.roundToInt

// This is the data-adapter that our RecyclerView uses
// to construct its Views. It just points to our list of Tasks.
class ListRecyclerAdapter(
    private val getTaskList: () -> List<Task>,
    private val setTaskListElement: (index: Int, newTask: Task) -> Unit,
    private val taskListRemove: (index: Int) -> Unit,
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

    private fun buildDisplayList() = getTaskList()
        .withIndex()
        .filter{ searchFilter(it.value) }
        .filter{ if (showDone) { true } else { !it.value.done }}
        .toList()

    override fun getItemCount() = buildDisplayList().size
    override fun getItemId(position: Int): Long = buildDisplayList()[position].index.toLong()

    override fun getItemViewType(position: Int): Int {
        val displayElement = buildDisplayList()[position]
        val task = displayElement.value
        if(!task.progressTask){
            return 0
        } else {
            return 1
        }

    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {}



    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        if(viewType == 0){
            var view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.task_item, viewGroup, false)
            return ViewHolder(view)
        } else{
            var view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.progress_task_item, viewGroup, false)
            return ViewHolder(view)
        }

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
        if(LocalDateTime.now().isAfter(task.deadline)){
            val frameLayout = view.findViewById<FrameLayout>(R.id.frameLayout)
            frameLayout.setBackgroundResource(R.drawable.taskfragment_frame_red)
            //deadlineLabel.text = "Deadline has passed. Too late!"
        }else{
            val frameLayout = view.findViewById<FrameLayout>(R.id.frameLayout)
            frameLayout.setBackgroundResource(R.drawable.taskfragment_frame)
        }
        title.setOnClickListener(){
            taskListRemove(displayElement.index)
        }
        // Setup the checkbox
        if(viewHolder.itemViewType == 0){
            val checkbox = view.findViewById<CheckBox>(R.id.taskItemDoneCheckbox)
            checkbox.isChecked = task.done
            checkbox.setOnCheckedChangeListener { _, value ->
                setTaskListElement(displayElement.index, task.copy( done = value, lastEdited = LocalDateTime.now()))
            }
        }
        if(viewHolder.itemViewType == 1){
            val progressText = view.findViewById<TextView>(R.id.progressText)
            progressText.text = task.progress.toString() + "/" + task.goal.toString()

            val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
            var progressPercent : Float = (task.progress.toFloat()/task.goal.toFloat())*100
            progressBar.setProgress(progressPercent.roundToInt())

            val button = view.findViewById<Button>(R.id.addToProgress)
            val progressInput = view.findViewById<TextInputEditText>(R.id.addValue)
            button.setOnClickListener(){
                task.progress += progressInput.text.toString().toInt()
                progressText.text = task.progress.toString() + "/" + task.goal.toString()
                progressPercent = (progressInput.text.toString().toInt()/task.goal.toFloat())*100
                progressBar.incrementProgressBy(progressPercent.roundToInt())
                if(task.progress >= task.goal){
                    task.done = true
                }
                setTaskListElement(
                    displayElement.index,
                    task.copy(progress = task.progress, done = task.done, lastEdited = LocalDateTime.now()))
            }
        }
    }
}
//Mostly the same code as the ListRecylerAdapter, but for the tasks of groups. The key differences are the scores for groups and the need to store last edit for own potentially offline created tasks
class GroupListRecyclerAdapter(
    private val groupTasksActivity : GroupTasksActivity,
    showDoneTasks: Boolean) :
    RecyclerView.Adapter<GroupListRecyclerAdapter.ViewHolder>() {

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

    private fun buildDisplayList() = groupTasksActivity.taskList
        .withIndex()
        .filter{ searchFilter(it.value) }
        .filter{ if (showDone) { true } else { !it.value.done }}
        .toList()

    override fun getItemCount() = buildDisplayList().size
    override fun getItemId(position: Int): Long = buildDisplayList()[position].index.toLong()

    override fun getItemViewType(position: Int): Int {
        val displayElement = buildDisplayList()[position]
        val task = displayElement.value
        if(!task.progressTask){
            return 0
        } else {
            return 1
        }

    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {}

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        if(viewType == 0){
            var view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.task_item, viewGroup, false)
            return ViewHolder(view)
        } else{
            var view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.progress_task_item, viewGroup, false)
            return ViewHolder(view)
        }
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

        if(LocalDateTime.now().isAfter(task.deadline)){
            val frameLayout = view.findViewById<FrameLayout>(R.id.frameLayout)
            frameLayout.setBackgroundResource(R.drawable.taskfragment_frame_red)
        }else{
            val frameLayout = view.findViewById<FrameLayout>(R.id.frameLayout)
            frameLayout.setBackgroundResource(R.drawable.taskfragment_frame)
        }

        // Setup the checkbox
        val checkbox = view.findViewById<CheckBox>(R.id.taskItemDoneCheckbox)
        checkbox.isChecked = task.done
        checkbox.setOnCheckedChangeListener { _, value ->
            groupTasksActivity.taskListSet(displayElement.index, task.copy( done = value))
            groupTasksActivity.addScore(value)
        }
        if(viewHolder.itemViewType == 1){
            val progressText = view.findViewById<TextView>(R.id.progressText)
            progressText.text = task.progress.toString() + "/" + task.goal.toString()

            val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
            var progressPercent : Float = (task.progress.toFloat()/task.goal.toFloat())*100
            progressBar.setProgress(progressPercent.roundToInt())

            val button = view.findViewById<Button>(R.id.addToProgress)
            val progressInput = view.findViewById<TextInputEditText>(R.id.addValue)
            button.setOnClickListener(){
                task.progress += progressInput.text.toString().toInt()
                progressText.text = task.progress.toString() + "/" + task.goal.toString()
                progressPercent = (progressInput.text.toString().toInt()/task.goal.toFloat())*100
                progressBar.incrementProgressBy(progressPercent.roundToInt())
                if(task.progress >= task.goal){
                    task.done = true
                }
                groupTasksActivity.todoListApp.taskListSet(
                    displayElement.index,
                    task.copy(progress = task.progress, done = task.done))
            }
        }
    }
}