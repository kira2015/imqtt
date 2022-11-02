package com.itouchtv.mqtt.imqtt.tmq;

/**
 * Created by heweizong on 2018/1/25.
 */

public interface TMQConnectionListener {
    void onConnected(boolean reconnect);

    void onDisconnected(int errorCode);
}
