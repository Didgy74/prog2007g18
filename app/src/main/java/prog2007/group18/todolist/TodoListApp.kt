package prog2007.group18.todolist

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

typealias TaskListChangeListener = (taskList: List<Task>, pushToOnline: Boolean) -> Unit

class TodoListApp : Application() {
    companion object {
        const val firebaseDbRepoUrl = "https://todolist-a4182-default-rtdb.europe-west1.firebasedatabase.app/"
        // Change this to "" if using the release config?
        const val firebaseTopLevelDirName = "main-testing"

        const val notificationChannelId = "0"
        val notificationWorkerRefreshPeriod = Duration.ofHours(1)
        val notificationWorkerFlexPeriod = Duration.ofMinutes(15)
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

        rebuildTaskListNotificationAlarms()

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
    fun taskListSet(index: Int, newTask: Task, pushToOnline: Boolean = true) {
        _taskListInternal[index] = newTask
        taskListNotifyChange(pushToOnline = pushToOnline)
    }
    fun taskListRemove(index: Int, pushToOnline: Boolean = true) {
        _taskListInternal.removeAt(index)
        taskListNotifyChange(pushToOnline = pushToOnline)
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

        initNotifications()
    }


    //
    // Notifications
    //
    private fun initNotifications() {
        createNotificationChannel()
        rebuildTaskListNotificationAlarms()
        val myUploadWork =
            PeriodicWorkRequestBuilder<NotificationRefreshWorker>(
                notificationWorkerRefreshPeriod,
                notificationWorkerFlexPeriod)
                .setInitialDelay(notificationWorkerRefreshPeriod)
                .build()
        WorkManager
            .getInstance(this)
            .enqueueUniquePeriodicWork(
                "A",
                ExistingPeriodicWorkPolicy.REPLACE,
                myUploadWork)
    }
    private fun createNotificationChannel() {
        val name = "Task notifications"
        val descriptionText = "Get notifications about tasks that expire."
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(notificationChannelId, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    private var _notificationIdTracker = 0
    private fun getNextNotificationId() = _notificationIdTracker++

    var pendingTaskListAlarms = mutableListOf<AlarmManager.OnAlarmListener>()
    private fun clearPendingTaskListAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (callback in pendingTaskListAlarms) {
            alarmManager.cancel(callback)
        }
        pendingTaskListAlarms.clear()
    }

    fun rebuildTaskListNotificationAlarms() {
        clearPendingTaskListAlarms()
        val now = LocalDateTime.now()
        taskList
            .filter { it.notify }
            .filter {
                // We only want to set up notifications for tasks that are
                // set up for deadline in the future.
                it.deadline >= now
            }
            .forEach { setAlarmEventForTask(it) }
    }

    private fun runNotificationForTask(task: Task) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.areNotificationsEnabled()) {
            val builder = NotificationCompat.Builder(this, TodoListApp.notificationChannelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(task.title)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            with(NotificationManagerCompat.from(this)) {
                // notificationId is a unique int for each notification that you must define
                notify(getNextNotificationId(), builder.build())
            }
        }
    }

    private fun setAlarmEventForTask(task: Task) {
        val callback = AlarmManager.OnAlarmListener {
            // First check if this task has been marked done
            val foundTask = taskList
                .find { it.title == task.title && it.deadline == task.deadline }
            if (foundTask != null && !foundTask.done)
                runNotificationForTask(task)
        }

        val alarmManager =
            this.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val zone = ZoneId.systemDefault()
        val timeToTriggerMilli =
            task.deadline.atZone(zone).toInstant().toEpochMilli()
        pendingTaskListAlarms.add(callback)
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            timeToTriggerMilli,
            task.title,
            callback,
            null)
    }
}