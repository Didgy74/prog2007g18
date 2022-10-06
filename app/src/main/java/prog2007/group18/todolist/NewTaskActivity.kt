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

class NewTaskActivity : AppCompatActivity() {
    // Couldn't figure out how to do this without storing
    // a Deadline as a variable yet.
    //
    // Ideally we would build the deadline
    // out of whatever is stored in the GUI
    private var deadline = Deadline()

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

    private fun setDeadlineDate(year: Int, monthOfYear: Int, dayOfMonth: Int) {
        deadline.year = year
        deadline.monthOfYear = monthOfYear
        deadline.dayOfMonth = dayOfMonth
        // Update the label
        val dateTimeLabel = findViewById<TextView>(R.id.deadlineDateTimeLabel)
        dateTimeLabel.text = deadline.toFormattedString()
    }

    fun setDeadlineTime(hour: Int, minute: Int)  {
        deadline.hourOfDay = hour
        deadline.minuteOfHour = minute
        // Update the label
        val dateTimeLabel = findViewById<TextView>(R.id.deadlineDateTimeLabel)
        dateTimeLabel.text = deadline.toFormattedString()
    }

    fun onDonePressed(view: View) {
        val newTask = CreateTaskJob()

        val titleInput = findViewById<AutoCompleteTextView>(R.id.titleInput)

        newTask.title = titleInput.text.toString()
        newTask.deadline = deadline

        setResult(Activity.RESULT_OK, newTask.toIntent())
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
        private val deadline: Deadline) : DialogFragment(), DatePickerDialog.OnDateSetListener {
        private var dateWasSet = false

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            // Create a new instance of DatePickerDialog and return it
            return DatePickerDialog(
                requireContext(),
                this,
                deadline.year,
                deadline.monthOfYear,
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
        private val deadline: Deadline) : DialogFragment(), TimePickerDialog.OnTimeSetListener {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            // Create a new instance of TimePickerDialog and return it
            return TimePickerDialog(
                activity,
                this,
                deadline.hourOfDay,
                deadline.minuteOfHour,
                DateFormat.is24HourFormat(activity))
        }

        override fun onTimeSet(view: TimePicker, hourOfDay: Int, minute: Int) {
            (activity as NewTaskActivity).setDeadlineTime(hourOfDay, minute)
        }
    }
}


