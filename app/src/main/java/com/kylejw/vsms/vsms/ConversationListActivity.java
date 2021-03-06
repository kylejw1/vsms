package com.kylejw.vsms.vsms;

import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.kylejw.vsms.vsms.Database.SmsMessageContentProvider;
import com.kylejw.vsms.vsms.Database.SmsMessageTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class ConversationListActivity extends ListActivity implements LoaderManager.LoaderCallbacks<Cursor> {

//    private SimpleCursorAdapter adapter;
    private CursorAdapter adapter;

    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final String SENDER_ID = "88730814122";

    private GoogleCloudMessaging gcm;
    private String regid;
    private Context context;

    private ConcurrentHashMap<String, String> contactNameCache = new ConcurrentHashMap<>();

    private void checkPlayServices() {
        try {
            int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
            if (resultCode != ConnectionResult.SUCCESS) {
                if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                    GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                            PLAY_SERVICES_RESOLUTION_REQUEST).show();
                } else {
                    finish();
                }
            }

            context = getApplicationContext();
            gcm = GoogleCloudMessaging.getInstance(this);
            regid = getRegistrationId(this);

            if (regid.isEmpty()) {
                registerInBackground();
            }
        } catch (Exception ex) {
            Toast.makeText(this, ex.toString(), Toast.LENGTH_LONG);
        }
    }

    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            return "";
        }
        return registrationId;
    }

    private SharedPreferences getGCMPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return getSharedPreferences(ConversationListActivity.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }

    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    private void registerInBackground() {
        new AsyncTask() {

            @Override
            protected Object doInBackground(Object[] params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    regid = gcm.register(SENDER_ID);
                    msg = "Device registered, registration ID=" + regid;

                    // You should send the registration ID to your server over HTTP,
                    // so it can use GCM/HTTP or CCS to send messages to your app.
                    // The request to your server should be authenticated if your app
                    // is using accounts.
                    sendRegistrationIdToBackend();

                    // For this demo: we don't need to send it because the device
                    // will send upstream messages to a server that echo back the
                    // message using the 'from' address in the message.

                    // Persist the regID - no need to register again.
                    storeRegistrationId(context, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

        }.execute(null, null, null);

    }

    private void sendRegistrationIdToBackend() {
        // Your implementation here.
    }

    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGCMPreferences(context);
        int appVersion = getAppVersion(context);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        checkPlayServices();

        //SmsMessageContentProvider.refresh(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkPlayServices();

        SmsMessageContentProvider.refresh(this);

        fillData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        Cursor c = (Cursor)l.getItemAtPosition(position);

        String contact = c.getString(c.getColumnIndex(SmsMessageTable.COLUMN_CONTACT));
        String did = c.getString(c.getColumnIndex(SmsMessageTable.COLUMN_DID));

        Intent i = new Intent(this, ConversationActivity.class);
        i.putExtra(SmsMessageTable.COLUMN_CONTACT, contact);
        i.putExtra(SmsMessageTable.COLUMN_DID, did);

        startActivity(i);
    }

    private void fillData() {

        // Fields from the database (projection)
        // Must include the _id column for the adapter to work
        String[] from = new String[] { SmsMessageTable.COLUMN_CONTACT, SmsMessageTable.COLUMN_MESSAGE, SmsMessageTable.COLUMN_INTERNAL_ID, SmsMessageTable.COLUMN_DATE };
        // Fields on the UI to which we map
        int[] to = new int[] { R.id.name_entry, R.id.message_entry, R.id.id_entry, R.id.date_entry };

        getLoaderManager().initLoader(0, null, this);
//        adapter = new SimpleCursorAdapter(this, R.layout.list_conversation_entry, null, from,
//                to, 0);
        adapter = new SimpleCursorAdapter(this, R.layout.list_conversation_entry, null, from,to, 0) {
            @Override
            public void bindView(View view, final Context context, final Cursor cursor) {
                super.bindView(view, context, cursor);

                final ViewBinder binder = this.getViewBinder();

                View v = view.findViewById(R.id.name_entry);

                final int contactIndex = cursor.getColumnIndex(SmsMessageTable.COLUMN_CONTACT);

                if (v != null) {
                    boolean bound = false;
                    if (binder != null) {
                        bound = binder.setViewValue(v, cursor, contactIndex);
                    }

                    if (!bound) {

                        final String contact = cursor.getString(contactIndex);

                        if (contactNameCache.containsKey(contact)) {
                            setViewText((TextView) v, contactNameCache.get(contact));
                        } else {
                            String displayName = ContactsHelpers.displayNameFromContactNumber(context, contact);
                            if (displayName == null || displayName.isEmpty()) {
                                displayName = contact;
                            }
                            setViewText((TextView) v, displayName);
                        }

                    }
                }

                final int dateIndex = cursor.getColumnIndex(SmsMessageTable.COLUMN_DATE);
                v = view.findViewById(R.id.date_entry);
                if (null != v) {
                    boolean bound = false;
                    if (binder != null) {
                        bound = binder.setViewValue(v, cursor, dateIndex);
                    }

                    if (!bound) {

                        final long date = cursor.getLong(dateIndex);
                        String dateString= DateFormat.format("MM/dd/yyyy", new Date(date)).toString();

                        setViewText((TextView) v, dateString);

                    }
                }
            }
        };

        setListAdapter(adapter);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] projection = {SmsMessageTable.COLUMN_INTERNAL_ID, SmsMessageTable.COLUMN_DID, SmsMessageTable.COLUMN_CONTACT, SmsMessageTable.COLUMN_MESSAGE, "MAX(" + SmsMessageTable.COLUMN_DATE + ") as " + SmsMessageTable.COLUMN_DATE};

        CursorLoader cursorLoader = new CursorLoader(this,
                SmsMessageContentProvider.CONVERSATIONS_URI, projection, null, null, SmsMessageTable.COLUMN_DATE + " DESC");
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, final Cursor data) {
        try {

            // Get initial position
            int initialPosition = data.getPosition();

            data.moveToFirst();

            final int contactColumn = data.getColumnIndex(SmsMessageTable.COLUMN_CONTACT);
            while(!data.isAfterLast()) {
                String contactNum = data.getString(contactColumn);
                if (!contactNameCache.containsKey(contactNum)) {
                    contactNameCache.put(contactNum, contactNum);
                }
                data.moveToNext();
            }

            // Restore initial position
            data.moveToPosition(initialPosition);

            final Context context = this;

            // Refresh cache
            new AsyncTask<Void, Void, Integer>() {
                @Override
                protected Integer doInBackground(Void... params) {

                    for (String contactName : contactNameCache.keySet()) {
                        String displayName = ContactsHelpers.displayNameFromContactNumber(context, contactName);

                        if (displayName == null || displayName.isEmpty()) continue;

                        contactNameCache.replace(contactName, displayName);
                    }

                    return 0;
                }
            }.doInBackground();

            adapter.swapCursor(data);

        } catch (Exception ex) {
            Toast.makeText(this, ex.toString(), Toast.LENGTH_LONG);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        adapter.swapCursor(null);
    }
}
