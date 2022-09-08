//
//  ImqttEvent.m
//  imqtt
//
//  Created by wzy on 2022/9/6.
//

#import "ImqttEvent.h"

@interface ImqttEvent ()<FlutterStreamHandler>
@property (nonatomic, strong) FlutterEventChannel *eventChannel;
// typedef void (^FlutterEventSink)(id _Nullable event);
@property (nonatomic, strong) FlutterEventSink eventSink;
@end
@implementation ImqttEvent

static ImqttEvent *_ee=nil;

+ (ImqttEvent*)event{
    if (_ee == nil) {
        _ee = [[self alloc]init];
    }
    return _ee;
}

+ (instancetype)shareWithName:(NSString*)name binaryMessenger:(NSObject<FlutterBinaryMessenger>*)messenger{
    [self event].eventChannel = [FlutterEventChannel eventChannelWithName:name binaryMessenger:messenger];
    // 设置代理
    [[self event].eventChannel setStreamHandler:[self event]];
    return _ee;
}

- (FlutterError * _Nullable)onCancelWithArguments:(id _Nullable)arguments {
    return  nil;
}

- (FlutterError * _Nullable)onListenWithArguments:(id _Nullable)arguments eventSink:(nonnull FlutterEventSink)events {
    self.eventSink = events;
    return nil;
}
-(void)sendEvent:(id)obj{
    if (self.eventSink) {
        self.eventSink(obj);
    }
}
@end
