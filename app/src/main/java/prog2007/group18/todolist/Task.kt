package prog2007.group18.todolist

import android.content.Intent
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
    var frequency : Frequency = Frequency.oneTime,
    var notify: Boolean = false)
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

        fun exampleTasks(): List<Task> {
            val now = LocalDateTime.now()
            return listOf(
                Task(
                    "Do laundry",
                    now.plusSeconds(10),
                    notify = true),
                Task(
                    "Go to church",
                    now.plusSeconds(20),
                    notify = true),
                Task(
                    "Go to school",
                    now),
                Task(
                    "Pray to God",
                    now),
                Task(
                    "Perform dinner prayer",
                    now),
                Task(
                    "Smite the heathens",
                    now),
            )
        }
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