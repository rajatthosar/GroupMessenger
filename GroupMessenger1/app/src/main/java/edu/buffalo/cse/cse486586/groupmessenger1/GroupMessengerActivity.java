package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.cert.TrustAnchor;
import java.util.Arrays;
import java.util.Collections;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {
    static final String TAG = GroupMessengerActivity.class.getSimpleName();

    // This array of strings holds the remote ports
    static final String[] REMOTE_PORTS = {"11108", "11112", "11116", "11120", "11124"};
    static final int SERVER_PORT = 10000;
    // ackCounter holds the limiting value of acknowledgements before the connection is broken
    static int ackCounter = 0;
    // messageCounter holds the index of the messages received by the server.
    // -The index is used to name the files that store the messages.
    private int messageCounter = 1;
    private Uri mUri;
    // A variable to store the port number of current device
    private static String portNumber;


    // A getter method to access the port number of current device
    public static String getPortNumber() {
        return portNumber;
    }

    /**
     * Called when the Activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_group_messenger);

        // The device port is set by first reading the emulator IDs and then
        // -multiplying the ID by 2
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        portNumber = myPort;
        try {
            // Set the server to listen at port number 10000
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {

            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        final EditText editText = (EditText) findViewById(R.id.editText1);

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        // Create an OnClickListener on PTest button.
        // The PTest button is identified by the ID:button1 in the layout
        // -which is used in the findViewById() method to locate the button
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));

        // Similarly, the Send button is mapped to the button with ID:button4
        // The send button is used to invoke the CilentTask.
        findViewById(R.id.button4).setOnClickListener(new OnSendClickListener(tv, myPort, editText));

        // I have also implemented "Enter Key to send" from PA1.
        editText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    /*
                     * If the key is pressed (i.e., KeyEvent.ACTION_DOWN) and it is an enter key
                     * (i.e., KeyEvent.KEYCODE_ENTER), then we display the string. Then we create
                     * an AsyncTask that sends the string to the remote AVD.
                     */
                    TextView tmpTxtView = (TextView) findViewById(R.id.textView1);
                    String msg = editText.getText().toString() + "\n";
                    editText.setText(""); // This is one way to reset the input box.
                    //TextView localTextView = tmpTxtView;
                    tmpTxtView.append("\t" + msg); // This is one way to display a string.
                    //TextView remoteTextView = tmpTxtView;
                    tmpTxtView.append("\n");

                    /*
                     * Note that the following AsyncTask uses AsyncTask.SERIAL_EXECUTOR, not
                     * AsyncTask.THREAD_POOL_EXECUTOR as the above ServerTask does. To understand
                     * the difference, please take a look at
                     * http://developer.android.com/reference/android/os/AsyncTask.html
                     */
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);
                    return true;
                }
                return false;
            }
        });

    }

    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko
     *
     */
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        ContentResolver contentResolver = getContentResolver();

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            ContentValues keyValuesToInsert = new ContentValues();
            try {
                // The server listens to a connection request indefinitely.
                while (true) {

                    Log.d("====================", "In While");
                    //Spawn a client socket for an accepted connection
                    Socket clientSocket = serverSocket.accept();
                    Log.d("====================", "In While after");
                    /* This block receives the bytestream sent by the client
                     * and converts it into String object.
                     * Reference: https://docs.oracle.com/javase/8/docs/api/java/io/DataInputStream.html*/
                    InputStream inStream = clientSocket.getInputStream();
                    DataInputStream inDataStream = new DataInputStream(inStream);
                    String receivedMsg = inDataStream.readUTF();

                    /* This block checks if server has actually received any data.
                     * This, however does not check for the authenticity of the sent message.
                     * It is assumed that the connection is safe from any sort of message
                     * tampering. If the message received by server has a nonzero length,
                     * the server sends an acknowledgement (ACK) signal back to the client*/

                    /* The following block checks the received messages. I have created
                     * Header tags for every kind of message. An initial multicast sent
                     * by the device is labeled as INIMCMSG. When such a tag is received,
                     * The recipient servers reply the sender with a sequence number proposal.
                     *
                     */

                    if (receivedMsg.contains("FINSEQMSG:")) {
                        String msgDump[] = receivedMsg.split(":");
                        Log.d("SEQ NUMBER SELECTED", msgDump[1]);
                        Log.d("MSG SELECTED", msgDump[2]);
                    } else if (receivedMsg.contains("INITMCMSG:")) {
                        OutputStream dataStream = clientSocket.getOutputStream();
                        DataOutputStream outDataStream = new DataOutputStream(dataStream);
                        outDataStream.writeUTF("SEQ:" + Integer.toString(messageCounter) + "_" + GroupMessengerActivity.getPortNumber());
                        outDataStream.flush();
                        outDataStream.close();
                        // This counter updates the index of the messages
                        messageCounter++;
                    }

                    // Create a keyValue pair to be pushed into the contentResolver object
                    keyValuesToInsert.put("key", Integer.toString(messageCounter));
                    keyValuesToInsert.put("value", receivedMsg);

                    // This block builds the URI from predefined scheme and authority
                    Uri.Builder uriBuilder = new Uri.Builder();
                    uriBuilder.authority("edu.buffalo.cse.cse486586.groupmessenger1.provider");
                    uriBuilder.scheme("content");
                    mUri = uriBuilder.build();

                    // Insert the ContentValues object to the ContentProvider
                    // -pointed by the URI that was built into mUri variable
                    contentResolver.insert(mUri, keyValuesToInsert);

                    //This function calls the onProgressUpdate method
                    publishProgress(receivedMsg);

                }

            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            TextView tmpTxtView = (TextView) findViewById(R.id.textView1);
            String strReceived = strings[0].trim();
            //TextView remoteTextView = tmpTxtView;
            tmpTxtView.append(strReceived + "\t\n");
            //TextView localTextView = tmpTxtView;
            tmpTxtView.append("\n");
            return;
        }
    }

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    static class ClientTask extends AsyncTask<String, Void, Void> {
        private static String[] seqNumbers = new String[5];
        private static int seqIdx = 0;
        String electedSeqNumber;
        String currentSeqNumber;

        static String homeport;
        static boolean isSeqDecided = false;

        @Override
        protected Void doInBackground(String... msgs) {
            try {
                String finalizedMsg="";
                // Get the port number of the sender of the message.
                // This variable has no use in the current code, however,
                // I have kept the variable for logging.
                homeport = msgs[1];
                Log.i(TAG, "The sender of the message: " + homeport + "\n");

                // Iterate over all the ports in the port lookup table
                for (String remotePort : REMOTE_PORTS) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));

                    String msgToSend = msgs[0];

                    /* This block prepares a bytestream from the input message to be
                     * sent to the server at the receiving end.
                     * Reference: https://docs.oracle.com/javase/8/docs/api/java/io/DataOutputStream.html*/
                    OutputStream outStream = socket.getOutputStream();
                    DataOutputStream outDataStream = new DataOutputStream(outStream);

                    // A header tag of INITMCMSG is added to distinguish it as an initial multicast
                    outDataStream.writeUTF("INITMCMSG:" + msgToSend);

                    /* This line purges the buffer of the data. This is done to ensure
                     * that the buffer does not hold up the messages and preempts itself
                     * when send command is given*/
                    outDataStream.flush();
                    //outDataStream.close();

                    // This block acts as a listener to the ACK signal sent by the server
                    InputStream ackInStream = socket.getInputStream();
                    DataInputStream inDataStream = new DataInputStream(ackInStream);
                    String receivedACK = inDataStream.readUTF();

                    // This block cleans out the sequence number from the received message
                    String sequence = receivedACK.split(":")[1];
                    String tmpSeq = sequence.split("_")[0];
                    int seqNumber = Integer.parseInt(tmpSeq);
                    seqNumbers[seqIdx] = sequence;
                    Log.d("SeqNumber", Integer.toString(seqNumber));

                    // Increment the message sequence index
                    seqIdx++;

                    // This block checks if all proposals are received from all of the participants
                    if (seqIdx > 4) {
                        seqIdx = 0;

                        // This section finds the maximum sequence number from the received values
                        Arrays.sort(seqNumbers);
                        electedSeqNumber = seqNumbers[seqNumbers.length - 1];
                        currentSeqNumber = electedSeqNumber;
                        Log.d("Elected Sequence Number", electedSeqNumber);

                        /* This line adds a header to indicate that the final sequence number
                         * of the message that was multicast.
                         */
                        finalizedMsg = "FINSEQMSG:" + electedSeqNumber + ":" + msgToSend;
                        isSeqDecided = true;
                        }


                    /* This block keeps track of the ACKs received by the server.
                     * The ackCounter is a class variable that would increment on every ACK.*/
                    if (receivedACK.equals("ACK")) {
                        ackCounter++;
                    }

                    /* Here, at least 2 ACKs must be received to establish the idea
                     * that both devices have exchanged at least one message among them.
                     * Thus, when ackCounter crosses 2 ACKs, we can close the connection*/
                    if (ackCounter > 2000) {
                        socket.close();
                        ackCounter = 0;
                    }
                }

                if(isSeqDecided)
                {
                    for (String recPort: REMOTE_PORTS)
                    {
                        Socket finSeqSocket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(recPort));
                        DataOutputStream finSeqOutStream = new DataOutputStream(finSeqSocket.getOutputStream());
                        finSeqOutStream.writeUTF(finalizedMsg);
                        finSeqOutStream.flush();

                    }
                    isSeqDecided = false;
                }Log.d("---------------", "Client Task finished");
            } catch (Exception e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            }

            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }
}
