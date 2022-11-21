package prog2007.group18.todolist



import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.SearchView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime


class GroupTasksActivity : AppCompatActivity() {
    val todoListApp get() = application as TodoListApp

    private var dataListenerAdded = false
    // Don't use directly
    private var listOfFirebaseGroups = mutableListOf<Group>()
    private lateinit var _loadedPreferences: PersistentPreferences
    private val preferences get() = _loadedPreferences
    private fun setPreferences(new: PersistentPreferences) {
        _loadedPreferences = new
        PersistentPreferences.writeToFile(preferences, this)
        updateMenuLabels()
    }

    private lateinit var menu: Menu
    private fun updateMenuLabels() {
        val showDoneItem = menu.findItem(R.id.showDoneTasksBtn)
        showDoneItem.title = if (preferences.showDoneTasks) {
            "Hide done tasks"
        } else {
            "Show done tasks"
        }

    }

    private fun toggleShowDoneTasks() {
        val newValue = !preferences.showDoneTasks
        setPreferences(preferences.copy(showDoneTasks = newValue))
        recyclerAdapter.showDone = newValue

        updateMenuLabels()
    }

    private val isLoggedIn get() = Firebase.auth.currentUser != null

    private lateinit var firebaseDb: FirebaseDatabase
    private lateinit var firebaseDir: DatabaseReference
    private lateinit var firebaseGroups: DatabaseReference
    private lateinit var recyclerAdapter: GroupListRecyclerAdapter

    // Don't use directly.
    private val _taskListInternal = mutableListOf<Task>()
    // This makes a copy, since we are not allowed to access the internal data directly.
    val taskList get() = _taskListInternal.map{ it.copy() }
    private fun taskListNotifyChange(pushToOnline: Boolean = true) {
        recyclerView.post {
            recyclerAdapter.notifyDataSetChanged()
        }


        if(checkIfLoggedIn()){
            println("You are logged in on user " + Firebase.auth.currentUser?.email)
        }
        if (checkIfLoggedIn()  && pushToOnline && isOnline(this)) {
            // Only run if we are logged in?
            if(dataListenerAdded == false){
                setupFirebaseDb()
                dataListenerAdded = true
                firebaseGroups.addValueEventListener(firebaseDbValueListenerAllGroups)
                firebaseDir.addValueEventListener(firebaseDbValueListener)
            }
            val task = firebaseDir.setValue(Utils.serializeTaskList(taskList))
            if (!task.isSuccessful) {
                Log.e("TodoList", "Unable to push updates online.")
            }
        }
    }
    private fun checkIfLoggedIn() : Boolean{
        val user = Firebase.auth.currentUser
        return user != null
    }
    private fun taskListAdd(task: Task) = taskListAdd(listOf(task))
    private fun taskListAdd(newTasks: List<Task>, pushToOnline: Boolean = true) {
        _taskListInternal.addAll(newTasks)
        taskListNotifyChange(pushToOnline = pushToOnline)
    }
    private fun taskListOverwrite(newList: List<Task>, pushToOnline: Boolean = true) {
        _taskListInternal.clear()
        setNewDeadlines(newList)
        taskListAdd(newList, pushToOnline = pushToOnline)
    }
    private fun taskListClear(pushToOnline: Boolean = true) =
        taskListOverwrite(listOf(), pushToOnline = pushToOnline)
    fun taskListSet(index: Int, newTask: Task) {
        _taskListInternal[index] = newTask
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


    private val firebaseDbValueListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if(snapshot.exists()){
                var loadedList = Utils.deserializeTaskList(snapshot.value as String)
                taskListOverwrite(loadedList, pushToOnline = false)
            }

        }
        override fun onCancelled(error: DatabaseError) {}
    }
    private val firebaseDbValueListenerAllGroups = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if(snapshot.exists()){
                val allGroups = Json.decodeFromString(snapshot.value as String) as List<Group>
                listOfFirebaseGroups = allGroups.toMutableList()
            }

        }
        override fun onCancelled(error: DatabaseError) {}
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_tasks)

        initialSetup()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu, menu)
        this.menu = menu
        updateMenuLabels()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuClearBtn -> {
                taskListClear()
                true
            }
            R.id.showDoneTasksBtn -> {
                toggleShowDoneTasks()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun setNewDeadlines(taskList : List<Task>){
        for(task in taskList){
            if(LocalDateTime.now().isAfter(task.deadline)){
                if(task.frequency == Frequency.daily){
                    task.done = false
                    task.progress = 0
                    while (LocalDateTime.now().isAfter(task.deadline)){
                        task.deadline = task.deadline.plusDays(1)
                    }

                } else if(task.frequency == Frequency.weekly){
                    task.done = false
                    task.progress = 0
                    while (LocalDateTime.now().isAfter(task.deadline)){
                        task.deadline = task.deadline.plusDays(7)
                    }
                }
            }
        }


    }
    private fun setupFirebaseDb() {
        //FirebaseApp.initializeApp(this)
        firebaseDb = Firebase.database(TodoListApp.firebaseDbRepoUrl)
        val groupID = intent.getStringExtra("groupID")
        firebaseDir = todoListApp.firebaseTopLevelDir.child(groupID!!)
        firebaseGroups = firebaseDb.reference.child("Groups")
    }

    private fun initialSetup() {
        _loadedPreferences = PersistentPreferences.readFromFile(this)

        setupFirebaseDb()
        recyclerAdapter = GroupListRecyclerAdapter(this, preferences.showDoneTasks)
        recyclerView = findViewById(R.id.mainList2)
        if (isLoggedIn && isOnline(this) && !dataListenerAdded){
            dataListenerAdded = true
            firebaseDir.addValueEventListener(firebaseDbValueListener)
            firebaseGroups.addValueEventListener(firebaseDbValueListenerAllGroups)
        }


        // Setup the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recyclerAdapter

        // Setup the FAB that opens NewTaskActivity
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd2)
        fabAdd.setOnClickListener { beginNewTaskActivity() }

        // Setup searchview
        val searchView = findViewById<SearchView>(R.id.search2)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(input: String?): Boolean = false
            override fun onQueryTextChange(input: String?): Boolean {
                recyclerAdapter.setSearchFilter(input ?: "")
                return false
            }
        })

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





    fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connectivityManager != null) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_CELLULAR")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_WIFI")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_ETHERNET")
                    return true
                }
            }
        }
        return false
    }
}