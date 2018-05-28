package org.loyalx;

import android.content.Intent;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

import static android.app.Activity.RESULT_OK;

/**
 * This class exposes methods in Cordova that can be called from JavaScript.
 */
public class GoogleDrive extends CordovaPlugin {

    private static final String TAG = "plugin-googledrive";

    /**
     * Request code for Google Sign-in
     */
    private static final int REQUEST_CODE_SIGN_IN = 0;

    /**
     * Handles high-level drive functions like sync
     */
    private DriveClient mDriveClient;

    /**
     * Handle access to Drive resources/files.
     */
    private DriveResourceClient mDriveResourceClient;

    private CallbackContext mCallbackContext;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.cordova = cordova;
        if (mDriveResourceClient == null || mDriveClient == null) {
            this.signIn();
        }
        Log.i(TAG, "Plugin initialized. Cordova has activity: " + cordova.getActivity());
    }

    /**
     * Handles resolution callbacks.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SIGN_IN && resultCode == RESULT_OK) {
            Task<GoogleSignInAccount> getAccountTask =
                GoogleSignIn.getSignedInAccountFromIntent(data);
            if (getAccountTask.isSuccessful()) {
                initializeDriveClient(getAccountTask.getResult());
            } else {
                Log.e(TAG, "Sign-in failed.");
            }
        } else {
            mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "user cancelled authorization"));
        }
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action          The action to execute.
     * @param args            JSONArry of arguments for the plugin.
     * @param callbackContext The callback context from which we were invoked.
     */
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        mCallbackContext = callbackContext;
        if (action.equals("createFile")) {
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String title = args.getString(0);
                        String contents = args.getString(1);
                        String mimeType = args.getString(2);
                        Boolean inAppFolder = args.getBoolean(3);

                        createFile(title, contents, mimeType, inAppFolder)
                            .addOnSuccessListener(cordova.getActivity(),
                                driveFile -> {
                                    try {
                                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, new JSONObject().put("resourceId", driveFile.getDriveId())));
                                    } catch (JSONException ex) {
                                        Log.i(TAG, ex.getMessage());
                                    }
                                })
                            .addOnFailureListener(cordova.getActivity(), e -> {
                                Log.e(TAG, "Unable to create file", e);
                            });
                    } catch (JSONException ex) {
                        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, ex.getLocalizedMessage()));
                    }
                }
            });
            return true;
        } else {
            return false;
        }
    }

    private void signIn() {

        Set<Scope> requiredScopes = new HashSet<>(2);
        requiredScopes.add(Drive.SCOPE_FILE);
        requiredScopes.add(Drive.SCOPE_APPFOLDER);

        GoogleSignInAccount signInAccount = GoogleSignIn.getLastSignedInAccount(cordova.getActivity().getApplicationContext());

        if (signInAccount != null && signInAccount.getGrantedScopes().containsAll(requiredScopes)) {
            initializeDriveClient(signInAccount);
        } else {
            GoogleSignInOptions signInOptions =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestScopes(Drive.SCOPE_FILE)
                    .requestScopes(Drive.SCOPE_APPFOLDER)
                    .build();
            GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(cordova.getActivity(), signInOptions);
            cordova.getActivity().startActivityForResult(googleSignInClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
        }
    }


    /**
     * Continues the sign-in process, initializing the Drive clients with the current
     * user's account.
     */
    private void initializeDriveClient(GoogleSignInAccount signInAccount) {
        mDriveClient = Drive.getDriveClient(cordova.getActivity().getApplicationContext(), signInAccount);
        mDriveResourceClient = Drive.getDriveResourceClient(cordova.getActivity().getApplicationContext(), signInAccount);
    }

    /**
     * create a file in the root folder or the app folder
     *
     * @param title       The file title with extension
     * @param contents    The file content ex: text
     * @param inAppFolder Flag the app folder
     * @param mimeType    The meme type of the file
     */
    private Task<DriveFile> createFile(String title, String contents, String mimeType, Boolean inAppFolder) {
        final Task<DriveFolder> appFolderTask = inAppFolder ? getDriveResourceClient().getAppFolder() : getDriveResourceClient().getRootFolder();
        final Task<DriveContents> createContentsTask = getDriveResourceClient().createContents();

        final Task<DriveFile> driveFileTask = Tasks.whenAll(appFolderTask, createContentsTask)
            .continueWithTask(task -> {
                DriveFolder parent = appFolderTask.getResult();
                DriveContents driveContents = createContentsTask.getResult();
                OutputStream outputStream = driveContents.getOutputStream();
                try (Writer writer = new OutputStreamWriter(outputStream)) {
                    writer.write(contents);
                }
                MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle(title)
                    .setMimeType(mimeType)
                    .setStarred(inAppFolder)
                    .build();
                return getDriveResourceClient().createFile(parent, changeSet, driveContents);
            });
        return driveFileTask;
    }

    private DriveResourceClient getDriveResourceClient() {
        return mDriveResourceClient;
    }

}
