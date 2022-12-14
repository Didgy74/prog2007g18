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
    var inDeleteMode = false
    private fun isInDeleteMode() : Boolean{
        return inDeleteMode
    }
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
        val deleteItem = menu.findItem(R.id.deleteBtn)
        deleteItem.title = if (!inDeleteMode) { "Delete mode" } else { "Exit delete mode" }
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
            if (!task.isSuccessful) {
                Log.e("TodoList", "Unable to push updates online.")
            }
        }
    }
    private fun loginSetup(){
        setupFirebaseDb()
        findViewById<Button>(R.id.onlineGroupButton).setVisibility(View.VISIBLE)
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
    private fun writeLastFirebaseListToFile(taskList : List<Task>){
        //Utils.writeLastFirebaseListToFile()
    }

    private fun isOnline() : Boolean{
        return (isOnline(this) && todoListApp.isLoggedIn)
    }
    private val firebaseDbValueListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if(snapshot.value != null && isOnline()){
                dataListenerAdded = true
                //writeLastFirebaseListToFile()
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
    //This code finds the tasks with deadlines that have been passed and updates them if they're marked as daily or weekly tasks
    private fun setNewDeadlines(taskList : List<Task>) : List<Task>{
        var newTaskList = taskList
        for(task in taskList){
            if(LocalDateTime.now().isAfter(task.deadline)){
                if(task.frequency == Frequency.daily){
                    task.done = false
                    task.progress = 0
                    while (LocalDateTime.now().isAfter(task.deadline)){
                        task.deadline = task.deadline.plusDays(1)
                        task.lastEdited = LocalDateTime.now()
                    }

                } else if(task.frequency == Frequency.weekly){
                    task.done = false
                    task.progress = 0
                    while (LocalDateTime.now().isAfter(task.deadline)){
                        task.deadline = task.deadline.plusDays(7)
                        task.lastEdited = LocalDateTime.now()
                    }
                }
            }
        }
    return newTaskList

    }



    //Cloud tasks in last sync missing in new one should be deleted from the final list.
    //Cloud tasks missing from last sync that are in the new one are new and should be downloaded to the final list

    //Task missing from both syncs should be added to the final list
    //Tasks in both syncs missing from local should be deleted from the final list
    //For tasks in both lists, use timestamp and add the newest one to the final task list

    //Locally created tasks shouldn't just overwrite the firebase in case the user has multiple devices, so we run this sync function every time the task list is changed
    private fun sync(newlyRetrievedTaskList : List<Task>) : List<Task>{

        val localStoredTaskList = Utils.loadTaskListFromFile(this)
        val lastRetrievedTaskList = Utils.loadLastFirebaseListFromFile(this)

        //Create new FinalList that starts with all tasks from local storage that haven't been uploaded

        var bufferList = mutableListOf<Task>()

        //Add the cloud tasks that shouldn't be deleted
        for(task in newlyRetrievedTaskList){
            if(!toBeDeleted(task, localStoredTaskList, lastRetrievedTaskList, bufferList)) {
                //If the task has been changed locally since last sync, the firebase one shouldn't overwrite it
                var newestTask = getNewestVersion(localStoredTaskList,task)
                bufferList.add(newestTask)
            }
        }
        for (task in localStoredTaskList){
            //Tasks that haven't been uploaded yet
            if(!containsTask(lastRetrievedTaskList, task)){
                //Not allowing two tasks with the same name
                if(!containsTask(bufferList, task)){
                    bufferList.add(task)
                }

            }
        }

        //Overwriting last with new
        Utils.writeLastFirebaseListToFile(this, newlyRetrievedTaskList)
        setNewDeadlines(bufferList)
        return bufferList
    }

    private fun getNewestVersion(localList: List<Task>, firebaseTask : Task) : Task{
        for (task in localList){
            //We used to have title and deadline as unique identifier, but had to change this when extending deadlines were added
            if(task.title == firebaseTask.title){
                if(task.lastEdited > firebaseTask.lastEdited){
                    return task
                }else return firebaseTask
            }
        }
        return firebaseTask
    }

    private fun containsTask(taskList2 : List<Task>, task2 : Task) : Boolean{
        for (task in taskList2) {
            if(task.title ==  task2.title){
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
        //Delete task if duplicate
        if(containsTask(bufferList,taskFromNewSync)){return true}
        //taskFromNewSync must not be deleted if not in old sync
        return false
    }
    //Variable that keeps track of which month the user will see. +2 means two months from now, -1 means last month.
    var monthInCalendar : Long = 0
    private fun calendarListSetUp(){
        // getting the recyclerview by its id
        val recyclerview = findViewById<RecyclerView>(R.id.calendarView)
        val localStoredTaskList = Utils.loadTaskListFromFile(this)
        // this creates a horizontal layout Manager
        recyclerview.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        //Plussing with monthInCalendar so that we can get the previous and next months if those buttons are pressed
        var currentMonth = LocalDateTime.now().month
        var eachDate = LocalDateTime.now() //.plusMonths(monthInCalendar)
        var longZero : Long = 0
        //Getting other months if user has clicked on previous or next months
        if(monthInCalendar != longZero){
            currentMonth = LocalDateTime.now().month.plus(monthInCalendar)
            eachDate = LocalDateTime.now().plusMonths(monthInCalendar)
            eachDate = eachDate.withDayOfMonth(1)
        }

        var  listOfRemainingDays : MutableList<CalendarDay> = mutableListOf()
        //For each remaining day in the month
        while(currentMonth ==  eachDate.month){
            var tasksPerDay = 0
            //Find number of tasks for current day
            for (task in localStoredTaskList.filter { it -> it.deadline.month == eachDate.month && it.deadline.dayOfMonth == eachDate.dayOfMonth }) {
                tasksPerDay++
            }
            listOfRemainingDays.add(CalendarDay(eachDate,tasksPerDay))
            eachDate = eachDate.plusDays(1)
        }



        val adapter = CalendarAdapter(listOfRemainingDays, this)

        // Setting the Adapter with the recyclerview
        recyclerview.adapter = adapter

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        var onlineGroupButton : Button = findViewById(R.id.onlineGroupButton)
        onlineGroupButton.setOnClickListener(){
            if(isOnline()){
                val intent = Intent(this, OnlineGroupActivity::class.java)
                startActivity(intent)
            }
        }

        var previousMonthButton : Button = findViewById(R.id.previousMonthButton)
        previousMonthButton.setOnClickListener(){
            monthInCalendar--
            calendarListSetUp()
        }
        var nextMonthButton : Button = findViewById(R.id.nextMonthButton)
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
            R.id.deleteBtn -> {
                toggleDelete()
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
            { index -> todoListApp.taskListRemove(index)},
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
        todoListApp.taskListOverwrite(setNewDeadlines(todoListApp.taskList), false)
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
        val intent = Intent(this, NewPrivateTaskActivity::class.java)
        newTaskActivityLauncher.launch(intent)
    }

    private fun toggleSignInOut() {
        if (isLoggedIn) {
            signOutProcedure()
        } else {
            beginSignInActivity()
        }
    }
    private fun toggleDelete() {
        inDeleteMode = !inDeleteMode
        todoListApp.toggleDeleteMode()
        updateMenuLabels()
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
    //Got this code from stackOverlow. Figured it would be okay to copy and paste since we didn't learn it in class.
    //https://stackoverflow.com/questions/51141970/check-internet-connectivity-android-in-kotlin
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