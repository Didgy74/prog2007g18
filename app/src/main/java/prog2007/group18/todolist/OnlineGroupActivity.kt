package prog2007.group18.todolist

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var firebaseGroups: DatabaseReference
    private lateinit var recyclerAdapter: GroupRecyclerAdapter
    private lateinit var recyclerView: RecyclerView
    //List of pairs with group names and IDs
    private var listOfOwnGroups = mutableListOf<Pair<String, Int>>()
    private var listOfFirebaseGroups = mutableListOf<Group>()
    private fun setupFirebaseDb() {
        firebaseDb = Firebase.database(firebaseDbRepo)

        firebaseDir = firebaseDb.reference.child(Firebase.auth.currentUser?.uid!! + "Groups")
        firebaseDir.addValueEventListener(firebaseDbValueListenerOwnGroups)

        firebaseGroups = firebaseDb.reference.child("Groups")
        firebaseGroups.addValueEventListener(firebaseDbValueListenerAllGroups)
    }
    private fun isOnline() : Boolean{
        return (isOnline(this) && Firebase.auth.currentUser != null)
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
        recyclerAdapter = GroupRecyclerAdapter(this, listOfOwnGroups, this)
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
            if(isOnline()) {
                createNewGroup(groupNameInput.text.toString())
            }
        }
        val groupIDInput: EditText = findViewById(R.id.groupIDInput)
        val joinGroupButton: Button = findViewById(R.id.button2)
        joinGroupButton.setOnClickListener(){
            if(isOnline()){

                if (validIdInput(groupIDInput.text.toString())) {
                    joinGroup(groupIDInput.text.toString().toInt())
                } else findViewById<TextView>(R.id.errorMessage).text = "Please write a valid ID"
            }
        }
    }
    private fun updateAdapter(){
        recyclerAdapter = GroupRecyclerAdapter(this, listOfOwnGroups, this)
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
        //Creating ID with random number between 1 and 10000
        //Check if ID exists. Try again if it does.
        var groupID = (0..100000).random()
        while(groupExists(groupID)){
            groupID = (0..100000).random()
        }
        val group = Pair(groupName, groupID)
        listOfOwnGroups.add(group)
        firebaseDir.setValue(Json.encodeToString(listOfOwnGroups))
        recyclerAdapter = GroupRecyclerAdapter(this, listOfOwnGroups, this)
        recyclerView.adapter = recyclerAdapter
        //Each firebaseGroup has a taskList
        val placeholderList = mutableListOf<Task>()
        var memberAndScoreList = mutableListOf<Pair<String,Int>>()
        memberAndScoreList.add(Pair(Firebase.auth.currentUser?.uid!!,0))
        listOfFirebaseGroups.add(Group(groupID,groupName,memberAndScoreList))
        //Making the directory for the group
        (firebaseDb.reference.child(groupID.toString())).setValue(Json.encodeToString(placeholderList))
        firebaseDb.reference.child("Groups").setValue(Json.encodeToString(listOfFirebaseGroups))

    }
    private fun joinGroup(groupID: Int){
        val groupName = validJoin(groupID)
        if(groupName == " "){
            return
        }
        val group = Pair(groupName, groupID)
        listOfOwnGroups.add(group)
        firebaseDir.setValue(Json.encodeToString(listOfOwnGroups))
        recyclerAdapter = GroupRecyclerAdapter(this, listOfOwnGroups, this)
        recyclerView.adapter = recyclerAdapter


        firebaseDb.reference.child("Groups").setValue(Json.encodeToString(listOfFirebaseGroups))
    }
    private fun validIdInput(input : String?): Boolean{
            if (input.isNullOrEmpty()) {
                return false
            }

            return input.all { Character.isDigit(it) }

    }
    private fun groupExists(groupID: Int) : Boolean{
        for(joinedGroup in listOfOwnGroups){
            if(joinedGroup.second == groupID){
                return true
            }
        }
        return false
    }
    private fun validJoin(groupID: Int) : String{
        var errorTextView = findViewById<TextView>(R.id.errorMessage)
        if(groupExists(groupID)){
            errorTextView.text = "You are already in this group"
            return " "
        }
        for(firebaseGroup in listOfFirebaseGroups){
            if(firebaseGroup.ID == groupID){
                firebaseGroup.membersAndScores.add(Pair(Firebase.auth.currentUser?.uid!!,0))
                return firebaseGroup.groupName
            }
        }
        errorTextView.text = "This group does not exist"
        return " "
    }
    fun leaveGroup(groupID: Int, groupName: String){
        if(!isOnline()) return
        var groupToBeRemoved : Group? = null
        for(firebaseGroup in listOfFirebaseGroups){
            if(firebaseGroup.ID == groupID){
                firebaseGroup.membersAndScores.remove(Pair(Firebase.auth.currentUser?.uid!!,0))
                if(firebaseGroup.membersAndScores.size == 0){
                    groupToBeRemoved = firebaseGroup
                }
            }
        }
        if (groupToBeRemoved != null){
            listOfFirebaseGroups.remove(groupToBeRemoved)
        }
        val group = Pair(groupName, groupID)
        listOfOwnGroups.remove(group)
        firebaseDir.setValue(Json.encodeToString(listOfOwnGroups))
        firebaseDb.reference.child(groupID.toString()).removeValue()
        firebaseDb.reference.child("Groups").setValue(Json.encodeToString(listOfFirebaseGroups))
        recyclerAdapter = GroupRecyclerAdapter(this, listOfOwnGroups, this)
        recyclerView.adapter = recyclerAdapter



    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
    fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (connectivityManager != null) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_CELLULAR")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_WIFI")
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    Log.i("Internet", "NetworkCapabilities.TRANSPORT_ETHERNET")
                    return true
                }
            }
        }
        return false
    }
}

