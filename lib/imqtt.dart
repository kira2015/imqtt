import 'imqtt_platform_interface.dart';

enum ImqttState { connecting, error, connected, closing, closed }

class Imqtt {
  ///连接
  /// /* args:
  /// {
  ///    'host': 'xx.com',
  ///    'port': 1813,
  ///    'userName': 'abc',
  ///    'password': '123456',
  ///    'clientId': 'RootBot999',
  ///    'keepAlive': 15,
  ///    'willTopic': 'xxx/willTopic',
  ///    'willData': {},
  ///    'log':false
  ///  }
  /// */
  Future<bool> connect({required Map args}) {
    return ImqttPlatform.instance.connect(args: args);
  }

  /// 获取流
  /// /*
  /// {
  ///   state: $ImqttState,
  ///   content:{
  ///     topic:topic,payload:payload
  ///    },
  ///   error:xxx
  /// }
  ///  */
  Stream getStream() {
    return ImqttPlatform.instance.getStream();
  }

  ///断开连接
  void disconnect() {
    ImqttPlatform.instance.disconnect();
  }

  ///订阅主题
  void join({required String topic}) {
    ImqttPlatform.instance.join(topic: topic);
  }

  ///取消订阅
  void unsubscribe({required String topic}) {
    ImqttPlatform.instance.unsubscribe(topic: topic);
  }
}
