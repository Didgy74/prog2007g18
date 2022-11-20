package prog2007.group18.todolist

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

// This is the WorkManager task that
// runs periodically to setup alarms.
class NotificationRefreshWorker(appContextIn: Context, workerParams: WorkerParameters):
    Worker(appContextIn, workerParams)
{
    val app = appContextIn.applicationContext as TodoListApp
    override fun doWork(): Result {
        app.rebuildTaskListNotificationAlarms()
        return Result.success()
    }
}