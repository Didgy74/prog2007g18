package prog2007.group18.todolist

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PersistentPreferences(
    var prefersOnline: Boolean = false,
    var showDoneTasks: Boolean = false,)
{
    companion object {
        private const val fileName: String = "preferences"

        fun encodeToString(input: PersistentPreferences) = Json.encodeToString(input)
        fun decodeFromString(string: String): PersistentPreferences {
            if (string == "") {
                return PersistentPreferences()
            }
            return Json.decodeFromString(string)
        }

        fun writeToFile(
            data: PersistentPreferences,
            ctx: Context)
        {
            Utils.writeStringToFile(
                ctx,
                encodeToString(data),
                fileName)
        }

        fun readFromFile(ctx: Context) =
            decodeFromString(Utils.loadStringFromFile(ctx, fileName))
    }
}