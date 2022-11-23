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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase
import java.time.LocalDateTime

class MainActivity : AppCompatActivity() {
    companion object {
        private const val firebaseDbRepo = "https://todolist-a4182-default-rtdb.europe-west1.firebasedatabase.app/"
        // Change this to "" if using the release config?
        private const val firebaseDirName = "erlend-testing"
    }
    val todoListApp get() = application as TodoListApp

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

    private lateinit var firebaseDir: DatabaseReference
    private lateinit var recyclerAdapter: ListRecyclerAdapter
    private val taskListChangeListener: TaskListChangeListener = { taskList, pushToOnline ->
        recyclerView.post {
            recyclerAdapter.notifyDataSetChanged()
        }
        calendarListSetUp()
        if (checkIfLoggedIn()  && pushToOnline && isOnline(this)) {
            // Only run if we are logged in?
            if (dataListenerAdded == false) {
                loginSetup()
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
    private fun loginSetup(){
        setupFirebaseDb()
        findViewById<Button>(R.id.onlineGroupButton).setVisibility(View.INVISIBLE)
        firebaseDir.addValueEventListener(firebaseDbValueListener)
    }
    private fun checkIfLoggedIn() : Boolean{
        val user = Firebase.auth.currentUser
        return user != null
    }
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
                dataListenerAdded = true
                var loadedList = Utils.deserializeTaskList(snapshot.value as String)
                //lastLoadedList = loadedList.toMutableList()
                val syncedList = sync(loadedList)
                if(loadedList != syncedList){
                    todoListApp.taskListOverwrite(syncedList, pushToOnline = true)
                } else{
                    todoListApp.taskListOverwrite(syncedList, pushToOnline = false)
                }
            }

        }
        override fun onCancelled(error: DatabaseError) {}
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
                var newestTask = getNewestVersion(localStoredTaskList,task)
                bufferList.add(newestTask)
            }
        }

        //Overwriting last with new
        Utils.writeLastFirebaseListToFile(this, newlyRetrievedTaskList)
        setNewDeadlines(bufferList)
        return bufferList
    }

    private fun getNewestVersion(localList: List<Task>, firebaseTask : Task) : Task{
        for (task in localList){
            //Title and deadline values as an unique identifier
            if(task.title == firebaseTask.title && task.deadline == firebaseTask.deadline){
                if(task.lastEdited > firebaseTask.lastEdited){
                    return task
                }else return firebaseTask
            }
        }
        return firebaseTask
    }

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
    //Add variable for previous/next month
    var monthInCalendar : Long = 0
    private fun calendarListSetUp(){
        //var newCalendar : CalendarFragment =
        // getting the recyclerview by its id
        val recyclerview = findViewById<RecyclerView>(R.id.calendarView)
        val localStoredTaskList = Utils.loadTaskListFromFile(this)
        // this creates a vertical layout Manager
        recyclerview.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                recyclerView.context, DividerItemDecoration.HORIZONTAL
            )
        )
        //Plussing with monthInCalendar so that we can get the previous and next months if those buttons are pressed
        var currentMonth = LocalDateTime.now().month
        var eachDate = LocalDateTime.now() //.plusMonths(monthInCalendar)
        var longZero : Long = 0
        if(monthInCalendar != longZero){
            currentMonth = LocalDateTime.now().month.plus(monthInCalendar)
            eachDate = LocalDateTime.now().plusMonths(monthInCalendar)
            eachDate = eachDate.withDayOfMonth(1)
        }

        var  listOfRemainingDays : MutableList<CalendarDay> = mutableListOf()
        while(currentMonth ==  eachDate.month){
            var tasksPerDay = 0
            for (task in localStoredTaskList.filter { it -> it.deadline.month == eachDate.month && it.deadline.dayOfMonth == eachDate.dayOfMonth }) {
                tasksPerDay++
            }
            listOfRemainingDays.add(CalendarDay(eachDate,tasksPerDay))
            eachDate = eachDate.plusDays(1)
        }



        // This will pass the ArrayList to our Adapter
        val adapter = CalendarAdapter(listOfRemainingDays, this)

        // Setting the Adapter with the recyclerview
        recyclerview.adapter = adapter

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        var onlineGroupButton : Button
        onlineGroupButton= findViewById(R.id.onlineGroupButton)
        onlineGroupButton.setOnClickListener(){
            if(checkIfLoggedIn()){
                val intent = Intent(this, OnlineGroupActivity::class.java)
                startActivity(intent)
            }
        }

        var previousMonthButton : Button
        previousMonthButton = findViewById(R.id.previousMonthButton)
        previousMonthButton.setOnClickListener(){
            monthInCalendar--
            calendarListSetUp()
        }
        var nextMonthButton : Button
        nextMonthButton = findViewById(R.id.nextMonthButton)
        nextMonthButton.setOnClickListener(){
            monthInCalendar++
            calendarListSetUp()
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
                todoListApp.taskListClear()
                true
            }
            R.id.menuAddExamplesBtn -> {
                todoListApp.taskListAdd(Task.exampleTasks())
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
        firebaseDir = todoListApp.firebaseTopLevelDir.child(Firebase.auth.currentUser!!.uid)
    }

    private fun initialSetup() {
        _loadedPreferences = PersistentPreferences.readFromFile(this)

        recyclerAdapter = ListRecyclerAdapter(
            { todoListApp.taskList },
            { index, task -> todoListApp.taskListSet(index, task) },
            preferences.showDoneTasks)
        recyclerView = findViewById(R.id.mainList)
        if (todoListApp.isLoggedIn && isOnline(this) && !dataListenerAdded){
            loginSetup()
        }

        // Setup the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recyclerAdapter

        todoListApp.taskListAddNotifyChangeListener(taskListChangeListener)

        // Setup the FAB that opens NewTaskActivity
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener { beginNewTaskActivity() }

        calendarListSetUp()

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
            todoListApp.taskListAdd(newTask)
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
        findViewById<Button>(R.id.onlineGroupButton).setVisibility(View.INVISIBLE)
        // Download the online data
        firebaseDir.get().addOnSuccessListener { snapshot ->
            this.runOnUiThread {
                snapshot.value?.let { value ->
                    val downloadedData = Utils.deserializeTaskList(value as String)
                    Utils.writeLastFirebaseListToFile(this, todoListApp.taskList)
                    todoListApp.taskListOverwrite(downloadedData, pushToOnline = false)
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
                loginSetup()
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