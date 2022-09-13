//enum ImqttState { connecting, error, connected, closing, closed }
import 'dart:convert';
import 'package:imqtt/imqtt.dart';

class MQMsgModel {
  late ImqttState state;
  String? topic;
  MQPayloadModel? payload;
  String? error;
  MQMsgModel(
      {this.state = ImqttState.starting, this.topic, this.payload, this.error});

  MQMsgModel.fromJson(Map map) {
    state = ImqttState.values[int.tryParse('${map['state']}') ?? 0];
    topic = map['topic'];
    if (map['payload'] is String && map['payload'].isNotEmpty) {
      Map payloadMap = json.decode(map['payload']);
      payload = MQPayloadModel.fromJson(payloadMap);
    }
    error = map['error'];
  }
  @override
  String toString() =>
      'MQMsgModel(state: $state, topic: $topic, payload: ${payload.toString()}, error: $error)';
}

class MQPayloadModel {
  ///IM的消息体（包含自建和环信）变为：（多了messageId字段）
  String? messageId;

  ///消息类型  0没用 1评论  2 送礼（包括点赞） 3加入聊天室 4离开聊天室 5主播发出图文消息 6主播删除图文消息
  int? msgType;

  ///消息体（当 msgType=1，此字段为JSON.toJSONString(UserCommentMsg)；
  ///当msgType=2, 该字段为JSON.toJSONString(GiftMessage)；
  ///当msgType=3,4,5时，该字段为空；
  ///当msgType=6时，该字段为删除图文消息id的列表，比如“111,222,333”）
  String? data;

  ///用户昵称
  String? nickName;

  ///肖像路径
  String? userAvatar;

  ///发言人（环信userId）
  String? fromUserId;

  String? appUserId;
  String? chatroomsId;
  int? gender;
  int? mediaLivePk;

  ///当 msgType=1
  MQMsgContentModel? content;
  MQPayloadModel(
      {required this.messageId,
      required this.msgType,
      required this.data,
      this.nickName,
      this.userAvatar,
      this.fromUserId,
      this.appUserId,
      this.chatroomsId,
      this.gender,
      this.mediaLivePk});
  MQPayloadModel.fromJson(Map map) {
    messageId = map['messageId'];
    msgType = map['msgType'];
    data = map['data'];
    nickName = map['nickName'];
    userAvatar = map['userAvatar'];
    fromUserId = map['fromUserId'];
    appUserId = map['appUserId'];
    chatroomsId = map['chatroomsId'];
    mediaLivePk = map['mediaLivePk'];
    gender = map['gender'];
    if (msgType == 1 && data?.isNotEmpty == true) {
      content = MQMsgContentModel.fromRawJson(data!);
    }
  }
  @override
  String toString() {
    return "MQPayloadModel( messageId: $messageId, msgType: $msgType, data: $data, nickName: $nickName, userAvatar: $userAvatar, fromUserId: $fromUserId, appUserId: $appUserId, chatroomsId: $chatroomsId, mediaLivePk:$mediaLivePk, gender:$gender )";
  }
}

class MQMsgContentModel {
  String? content;
  int? from;
  int? pk;
  MQMsgContentModel.fromRawJson(String str) {
    Map map = json.decode(str);
    content = map['content'];
    from = map['from'];
    pk = map['pk'];
  }
}
