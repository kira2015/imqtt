/*******************************************************************************
 * Copyright (c) 1999, 2016 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *   James Sutton - isOnline Null Pointer (bug 473775)
 */
package com.itouchtv.mqtt.imqtt;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.itouchtv.mqtt.imqtt.db.MessageStore;
import com.itouchtv.mqtt.imqtt.mqttv3.DisconnectedBufferOptions;
import com.itouchtv.mqtt.imqtt.mqttv3.IMqttDeliveryToken;
import com.itouchtv.mqtt.imqtt.mqttv3.IMqttMessageListener;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttClientPersistence;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttConnectOptions;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttDeliveryToken;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttException;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttMessage;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttPersistenceException;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttSecurityException;
import com.itouchtv.mqtt.imqtt.nocare.MqttServiceConstants;
import com.itouchtv.mqtt.imqtt.nocare.Status;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * The android service which interfaces with an MQTT client implementation
 * </p>
 * <p>
 * The main API of MqttService is intended to pretty much mirror the
 * IMqttAsyncClient with appropriate adjustments for the Android environment.<br>
 * These adjustments usually consist of adding two parameters to each method :-
 * </p>
 * <ul>
 * <li>invocationContext - a string passed from the application to identify the
 * context of the operation (mainly included for support of the javascript API
 * implementation)</li>
 * <li>activityToken - a string passed from the Activity to relate back to a
 * callback method or other context-specific data</li>
 * </ul>
 * <p>
 * To support multiple client connections, the bulk of the MQTT work is
 * delegated to MqttConnection objects. These are identified by "client
 * handle" strings, which is how the Activity, and the higher-level APIs refer
 * to them.
 * </p>
 * <p>
 * Activities using this service are expected to start it and bind to it using
 * the BIND_AUTO_CREATE flag. The life cycle of this service is based on this
 * approach.
 * </p>
 * <p>
 * Operations are highly asynchronous - in most cases results are returned to
 * the Activity by broadcasting one (or occasionally more) appropriate Intents,
 * which the Activity is expected to register a listener for.<br>
 * The Intents have an Action of
 * {@link MqttServiceConstants#CALLBACK_TO_ACTIVITY
 * MqttServiceConstants.CALLBACK_TO_ACTIVITY} which allows the Activity to
 * register a listener with an appropriate IntentFilter.<br>
 * Further data is provided by "Extra Data" in the Intent, as follows :-
 * </p>
 * <table border="1" summary="">
 * <tr>
 * <th align="left">Name</th>
 * <th align="left">Data Type</th>
 * <th align="left">Value</th>
 * <th align="left">Operations used for</th>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_CLIENT_HANDLE
 * MqttServiceConstants.CALLBACK_CLIENT_HANDLE}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">The clientHandle identifying the client which
 * initiated this operation</td>
 * <td align="left" valign="top">All operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">{@link MqttServiceConstants#CALLBACK_STATUS
 * MqttServiceConstants.CALLBACK_STATUS}</td>
 * <td align="left" valign="top">Serializable</td>
 * <td align="left" valign="top">An {@link Status} value indicating success or
 * otherwise of the operation</td>
 * <td align="left" valign="top">All operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_ACTIVITY_TOKEN
 * MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">the activityToken passed into the operation</td>
 * <td align="left" valign="top">All operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_INVOCATION_CONTEXT
 * MqttServiceConstants.CALLBACK_INVOCATION_CONTEXT}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">the invocationContext passed into the operation
 * </td>
 * <td align="left" valign="top">All operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">{@link MqttServiceConstants#CALLBACK_ACTION
 * MqttServiceConstants.CALLBACK_ACTION}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">one of
 * <table summary="">
 * <tr>
 * <td align="left" valign="top"> {@link MqttServiceConstants#SEND_ACTION
 * MqttServiceConstants.SEND_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#UNSUBSCRIBE_ACTION
 * MqttServiceConstants.UNSUBSCRIBE_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top"> {@link MqttServiceConstants#SUBSCRIBE_ACTION
 * MqttServiceConstants.SUBSCRIBE_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top"> {@link MqttServiceConstants#DISCONNECT_ACTION
 * MqttServiceConstants.DISCONNECT_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top"> {@link MqttServiceConstants#CONNECT_ACTION
 * MqttServiceConstants.CONNECT_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#MESSAGE_ARRIVED_ACTION
 * MqttServiceConstants.MESSAGE_ARRIVED_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#MESSAGE_DELIVERED_ACTION
 * MqttServiceConstants.MESSAGE_DELIVERED_ACTION}</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#ON_CONNECTION_LOST_ACTION
 * MqttServiceConstants.ON_CONNECTION_LOST_ACTION}</td>
 * </tr>
 * </table>
 * </td>
 * <td align="left" valign="top">All operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_ERROR_MESSAGE
 * MqttServiceConstants.CALLBACK_ERROR_MESSAGE}
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">A suitable error message (taken from the
 * relevant exception where possible)</td>
 * <td align="left" valign="top">All failing operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_ERROR_NUMBER
 * MqttServiceConstants.CALLBACK_ERROR_NUMBER}
 * <td align="left" valign="top">int</td>
 * <td align="left" valign="top">A suitable error code (taken from the relevant
 * exception where possible)</td>
 * <td align="left" valign="top">All failing operations</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_EXCEPTION_STACK
 * MqttServiceConstants.CALLBACK_EXCEPTION_STACK}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">The stacktrace of the failing call</td>
 * <td align="left" valign="top">The Connection Lost event</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_MESSAGE_ID
 * MqttServiceConstants.CALLBACK_MESSAGE_ID}</td>
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">The identifier for the message in the message
 * store, used by the Activity to acknowledge the arrival of the message, so
 * that the service may remove it from the store</td>
 * <td align="left" valign="top">The Message Arrived event</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_DESTINATION_NAME
 * MqttServiceConstants.CALLBACK_DESTINATION_NAME}
 * <td align="left" valign="top">String</td>
 * <td align="left" valign="top">The topic on which the message was received</td>
 * <td align="left" valign="top">The Message Arrived event</td>
 * </tr>
 * <tr>
 * <td align="left" valign="top">
 * {@link MqttServiceConstants#CALLBACK_MESSAGE_PARCEL
 * MqttServiceConstants.CALLBACK_MESSAGE_PARCEL}</td>
 * <td align="left" valign="top">Parcelable</td>
 * <td align="left" valign="top">The new message encapsulated in Android
 * Parcelable format as a {@link ParcelableMqttMessage}</td>
 * <td align="left" valign="top">The Message Arrived event</td>
 * </tr>
 * </table>
 */
@SuppressLint("Registered")
public class MqttService extends Service {

    // Identifier for Intents, log messages, etc..
    public static final String TAG = "MqttService";

    // somewhere to persist received messages until we're sure
    // that they've reached the application
    MessageStore messageStore;

    // An intent receiver to deal with changes in network connectivity
    private NetworkConnectionIntentReceiver networkConnectionMonitor;

    //a receiver to recognise when the user changes the "background data" preference
    // and a flag to track that preference
    // Only really relevant below android version ICE_CREAM_SANDWICH - see
    // android docs
    private BackgroundDataPreferenceReceiver backgroundDataPreferenceMonitor;
    private volatile boolean backgroundDataEnabled = true;

    // a way to pass ourself back to the activity
    private com.itouchtv.mqtt.imqtt.MqttServiceBinder mqttServiceBinder;

    // mapping from client handle strings to actual client connections.
    private Map<String/* clientHandle */, com.itouchtv.mqtt.imqtt.MqttConnection/* client */> connections = new ConcurrentHashMap<>();

    public MqttService() {
        super();
    }

    /**
     * 所有消息都经此方法发出
     * pass data back to the Activity, by building a suitable Intent object and
     * broadcasting it
     *
     * @param clientHandle source of the data
     * @param status       OK or Error
     * @param dataBundle   the data to be passed
     */
    void callbackToActivity(String clientHandle, Status status, Bundle dataBundle) {
        // Don't call traceDebug, as it will try to callbackToActivity leading
        // to recursion.
        Intent callbackIntent = new Intent(MqttServiceConstants.CALLBACK_TO_ACTIVITY);
        if (clientHandle != null) {
            callbackIntent.putExtra(MqttServiceConstants.CALLBACK_CLIENT_HANDLE, clientHandle);
        }
        callbackIntent.putExtra(MqttServiceConstants.CALLBACK_STATUS, status);
        if (dataBundle != null) {
            callbackIntent.putExtras(dataBundle);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(callbackIntent);
    }

    // The major API implementation follows :-

    /**
     * Get an MqttConnection object to represent a connection to a server
     *
     * @param serverURI   specifies the protocol, host name and port to be used to connect to an MQTT server
     * @param clientId    specifies the name by which this connection should be identified to the server
     * @param contextId   specifies the app conext info to make a difference between apps
     * @param persistence specifies the persistence layer to be used with this client
     * @return a string to be used by the Activity as a "handle" for this
     * MqttConnection
     */
    public String getClient(String serverURI, String clientId, String contextId, MqttClientPersistence persistence) {
        String clientHandle = serverURI + ":" + clientId + ":" + contextId;
        if (!connections.containsKey(clientHandle)) {
            com.itouchtv.mqtt.imqtt.MqttConnection client = new com.itouchtv.mqtt.imqtt.MqttConnection(this, serverURI, clientId, persistence, clientHandle);
            connections.put(clientHandle, client);
        }
        return clientHandle;
    }

    /**
     * Connect to the MQTT server specified by a particular client
     *
     * @param clientHandle      identifies the MqttConnection to use
     * @param connectOptions    the MQTT connection options to be used
     * @param invocationContext arbitrary data to be passed back to the application
     * @param activityToken     arbitrary identifier to be passed back to the Activity
     * @throws MqttSecurityException thrown if there is a security exception
     * @throws MqttException         thrown for all other MqttExceptions
     */
    public void connect(String clientHandle, MqttConnectOptions connectOptions,
                        String invocationContext, String activityToken) throws MqttSecurityException, MqttException {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.connect(connectOptions, null, activityToken);
    }

    /**
     * Request all clients to reconnect if appropriate
     */
    void reconnect() {
        for (com.itouchtv.mqtt.imqtt.MqttConnection client : connections.values()) {
            if (this.isOnline()) {
                client.reconnect();
            }
        }
    }

    /**
     * Close connection from a particular client
     *
     * @param clientHandle identifies the MqttConnection to use
     */
    public void close(String clientHandle) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.close();
    }

    /**
     * Disconnect from the server
     *
     * @param clientHandle      identifies the MqttConnection to use
     * @param invocationContext arbitrary data to be passed back to the application
     * @param activityToken     arbitrary identifier to be passed back to the Activity
     */
    public void disconnect(String clientHandle, String invocationContext,
                           String activityToken) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.disconnect(invocationContext, activityToken);
        connections.remove(clientHandle);


        // the activity has finished using us, so we can stop the service
        // the activities are bound with BIND_AUTO_CREATE, so the service will
        // remain around until the last activity disconnects
        stopSelf();
    }

    /**
     * Disconnect from the server
     *
     * @param clientHandle      identifies the MqttConnection to use
     * @param quiesceTimeout    in milliseconds
     * @param invocationContext arbitrary data to be passed back to the application
     * @param activityToken     arbitrary identifier to be passed back to the Activity
     */
    public void disconnect(String clientHandle, long quiesceTimeout,
                           String invocationContext, String activityToken) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.disconnect(quiesceTimeout, invocationContext, activityToken);
        connections.remove(clientHandle);

        // the activity has finished using us, so we can stop the service
        // the activities are bound with BIND_AUTO_CREATE, so the service will
        // remain around until the last activity disconnects
        stopSelf();
    }

    /**
     * Get the status of a specific client
     *
     * @param clientHandle identifies the MqttConnection to use
     * @return true if the specified client is connected to an MQTT server
     */
    public boolean isConnected(String clientHandle) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return false;
        return client.isConnected();
    }

    /**
     * Publish a message to a topic
     *
     * @param clientHandle      identifies the MqttConnection to use
     * @param topic             the topic to which to publish
     * @param payload           the content of the message to publish
     * @param qos               the quality of service requested
     * @param retained          whether the MQTT server should retain this message
     * @param invocationContext arbitrary data to be passed back to the application
     * @param activityToken     arbitrary identifier to be passed back to the Activity
     * @return token for tracking the operation
     * @throws MqttPersistenceException when a problem occurs storing the message
     * @throws MqttException            if there was an error publishing the message
     */
    public IMqttDeliveryToken publish(String clientHandle, String topic,
                                      byte[] payload, int qos, boolean retained,
                                      String invocationContext, String activityToken)
            throws MqttPersistenceException, MqttException {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        return client.publish(topic, payload, qos, retained, invocationContext,
                activityToken);
    }

    /**
     * Publish a message to a topic
     *
     * @param clientHandle      identifies the MqttConnection to use
     * @param topic             the topic to which to publish
     * @param message           the message to publish
     * @param invocationContext arbitrary data to be passed back to the application
     * @param activityToken     arbitrary identifier to be passed back to the Activity
     * @return token for tracking the operation
     * @throws MqttPersistenceException when a problem occurs storing the message
     * @throws MqttException            if there was an error publishing the message
     */
    public IMqttDeliveryToken publish(String clientHandle, String topic,
                                      MqttMessage message, String invocationContext, String activityToken)
            throws MqttPersistenceException, MqttException {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return new MqttDeliveryToken("");

        return client.publish(topic, message, invocationContext, activityToken);
    }

    /**
     * Subscribe to a topic
     *
     * @param clientHandle      identifies the MqttConnection to use
     * @param topic             a possibly wildcarded topic name
     * @param qos               requested quality of service for the topic
     * @param invocationContext arbitrary data to be passed back to the application
     * @param activityToken     arbitrary identifier to be passed back to the Activity
     */
    public void subscribe(String clientHandle, String topic, int qos,
                          String invocationContext, String activityToken) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.subscribe(topic, qos, invocationContext, activityToken);
    }

    /**
     * Subscribe to one or more topics
     *
     * @param clientHandle      identifies the MqttConnection to use
     * @param topic             a list of possibly wildcarded topic names
     * @param qos               requested quality of service for each topic
     * @param invocationContext arbitrary data to be passed back to the application
     * @param activityToken     arbitrary identifier to be passed back to the Activity
     */
    public void subscribe(String clientHandle, String[] topic, int[] qos,
                          String invocationContext, String activityToken) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.subscribe(topic, qos, invocationContext, activityToken);
    }

    /**
     * Subscribe using topic filters
     *
     * @param clientHandle      identifies the MqttConnection to use
     * @param topicFilters      a list of possibly wildcarded topicfilters
     * @param qos               requested quality of service for each topic
     * @param invocationContext arbitrary data to be passed back to the application
     * @param activityToken     arbitrary identifier to be passed back to the Activity
     * @param messageListeners  a callback to handle incoming messages
     */
    public void subscribe(String clientHandle, String[] topicFilters, int[] qos, String invocationContext, String activityToken, IMqttMessageListener[] messageListeners) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.subscribe(topicFilters, qos, invocationContext, activityToken, messageListeners);
    }

    /**
     * Unsubscribe from a topic
     *
     * @param clientHandle      identifies the MqttConnection
     * @param topic             a possibly wildcarded topic name
     * @param invocationContext arbitrary data to be passed back to the application
     * @param activityToken     arbitrary identifier to be passed back to the Activity
     */
    public void unsubscribe(String clientHandle, final String topic,
                            String invocationContext, String activityToken) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.unsubscribe(topic, invocationContext, activityToken);
    }

    /**
     * Unsubscribe from one or more topics
     *
     * @param clientHandle      identifies the MqttConnection
     * @param topic             a list of possibly wildcarded topic names
     * @param invocationContext arbitrary data to be passed back to the application
     * @param activityToken     arbitrary identifier to be passed back to the Activity
     */
    public void unsubscribe(String clientHandle, final String[] topic,
                            String invocationContext, String activityToken) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.unsubscribe(topic, invocationContext, activityToken);
    }

    /**
     * Get tokens for all outstanding deliveries for a client
     *
     * @param clientHandle identifies the MqttConnection
     * @return an array (possibly empty) of tokens
     */
    public IMqttDeliveryToken[] getPendingDeliveryTokens(String clientHandle) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return new IMqttDeliveryToken[0];
        return client.getPendingDeliveryTokens();
    }

    /**
     * Get the MqttConnection identified by this client handle
     *
     * @param clientHandle identifies the MqttConnection
     * @return the MqttConnection identified by this handle
     */
    private com.itouchtv.mqtt.imqtt.MqttConnection getConnection(String clientHandle) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = null;
        try {
            client = connections.get(clientHandle);
        } catch (Exception e) {
            e.printStackTrace();
        }
       /* if (client == null) {
            throw new IllegalArgumentException("Invalid ClientHandle");
        }*/
        return client;
    }

    /**
     * Called by the Activity when a message has been passed back to the
     * application
     *
     * @param clientHandle identifier for the client which received the message
     * @param id           identifier for the MQTT message
     * @return {@link Status}
     */
    public Status acknowledgeMessageArrival(String clientHandle, String id) {
        if (messageStore == null) return Status.OK;
        if (messageStore.discardArrived(clientHandle, id)) {
            return Status.OK;
        } else {
            return Status.ERROR;
        }
    }

    // Extend Service

    /**
     * @see Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();

        // create a binder that will let the Activity UI send
        // commands to the Service
        mqttServiceBinder = new com.itouchtv.mqtt.imqtt.MqttServiceBinder(this);

        // create somewhere to buffer received messages until
        // we know that they have been passed to the application
//        messageStore = new DatabaseMessageStore(this, this);
    }


    /**
     * @see Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        // disconnect immediately
        for (com.itouchtv.mqtt.imqtt.MqttConnection client : connections.values()) {
            client.disconnect(null, null);
        }

        // clear down
        if (mqttServiceBinder != null) {
            mqttServiceBinder = null;
        }

        unregisterBroadcastReceivers();

        if (this.messageStore != null)
            this.messageStore.close();

        super.onDestroy();
    }

    /**
     * @see Service#onBind(Intent)
     */
    @Override
    public IBinder onBind(Intent intent) {
        // What we pass back to the Activity on binding -
        // a reference to ourself, and the activityToken
        // we were given when started
        String activityToken = intent.getStringExtra(MqttServiceConstants.CALLBACK_ACTIVITY_TOKEN);
        mqttServiceBinder.setActivityToken(activityToken);
        return mqttServiceBinder;
    }

    /**
     * @see Service#onStartCommand(Intent, int, int)
     */
    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        // run till explicitly stopped, restart when
        // process restarted
        registerBroadcastReceivers();

        return START_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void registerBroadcastReceivers() {
        if (networkConnectionMonitor == null) {
            networkConnectionMonitor = new NetworkConnectionIntentReceiver();
            registerReceiver(networkConnectionMonitor, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }

        if (Build.VERSION.SDK_INT < 14 /**Build.VERSION_CODES.ICE_CREAM_SANDWICH**/) {
            // Support the old system for background data preferences
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            backgroundDataEnabled = cm.getBackgroundDataSetting();
            if (backgroundDataPreferenceMonitor == null) {
                backgroundDataPreferenceMonitor = new BackgroundDataPreferenceReceiver();
                registerReceiver(backgroundDataPreferenceMonitor, new IntentFilter(ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED));
            }
        }
    }

    private void unregisterBroadcastReceivers() {
        if (networkConnectionMonitor != null) {
            unregisterReceiver(networkConnectionMonitor);
            networkConnectionMonitor = null;
        }

        if (Build.VERSION.SDK_INT < 14 /**Build.VERSION_CODES.ICE_CREAM_SANDWICH**/) {
            if (backgroundDataPreferenceMonitor != null) {
                unregisterReceiver(backgroundDataPreferenceMonitor);
            }
        }
    }

    /*
     * Called in response to a change in network connection - after losing a
     * connection to the server, this allows us to wait until we have a usable
     * data connection again
     */
    private class NetworkConnectionIntentReceiver extends BroadcastReceiver {

        @Override
        @SuppressLint("Wakelock")
        public void onReceive(Context context, Intent intent) {
            // we protect against the phone switching off
            // by requesting a wake lock - we request the minimum possible wake
            // lock - just enough to keep the CPU running until we've finished
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MQTT");
                wl.acquire();
                if (isOnline()) {
                    // we have an internet connection - have another try at
                    // connecting
                    reconnect();
                } else {
                   /* //无网络时自动下线
                    notifyClientsOffline();*/
                }

                wl.release();
            }
        }
    }

    /**
     * @return whether the android service can be regarded as online
     */
    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        //noinspection RedundantIfStatement
        if (networkInfo != null
                && networkInfo.isAvailable()
                && networkInfo.isConnected()
                && backgroundDataEnabled) {
            return true;
        }

        return false;
    }

    /**
     * Notify clients we're offline
     */
    private void notifyClientsOffline() {
        for (com.itouchtv.mqtt.imqtt.MqttConnection connection : connections.values()) {
            connection.offline();
        }
    }

    /**
     * Detect changes of the Allow Background Data setting - only used below
     * ICE_CREAM_SANDWICH
     */
    private class BackgroundDataPreferenceReceiver extends BroadcastReceiver {

        @SuppressWarnings("deprecation")
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (cm.getBackgroundDataSetting()) {
                    if (!backgroundDataEnabled) {
                        backgroundDataEnabled = true;
                        // we have the Internet connection - have another try at connecting
                        reconnect();
                    }
                } else {
                    backgroundDataEnabled = false;
                   /* //网络断开时立即下线
                    notifyClientsOffline();*/
                }
            }
        }
    }

    /**
     * Sets the DisconnectedBufferOptions for this client
     *
     * @param clientHandle identifier for the client
     * @param bufferOpts   the DisconnectedBufferOptions for this client
     */
    public void setBufferOpts(String clientHandle, DisconnectedBufferOptions bufferOpts) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.setBufferOpts(bufferOpts);
    }

    public int getBufferedMessageCount(String clientHandle) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return 0;
        return client.getBufferedMessageCount();
    }

    public MqttMessage getBufferedMessage(String clientHandle, int bufferIndex) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return new MqttMessage();
        return client.getBufferedMessage(bufferIndex);
    }

    public void deleteBufferedMessage(String clientHandle, int bufferIndex) {
        com.itouchtv.mqtt.imqtt.MqttConnection client = getConnection(clientHandle);
        if (client==null) return;
        client.deleteBufferedMessage(bufferIndex);
    }

}
