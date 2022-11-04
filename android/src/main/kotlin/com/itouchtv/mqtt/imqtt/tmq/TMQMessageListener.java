package com.itouchtv.mqtt.imqtt.tmq;

/**
 * Created by heweizong on 2018/1/25.
 */

public interface TMQMessageListener {
    void onMessageReceived(TMQMessage msg);

//    void onCmdMessageReceived(List<String> var1);
//
//    void onMessageRead(List<String> var1);
//
//    void onMessageDelivered(List<String> var1);
//
//    void onMessageRecalled(List<String> var1);
//
//    void onMessageChanged(String var1, Object var2);
}
