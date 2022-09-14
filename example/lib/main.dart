import 'package:flutter/material.dart';
import 'dart:async';
import 'package:imqtt/imqtt.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _imqttPlugin = Imqtt();
  late StreamSubscription subscription;

  @override
  void initState() {
    super.initState();

    subscription = _imqttPlugin.getStream().listen((event) {
      print('Flutter监听:$event');
    });
  }

  @override
  void dispose() {
    subscription.cancel();
    _imqttPlugin.disconnect();
    super.dispose();
  }

  Future<bool> initMqtt() async {
    return await _imqttPlugin.connect(args: {
      
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextButton(
                  onPressed: () async {
                    final rr = await initMqtt();
                    print('连接情况:$rr');
                  },
                  child: const Text('连接mqtt')),
              TextButton(
                  onPressed: () async {
                    _imqttPlugin.subscribe(
                        topic: 'xxx/IM/willTopic');
                  },
                  child: const Text('订阅')),
              TextButton(
                  onPressed: () async {
                    _imqttPlugin.subscribe(topic: 'q123');
                  },
                  child: const Text('订阅2')),
              TextButton(
                  onPressed: () async {
                    _imqttPlugin.disconnect();
                  },
                  child: const Text('关闭')),
            ],
          ),
        ),
      ),
    );
  }
}
