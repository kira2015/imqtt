package com.itouchtv.mqtt.imqtt.tmq;

/**
 * Created by heweizong on 2018/1/24.
 */

public interface TMQCallback {
    void onSuccess();

    void onError(int errorCode, String errorMsg);
}
