//
//  ImqttEvent.h
//  imqtt
//
//  Created by wzy on 2022/9/6.
//

#import <Foundation/Foundation.h>
#import <Flutter/Flutter.h>

NS_ASSUME_NONNULL_BEGIN

@interface ImqttEvent : NSObject


+ (ImqttEvent*)event;

+ (instancetype)shareWithName:(NSString*)name binaryMessenger:(NSObject<FlutterBinaryMessenger>*)messenger;

-(void)sendEvent:(id)obj;
@end

NS_ASSUME_NONNULL_END
