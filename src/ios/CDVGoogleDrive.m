
#import <Cordova/CDV.h>

@interface CDVGoogleDrive : CDVPlugin
@end

@implementation CDVGoogleDrive

- (void)googleDrive:(CDVInvokedUrlCommand*)command
{
    id message = [command.arguments objectAtIndex:0];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:message];

    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

- (void)googleDriveAsyncHelper:(NSArray*)args
{
    [self.commandDelegate sendPluginResult:[args objectAtIndex:0] callbackId:[args objectAtIndex:1]];
}

- (void)googleDriveAsync:(CDVInvokedUrlCommand*)command
{
    id message = [command.arguments objectAtIndex:0];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:message];

    [self performSelector:@selector(googleDriveAsyncHelper:) withObject:[NSArray arrayWithObjects:pluginResult, command.callbackId, nil] afterDelay:0];
}

@end
