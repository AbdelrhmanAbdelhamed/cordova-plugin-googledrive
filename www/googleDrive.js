var exec = cordova.require('cordova/exec');

var service = 'GoogleDrive';

var googleDrive = {
    signIn: function (isFileScoped, isAppFolderScoped, errorCallBack, SuccessCallBack) {
        var action = 'signIn';
        console.error(service + " " + action + " cordova is not available");
    },
    createFile: function (title, contents, mimeType, inAppFolder, errorCallBack, SuccessCallBack) {
        var action = 'createFile';
        console.error(service + " " + action + " cordova is not available");
    },
    retrieveFileContentsByTitle: function (title, inAppFolder, errorCallBack, SuccessCallBack) {
        var action = 'retrieveFileContentsByTitle';
        console.error(service + " " + action + " cordova is not available");
    }
};

if (typeof cordova !== 'undefined') {
    googleDrive = {
        signIn: function (isFileScoped, isAppFolderScoped, successCallback, errorCallback) {
            var action = 'signIn';
            exec(successCallback, errorCallback, service, action, [isFileScoped, isAppFolderScoped]);
        },
        createFile: function (title, contents, mimeType, inAppFolder, successCallback, errorCallback) {
            var action = 'createFile';
            exec(successCallback, errorCallback, service, action, [title, contents, mimeType, inAppFolder]);
        },
        retrieveFileContentsByTitle: function (title, inAppFolder, successCallback, errorCallback) {
            var action = 'retrieveFileContentsByTitle';
            exec(successCallback, errorCallback, service, action, [title, inAppFolder]);
        }
    };

}
module.exports = googleDrive;
