package prog2007.group18.todolist

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

const val taskListDefaultFileName = "tasklist"
// It's important that this class, and any members is serializable
// in order to send the task across network or store to file.
@Serializable
@Parcelize
class Task(
    var title: String = "",
    var deadline: Deadline = Deadline()) : Parcelable {

    companion object {
        const val key = "task"
    }
}

// This entire class should probably be removed
// and use DateFormat or something in its place.
@Serializable
@Parcelize
class Deadline(
    var year: Int = 0,
    // Month is zero-indexed
    var monthOfYear: Int = 0,
    var dayOfMonth: Int = 0,
    var hourOfDay: Int = 0,
    var minuteOfHour: Int = 0) : Parcelable {

    // Probably won't need this in the future. Just needed this
    // to display it under testing.
    fun toFormattedString(): String {
        // Month is usually zero-indexed for some reason, this puts it in the
        // [1, 12] range.
        val actualMonthOfYear = monthOfYear + 1
        return "Date: $year / $actualMonthOfYear / $dayOfMonth. Time: $hourOfDay : $minuteOfHour"
    }
}

abstract class Utils {
    companion object {

        // Serializes a list of tasks into String
        //
        // Having this function wrapper lets us not care about
        // how the task list is serialized
        fun serializeTaskList(taskList: List<Task>) : String = Json.encodeToString(taskList)

        // Deserializes a previously serialized list of tasks, back into
        // its original value.
        //
        // Having this function wrapper lets us not care about
        // how the task list is deserialized
        fun deserializeTaskList(input: String) : List<Task> = Json.decodeFromString(input)

        fun writeTaskListToFile(
            context: Context,
            taskList: List<Task>,
            filename: String = taskListDefaultFileName)
        {
            val file = context.openFileOutput(filename, AppCompatActivity.MODE_PRIVATE)
            val writer = file.bufferedWriter()
            writer.write(serializeTaskList(taskList))
            writer.flush()
            file.close()
        }

        fun loadTaskListFromFile(
            context: Context,
            filename: String = taskListDefaultFileName) : List<Task>
        {
            val file = File(context.filesDir, filename)
            if (file.exists()) {
                // TODO: This is probably gonna need some error handling,
                // maybe show a dialog that says unable to load existing task-list
                // or something, and then assume it's all empty. Use exception-handling
                // for that, probably.
                val text = file.readText()
                if (text.isNotEmpty()) {
                    return deserializeTaskList(text)
                }
            }
            return listOf()
        }

        fun clearTaskListStorage(context: Context) {
            val file = File(context.filesDir, taskListDefaultFileName)
            if (file.exists())
                file.delete()
        }
    }
}

// This is a struct that describes the info of a task to be created,
// that will be returned from NewTaskListActivity
// Making the class Parcelable makes it easy to transfer across an Intent
// Member variables need to be declared in the constructor
// for the Parcelize to work on them, otherwise they will be ignored.
//
// For now this is separated from an actual Task, because
// I assumed there would be a difference between the struct
// needed to create a Task, versus the data of the Task itself,
// in the future.
@Parcelize
class CreateTaskJob(
    var title: String = "",
    var deadline: Deadline = Deadline()) : Parcelable {

    fun toIntent() : Intent {
        val intent = Intent()
        intent.putExtra(intentKey, this)
        return intent
    }

    companion object {
        const val intentKey = "task job"

        fun fromIntent(intent: Intent) : CreateTaskJob {
            val taskJob = intent.getParcelableExtra<CreateTaskJob>(intentKey)
            assert(taskJob != null)
            return taskJob!!
        }
    }
}
