package com.itouchtv.mqtt.imqtt.tmq;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.itouchtv.mqtt.imqtt.mqttv3.IMqttActionListener;
import com.itouchtv.mqtt.imqtt.mqttv3.IMqttDeliveryToken;
import com.itouchtv.mqtt.imqtt.mqttv3.IMqttToken;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttAsyncClient;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttCallbackExtended;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttConnectOptions;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttException;
import com.itouchtv.mqtt.imqtt.mqttv3.MqttMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Created by heweizong on 2018/1/24.
 * MQTT-SERVICE使用说明：http://192.168.31.49:4003/%E8%A7%A6%E7%94%B5%E6%96%B0%E9%97%BB%E5%90%8E%E5%8F%B0/%E6%9C%8D%E5%8A%A1/MQTT/MQTT-SERVICE%E4%BD%BF%E7%94%A8%E8%AF%B4%E6%98%8E.html
 */

public class TMQClient {
    private static boolean enableLog = false;
    private static volatile TMQClient instance;
   /* public static final String SERVERURI_TEST="ssl://im-test.itouchtv.cn:19814";//测试环境
    public static final String SERVERURI_PRODUCT="ssl://im.itouchtv.cn:19814";//线上环境
    public static String serverURI = SERVERURI_PRODUCT;//"ssl://im.itouchtv.cn:19814";//默认线上环境*/

//    public static String serverURI = "tcp://iot.eclipse.org:1883";

    private final int qos = 1;
    private boolean sdkInited = false;
    private MqttAsyncClient mqttClient;
    private MqttConnectOptions connOpts;

    private String TMQ_APPID = "b8qx12ofqj3i";
    private final String developer = "itouchtv";
    private final String purpose = "IM";
    //    private String clientId;// 格式：appId - [[IM | PUSH] purpose] - deviceId 举例：1001-IM-IMEI_1289192,1001-PUSH-IMEI_1289192
    private String willTopic = "willTopic";// = "itouchtv/b8qx12ofqj3i/IM/willTopic";
    private final HashMap<String, LruArrayList<String>> storeTopicMsgId = new HashMap<>();
    private final HashMap<String, String> subscriptions = new HashMap<>();
    private HashMap<String, TMQMessageListener> messageListeners;
    private List<TMQConnectionListener> connectionListeners;

    private TMQClient() {
    }

    public static TMQClient getInstance() {
        if (instance == null) {
            synchronized (TMQClient.class) {
                if (instance == null) {
                    instance = new TMQClient();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化
     *
     * @param connOpts MqttConnectOptions
     */
    public void init(MqttConnectOptions connOpts) {
        if (!sdkInited) {
            this.connOpts = connOpts;
            sdkInited = true;
        }
    }

    public void init(Context mContext) {
        init(createSSLOptions(mContext));
    }

    /**
     * 开启日志打印
     */
    public static void setDebugMode(boolean enable) {
        enableLog = enable;
    }

    public static void log(String info) {
        if (enableLog && !TextUtils.isEmpty(info)) {
            Log.d("TMQClient", info);
        }
    }

    private MqttConnectOptions createDefaultOptions() {
        MqttConnectOptions mOptions = new MqttConnectOptions();
        mOptions.setKeepAliveInterval(15);
//        mOptions.setUserName("admin");
//        mOptions.setPassword("password".toCharArray());
        mOptions.setAutomaticReconnect(true);
        return mOptions;
    }

    //添加ssl证书
    private MqttConnectOptions createSSLOptions(Context mContext) {
        MqttConnectOptions mOptions = createDefaultOptions();

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            CertificateFactory caCF = CertificateFactory.getInstance("X.509");
            X509Certificate ca = (X509Certificate) caCF.generateCertificate(mContext.getResources().getAssets().open("ca.crt"));
            String alias = ca.getSubjectX500Principal().getName();
            // Set propper alias name
            KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            caKeyStore.load(null, null);
            caKeyStore.setCertificateEntry(alias, ca);
            tmf.init(caKeyStore);

            sslContext.init(null, tmf.getTrustManagers(), null);
//            sslContext.init(null, new TrustManager[]{new MyHTTPSTrustManager()}, null);

/*            // CA certificate is used to authenticate server
            KeyStore caKs = KeyStore.getInstance(KeyStore.getDefaultType());
            caKs.load(null, null);
            caKs.setCertificateEntry("ca-certificate", ca);
            //=========信任证书(如果你的证书是比较大的ca 发的那么可以使用默认的)=========
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(caKs);
            // finally, create SSL socket factory
            SSLContext context = SSLContext.getInstance("TLSv1");
            context.init(null,tmf.getTrustManagers(), null);*/

            mOptions.setSocketFactory(sslContext.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return mOptions;
    }

    public void setupWill(MqttConnectOptions mConnOpts) {
        JSONObject mMap = new JSONObject();
        try {
            mMap.put("userName", mConnOpts.getUserName());
            mMap.put("topicId", "");
            mMap.put("subscribe", 2);//0取消订阅，1订阅 2.非正常掉线时取消订阅所有topic
            mMap.put("appId", TMQ_APPID);
            mMap.put("connId", mConnOpts.getConnId());
            mMap.put("messageId", TMQMessage.generateMessageId());
            mMap.put("client", 0);
        } catch (JSONException mE) {
            mE.printStackTrace();
        }
        String payload = mMap.toString();
        //设置遗嘱在disconnect之后发遗嘱消息给willTopic告诉java后台已退订该主题
        mConnOpts.setWill(subStringTopic(willTopic), payload.getBytes(), 1, false);
    }

    /**
     * 登录（连接broker）
     *
     * @param userName 用户名，也当做clientId使用。由后台提供(保证唯一性)，后台使用与clientId一样的拼接规则并经过MD5加密后返回给APP
     * @param callback callback
     */
    public void login(final String userName, String password, String serverURI, final TMQCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback is null!");
        } else {
            try {
                if (!sdkInited) {
                    callback.onError(TMQERROR.ERROR_SDK_NOT_INITED, "sdk not initialized!");
                    return;
                }
                mqttClient = new MqttAsyncClient(serverURI, userName);
                mqttClient.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        log("connectComplete: TMQClient\t" + System.currentTimeMillis());
                        if (connectionListeners == null) return;
                        for (TMQConnectionListener listener :
                                connectionListeners) {
                            listener.onConnected(reconnect);
                        }
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        log("connectionLost: TMQClient\t" + System.currentTimeMillis());
                        if (connectionListeners == null) return;
                        for (TMQConnectionListener listener :
                                connectionListeners) {
                            listener.onDisconnected(0);
                        }
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) throws Exception {
                        if (TextUtils.isEmpty(topic) || message == null) return;
                        String content = new String(message.getPayload());
                        log("messageArrived: " + content);
                        if (messageListeners != null) {
                            TMQMessageListener mMessageListener = messageListeners.get(topic);
                            if (mMessageListener != null) {
                                JSONObject mObject = new JSONObject(content);
                                String msgId = mObject.optString("messageId");

                                LruArrayList<String> topicMsgId = storeTopicMsgId.get(topic);
                                if (topicMsgId != null) {
                                    if (topicMsgId.contains(msgId)) return;//根据messageId过滤掉重复接收的消息
                                    topicMsgId.add(msgId);
                                } else {
                                    LruArrayList<String> topicMsgId_new = new LruArrayList<>(100);
                                    topicMsgId_new.add(msgId);
                                    storeTopicMsgId.put(topic, topicMsgId_new);
                                }
                                if (!TextUtils.isEmpty(userName) && userName.equals(mObject.optString("fromUserId"))) {
                                    //在SDK层过滤掉自己发送的消息
                                    return;
                                }
                                TMQMessage msg = TMQMessage.createTMQMsg(splitTopic(topic), message);
                                mMessageListener.onMessageReceived(msg);
                            }
                        }
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {

                    }
                });
                if (connOpts == null) {
                    connOpts = new MqttConnectOptions();
                }
                connOpts.setUserName(userName);
                connOpts.setPassword(password.toCharArray());
                connOpts.setConnId(System.currentTimeMillis());
                setupWill(connOpts);
                mqttClient.connect(connOpts, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        if (callback != null) {
                            callback.onError(1, "error occurs when login");
                        }
                    }
                });
            } catch (MqttException mE) {
                mE.printStackTrace();
            }

        }
    }

    /**
     * 退出登录（断开连接）
     */
    public void logout() {
        subscriptions.clear();
        if (!isConnected()) return;
        try {
            mqttClient.disconnect();
        } catch (MqttException mE) {
            mE.printStackTrace();
        }
    }

    /**
     * 发送消息
     *
     * @param mTMQMessage msg
     */
    public void sendMessage(final TMQMessage mTMQMessage) {
        if (!isConnected()) return;
        if (mTMQMessage == null) return;
        String topic = mTMQMessage.getTopic();
        if (TextUtils.isEmpty(topic)) return;
        try {
            mqttClient.publish(subStringTopic(topic), mTMQMessage.getContent().getBytes(), mTMQMessage.getQos(), mTMQMessage.isRetained(), null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    if (mTMQMessage != null) {
                        TMQCallback mCallback = mTMQMessage.getTMQCallback();
                        if (mCallback != null) {
                            mCallback.onSuccess();
                        }
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (mTMQMessage != null) {
                        TMQCallback mCallback = mTMQMessage.getTMQCallback();
                        if (mCallback != null) {
                            mCallback.onError(1, "error occurs when publish");
                        }
                    }
                }
            });
        } catch (MqttException mE) {
            mE.printStackTrace();
            if (mTMQMessage != null) {
                TMQCallback mCallback = mTMQMessage.getTMQCallback();
                if (mCallback != null) {
                    mCallback.onError(1, "error occurs when publish");
                }
            }
        }
    }

    /**
     * 发送消息
     *
     * @param mTMQMessage msg
     */
    public void sendChatMessage(final TMQMessage mTMQMessage) {
        if (!isConnected()) return;
        if (mTMQMessage == null) return;
        String topic = mTMQMessage.getTopic();
        if (TextUtils.isEmpty(topic)) return;
        try {
            mqttClient.publish(topic, mTMQMessage.getContent().getBytes(), mTMQMessage.getQos(), mTMQMessage.isRetained(), null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    if (mTMQMessage != null) {
                        TMQCallback mCallback = mTMQMessage.getTMQCallback();
                        if (mCallback != null) {
                            mCallback.onSuccess();
                        }
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (mTMQMessage != null) {
                        TMQCallback mCallback = mTMQMessage.getTMQCallback();
                        if (mCallback != null) {
                            mCallback.onError(1, "error occurs when publish");
                        }
                    }
                }
            });
        } catch (MqttException mE) {
            mE.printStackTrace();
        }
    }

    /**
     * 加入聊天室（订阅主题）
     *
     * @param topic     聊天室topic
     * @param mCallback callback
     */
    public void joinChatRoom(final String topic, final TMQCallback mCallback) {
        if (!isConnected()) return;
        try {
            mqttClient.subscribe(subStringTopic(topic), qos, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
//                    Log.d("hwz", "sub_Success: " + topic);
                    //订阅成功需要发消息到willTopic告诉java后台已订阅该主题
                    TMQMessage willMsg = new TMQMessage();
                    willMsg.setTopic(willTopic);
                    JSONObject mMap = new JSONObject();
                    try {
                        mMap.put("userName", connOpts.getUserName());
                        mMap.put("topicId", subStringTopic(topic));
                        mMap.put("subscribe", 1);//0取消订阅，1订阅
                        mMap.put("appId", TMQ_APPID);
                        mMap.put("connId", connOpts.getConnId());
                        mMap.put("messageId", TMQMessage.generateMessageId());
                        mMap.put("client", 0);
                    } catch (JSONException mE) {
                        mE.printStackTrace();
                    }
                    String payload = mMap.toString();
                    willMsg.setContent(payload);
                    sendMessage(willMsg);
                    if (mCallback != null) {
                        mCallback.onSuccess();
                    }

                    subscriptions.put(subStringTopic(topic), "");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (mCallback != null) {
                        mCallback.onError(1, "error occurs when subscribe");
                    }
                }
            });
        } catch (MqttException mE) {
            mE.printStackTrace();
        }
    }

    /**
     * （订阅主题）
     *
     * @param topic     订阅主题
     * @param mCallback callback
     */
    public void joinPushChatRoom(final String topic, final TMQCallback mCallback) {
        if (!isConnected()) return;
        try {
            mqttClient.subscribe(topic, qos, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
//                    Log.d("hwz", "sub_Success: " + topic);
                    //订阅成功需要发消息到willTopic告诉java后台已订阅该主题
                    TMQMessage willMsg = new TMQMessage();
                    willMsg.setTopic(/*"itouchtv/b8qx12ofqj3i/IM/"+*/willTopic);
                    JSONObject mMap = new JSONObject();
                    try {
                        mMap.put("userName", connOpts.getUserName());
                        mMap.put("topicId", topic);
                        mMap.put("subscribe", 1);//0取消订阅，1订阅
                        mMap.put("appId", TMQ_APPID);
                        mMap.put("connId", connOpts.getConnId());
                        mMap.put("messageId", TMQMessage.generateMessageId());
                        mMap.put("client", 0);
                    } catch (JSONException mE) {
                        mE.printStackTrace();
                    }
                    String payload = mMap.toString();
                    willMsg.setContent(payload);
//                    sendChatMessage(willMsg);
                    sendMessage(willMsg);
                    if (mCallback != null) {
                        mCallback.onSuccess();
                    }

                    subscriptions.put(topic, "");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (mCallback != null) {
                        mCallback.onError(1, "error occurs when subscribe");
                    }
                }
            });
        } catch (MqttException mE) {
            mE.printStackTrace();
        }
    }

    /**
     * 退出聊天室（取消已订阅主题）
     *
     * @param topic topic
     */
    public void quitChatRoom(final String topic) {
        if (!isConnected()) return;
        try {
            mqttClient.unsubscribe(subStringTopic(topic), null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    //退订成功需要发消息给willTopic告诉java后台已退订该主题
                    TMQMessage willMsg = new TMQMessage();
                    willMsg.setTopic(willTopic);
                    JSONObject mMap = new JSONObject();
                    try {
                        mMap.put("userName", connOpts.getUserName());
                        mMap.put("topicId", subStringTopic(topic));
                        mMap.put("subscribe", 0);//0取消订阅，1订阅
                        mMap.put("appId", TMQ_APPID);
                        mMap.put("connId", connOpts.getConnId());
                        mMap.put("messageId", TMQMessage.generateMessageId());
                        mMap.put("client", 0);
                    } catch (JSONException mE) {
                        mE.printStackTrace();
                    }
                    String payload = mMap.toString();
                    willMsg.setContent(payload);
                    sendMessage(willMsg);

                    subscriptions.remove(subStringTopic(topic));
                    messageListeners.remove(subStringTopic(topic));
                    storeTopicMsgId.remove(subStringTopic(topic));
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

                }
            });
        } catch (MqttException mE) {
            mE.printStackTrace();
        }
    }

    /**
     * 退出聊天室（取消已订阅主题）
     *
     * @param topic topic
     */
    public void quitPushChatRoom(final String topic, final TMQCallback mCallback) {
        if (!isConnected()) {
            if (mCallback != null) {
                mCallback.onError(1, "error occurs when unsubscribe");
            }
            return;
        }
        try {
            mqttClient.unsubscribe(topic, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    //退订成功需要发消息给willTopic告诉java后台已退订该主题
                    TMQMessage willMsg = new TMQMessage();
                    willMsg.setTopic(willTopic);
                    JSONObject mMap = new JSONObject();
                    try {
                        mMap.put("userName", connOpts.getUserName());
                        mMap.put("topicId", topic);
                        mMap.put("subscribe", 0);//0取消订阅，1订阅
                        mMap.put("appId", TMQ_APPID);
                        mMap.put("connId", connOpts.getConnId());
                        mMap.put("messageId", TMQMessage.generateMessageId());
                        mMap.put("client", 0);
                    } catch (JSONException mE) {
                        mE.printStackTrace();
                    }
                    String payload = mMap.toString();
                    willMsg.setContent(payload);

                    willMsg.setTMQCallback(new TMQCallback() {
                        @Override
                        public void onSuccess() {
                            try {
                                subscriptions.remove(topic);
                                messageListeners.remove(topic);
                                storeTopicMsgId.remove(topic);

                                if (mCallback != null) mCallback.onSuccess();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onError(int errorCode, String errorMsg) {
                            subscriptions.remove(topic);
                            messageListeners.remove(topic);
                            storeTopicMsgId.remove(topic);

                            if (mCallback != null) {
                                mCallback.onError(1, "error occurs when unsubscribe");
                            }
                        }
                    });

                    sendMessage(willMsg);


                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    if (mCallback != null) {
                        mCallback.onError(1, "error occurs when unsubscribe");
                    }

                }
            });
        } catch (Exception mE) {
            mE.printStackTrace();
            if (mCallback != null) {
                mCallback.onError(1, "error occurs when unsubscribe");
            }
        }
    }

    /**
     * 增加消息监听
     * 一个topic只能设置一个消息监听，重复设置会覆盖原监听
     *
     * @param mCallback callback
     */
    public void addMessageListener(String topic, TMQMessageListener mCallback) {
        if (TextUtils.isEmpty(topic) || mCallback == null) return;
        if (this.messageListeners == null) {
            messageListeners = new HashMap<>();
        }
        messageListeners.put(subStringTopic(topic), mCallback);
    }

    /**
     * 增加消息监听
     * 一个topic只能设置一个消息监听，重复设置会覆盖原监听
     *
     * @param mCallback callback
     */
    public void addPushMessageListener(String topic, TMQMessageListener mCallback) {
        if (TextUtils.isEmpty(topic) || mCallback == null) return;
        if (this.messageListeners == null) {
            messageListeners = new HashMap<>();
        }
        messageListeners.put(topic, mCallback);
    }

    /**
     * 移除消息监听
     *
     * @param mCallback callback
     */
    public void removeMessageListener(TMQMessageListener mCallback) {
        if (mCallback == null) return;
        if (messageListeners == null) return;
        messageListeners.remove(mCallback);
    }

    /**
     * 增加连接状态监听
     */
    public void addConnectionListener(TMQConnectionListener mCallback) {
        if (mCallback == null) return;
        if (this.connectionListeners == null) {
            this.connectionListeners = new ArrayList<>();
        }
        if (!this.connectionListeners.contains(mCallback)) {
            this.connectionListeners.add(mCallback);
        }
    }

    /**
     * 移除连接监听
     */
    public void removeConnectionListener(TMQConnectionListener mCallback) {
        if (mCallback == null) return;
        if (this.connectionListeners == null) return;
        this.connectionListeners.remove(mCallback);
    }

    /**
     * 检查是否已成功连接上broker
     *
     * @return isConnected
     */
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    /**
     * @param topic 账号密码只对单级主题生效，所以topic不能为多级主题
     * @return 拼接后的完整主题
     */
    private String subStringTopic(String topic) {
        if (topic == null) throw new RuntimeException("topic cannot be null!");
        //topic不能为多级主题
        if (topic.contains("/")) throw new RuntimeException("topic cannot contain '/'");
        return developer + "/" + TMQ_APPID + "/" + purpose + "/" + topic;
    }

    /**
     * 从拼接后的完整主题中拆分出末级topic
     *
     * @param fullTopic 完整主题
     * @return 末级topic
     */
    private String splitTopic(String fullTopic) {
        if (TextUtils.isEmpty(fullTopic)) return "";
        String[] split = fullTopic.split("/");
        if (split.length > 0) {
            return split[split.length - 1];
        }
        return "";
    }

    /**
     * 自动重连后订阅掉线时已订阅的主题
     */
    public void subscribeTopicsAfterAutoReconnect() {
        if (!isConnected()) return;
        for (Map.Entry<String, String> entry : subscriptions.entrySet()
        ) {
            joinChatRoom(splitTopic(entry.getKey()), null);
        }
    }

    private static class LruArrayList<T> {
        private ArrayList<T> mArray;
        private int maxCount;

        LruArrayList(int mMaxCount) {
            mArray = new ArrayList<>(mMaxCount);
            maxCount = mMaxCount;
        }

        public void add(T data) {
            if (mArray.size() == maxCount) {
                mArray.remove(0);
            }
            mArray.add(data);
        }

        public void remove(T data) {
            mArray.remove(data);
        }

        public boolean contains(T data) {
            return mArray.contains(data);
        }
    }
}
