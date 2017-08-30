package spikey.com.freeride;


import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import spikey.com.freeride.cloudmessaging.VALUES;
import spikey.com.freeride.taskCardsMapView.MapsActivity;

public class DatabaseOperations {

    private static final String TAG = DatabaseOperations.class.getSimpleName();
    private static boolean connected;
    private static final DatabaseReference mDatabaseUserTaskMessages =
            FirebaseDatabase.getInstance().getReference(VALUES.DB_MESSAGES_PATH);
    private static final DatabaseReference mDatabaseTasks =
            FirebaseDatabase.getInstance().getReference(VALUES.TASKS_PATH_DB);

    /**
     * Store User Task Info in database, to be read by server.
     * Queues all messages, to be easily read at once, more reliable than FCM upstream messaging.
     * @param dataPayload (user task info) data to be stored by database
     * @param taskId of task
     */
    public static void sendUserTaskInfo(Map<String, Object> dataPayload, String taskId) {
        if (!connectedToDatabase()) {
            return;
        }
        Log.d(TAG, "Adding User task Info to database for task: " + taskId);
        DatabaseReference newMessageRef = mDatabaseUserTaskMessages.child(taskId).push();
        newMessageRef.setValue(dataPayload, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                if (databaseError == null) {
                    Log.d(TAG, "User Task Info added to database SUCCESSFULLY ");
                } else {
                    Log.d(TAG, "Error on adding User Task Info : " + databaseError + ", " + databaseReference);
                }
            }
        });
    }

    /**
     * Attempts to set the user field of task to this user's Id.
     * Uses a transaction (concurrency safe).
     * transaction is recalled until the user field is set by a user.
     * @param taskId of task to secure
     */
    public static void secureTask(final String taskId) {
        final String userId = FirebaseInstanceId.getInstance().getToken();
        if (!connectedToDatabase()) {
            return;
        }

        DatabaseReference newMessageRef = mDatabaseTasks.child(taskId);
        //newMessageRef.setValue("sfb");
        Log.d(TAG, "Securing Task " + newMessageRef);

        newMessageRef.runTransaction(new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData mutableData) {
                Log.d(TAG, "pre-transaction Data value: " + mutableData);
                Task task = mutableData.getValue(Task.class);
                if (task == null) {
                    return Transaction.success(mutableData); //todo ??
                } else {
                    if (task.getUser() == null) {
                        task.setUser(userId);
                        mutableData.setValue(task);
                        Log.d(TAG, "Set user on Task: " + taskId + " set userId: " + userId);
                    } else {
                        Log.d(TAG, "Task: " + taskId + " already taken by user: " + task.getUser());
                    }
                    return Transaction.success(mutableData);
                }
            }


            @Override
            public void onComplete(DatabaseError databaseError, boolean error,
                                   DataSnapshot dataSnapshot) {
                if (error) {
                    Log.d(TAG, "Transaction completed");
                } else {
                    Log.d(TAG, "Error on securing task : " + databaseError);
                }
            }
        });
    }

    /**
     * Returns all available tasks from database as Json tree / string??
     */
    public static void getAvailableTasks(final TextView resultsTextView, final Context context) {
        connectedToDatabase();
        DatabaseReference allTasksRef = mDatabaseTasks;
        allTasksRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) { //TODO cleaner way? - use array
                String receivedData = dataSnapshot.getValue().toString();
                Log.d(TAG, "Get Available tasks: " + receivedData);
//                String allTasksDataMap = dataSnapshot.getValue().toString();
//                if (allTasksDataMap.length() != 0) { //todo check actual empty size
//                    resultsTextView.setText(allTasksDataMap);
//                    Intent openTasksView = new Intent(context, MapsActivity.class);
//                    openTasksView.putExtra("tasks", allTasksDataMap);
//                    context.startActivity(openTasksView);


                Gson gson = new Gson();
                ArrayList<Object> tasksObjectArray = new ArrayList<>();
                Iterator<DataSnapshot> tasksIterator = dataSnapshot.getChildren().iterator();
                if (tasksIterator.hasNext()) {
                    tasksObjectArray.add(tasksIterator.next().getValue());

//                    DataSnapshot taskData = tasksIterator.next();
//                    String taskId = taskData.getKey();
//                    StringBuilder tasksDataStringBuilder = new StringBuilder("[");
//                    tasksDataStringBuilder.append(gson.toJson(tasksIterator.next()));

                    while (tasksIterator.hasNext()) {
//                        tasksDataStringBuilder.append(",")
//                                .append(gson.toJson(tasksIterator.next().getValue()));
                        tasksObjectArray.add(tasksIterator.next().getValue());
                    }
//                    String tasksData = taskData2; //tasksDataStringBuilder.append("]").toString();
                    resultsTextView.setText(receivedData);
                    Object[] tasks = tasksObjectArray.toArray();
                    String tasksJson = gson.toJson(tasks);
                    Intent openTasksView = new Intent(context, MapsActivity.class);
                    Log.d(TAG, "task Ob Array: " + tasksJson);
                    openTasksView.putExtra("tasks", tasksJson);
                    context.startActivity(openTasksView);
                } else {
                    // No task data received from server
                    Toast.makeText(context, "No Tasks Available.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "OnCancelled: " + databaseError);
            }
        });
    }

    /**
     * Sends data (userId) to database to check connection / reconnect to database
     */
    public static void databaseMessageTest() {
        connectedToDatabase();
        DatabaseReference databaseReff = FirebaseDatabase.getInstance().getReference().child("ConnectTest").push();
        String userId = FirebaseInstanceId.getInstance().getToken(); //test..
        Object o = userId;
        databaseReff.setValue(o);
        Log.d(TAG, "DB TEST: " + databaseReff);

    }

    /**
     * Database - New task listener
     */
    public static void listen() {
        connectedToDatabase();
        final FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference ref = database.getReference("tasks/test");

        ref.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
                //TODO task is converted to object, then json again
                Task newTask = dataSnapshot.getValue(Task.class);
                String taskId = dataSnapshot.getKey();
                String treatment = dataSnapshot.getRef().getParent().getKey();
                Log.d(TAG, "New Task Added To DB, TASK ID::::" + taskId);
                //allowing to repeated testing \/ \/ damn italics ruining my arrows c'mon
                //dataSnapshot.getRef().removeValue();
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {}
            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}
            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "OnCancelled: " + databaseError);
            }

        });
    }

    /**
     * Attempts to reestablish connection to database
     * Prints out and returns connection status (true or false)
     * @return connection status
     */
    public static boolean connectedToDatabase() {
        DatabaseReference.goOnline();/////////////
        DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        connectedRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                connected = snapshot.getValue(Boolean.class);
                Log.e(TAG, "Connected = " + connected);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                System.err.println("Listener was cancelled");
            }
        });
        return connected;
    }
}
