#import "FlutterzPlayer.h"
#import <flutter_zplayer/flutter_zplayer-Swift.h>

@implementation FlutterZplayerPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterzPlayer registerWithRegistrar:registrar];
}
@end
