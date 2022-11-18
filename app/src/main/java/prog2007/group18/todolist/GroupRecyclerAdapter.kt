package prog2007.group18.todolist

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GroupRecyclerAdapter(
    private val groups: MutableList<Pair<String,Int>>,
    private val context: Context):
    RecyclerView.Adapter <GroupRecyclerAdapter.GroupViewHolder>()
{
    class GroupViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        var view: View = v
        var groupName: String = ""
        fun bindGroup(groupName: String, groupID: Int, context: Context){
            val groupInfo = view.findViewById<TextView>(R.id.groupDataTextView)
            this.groupName = groupName
            groupInfo.text = "Group name: $groupName Group ID: $groupID"

            println("Teeeeeest + $groupID")

            groupInfo.setOnClickListener {
                val intent = Intent(context, GroupTasksActivity::class.java)
                intent.putExtra("groupID",groupID.toString())
                context.startActivity(intent)
            }
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        return GroupViewHolder(
            LayoutInflater.from(parent.context)
            .inflate(R.layout.group_recyclerview_item_row,parent, false))
    }
    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        holder.bindGroup(group.first, group.second, context)
        println("Test: $position")
    }

    override fun getItemCount() = groups.count()
}