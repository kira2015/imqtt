#import "ImqttPlugin.h"
#import <MQTTClient/MQTTClient.h>
#import "ImqttEvent.h"

@interface ImqttPlugin ()<MQTTSessionManagerDelegate>
@property (nonatomic,strong) MQTTSessionManager *sessionManager;
@property (nonatomic,assign) MQTTSessionManagerState state;
@property (nonatomic,strong) NSString *errorCode;
@property (nonatomic,strong) NSDictionary *contentMap;
@end


@implementation ImqttPlugin

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
    FlutterMethodChannel* channel = [FlutterMethodChannel
                                     methodChannelWithName:@"imqtt"
                                     binaryMessenger:[registrar messenger]];
    ImqttPlugin* instance = [[ImqttPlugin alloc] init];
    [instance initValue];
    [registrar addMethodCallDelegate:instance channel:channel];
    
    [ImqttEvent shareWithName:@"imqtt_event" binaryMessenger:[registrar messenger]];
    
}
- (void)initValue{
    self.state = 0;
    self.errorCode = @"";
    self.contentMap = @{};
}

- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
    
    if([@"connect" isEqualToString:call.method]){
        NSLog(@"connect收到信息参数:%@",call.arguments);
        [self connectWithMap:call.arguments result:result];
    }else if([@"join" isEqualToString:call.method]){
        NSLog(@"加入订阅:%@",call.arguments);
        self.sessionManager.subscriptions = call.arguments;
    }else if([@"unsubscribe" isEqualToString:call.method]){
        NSLog(@"取消订阅:%@",call.arguments);
        [self.sessionManager.session unsubscribeTopic:call.arguments];
    }else if([@"disconnect" isEqualToString:call.method]){
        NSLog(@"关闭:%@",call.arguments);
        [self.sessionManager disconnectWithDisconnectHandler:nil];
    }else {
        result(FlutterMethodNotImplemented);
    }
}

- (void)connectWithMap:(NSDictionary*)map result:(FlutterResult)result{
    
    MQTTSSLSecurityPolicy *securityPolicy = [MQTTSSLSecurityPolicy policyWithPinningMode:MQTTSSLPinningModeNone];
    securityPolicy.allowInvalidCertificates = YES;
    securityPolicy.validatesDomainName = NO;
    securityPolicy.validatesCertificateChain = NO;
    NSMutableDictionary *dictM = map[@"willData"];
    NSError *error = nil;
    NSData *willData = [NSJSONSerialization dataWithJSONObject:dictM options:NSJSONWritingPrettyPrinted error:&error];
    
    NSString *host = map[@"host"];
    NSInteger port = [map[@"port"] integerValue];
    NSInteger keepAlive = [map[@"keepAlive"] integerValue];
    NSString *userName = map[@"userName"];
    NSString *password = map[@"password"];
    NSString *clientId = map[@"clientId"];
    NSString *willTopic = map[@"willTopic"];
    BOOL log = [map[@"log"] boolValue];
    if (log) {
        [MQTTLog setLogLevel:DDLogLevelAll];
    }else{
        [MQTTLog setLogLevel:DDLogLevelOff];
    }
    [self.sessionManager connectTo:host port:port tls:YES keepalive:keepAlive clean:YES auth:YES user:userName pass:password will:YES willTopic:willTopic willMsg:willData willQos:MQTTQosLevelAtLeastOnce willRetainFlag:NO withClientId:clientId securityPolicy:securityPolicy certificates:nil protocolLevel:MQTTProtocolVersion311 connectHandler:^(NSError *error) {
        if (error) {
            NSLog(@"报错了%@",error);
            result(@0);
        }else{
            result(@1);
        }
        
    }];
    
}

- (void)handleMessage:(NSData *)data onTopic:(NSString *)topic retained:(BOOL)retained{
    NSString *jsonString = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    NSDictionary *map = @{
        @"topic":topic,
        @"payload":jsonString
    };
    self.contentMap = map;
    [[ImqttEvent event] sendEvent:[self sinkContent]];
}

- (void)sessionManager:(MQTTSessionManager *)sessionManager didChangeState:(MQTTSessionManagerState)newState{
    self.state = newState;
    switch (newState) {
        case MQTTSessionManagerStateClosed:
            NSLog(@"MQTT连接已经关闭");
            break;
        case MQTTSessionManagerStateClosing:
            NSLog(@"MQTT连接正在关闭");
            break;
        case MQTTSessionManagerStateConnected:
            NSLog(@"MQTT连接成功----");
            break;
            
        case MQTTSessionManagerStateConnecting:
            NSLog(@"MQTT正在连接中");
            break;
        case MQTTSessionManagerStateError: {
            NSString *errorCode = self.sessionManager.lastErrorCode.localizedDescription;
            NSLog(@"MQTT连接异常%@",errorCode);
            self.errorCode = errorCode;
        }
            break;
        case MQTTSessionManagerStateStarting:
            NSLog(@"MQTT开始连接");
            break;
        default:
            break;
    }
    [[ImqttEvent event] sendEvent:[self sinkContent]];
}


- (MQTTSessionManager *)sessionManager{
    if (!_sessionManager) {
        _sessionManager = [[MQTTSessionManager alloc]init];
        _sessionManager.delegate = self;
        
    }
    return _sessionManager;
}

- (NSDictionary*)sinkContent{
    return @{
        @"state":@(self.state),
        @"content":self.contentMap,
        @"error":self.errorCode
    };
}


@end
