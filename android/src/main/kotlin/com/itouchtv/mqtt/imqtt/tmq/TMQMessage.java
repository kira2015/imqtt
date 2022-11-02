package com.itouchtv.mqtt.imqtt.tmq;


import com.itouchtv.mqtt.imqtt.mqttv3.MqttMessage;

import java.util.Random;

/**
 * Created by heweizong on 2018/1/24.
 */

public class TMQMessage {
    private String topic;
    private String content;
    private TMQCallback mTMQCallback;
    private MqttMessage mMqttMessage;

    private int qos = 1;
    private boolean retained = false;

    public static TMQMessage createTMQMsg(String topic, MqttMessage msg) {
        TMQMessage tmq = new TMQMessage();
        tmq.setTopic(topic);
        tmq.setContent(new String(msg.getPayload()));
        tmq.setMqttMessage(msg);
        return tmq;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String mTopic) {
        topic = mTopic;
    }

    public int getQos() {
        return qos;
    }

    public boolean isRetained() {
        return retained;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String mContent) {
        content = mContent;
    }

    public TMQCallback getTMQCallback() {
        return mTMQCallback;
    }

    public void setTMQCallback(TMQCallback mTMQCallback) {
        this.mTMQCallback = mTMQCallback;
    }

    public MqttMessage getMqttMessage() {
        return mMqttMessage;
    }

    public void setMqttMessage(MqttMessage mMqttMessage) {
        this.mMqttMessage = mMqttMessage;
    }

    private static int id_tail = 0;//messageId的后四位

    public static String generateMessageId() {
        String timeStamp = System.currentTimeMillis() + "";
        int radius;
        Random mRandom = new Random();
        do {
            radius = mRandom.nextInt(100);
        } while (radius == 0);
        id_tail += radius;
        if (id_tail > 9999) {
            id_tail = 0;
        }
        String tail = id_tail + "";
        while (tail.length() < 4) {
            tail = "0" + tail;
        }
        return timeStamp + tail;
    }
}
