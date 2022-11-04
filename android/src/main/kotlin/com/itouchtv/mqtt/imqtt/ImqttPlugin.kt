package com.itouchtv.mqtt.imqtt

import androidx.annotation.NonNull
import com.itouchtv.mqtt.imqtt.tmq.TMQCallback
import com.itouchtv.mqtt.imqtt.tmq.TMQClient
import com.itouchtv.mqtt.imqtt.tmq.TMQTrustManager

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager

/** ImqttPlugin */
class ImqttPlugin : FlutterPlugin, MethodCallHandler {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "imqtt")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        if (call.method == "getPlatformVersion") {
            result.success("Android ${android.os.Build.VERSION.RELEASE}")
        } else if (call.method == "connect") {
            val map: Map<String, String> = call.arguments as Map<String, String>
            TMQClient.getInstance().init(createSSLOptions())
            val loginCallback = object : TMQCallback {
                override fun onSuccess() {
                    result.success(null)
                }

                override fun onError(errorCode: Int, errorMsg: String?) {
                    result.error(errorCode.toString(), errorMsg, null)
                }
            }
            TMQClient.getInstance().login(map.get("userName"), map.get("password"), map.get("serverURI"), loginCallback)
        } else if (call.method == "subscribe") {
            val callback = object : TMQCallback {
                override fun onSuccess() {
                    result.success(null)
                }

                override fun onError(errorCode: Int, errorMsg: String?) {
                    result.error(errorCode.toString(), errorMsg, null)
                }
            }
            TMQClient.getInstance().joinChatRoom(call.argument("topic"), callback)
        } else if (call.method == "unsubscribe") {
            TMQClient.getInstance().quitChatRoom(call.argument("topic"))
        } else if (call.method == "disconnect") {
            TMQClient.getInstance().logout()
        } else {
            result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    fun createSSLOptions(): MqttConnectOptions {
        val mOptions = MqttConnectOptions()
        mOptions.setKeepAliveInterval(15)
        mOptions.setAutomaticReconnect(true)

        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf<TrustManager>(TMQTrustManager()), null)
            mOptions.setSocketFactory(sslContext.socketFactory)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return mOptions
    }
}
