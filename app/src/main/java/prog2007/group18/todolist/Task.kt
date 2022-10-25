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
data class Task(
    var title: String = "",
    var deadline: Deadline = Deadline()) : Parcelable {

    fun toIntent(): Intent = Intent().apply {
        putExtra(intentKey, this@Task)
    }

    companion object {
        private const val intentKey = "task"

        fun fromIntent(intent: Intent): Task =
            intent.getParcelableExtra(intentKey)!!
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
