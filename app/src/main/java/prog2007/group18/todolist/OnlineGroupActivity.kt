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

private const val firebaseDbRepo = "https://todolist-a4182-default-rtdb.europe-west1.firebasedatabase.app/"
// Change this to "" if using the release config? private const val firebaseDirName = "erlend-testing"


class OnlineGroupActivity : AppCompatActivity() {
    private lateinit var firebaseDb: FirebaseDatabase
    private lateinit var firebaseDir: DatabaseReference
    private lateinit var recyclerAdapter: GroupRecyclerAdapter
    private lateinit var recyclerView: RecyclerView
    private var listOfGroups = mutableListOf<Pair<String, Int>>()
    private fun setupFirebaseDb() {
        //TODO Make a firebase list with all group IDs so that joinGroup function can check if it exists and get the name
        //TODO firebaseDb.reference.child(groupInfo)
        firebaseDb = Firebase.database(firebaseDbRepo)
        //firebaseDir keeps track of which users that are in which groups
        //DELETE??? firebaseDir = firebaseDb.reference.child("Groups")

        firebaseDir = firebaseDb.reference.child(Firebase.auth.currentUser?.uid!! + "Groups")

        firebaseDir.addValueEventListener(firebaseDbValueListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_group)

        setupFirebaseDb()

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true)
        }

        firebaseDir.addValueEventListener(firebaseDbValueListener)
        recyclerAdapter = GroupRecyclerAdapter(listOfGroups, this)
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
        recyclerAdapter = GroupRecyclerAdapter(listOfGroups, this)
        recyclerView.adapter = recyclerAdapter
    }
    private val firebaseDbValueListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            if(snapshot.value != null){
                //Turn it to string
                val allGroups = Json.decodeFromString(snapshot.value as String) as List<Pair<String, Int>>
                listOfGroups = allGroups.toMutableList()
                updateAdapter()
                /*
                var allGroups = snapshot.value as MutableMap<String, Int>
                listOfGroups = (allGroups.toList()).toMutableList()
                recyclerAdapter.notifyDataSetChanged()

                 */
            }


            //TODO NotifyRecyclerViewChange
        }
        override fun onCancelled(error: DatabaseError) {}
    }
    private fun createNewGroup(groupName: String){
        //TODO Ensure truly unique ID
        val groupID = (0..10000).random()
        val group = Pair(groupName, groupID)
        listOfGroups.add(group)
        firebaseDir.setValue(Json.encodeToString(listOfGroups))
        recyclerAdapter = GroupRecyclerAdapter(listOfGroups, this)
        recyclerView.adapter = recyclerAdapter
        println("User's groups: " + listOfGroups)
        println(Firebase.auth.currentUser?.uid!!)
        //Each firebaseGroup has a taskList
        //Creating ID with random number between 1 and 10000
        //Check if ID exists. Try again if it does.
        val placeholderList = mutableListOf<Task>()
        (firebaseDb.reference.child(groupID.toString())).setValue(Json.encodeToString(placeholderList))
        //firebaseGroup.setValue("Test")
        //Add to list of all groups.
        //

    }
    private fun joinGroup(groupID: Int){
        //TODO Make sure user only joins existing groups. Also not an already joined group
        val group = Pair("Joined Group", groupID)
        listOfGroups.add(group)
        firebaseDir.setValue(Json.encodeToString(listOfGroups))
        recyclerAdapter = GroupRecyclerAdapter(listOfGroups, this)
        recyclerView.adapter = recyclerAdapter
    }

}

