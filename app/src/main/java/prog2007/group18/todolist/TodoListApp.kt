package prog2007.group18.todolist

import android.annotation.SuppressLint
import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

typealias TaskListChangeListener = (taskList: List<Task>, pushToOnline: Boolean) -> Unit

class TodoListApp : Application() {
    companion object {
        const val firebaseDbRepoUrl = "https://todolist-a4182-default-rtdb.europe-west1.firebasedatabase.app/"
        // Change this to "" if using the release config?
        const val firebaseTopLevelDirName = "nils-testing"
    }

    val isLoggedIn get() = Firebase.auth.currentUser != null
    private lateinit var firebaseDb: FirebaseDatabase
    private lateinit var _firebaseTopLevelDir: DatabaseReference
    val firebaseTopLevelDir get() = _firebaseTopLevelDir

    // Don't use directly.
    private val _taskListInternal = mutableListOf<Task>()
    // This makes a copy, since we are not allowed to access the internal data directly.
    val taskList get() = _taskListInternal.map{ it.copy() }.toList()
    @SuppressLint("NotifyDataSetChanged")
    private fun taskListNotifyChange(pushToOnline: Boolean = true) {

        for (listener in taskListNotifyChangeListeners) {
            listener.invoke(taskList, pushToOnline)
        }

        if (taskList.isEmpty()) {
            Utils.clearTaskListStorage(this)
        } else {
            Utils.writeTaskListToFile(this, taskList)
        }
    }
    fun taskListAdd(task: Task) = taskListAdd(listOf(task))
    fun taskListAdd(newTasks: List<Task>, pushToOnline: Boolean = true) {
        _taskListInternal.addAll(newTasks)
        taskListNotifyChange(pushToOnline = pushToOnline)
    }
    fun taskListOverwrite(newList: List<Task>, pushToOnline: Boolean = true) {
        _taskListInternal.clear()
        taskListAdd(newList, pushToOnline = pushToOnline)
    }
    fun taskListClear(pushToOnline: Boolean = true) =
        taskListOverwrite(listOf(), pushToOnline = pushToOnline)
    fun taskListSet(index: Int, newTask: Task) {
        _taskListInternal[index] = newTask
        taskListNotifyChange()
    }
    fun taskListSize() = taskList.size
    // Returns a copy
    fun taskListGet(index: Int): Task = taskList[index].copy()
    private val taskListNotifyChangeListeners = mutableListOf<TaskListChangeListener>()
    fun taskListAddNotifyChangeListener(listener: TaskListChangeListener) {
        // Check for duplicate first.
        if (!taskListNotifyChangeListeners.contains(listener))
            taskListNotifyChangeListeners.add(listener)
    }

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        firebaseDb = Firebase.database(firebaseDbRepoUrl)
        _firebaseTopLevelDir = Firebase.database.reference.child(firebaseTopLevelDirName)

        taskListOverwrite(Utils.loadTaskListFromFile(this))
        //taskListOverwrite(Task.exampleTasks())
    }

}