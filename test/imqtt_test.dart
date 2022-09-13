import 'package:flutter_test/flutter_test.dart';
import 'package:imqtt/imqtt_msg_model.dart';
import 'package:imqtt/imqtt_platform_interface.dart';
import 'package:imqtt/imqtt_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockImqttPlatform 
    with MockPlatformInterfaceMixin
    implements ImqttPlatform {

  @override
  // Future<String?> getPlatformVersion() => Future.value('42');
  
  @override
  Future<bool> connect({required Map args}) {
    // TODO: implement connect
    throw UnimplementedError();
  }
  
  @override
  Stream<MQMsgModel> getStream() {
    // TODO: implement getStream
    throw UnimplementedError();
  }
  
  @override
  void disconnect() {
    // TODO: implement disconnect
  }
  
  @override
  void join({required String topic}) {
    // TODO: implement join
  }
  
  @override
  void unsubscribe({required String topic}) {
    // TODO: implement unsubscribe
  }
  
  @override
  // TODO: implement topicsSource
  Map<String, int> get topicsSource => {};
}

void main() {
  final ImqttPlatform initialPlatform = ImqttPlatform.instance;

  test('$MethodChannelImqtt is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelImqtt>());
  });


}
