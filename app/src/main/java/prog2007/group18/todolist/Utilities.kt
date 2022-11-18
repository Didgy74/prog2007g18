package prog2007.group18.todolist

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File


typealias Utils = Utilities
abstract class Utilities {
    companion object {

        const val firebaseDbRepo = "https://todolist-a4182-default-rtdb.europe-west1.firebasedatabase.app/"
        // Change this to "" if using the release config?
        const val firebaseDirName = "erlend-testing"
        const val firebaseListDefaultFileName = "firebaselist"

        fun serializeTask(task: Task) = Json.encodeToString(task)
        fun deserializeTask(string: String): Task = Json.decodeFromString(string)

        // Serializes a list of tasks into String
        //
        // Having this function wrapper lets us not care about
        // how the task list is serialized
        fun serializeTaskList(taskList: List<Task>): String {
            if (taskList.isEmpty()) {
                return ""
            }
            return Json.encodeToString(taskList)
        }

        // Deserializes a previously serialized list of tasks, back into
        // its original value.
        //
        // Having this function wrapper lets us not care about
        // how the task list is deserialized
        fun deserializeTaskList(input: String): List<Task> {
            if (input.isEmpty())
                return listOf()
            return Json.decodeFromString(input)
        }

        fun writeStringToFile(
            context: Context,
            string: String,
            filename: String)
        {
            val file = context.openFileOutput(filename, AppCompatActivity.MODE_PRIVATE)
            val writer = file.bufferedWriter()
            writer.write(string)
            writer.flush()
            file.close()
        }

        fun writeTaskListToFile(
            context: Context,
            taskList: List<Task>,
            filename: String = taskListDefaultFileName) = writeStringToFile(
                context,
                serializeTaskList(taskList),
                filename)

        fun loadStringFromFile(
            context: Context,
            filename: String): String
        {
            val file = File(context.filesDir, filename)
            if (file.exists()) {
                return file.readText()
            }
            return ""
        }

        fun loadTaskListFromFile(
            context: Context,
            filename: String = taskListDefaultFileName): List<Task>
        {
            val text = loadStringFromFile(context, filename)
            if (text.isNotEmpty())
                return deserializeTaskList(text)
            return listOf()
        }
        fun loadLastFirebaseListFromFile(
            context: Context,
            filename: String = firebaseListDefaultFileName) =
                loadTaskListFromFile(context, filename)

        fun writeLastFirebaseListToFile(
            context: Context,
            taskList: List<Task>,
            filename: String = firebaseListDefaultFileName) =
                writeTaskListToFile(context, taskList, filename)

        fun clearTaskListStorage(context: Context) {
            val file = File(context.filesDir, taskListDefaultFileName)
            if (file.exists())
                file.delete()
        }
    }
}