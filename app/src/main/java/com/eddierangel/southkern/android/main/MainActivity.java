package com.eddierangel.southkern.android.main;

import android.content.Intent;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.eddierangel.southkern.android.BuildConfig;
import com.eddierangel.southkern.android.utils.AlertAdapter;
import com.eddierangel.southkern.android.utils.EventParser;
import com.eddierangel.southkern.android.utils.FeedAdapter;
import com.eddierangel.southkern.android.utils.ReconnectionManager;
import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;
import com.google.firebase.functions.HttpsCallableResult;
import com.sendbird.android.SendBird;
import com.sendbird.android.SendBirdException;
import com.eddierangel.southkern.android.R;
import com.eddierangel.southkern.android.groupchannel.GroupChannelActivity;
import com.eddierangel.southkern.android.openchannel.OpenChannelActivity;
import com.eddierangel.southkern.android.utils.PreferenceUtils;
import com.eddierangel.southkern.android.utils.LogUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.eddierangel.southkern.android.utils.InternetCheck;

// TODO: Add Documentation to Public Interface
public class MainActivity extends AppCompatActivity {

    final String TAG = "MainActivity";

    private Toolbar mToolbar;
    private NavigationView mNavView, alertNavView;
    private ImageButton navButton, viewAlertButton;
    private Button submitStatusUpdate;
    private FirebaseFunctions mFunctions;
    private DatabaseReference mDatabase;
    private DatabaseReference mSouthKernUserRef;
    private DatabaseReference mStatusUpdatesRef;
    private ValueEventListener mSouthKernUserListener;
    private ValueEventListener mStatusUpdatesListener;
    private OnCompleteListener mEventsListener;
    private OnClickListener mNavButtonListener;
    private OnClickListener mViewAlertButtonListener;
    private OnClickListener msubmitStatusUpdateButtonListener;
    private Task<List<Event>> mEventItems;
    private RelativeLayout alertCreationLayout;
    private List<Event> events, dummyEvents = new ArrayList<>(), alertEvents = new ArrayList<>();
    private EditText statusText;
    private RecyclerView feedRecyclerView, alertRecyclerView;
    private FeedAdapter mAdapter;
    private AlertAdapter alertAdapter;
    private HashMap userMetaData;
    private String firebaseUID;
    private HashMap statusUpdate = new HashMap();
    private String profileURL;
    private long twoWeekTime = 1209600000;


    private Task<List<Event>> getCalendarEvents(String firebaseToken) {
        // Create the arguments to the callable function.
        Map<String, Object> data = new HashMap<>();
        data.put("firebaseToken", firebaseToken);

        return mFunctions
                .getHttpsCallable("getCalendarEvents")
                .call(data)
                .continueWith(new Continuation<HttpsCallableResult, List<Event>>() {
                    @Override
                    public List<Event> then(@NonNull Task<HttpsCallableResult> task) throws Exception {
                        // This continuation runs on either success or failure, but if the task
                        // has failed then getResult() will throw an Exception which will be
                        // propagated down.
                        return EventParser.parse((List) task.getResult().getData());
                    }
                });
    }

    private void sortCalendarEvents(List<Event> events) {
        Collections.sort(dummyEvents, new Comparator<Event>() {
            long firstTime;
            long secondTime;
            @Override
            public int compare(Event firstEvent, Event secondEvent) {
                firstTime = firstEvent.getStart().getDate().getValue();
                secondTime = secondEvent.getStart().getDate().getValue();

                if (
                        (new Date().getTime() - firstTime) < (new Date().getTime() - secondTime) &&
                                (new Date().getTime() - firstTime < 0)
                ) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });

        mAdapter.notifyDataSetChanged();

    }

    private void filterAlerts(DataSnapshot dataSnapshot) {
        alertEvents.clear();
        for (DataSnapshot mSnapshot : dataSnapshot.getChildren()) {
            HashMap statusObj = (HashMap) mSnapshot.getValue();
            Event tempEvent = new Event();
            tempEvent.setDescription((String) statusObj.get("text"));
            tempEvent.setSummary("Status update");

            EventDateTime dummyTime = new EventDateTime();
            Long dateTime = Long.parseLong(statusObj.get("createdAt").toString());
            DateTime createdAtTime = new DateTime(dateTime);
            dummyTime.setDate(createdAtTime);
            tempEvent.setStart(dummyTime);

            if (!dummyEvents.contains(tempEvent) && new Date().getTime() - tempEvent.getStart().getDate().getValue() < (twoWeekTime / 2)) {
                dummyEvents.add(tempEvent);
            } else if (new Date().getTime() - tempEvent.getStart().getDate().getValue() > (twoWeekTime / 2)) {
                mSnapshot.getRef().removeValue();
            }

            if (!alertEvents.contains(tempEvent) && new Date().getTime() - tempEvent.getStart().getDate().getValue() < (twoWeekTime / 2)) {
                alertEvents.add(tempEvent);
            }

        }

    }

    private void filterEvents(List<Event> events) {
        if (events != null) {
            for (Event event : events) {
                Log.i("EVENT TIME FILTER", "" + (new Date().getTime() - event.getStart().getDate().getValue() < twoWeekTime));
                Log.i("EVENT TIME FILTER2", "" + (event.getStart().getDate().getValue() < new Date().getTime() + (2 * twoWeekTime)));
                if (new Date().getTime() - event.getStart().getDate().getValue() < twoWeekTime &&
                        event.getStart().getDate().getValue() < new Date().getTime() + (2 * twoWeekTime) &&
                        !dummyEvents.contains(event)) {
                    dummyEvents.add(event);
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        alertNavView = findViewById(R.id.nav_view_alerts);
        navButton = findViewById(R.id.menu_button_main);
        alertRecyclerView = findViewById(R.id.alert_recycler_view);
        viewAlertButton = findViewById(R.id.alert_view_button);
        statusText = findViewById(R.id.alert_description);
        submitStatusUpdate = findViewById(R.id.alert_submit);

        // Check for internet connection before starting activity.
        new InternetCheck(new InternetCheck.Consumer() {
            @Override
            public void accept(Boolean internet) {
                if (!internet) {
                    Intent intent = new Intent(MainActivity.this, ReconnectionManager.class);
                    startActivity(intent);
                    finish();
                }
            }
        });

    }


    @Override
    public void onResume() {
        super.onResume();

        new InternetCheck(new InternetCheck.Consumer() {
            @Override
            public void accept(Boolean internet) {
                LogUtility.i("connectionTest", "" + internet);
                if (!internet) {
                    Intent intent = new Intent(MainActivity.this, ReconnectionManager.class);
                    startActivityForResult(intent, 1);
                    finish();
                }
            }
        });

    }

    @Override
    public void onStart() {
        super.onStart();
        initializeFirebaseComponents();
        setupToolbar();
        setupFeedView();
        setupCalendar();
        setupNavView();
        setupAlertView();
        navigationButtonListen();
        userListen();
        viewAlertButtonListen();
        submitStatusButtonListen();
    }

    @Override
    public void onStop() {
        super.onStop();
        cleanBasicListener();
    }

    private void submitStatusButtonListen() {

        msubmitStatusUpdateButtonListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                String status = (String) statusText.getText().toString();

                if (!status.equals("")) {
                    statusUpdate.put("text", status);
                    statusUpdate.put("createdAt", new Date().getTime());

                    mDatabase.child("statusUpdates").push().setValue(statusUpdate);

                    statusText.setText("");
                }
            }
        };
        submitStatusUpdate.setOnClickListener(msubmitStatusUpdateButtonListener);
    }

    private void viewAlertButtonListen() {

        mViewAlertButtonListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                alertNavView.setVisibility(alertNavView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                viewAlertButton.setBackgroundColor(alertNavView.getVisibility() == View.VISIBLE ? Color.DKGRAY : Color.TRANSPARENT);
                mNavView.setVisibility(View.GONE);
                navButton.setBackgroundColor(Color.TRANSPARENT);

            }
        };
        viewAlertButton.setOnClickListener(mViewAlertButtonListener);
    }

    private void setupAlertView() {
        RecyclerView.LayoutManager mLayoutAlertManager = new LinearLayoutManager(getApplicationContext());
        alertRecyclerView.setLayoutManager(mLayoutAlertManager);
        alertRecyclerView.setItemAnimator(new DefaultItemAnimator());
        alertAdapter = new AlertAdapter(alertEvents);
        alertRecyclerView.setAdapter(alertAdapter);
    }

    private void navigationButtonListen() {

        mNavButtonListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                mNavView.setVisibility(mNavView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                navButton.setBackgroundColor(mNavView.getVisibility() == View.VISIBLE ? Color.DKGRAY : Color.TRANSPARENT);
                alertNavView.setVisibility(View.GONE);
                viewAlertButton.setBackgroundColor(Color.TRANSPARENT);
            }
        };
        navButton.setOnClickListener(mNavButtonListener);
    }

    private void setupCalendar() {

        mEventItems = getCalendarEvents(PreferenceUtils.getFirebaseToken(MainActivity.this.getApplicationContext()));
        mEventsListener = new OnCompleteListener<List<Event>>() {
            @Override
            public void onComplete(@NonNull Task<List<Event>> task) {
                if (!task.isSuccessful()) {
                    Exception e = task.getException();
                    if (e instanceof FirebaseFunctionsException) {
                        FirebaseFunctionsException ffe = (FirebaseFunctionsException) e;
                        FirebaseFunctionsException.Code code = ffe.getCode();
                        Object details = ffe.getDetails();
                    }
                }
                // Success
                Log.i("EVENTS_LIST", "" + task.getResult());
                events = task.getResult();

                // Filter events that are too old or too far in the future.
                // Keep events that are within one month of now, or two weeks in the past.
                filterEvents(events);

                statusUpdateListen();

                sortCalendarEvents(dummyEvents);

                mAdapter.notifyDataSetChanged();

            }
        };
        mEventItems.addOnCompleteListener(mEventsListener);
    }

    private void statusUpdateListen() {

        mStatusUpdatesRef = mDatabase.child("statusUpdates");
        mStatusUpdatesListener =  new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                filterAlerts(dataSnapshot);

                // Reverse so that recently added alerts are
                // on top of the list instead of the bottom.
                Collections.reverse(alertEvents);

                sortCalendarEvents(dummyEvents);

                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        };
        mStatusUpdatesRef.addValueEventListener(mStatusUpdatesListener);
    }

    private void setupNavView() {
        mNavView = findViewById(R.id.nav_view_main);
        mNavView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                mNavView.setVisibility(View.GONE);
                navButton.setBackgroundColor(mNavView.getVisibility() == View.VISIBLE ? Color.DKGRAY : Color.TRANSPARENT);

                if (id == R.id.nav_item_open_channels) {
                    Intent intent = new Intent(MainActivity.this, OpenChannelActivity.class);
                    startActivity(intent);
                    return true;

                } else if (id == R.id.nav_item_group_channels) {
                    Intent intent = new Intent(MainActivity.this, GroupChannelActivity.class);
                    startActivity(intent);
                    return true;

                } else if (id == R.id.nav_item_calendar) {
                    Intent intent = new Intent(MainActivity.this, CalendarActivity.class);
                    startActivity(intent);
                    finish();
                    return true;

                } else if (id == R.id.nav_item_user_list) {
                    Intent intent = new Intent(MainActivity.this, UserList.class);
                    startActivity(intent);
                    finish();
                    return true;

                } else if (id == R.id.nav_item_view_own_profile) {
                    Intent intent = new Intent(MainActivity.this, ViewOwnProfile.class);
                    startActivity(intent);
                    return true;

                } else if (id == R.id.nav_item_settings) {
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                    return true;

                } else if (id == R.id.nav_item_sign_off) {
                    signOut();
                    return true;
                }

                return false;
            }
        });
    }

    private void setupFeedView() {
        feedRecyclerView = findViewById(R.id.feed_recycler_view);
        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(getApplicationContext());
        feedRecyclerView.setLayoutManager(mLayoutManager);
        feedRecyclerView.setItemAnimator(new DefaultItemAnimator());
        Log.i("EVENT LIST FEED", "" + dummyEvents);
        mAdapter = new FeedAdapter(dummyEvents, MainActivity.this.getApplicationContext());
        feedRecyclerView.setAdapter(mAdapter);
    }

    private void setupToolbar() {
        mToolbar = findViewById(R.id.main_activity_toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }

    private void initializeFirebaseComponents() {
        mFunctions = FirebaseFunctions.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference().getRoot();
        firebaseUID = FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    private void userListen() {

        if(firebaseUID != null)
        {
            mSouthKernUserRef = mDatabase.child("southkernUsers").child(firebaseUID);
            mSouthKernUserListener = new ValueEventListener(){
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    userMetaData = (HashMap) dataSnapshot.getValue();

                    // Save user to shared preferences
                    PreferenceUtils.setUser(MainActivity.this.getApplicationContext(), userMetaData);

                    alertCreationLayout = (RelativeLayout) findViewById(R.id.alert_creator);
                    if (userMetaData.get("user_type") != null) {
                        if (userMetaData.get("user_type").equals("admin")) {
                            alertCreationLayout.setVisibility(View.VISIBLE);
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };
            mSouthKernUserRef.addListenerForSingleValueEvent(mSouthKernUserListener);
        }
    }

    private void cleanBasicListener() {
        // Clean up value listener
        // [START clean_basic_listen]
        if (mSouthKernUserListener != null) {
            mSouthKernUserRef.removeEventListener(mSouthKernUserListener);
        }

        if (mStatusUpdatesListener != null) {
            mStatusUpdatesRef.removeEventListener(mStatusUpdatesListener);
        }
        // [END clean_basic_listen]
    }

    public void signOut() {
        // [START auth_fui_signout]
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    public void onComplete(@NonNull Task<Void> task) {
                        if(task.isSuccessful()){
                            disconnect();
                        }else{
                            LogUtility.e(TAG, "Sign Out was not successful.");
                        }
                    }
                });
        // [END auth_fui_signout]
    }

    /**
     * Unregisters all push tokens for the current user so that they do not receive any notifications,
     * then disconnects from SendBird.
     */
    private void disconnect() {

        SendBird.unregisterPushTokenAllForCurrentUser(new SendBird.UnregisterPushTokenHandler() {
            @Override
            public void onUnregistered(SendBirdException e) {
                if (e != null) {
                    // Error!
                    e.printStackTrace();

                    // Don't return because we still need to disconnect.
                } else {
                    Toast.makeText(MainActivity.this, "All push tokens unregistered.", Toast.LENGTH_SHORT)
                            .show();
                }

                SendBird.disconnect(new SendBird.DisconnectHandler() {
                    @Override
                    public void onDisconnected() {
                        PreferenceUtils.setConnected(MainActivity.this, false);
                        Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
            }
        });
    }
}
