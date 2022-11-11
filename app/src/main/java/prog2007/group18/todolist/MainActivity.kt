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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    private var taskList = mutableListOf<Task>()
    private lateinit var recyclerAdapter: ListRecyclerAdapter

    private lateinit var firebaseDb: FirebaseDatabase

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
            R.id.menuClearDoneBtn -> {
                clearDoneTasks()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initialSetup() {
        firebaseDb = Firebase.database("https://todolist-a4182-default-rtdb.europe-west1.firebasedatabase.app/")
        firebaseDb.reference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val test = snapshot.getValue<String>()
                val tempList = Utils.deserializeTaskList(test!!)
                taskList.clear()
                taskList.addAll(tempList)
                recyclerAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {

            }
        })


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
        taskList.addAll(Task.exampleTasks())
        recyclerAdapter.notifyDataSetChanged()
        firebaseDb.reference.setValue(Utils.serializeTaskList(taskList))
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
        firebaseDb.reference.setValue(Utils.serializeTaskList(taskList))
    }

    private fun clearDoneTasks() {
        val temp = taskList.filter { !it.done }
        taskList.clear()
        taskList.addAll(temp)
        recyclerAdapter.notifyDataSetChanged()
        firebaseDb.reference.setValue(Utils.serializeTaskList(taskList))
    }

    // This is called by the NewTaskActivity when it is done.
    private fun onNewTaskActivityResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            // OK result means we successfully made a new task,
            // let's add it to our task list.
            assert(result.data != null)
            val newTask = Task.fromIntent(result.data!!)
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
    private fun addNewTask(task: Task) {
        // Add the new task to our list-variable
        taskList.add(task)

        recyclerAdapter.notifyDataSetChanged()

        // Write that list to file.
        writeTaskListToStorage()
        firebaseDb.reference.setValue(Utils.serializeTaskList(taskList))
    }
}