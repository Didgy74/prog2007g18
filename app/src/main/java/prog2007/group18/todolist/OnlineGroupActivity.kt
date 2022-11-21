package prog2007.group18.todolist

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

private const val firebaseDbRepo = "https://todolist-a4182-default-rtdb.europe-west1.firebasedatabase.app/"
// Change this to "" if using the release config? private const val firebaseDirName = "erlend-testing"


class OnlineGroupActivity : AppCompatActivity() {
    private lateinit var firebaseDb: FirebaseDatabase
    private lateinit var firebaseDir: DatabaseReference
    private lateinit var firebaseGroups: DatabaseReference
    private lateinit var recyclerAdapter: GroupRecyclerAdapter
    private lateinit var recyclerView: RecyclerView
    private var listOfOwnGroups = mutableListOf<Pair<String, Int>>()
    private var listOfFirebaseGroups = mutableListOf<Group>()
    private fun setupFirebaseDb() {
        firebaseDb = Firebase.database(firebaseDbRepo)
        //firebaseDir keeps track of which users that are in which groups
        //DELETE??? firebaseDir = firebaseDb.reference.child("Groups")

        firebaseDir = firebaseDb.reference.child(Firebase.auth.currentUser?.uid!! + "Groups")
        firebaseDir.addValueEventListener(firebaseDbValueListenerOwnGroups)

        firebaseGroups = firebaseDb.reference.child("Groups")
        firebaseGroups.addValueEventListener(firebaseDbValueListenerAllGroups)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_group)

        setupFirebaseDb()

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
        }

        //firebaseDir.addValueEventListener(firebaseDbValueListener)
        recyclerAdapter = GroupRecyclerAdapter(listOfOwnGroups, this)
        recyclerView = findViewById(R.id.groupRecyclerView)
        // Setup the RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recyclerAdapter
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                recyclerView.context, DividerItemDecoration.VERTICAL
            )
        )
        val groupNameInput: EditText = findViewById(R.id.groupNameInput)
        val createNewGroupButton: Button = findViewById(R.id.button3)
        createNewGroupButton.setOnClickListener {
            createNewGroup(groupNameInput.text.toString())
        }
        val groupIDInput: EditText = findViewById(R.id.groupIDInput)
        val joinGroupButton: Button = findViewById(R.id.button2)
        joinGroupButton.setOnClickListener(){
            joinGroup(groupIDInput.text.toString().toInt())
        }
    }
    private fun updateAdapter(){
        recyclerAdapter = GroupRecyclerAdapter(listOfOwnGroups, this)
        recyclerView.adapter = recyclerAdapter
    }
    private val firebaseDbValueListenerOwnGroups = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if(snapshot.value != null){
                //Turn it to string
                val ownGroups = Json.decodeFromString(snapshot.value as String) as List<Pair<String, Int>>
                listOfOwnGroups = ownGroups.toMutableList()
                updateAdapter()

            }

        }
        override fun onCancelled(error: DatabaseError) {}
    }
    private val firebaseDbValueListenerAllGroups = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if(snapshot.value != null){
                //Turn it to string
                val allGroups = Json.decodeFromString(snapshot.value as String) as List<Group>
                listOfFirebaseGroups = allGroups.toMutableList()

            }

        }
        override fun onCancelled(error: DatabaseError) {}
    }
    private fun createNewGroup(groupName: String){
        //TODO Ensure truly unique ID
        //Creating ID with random number between 1 and 10000
        //Check if ID exists. Try again if it does.
        val groupID = (0..100000).random()
        val group = Pair(groupName, groupID)
        listOfOwnGroups.add(group)
        firebaseDir.setValue(Json.encodeToString(listOfOwnGroups))
        recyclerAdapter = GroupRecyclerAdapter(listOfOwnGroups, this)
        recyclerView.adapter = recyclerAdapter
        //Each firebaseGroup has a taskList
        val placeholderList = mutableListOf<Task>()
        //Making the directory for the group
        //Firebase.auth.currentUser?.uid!!
        var memberAndScoreList = mutableListOf<Pair<String,Int>>()
        memberAndScoreList.add(Pair(Firebase.auth.currentUser?.uid!!,0))
        listOfFirebaseGroups.add(Group(groupID,groupName,memberAndScoreList))
        (firebaseDb.reference.child(groupID.toString())).setValue(Json.encodeToString(placeholderList))
        firebaseDb.reference.child("Groups").setValue(Json.encodeToString(listOfFirebaseGroups))
        //firebaseGroup.setValue("Test")
        //Add to list of all groups.
        //

    }
    private fun joinGroup(groupID: Int){
        //TODO Make sure user only joins existing groups. Also not an already joined group
        val groupName = validJoin(groupID)
        if(groupName == " "){
            return
        }
        val group = Pair(groupName, groupID)
        listOfOwnGroups.add(group)
        firebaseDir.setValue(Json.encodeToString(listOfOwnGroups))
        recyclerAdapter = GroupRecyclerAdapter(listOfOwnGroups, this)
        recyclerView.adapter = recyclerAdapter


        firebaseDb.reference.child("Groups").setValue(Json.encodeToString(listOfFirebaseGroups))
    }
    private fun validJoin(groupID: Int) : String{
            for(joinedGroup in listOfOwnGroups){
                if(joinedGroup.second == groupID){
                    return " "
                }
            }
            for(firebaseGroup in listOfFirebaseGroups){
                if(firebaseGroup.ID == groupID){
                    firebaseGroup.membersAndScores.add(Pair(Firebase.auth.currentUser?.uid!!,0))
                    return firebaseGroup.groupName
                }
            }
        return " "
    }
}

