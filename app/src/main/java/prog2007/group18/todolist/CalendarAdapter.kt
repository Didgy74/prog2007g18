package prog2007.group18.todolist

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDateTime

class CalendarAdapter (private val Days: MutableList<CalendarDay>, private val context: Context) : RecyclerView.Adapter <CalendarAdapter.DayViewHolder>(){

    class DayViewHolder(v: View) : RecyclerView.ViewHolder(v){
        var view: View = v
        //Each day will show number of tasks that day
        fun bindDay(day: LocalDateTime, numberOfTasks : Int, context: Context){
            val numberOfTasksTextView = view.findViewById<TextView>(R.id.numberOfTasks)
            numberOfTasksTextView.text = numberOfTasks.toString()
            val dayTextView= view.findViewById<TextView>(R.id.dayTextView)
            dayTextView.text = day.dayOfMonth.toString()

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        return DayViewHolder(
            LayoutInflater.from(parent.context)
            .inflate(R.layout.day_recyclerview_item_column,parent, false))

    }
    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        var day = Days[position]
        holder.bindDay(day.Date, day.numberOfTasks, context)
    }

    override fun getItemCount(): Int {
        return Days.count()
    }

}