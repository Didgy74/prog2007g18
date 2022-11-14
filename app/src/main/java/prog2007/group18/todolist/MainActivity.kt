package prog2007.group18.todolist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.SearchView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.FirebaseApp
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {
    companion object {
        private const val firebaseDbRepo = "https://todolist-a4182-default-rtdb.europe-west1.firebasedatabase.app/"
        // Change this to "" if using the release config?
        private const val firebaseDirName = "nils-testing"
    }

    private var isOnlineUser = false

    // Don't use directly.
    private val taskListInternal = mutableListOf<Task>()
    val taskList get() = taskListInternal
    private lateinit var firebaseDb: FirebaseDatabase
    private lateinit var firebaseDir: DatabaseReference
    private lateinit var recyclerAdapter: ListRecyclerAdapter
    private fun taskListNotifyChange(pushToOnline: Boolean = true) {
        recyclerView.post {
            recyclerAdapter.notifyDataSetChanged()
        }

        if (taskList.isEmpty()) {
            Utils.clearTaskListStorage(this)
        } else {
            Utils.writeTaskListToFile(this, taskList)
        }
        if (isOnlineUser && pushToOnline) {
            // Only run if we are logged in?
            val task = firebaseDir.setValue(Utils.serializeTaskList(taskList))
            if (!task.isSuccessful) {
                Log.e("TodoList", "Unable to push updates online.")
            }
        }
    }
    private fun taskListAdd(task: Task) = taskListAdd(listOf(task))
    private fun taskListAdd(newTasks: List<Task>, pushToOnline: Boolean = true) {
        taskList.addAll(newTasks)
        taskListNotifyChange(pushToOnline = pushToOnline)
    }
    private fun taskListOverwrite(newList: List<Task>, pushToOnline: Boolean = true) {
        taskList.clear()
        taskListAdd(newList, pushToOnline = pushToOnline)
    }
    private fun taskListClear() = taskListOverwrite(listOf())
    fun taskListSet(index: Int, newTask: Task) {
        taskList[index] = newTask
        taskListNotifyChange()
    }
    fun taskListSize() = taskList.size
    // Returns a copy
    fun taskListGet(index: Int): Task = taskList[index].copy()

    private lateinit var recyclerView: RecyclerView

    // This is a launcher for an Activity that will also return an
    // Intent as a result. This one in particular is for the NewTask activity.
    // It HAS to be initialized during onCreate
    //
    // A launcher is required for activities that are meant to return results.
    // Read more: https://developer.android.com/training/basics/intents/result
    //private lateinit var newTaskActivityLauncher: ActivityResultLauncher<Intent>
    private val newTaskActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult())
    { res ->
        onNewTaskActivityResult(res)
    }

    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract())
    { res ->
        onSignInResult(res)
    }

    private val firebaseDbValueListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val loadedList = Utils.deserializeTaskList(snapshot.value as String)
            taskListOverwrite(loadedList, pushToOnline = false)
        }
        override fun onCancelled(error: DatabaseError) {}
    }

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
                taskListClear()
                true
            }
            R.id.menuAddExamplesBtn -> {
                taskListAdd(Task.exampleTasks())
                true
            }
            R.id.showDoneTasksBtn -> {
                recyclerAdapter.showDone = !recyclerAdapter.showDone
                if (recyclerAdapter.showDone) {
                    item.title = "Hide done tasks"
                } else {
                    item.title = "Show done tasks"
                }
                true
            }
            R.id.signInBtn -> {
                beginSignIn()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupFirebaseDb() {
        FirebaseApp.initializeApp(this)
        firebaseDb = Firebase.database(firebaseDbRepo)
        firebaseDir = firebaseDb.reference.child(firebaseDirName)
    }

    private fun initialSetup() {
        setupFirebaseDb()
        recyclerAdapter = ListRecyclerAdapter(this)
        recyclerView = findViewById(R.id.mainList)

        taskListOverwrite(Utils.loadTaskListFromFile(this))

        // Setup the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recyclerAdapter

        // Setup the FAB that opens NewTaskActivity
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener { beginNewTaskActivity() }

        // Setup searchview
        val searchView = findViewById<SearchView>(R.id.search)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(input: String?): Boolean = false
            override fun onQueryTextChange(input: String?): Boolean {
                recyclerAdapter.setSearchFilter(input ?: "")
                return false
            }
        })
    }

    private fun clearDoneTasks() {
        val temp = taskList.filter { !it.done }
        taskListOverwrite(temp)
    }

    // This is called by the NewTaskActivity when it is done.
    private fun onNewTaskActivityResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            // OK result means we successfully made a new task,
            // let's add it to our task list.
            assert(result.data != null)
            val newTask = Task.fromIntent(result.data!!)
            taskListAdd(newTask)
        }
    }

    private fun beginNewTaskActivity() {
        val intent = Intent(this, NewTaskActivity::class.java)
        newTaskActivityLauncher.launch(intent)
    }

    private fun beginSignIn() {
        // Choose authentication providers
        val providers = arrayListOf(AuthUI.IdpConfig.GoogleBuilder().build())

        // Create and launch sign-in intent
        val signInIntent = AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .build()
        signInLauncher.launch(signInIntent)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        val response = result.idpResponse
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            //val user = Firebase.auth.currentUser!!
            // Connect to Firebase and load their copy of the tasklist?
            firebaseDir.addValueEventListener(firebaseDbValueListener)
            isOnlineUser = true
        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            // ...

            if (response == null) {
                // I don't think we need to do anything if the user canceled sign in?
            } else {
                // Maybe display error?
            }
        }
    }
}