package prog2007.group18.todolist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    // This is a launcher for an Activity that will also return an
    // Intent as a result. This one in particular is for the NewTask activity.
    //
    // A launcher is required for activities that are meant to return results.
    // Read more: https://developer.android.com/training/basics/intents/result
    private var newTaskActivityLauncher: ActivityResultLauncher<Intent>? = null

    private var taskList = ArrayList<Task>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initialSetup()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection
        return when (item.itemId) {
            R.id.clearTasksBtn -> {
                clearAllTasks()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initialSetup() {
        // Register the ActivityLauncher for NewTaskActivity
        newTaskActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult())
            { result -> onNewTaskActivityResult(result) }

        // Setup the FAB that opens NewTaskActivity
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener { beginNewTaskActivity() }

        taskList.clear()
        loadTasksFromFile().toCollection(taskList)
        repopulateGuiWithTasks(taskList.toTypedArray())
    }

    // Tries to load the list of task-files
    private fun loadTasksFromFile() : Array<Task> = Utils.loadTaskListFromFile(this)

    // Fills the main LinearLayout with
    // text views of our tasks.
    //
    // This will need to be modified to account for
    // whatever our GUI uses.
    private fun repopulateGuiWithTasks(taskList: Array<Task>) {
        val list = findViewById<LinearLayout>(R.id.tasklist)
        list.removeAllViews()
        for (task in taskList) {
            // TODO: Here we should probably insert a Fragment(?) for each
            // task-element. Sorting/filtering probably also factors in here somehow?
            // For now we just add one text-widget for title and deadline
            val newTitleView = TextView(applicationContext)
            newTitleView.text = task.title
            list.addView(newTitleView)

            val deadlineView = TextView(applicationContext)
            deadlineView.text = task.deadline.toFormattedString()
            list.addView(deadlineView)
        }
    }

    private fun clearTaskListStorage() = Utils.clearTaskListStorage(this)

    private fun writeTaskListToStorage(taskList: Array<Task>) =
        Utils.writeTaskListToFile(this, taskList)

    private fun clearAllTasks() {
        clearTaskListStorage()

        taskList.clear()
        val list = findViewById<LinearLayout>(R.id.tasklist)
        list.removeAllViews()
    }

    // This is called by the NewTaskActivity when it is done.
    private fun onNewTaskActivityResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            // OK result means we successfully made a new task,
            // let's add it to our task list.
            assert(result.data != null)
            val newTask = CreateTaskJob.fromIntent(result.data!!)
            addNewTask(newTask)
        }
    }

    private fun beginNewTaskActivity() {
        val intent = Intent(this, NewTaskActivity::class.java)
        assert(newTaskActivityLauncher != null)
        newTaskActivityLauncher!!.launch(intent)
    }

    // Constructs a new task, writes it to file and updates the GUI
    //
    // This function probably does too many things...
    private fun addNewTask(taskJob: CreateTaskJob) {
        // Add the new task to our list-variable
        val task = Task()
        task.title = taskJob.title
        task.deadline = taskJob.deadline
        taskList.add(task)

        // Write that list to file.
        writeTaskListToStorage(taskList.toTypedArray())

        repopulateGuiWithTasks(taskList.toTypedArray())
    }
}