
var exec = cordova.require('cordova/exec');

/**
 * @namespace cordova.plugins
 */

module.exports = {
    createFile: function (successCallback, errorCallback, title, contents, mimeType, inAppFolder) {
        var service = 'GoogleDrive';
        var action = 'createFile';
        exec(successCallback, errorCallback, service, action, [title, contents, mimeType, inAppFolder]);
    }
};