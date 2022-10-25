package prog2007.group18.todolist

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.DialogInterface
import android.icu.util.Calendar
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.DatePicker
import android.widget.TextView
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import java.time.LocalDateTime

class NewTaskActivity : AppCompatActivity() {
    // Couldn't figure out how to do this without storing
    // a Deadline as a variable yet.
    //
    // Ideally we would build the deadline
    // out of whatever is stored in the GUI
    private var deadline = LocalDateTime.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_task)

        // Use the current time as the default values for the deadline
        setCurrentDateTimeAsDeadline()
    }

    private fun setCurrentDateTimeAsDeadline() {
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        setDeadlineTime(hour, minute)
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH)
        val day = c.get(Calendar.DAY_OF_MONTH)
        setDeadlineDate(year, month, day)
    }

    private fun updateDeadlineLabel() {
        val dateTimeLabel = findViewById<TextView>(R.id.deadlineDateTimeLabel)
        dateTimeLabel.text = Task.formattedDeadline(deadline)
    }

    private fun setDeadlineDate(year: Int, monthOfYear: Int, dayOfMonth: Int) {
        deadline = deadline
            .withYear(year)
            .withMonth(monthOfYear)
            .withDayOfMonth(dayOfMonth);
        updateDeadlineLabel()
    }

    fun setDeadlineTime(hour: Int, minute: Int)  {
        deadline = deadline
            .withHour(hour)
            .withMinute(minute);
        updateDeadlineLabel()
    }


    fun onDonePressed(view: View) {
        // We should likely have some checks here, to see if this would be a valid task
        // and then show a little error prompt if i.e title is empty
        val titleInput = findViewById<AutoCompleteTextView>(R.id.titleInput)

        val intent = Task(
                title = titleInput.text.toString(),
                deadline)
            .toIntent()

        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    fun onSetDeadlinePressed(view: View) {
        DatePickerFragment(deadline).show(supportFragmentManager, "datePicker")
    }

    fun onDatePickerDismissed(cancel: Boolean) {
        // If the date-picker was not cancelled when dismissed,
        // we want to show the time picker immediately.
        if (!cancel)
            TimePickerFragment(deadline).show(supportFragmentManager, "timePicker")
    }

    // Reference: https://developer.android.com/develop/ui/views/components/pickers
    class DatePickerFragment(
        private val deadline: LocalDateTime) : DialogFragment(), DatePickerDialog.OnDateSetListener {
        private var dateWasSet = false

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            // Create a new instance of DatePickerDialog and return it
            return DatePickerDialog(
                requireContext(),
                this,
                deadline.year,
                deadline.month.value,
                deadline.dayOfMonth)
        }

        override fun onDismiss(dialog: DialogInterface) {
            super.onDismiss(dialog)
            (activity as NewTaskActivity).onDatePickerDismissed(!dateWasSet)
        }

        override fun onDateSet(view: DatePicker, year: Int, month: Int, day: Int) {
            // Do something with the date chosen by the user
            // Month is zero-indexed for some reason.
            (activity as NewTaskActivity).setDeadlineDate(year, month, day)
            dateWasSet = true
        }
    }

    // Reference: https://developer.android.com/develop/ui/views/components/pickers
    class TimePickerFragment(
        private val deadline: LocalDateTime) : DialogFragment(), TimePickerDialog.OnTimeSetListener {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            // Create a new instance of TimePickerDialog and return it
            return TimePickerDialog(
                activity,
                this,
                deadline.hour,
                deadline.minute,
                DateFormat.is24HourFormat(activity))
        }

        override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
            (activity as NewTaskActivity).setDeadlineTime(hourOfDay, minute)
        }
    }
}


