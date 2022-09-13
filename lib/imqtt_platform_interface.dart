import 'package:imqtt/imqtt_msg_model.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'imqtt_method_channel.dart';

abstract class ImqttPlatform extends PlatformInterface {
  /// Constructs a ImqttPlatform.
  ImqttPlatform() : super(token: _token);

  static final Object _token = Object();

  static ImqttPlatform _instance = MethodChannelImqtt();

  /// The default instance of [ImqttPlatform] to use.
  ///
  /// Defaults to [MethodChannelImqtt].
  static ImqttPlatform get instance => _instance;
  
  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [ImqttPlatform] when
  /// they register themselves.
  static set instance(ImqttPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }
  ///订阅主题集合
  final Map<String,int> topicsSource = {};

  Future<bool> connect({required Map args}) {
    throw UnimplementedError('ImqttPlatform() has not been implemented.');
  }
  Stream<MQMsgModel?> getStream(){
    throw UnimplementedError('ImqttPlatform() has not been implemented.');
  }
  void disconnect(){
    throw UnimplementedError('ImqttPlatform() has not been implemented.');
  }

  void join({required String topic}) {
    throw UnimplementedError('ImqttPlatform() has not been implemented.');
  }

  void unsubscribe({required String topic}) {
    throw UnimplementedError('ImqttPlatform() has not been implemented.');
  }
}
