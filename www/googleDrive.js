var exec = cordova.require('cordova/exec');

var service = 'GoogleDrive';

module.exports = {
    signIn: function (isFileScoped, isAppFolderScoped, successCallback, errorCallback) {
        var action = 'signIn';
        exec(successCallback, errorCallback, service, action, [isFileScoped, isAppFolderScoped]);
    },
    createFile: function (title, contents, mimeType, inAppFolder, successCallback, errorCallback) {
        var action = 'createFile';
        exec(successCallback, errorCallback, service, action, [title, contents, mimeType, inAppFolder]);
    },
    getFileWithTitle: function (title, inAppFolder, successCallback, errorCallback) {
        var action = 'getFileWithTitle';
        exec(successCallback, errorCallback, service, action, [title, inAppFolder]);
    }
};
