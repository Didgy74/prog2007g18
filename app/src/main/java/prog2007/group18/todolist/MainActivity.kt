package prog2007.group18.todolist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private var taskList = mutableListOf<Task>()
    private lateinit var recyclerAdapter: ListRecyclerAdapter

    // This is a launcher for an Activity that will also return an
    // Intent as a result. This one in particular is for the NewTask activity.
    // It HAS to be initialized during onCreate
    //
    // A launcher is required for activities that are meant to return results.
    // Read more: https://developer.android.com/training/basics/intents/result
    private lateinit var newTaskActivityLauncher: ActivityResultLauncher<Intent>

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
        return when (item.itemId) {
            R.id.menuClearBtn -> {
                clearAllTasks()
                true
            }
            R.id.menuAddExamplesBtn -> {
                addExampleTasks()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initialSetup() {
        // Setup the FAB that opens NewTaskActivity
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener { beginNewTaskActivity() }

        // Setup the launcher NewTaskActivity
        newTaskActivityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult())
            { result -> onNewTaskActivityResult(result) }

        // Setup the RecyclerView
        val recycler = findViewById<RecyclerView>(R.id.mainList)
        recycler.layoutManager = LinearLayoutManager(this)
        recyclerAdapter = ListRecyclerAdapter(taskList)
        recycler.adapter = recyclerAdapter
    }

    private fun addExampleTasks() {
        taskList.add(Task("Test"))
        recyclerAdapter.notifyDataSetChanged()
    }

    // Tries to load the list of task-files
    private fun loadTasksFromFile() : List<Task> = Utils.loadTaskListFromFile(this)

    private fun clearTaskListStorage() = Utils.clearTaskListStorage(this)

    private fun writeTaskListToStorage() = Utils.writeTaskListToFile(this, taskList)

    private fun writeTaskListToStorage(taskList: List<Task>) =
        Utils.writeTaskListToFile(this, taskList)

    private fun clearAllTasks() {
        clearTaskListStorage()

        taskList.clear()
        recyclerAdapter.notifyDataSetChanged()
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
        newTaskActivityLauncher.launch(intent)
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

        recyclerAdapter.notifyDataSetChanged()

        // Write that list to file.
        writeTaskListToStorage()
    }
}