package prog2007.group18.todolist

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.appcompat.app.AppCompatActivity
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val taskListDefaultFileName = "tasklist"

// It's important that this class, and any members is serializable
// in order to send the task across network or store to file.
enum class Frequency {
    oneTime, daily, weekly
}

@Serializable
@Parcelize
data class Task(
    var title: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    var deadline: LocalDateTime,
    var done: Boolean = false,
    var progressTask : Boolean = false,
    var progress : Int = 0,
    var goal : Int = 0,
    var frequency : Frequency = Frequency.oneTime,)
    : Parcelable
{
    fun toIntent() = Intent().apply {
        putExtra(intentKey, this@Task)
    }

    fun formattedDeadline(): String = formattedDeadline(deadline)

    companion object {
        private const val intentKey = "task"

        fun fromIntent(intent: Intent) =
            intent.getParcelableExtra<Task>(intentKey)!!

        fun formattedDeadline(input: LocalDateTime): String =
            input.format(DateTimeFormatter.ofPattern("uuuu LLLL d - HH:mm"))

        fun exampleTasks() = listOf(
            Task(
                "Do laundry",
                LocalDateTime.now()),
            Task(
                "Go to church",
                LocalDateTime.now()),
            Task(
                "Go to school",
                LocalDateTime.now()),
            Task(
                "Pray to God",
                LocalDateTime.now()),
            Task(
                "Perform dinner prayer",
                LocalDateTime.now()),
            Task(
                "Smite the heathens",
                LocalDateTime.now()),)
    }
}

// The Kotlin serializer doesn't work on Java serializable types, so this is some wrapper
// serializing LocalDateTime?
// I just fitted the code from
// https://stackoverflow.com/questions/65398284/kotlin-serialization-serializer-has-not-been-found-for-type-uuid
object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDateTime {
        return LocalDateTime.parse(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.toString())
    }
}