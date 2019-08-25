package edu.buffalo.cse.cse486586.groupmessenger1;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class OnSendClickListener implements View.OnClickListener {
    private static final String TAG = OnSendClickListener.class.getName();
    private static final String KEY_FIELD = "key";
    private static final String VALUE_FIELD = "value";
    private static String homePort = "";

    private final EditText mEditText;
    private final TextView mTextView;
    //private final ContentResolver mContentResolver;
    private final Uri mUri;


    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }


    public OnSendClickListener(TextView _tv, String myport, EditText editText) {
        mTextView = _tv;
        mEditText = editText;
        mUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger1.provider");
        homePort = myport;
    }

    @Override
    public void onClick(View v) {
        // Get the message from EditText
        String msg = mEditText.getText().toString() + "\n";
        mEditText.getText().clear();
        //mTextView.setText(""); // This is one way to reset the input box.
        mTextView.append("\t" + msg); // This is one way to display a string.
        mTextView.append("\n");

        // Invoke ClientTask
        new GroupMessengerActivity.ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, homePort);
    }
}
