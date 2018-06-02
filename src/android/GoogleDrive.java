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
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

  /**
   * Handles resolution callbacks.
   */
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_CODE_SIGN_IN && resultCode == RESULT_OK) {
      Task<GoogleSignInAccount> getAccountTask = GoogleSignIn.getSignedInAccountFromIntent(data);
      if (getAccountTask.isSuccessful()) {
        initializeDriveClient(getAccountTask.getResult());
        try {
          mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK,
              new JSONObject().put("accountId", getAccountTask.getResult().getId())));
        } catch (JSONException ex) {
          mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, ex.getLocalizedMessage()));
        }
      } else {
        Log.e(TAG, "Sign-in failed.");
      }
    } else {
      mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "user cancelled authorization"));
    }
  }

  private DriveResourceClient getDriveResourceClient() {
    return mDriveResourceClient;
  }

  /**
   * Executes the request and returns PluginResult.
   *
   * @param action          The action to execute.
   * @param args            JSONArry of arguments for the plugin.
   * @param callbackContext The callback context from which we were invoked.
   */
  public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) {
    mCallbackContext = callbackContext;
    if (action.equals("signIn")) {
      cordova.getActivity().runOnUiThread(() -> {
        try {
          Boolean isFileScoped = args.getBoolean(0);
          Boolean isAppFolderScoped = args.getBoolean(1);
          signIn(isFileScoped, isAppFolderScoped);
        } catch (JSONException e) {
          e.printStackTrace();
        }
      });
      return true;
    } else if (action.equals("createFile")) {
      cordova.getThreadPool().execute(() -> {
        try {
          String title = args.getString(0);
          String contents = args.getString(1);
          String mimeType = args.getString(2);
          Boolean inAppFolder = args.getBoolean(3);

          createFile(title, contents, mimeType, inAppFolder).addOnSuccessListener(cordova.getActivity(), driveFile -> {
            try {
              callbackContext.sendPluginResult(
                  new PluginResult(PluginResult.Status.OK, new JSONObject().put("resourceId", driveFile.getDriveId())));
            } catch (JSONException ex) {
              callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, ex.getLocalizedMessage()));
            }
          }).addOnFailureListener(cordova.getActivity(), e -> {
            mCallbackContext.sendPluginResult(
                new PluginResult(PluginResult.Status.ERROR, "Failed to upload file: " + e.getLocalizedMessage()));
          });
        } catch (JSONException ex) {
          callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, ex.getLocalizedMessage()));
        }
      });
      return true;
    } else if (action.equals("getFileWithTitle")) {
      cordova.getThreadPool().execute(() -> {
        try {
          String title = args.getString(0);
          Boolean inAppFolder = args.getBoolean(1);
          getFileWithTitle(title, inAppFolder).addOnSuccessListener(cordova.getActivity(), metadataBuffer -> {
            if (metadataBuffer.getCount() > 0) {
              Metadata mb = metadataBuffer.get(0);
              String contents = retrieveContents(mb.getDriveId().asDriveFile());
              try {
                mCallbackContext.sendPluginResult(
                    new PluginResult(PluginResult.Status.OK, new JSONObject().put("contents", contents)));
              } catch (JSONException e) {
                mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage()));
              }
              metadataBuffer.release();
            }
          }).addOnFailureListener(e -> {
            mCallbackContext.sendPluginResult(
                new PluginResult(PluginResult.Status.ERROR, "Error retrieving files: " + e.getLocalizedMessage()));
          });
        } catch (JSONException e) {
          e.printStackTrace();
        }
      });
      return true;
    } else {
      return false;
    }
  }

  public void signIn(Boolean isFileScoped, Boolean isAppFolderScoped) {

    Set<Scope> requiredScopes = new HashSet<>(2);
    if (isFileScoped)
      requiredScopes.add(Drive.SCOPE_FILE);
    if (isAppFolderScoped)
      requiredScopes.add(Drive.SCOPE_APPFOLDER);

    GoogleSignInAccount signInAccount = GoogleSignIn
        .getLastSignedInAccount(cordova.getActivity().getApplicationContext());

    if (signInAccount != null && signInAccount.getGrantedScopes().containsAll(requiredScopes)) {
      try {
        mCallbackContext.sendPluginResult(
            new PluginResult(PluginResult.Status.OK, new JSONObject().put("accountId", signInAccount.getId())));
      } catch (JSONException ex) {
        mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, ex.getLocalizedMessage()));
      }
      initializeDriveClient(signInAccount);
    } else {
      GoogleSignInOptions.Builder googleSignInOptionsBuilder = new GoogleSignInOptions.Builder(
          GoogleSignInOptions.DEFAULT_SIGN_IN);

      if (isFileScoped)
        googleSignInOptionsBuilder.requestScopes(Drive.SCOPE_FILE);
      if (isAppFolderScoped)
        googleSignInOptionsBuilder.requestScopes(Drive.SCOPE_APPFOLDER);

      GoogleSignInOptions signInOptions = googleSignInOptionsBuilder.build();
      GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(cordova.getActivity(), signInOptions);

      cordova.setActivityResultCallback(this);
      cordova.getActivity().startActivityForResult(googleSignInClient.getSignInIntent(), REQUEST_CODE_SIGN_IN);
    }
  }

  /**
   * Continues the sign-in process, initializing the Drive clients with the
   * current user's account.
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

    final Task<DriveFolder> appFolderTask = inAppFolder ? getDriveResourceClient().getAppFolder()
        : getDriveResourceClient().getRootFolder();
    final Task<DriveContents> createContentsTask = getDriveResourceClient().createContents();

    final Task<DriveFile> driveFileTask = Tasks.whenAll(appFolderTask, createContentsTask).continueWithTask(task -> {
      DriveFolder parent = appFolderTask.getResult();
      DriveContents driveContents = createContentsTask.getResult();
      OutputStream outputStream = driveContents.getOutputStream();
      try (Writer writer = new OutputStreamWriter(outputStream)) {
        writer.write(contents);
      }
      MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setTitle(title).setMimeType(mimeType)
          .setStarred(inAppFolder).build();
      return getDriveResourceClient().createFile(parent, changeSet, driveContents);
    });
    return driveFileTask;
  }

  private Task<MetadataBuffer> getFileWithTitle(String title, Boolean inAppFolder) {
    Query query = new Query.Builder().addFilter(Filters.eq(SearchableField.TITLE, title)).build();
    Task<MetadataBuffer> queryTask = getDriveResourceClient()
        .queryChildren(inAppFolder ? getDriveResourceClient().getAppFolder().getResult()
            : getDriveResourceClient().getRootFolder().getResult(), query);
    return queryTask;
  }

  private String retrieveContents(DriveFile file) {
    StringBuilder contentsBuilder = new StringBuilder();

    Task<DriveContents> openFileTask = getDriveResourceClient().openFile(file, DriveFile.MODE_READ_ONLY);
    openFileTask.continueWithTask(task -> {
      DriveContents contents = task.getResult();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(contents.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          contentsBuilder.append(line).append("\n");
        }
      }
      Task<Void> discardTask = getDriveResourceClient().discardContents(contents);
      return discardTask;
    }).addOnFailureListener(e -> {
      mCallbackContext.sendPluginResult(
          new PluginResult(PluginResult.Status.ERROR, "Unable to read contents: " + e.getLocalizedMessage()));
    });

    return contentsBuilder.toString();
  }

}
