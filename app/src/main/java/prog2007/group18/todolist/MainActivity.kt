package prog2007.group18.todolist

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Button
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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.time.LocalDateTime


class MainActivity : AppCompatActivity() {
    companion object {
        private const val firebaseDbRepo = "https://todolist-a4182-default-rtdb.europe-west1.firebasedatabase.app/"
        // Change this to "" if using the release config?
        private const val firebaseDirName = "erlend-testing"
    }
    private var dataListenerAdded = false
    // Don't use directly
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

        val signInItem = menu.findItem(R.id.signInBtn)
        signInItem.title = if (isLoggedIn) { "Sign out" } else { "Sign in" }
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
    private lateinit var recyclerAdapter: ListRecyclerAdapter

    // Don't use directly.
    private val _taskListInternal = mutableListOf<Task>()
    // This makes a copy, since we are not allowed to access the internal data directly.
    val taskList get() = _taskListInternal.map{ it.copy() }
    private fun taskListNotifyChange(pushToOnline: Boolean = true) {
        recyclerView.post {
            recyclerAdapter.notifyDataSetChanged()
        }

        if (taskList.isEmpty()) {
            Utils.clearTaskListStorage(this)
        } else {
            Utils.writeTaskListToFile(this, taskList)
        }

        if(checkIfLoggedIn()){
            println("You are logged in on user " + Firebase.auth.currentUser?.email)
        }

        if (checkIfLoggedIn()  && pushToOnline && isOnline(this)) {
            // Only run if we are logged in?
            if(dataListenerAdded == false){
                setupFirebaseDb()
                dataListenerAdded = true
                firebaseDir.addValueEventListener(firebaseDbValueListener)
            }
            val task = firebaseDir.setValue(Utils.serializeTaskList(taskList))
            if (task.isSuccessful) {

                Utils.writeLastFirebaseListToFile(this, taskList)
            }
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

    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract())
    { res ->
        onSignInResult(res)
    }

    private val firebaseDbValueListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if(snapshot != null){

                var loadedList = Utils.deserializeTaskList(snapshot.value as String)
                //lastLoadedList = loadedList.toMutableList()
                val syncedList = sync(loadedList)
                if(loadedList != syncedList){
                    taskListOverwrite(syncedList, pushToOnline = true)
                } else{taskListOverwrite(syncedList, pushToOnline = false)}
            }

        }
        override fun onCancelled(error: DatabaseError) {}
    }
    private fun addValueListener(){
        dataListenerAdded = true
        Firebase.auth.currentUser?.uid!!

    }
    private fun setNewDeadlines(taskList : List<Task>){
        for(task in taskList){
            if(LocalDateTime.now().isAfter(task.deadline)){
                if(task.frequency == Frequency.daily){
                    task.done = false
                    while (LocalDateTime.now().isAfter(task.deadline)){
                        task.deadline = task.deadline.plusDays(1)
                    }

                } else if(task.frequency == Frequency.weekly){
                    task.done = false
                    while (LocalDateTime.now().isAfter(task.deadline)){
                        task.deadline = task.deadline.plusDays(7)
                    }
                }
            }
        }


    }

    //For tasks in both lists, use timestamp and add the newest one to the final task list

    //Cloud tasks in last sync missing in new one should be deleted from the final list.
    //Cloud tasks missing from last sync that are in the new one are new and should be downloaded to the final list

    //Task missing from both syncs should be added to the final list
    //Tasks in both syncs missing from local should be deleted from the final list
    private fun sync(newlyRetrievedTaskList : List<Task>) : List<Task>{

        val localStoredTaskList = Utils.loadTaskListFromFile(this)
        val lastRetrievedTaskList = Utils.loadLastFirebaseListFromFile(this)

        //Create new FinalList that starts with all tasks from local storage
        //
        var bufferList = mutableListOf<Task>()

        for (task in localStoredTaskList){
            //Those tasks that have never been uploaded to cloud from main
            if(!containsTask(lastRetrievedTaskList, task)){
                bufferList.add(task)
            }
        }
        for(task in newlyRetrievedTaskList){
            if(!toBeDeleted(task, localStoredTaskList, lastRetrievedTaskList, bufferList)) {
                //TODO: newestTask = getNewestVersion(task, localStoredTaskList)
                bufferList.add(task)
            }

        }

        //Overwriting last with new
        Utils.writeLastFirebaseListToFile(this, newlyRetrievedTaskList)
        setNewDeadlines(bufferList)
        return bufferList
    }
    //TODO
    /*
    private fun getNewestVersion(localList: List<Task>, firebaseTask : Task) : Task{
        for (task in localList){
            //Title and time functions as an unique identifier
            if(task.title == firebaseTask && task.timeAndDateCreated == firebaseTask.timeAndDateCreated){
                if(task.lastUpdateTime > firebaseTask.lastUpdateTime){
                    return task
                }else{return firebaseTask}
            }
        }
        return firebaseTask
    }
    */
    private fun containsTask(taskList2 : List<Task>, task2 : Task) : Boolean{
        for (task in taskList2) {
            if(task.title ==  task2.title && task.deadline == task2.deadline){
                return true
            }
        }
        return false
    }
    private fun toBeDeleted(taskFromNewSync : Task, localList : List<Task>, oldSyncList : List<Task>, bufferList : List<Task>) : Boolean{
        //taskFromNewSync must be deleted if also in old sync but not in local
        if(containsTask(oldSyncList,taskFromNewSync) && !containsTask(localList, taskFromNewSync)){
            return true
        }
        if(containsTask(bufferList,taskFromNewSync)){return true}
        //taskFromNewSync must not be deleted if not in old sync
        return false

    }
    private fun calendarListSetUp(){
        val name = intent.getStringExtra("name")

        // Creating the new Fragment with the name passed in.
        val fragment = CalendarFragment.newInstance("Testing")

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        calendarListSetUp()
        var onlineGroupButton : Button
        onlineGroupButton= findViewById(R.id.onlineGroupButton)
        onlineGroupButton.setOnClickListener(){
            if(checkIfLoggedIn()){
                val intent = Intent(this, onlineGroupActivity::class.java)
                startActivity(intent)
            }
        }
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
            R.id.menuAddExamplesBtn -> {
                taskListAdd(Task.exampleTasks())
                true
            }
            R.id.showDoneTasksBtn -> {
                toggleShowDoneTasks()
                true
            }
            R.id.signInBtn -> {
                toggleSignInOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupFirebaseDb() {
        FirebaseApp.initializeApp(this)
        firebaseDb = Firebase.database(firebaseDbRepo)
        firebaseDir = firebaseDb.reference.child(Firebase.auth.currentUser?.uid!!)

    }

    private fun initialSetup() {
        _loadedPreferences = PersistentPreferences.readFromFile(this)


        recyclerAdapter = ListRecyclerAdapter(this, preferences.showDoneTasks)
        recyclerView = findViewById(R.id.mainList)
        if (checkIfLoggedIn() && isOnline(this) && dataListenerAdded == false){
            setupFirebaseDb()
            dataListenerAdded = true
            firebaseDir.addValueEventListener(firebaseDbValueListener)
        }

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

        // Check if we should attempt to automatically sign in
        if (preferences.prefersOnline && !isLoggedIn)
            beginSignInActivity()
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

    private fun toggleSignInOut() {
        if (isLoggedIn) {
            signOutProcedure()
        } else {
            beginSignInActivity()
        }
    }

    private fun signOutProcedure() {
        firebaseDir.removeEventListener(firebaseDbValueListener)
        dataListenerAdded = false
        // Download the online data
        firebaseDir.get().addOnSuccessListener { snapshot ->
            this.runOnUiThread {
                snapshot.value?.let { value ->
                    val downloadedData = Utils.deserializeTaskList(value as String)
                    Utils.writeLastFirebaseListToFile(this, taskList)
                    taskListOverwrite(downloadedData, pushToOnline = false)
                }
            }
        }


        Firebase.auth.signOut()
        setPreferences(preferences.copy(prefersOnline = false))
    }

    private fun beginSignInActivity() {
        // We shouldn't be starting the sign-in activity
        // if we are already signed in.
        assert(!isLoggedIn)

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

            // If Firebase already has data, then download it.
            // Otherwise upload ours.
            if(dataListenerAdded == false){
                setupFirebaseDb()
                dataListenerAdded = true
                firebaseDir.addValueEventListener(firebaseDbValueListener)
            }
            updateMenuLabels()
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