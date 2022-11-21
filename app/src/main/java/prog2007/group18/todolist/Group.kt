package prog2007.group18.todolist

import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
data class Group(
    var ID : Int,
    var groupName : String,
    var membersAndScores : MutableList<Pair<String,Int>>
) {
}