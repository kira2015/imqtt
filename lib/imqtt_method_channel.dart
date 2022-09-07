import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'imqtt_platform_interface.dart';

/// An implementation of [ImqttPlatform] that uses method channels.
class MethodChannelImqtt extends ImqttPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('imqtt');
  final EventChannel eventChannel = const EventChannel('imqtt_event');

  @override
  Future<bool> connect({required Map args}) async {
    final state = await methodChannel.invokeMethod<int>('connect', args);
    return state == 1;
  }

  @override
  Stream getStream() {
    return eventChannel.receiveBroadcastStream();
  }

  @override
  void disconnect() {
    methodChannel.invokeMethod('disconnect');
  }

  @override
  void join({required String topic}) {
    topicsSource[topic] = 1;
    methodChannel.invokeMethod('join', topicsSource);
  }
  @override
  void unsubscribe({required String topic}) {
    topicsSource.remove(topic);
    methodChannel.invokeMethod('unsubscribe', topic);
  }
}
