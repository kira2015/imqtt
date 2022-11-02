package com.itouchtv.mqtt.imqtt;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * Name: MqttAlarmService
 * Author: mxs
 * Email:
 * Comment: //TODO
 * Date: 2020-09-08 10:39
 */
public class MqttAlarmService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }
}
