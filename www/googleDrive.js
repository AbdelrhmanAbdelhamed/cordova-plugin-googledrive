
var exec = cordova.require('cordova/exec');

module.exports = {
    createFile: function (title, contents, mimeType, inAppFolder, successCallback, errorCallback) {
        var service = 'GoogleDrive';
        var action = 'createFile';
        exec(successCallback, errorCallback, service, action, [title, contents, mimeType, inAppFolder]);
    }
};