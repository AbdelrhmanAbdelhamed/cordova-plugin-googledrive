
package org.apache.cordova.exception;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;


/**
* This class exposes methods in Cordova that can be called from JavaScript.
*/
public class GoogleDrive extends CordovaPlugin {



     /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArry of arguments for the plugin.
     * @param callbackContext   The callback context from which we were invoked.
     */
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("createFile")) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, "PLUGIN TEST!"));
        } else {
            return false;
        }
        return true;
    }
}
