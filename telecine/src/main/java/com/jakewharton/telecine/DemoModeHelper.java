package com.jakewharton.telecine;

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.AsyncTask;
import android.widget.Toast;
import com.nightlynexus.demomode.DemoMode;
import com.nightlynexus.demomode.DemoModeInitializer;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.M;
import static android.widget.Toast.LENGTH_SHORT;
import static com.nightlynexus.demomode.DemoModeInitializer.DemoModeSetting.ENABLED;
import static com.nightlynexus.demomode.DemoModeInitializer.GrantPermissionResult.FAILURE;
import static com.nightlynexus.demomode.DemoModeInitializer.GrantPermissionResult.SUCCESS;

final class DemoModeHelper {
  private static final int REQUEST_CODE_ENABLE_DEMO_MODE = 5309;

  private DemoModeHelper() {
    throw new AssertionError("No instances.");
  }

  private enum DemoModeAvailability {
    AVAILABLE, UNAVAILABLE, NEEDS_ROOT_ACCESS, NEEDS_DEMO_MODE_SETTING
  }

  interface ShowDemoModeSetting {
    void show();

    void hide();
  }

  static void showDemoModeSetting(final Activity activity, final ShowDemoModeSetting callback) {
    if (SDK_INT < M) {
      callback.hide();
      return;
    }
    showDemoModeSetting23(activity, callback);
  }

  @TargetApi(M) private static void showDemoModeSetting23(final Activity activity,
      final ShowDemoModeSetting callback) {
    final DemoModeInitializer demoModeInitializer = DemoMode.initializer(activity);
    new AsyncTask<Void, Void, DemoModeAvailability>() {
      @Override protected DemoModeAvailability doInBackground(Void... params) {
        DemoModeInitializer.GrantPermissionResult grantBroadcastPermissionResult =
            demoModeInitializer.grantBroadcastPermission();
        if (grantBroadcastPermissionResult == FAILURE) {
          return DemoModeAvailability.NEEDS_ROOT_ACCESS;
        }
        if (grantBroadcastPermissionResult != SUCCESS) {
          return DemoModeAvailability.UNAVAILABLE;
        }
        DemoModeInitializer.GrantPermissionResult setDemoModeSettingResult =
            demoModeInitializer.setDemoModeSetting(ENABLED);
        if (setDemoModeSettingResult != SUCCESS) {
          return DemoModeAvailability.NEEDS_DEMO_MODE_SETTING;
        }
        return DemoModeAvailability.AVAILABLE;
      }

      @Override protected void onPostExecute(DemoModeAvailability demoModeAvailability) {
        switch (demoModeAvailability) {
          case AVAILABLE:
            callback.show();
            break;
          case UNAVAILABLE:
            callback.hide();
            break;
          case NEEDS_ROOT_ACCESS:
            callback.hide();
            Toast.makeText(activity, R.string.root_permission_denied, LENGTH_SHORT).show();
            break;
          case NEEDS_DEMO_MODE_SETTING:
            callback.hide();
            Toast.makeText(activity, R.string.enable_demo_mode_in_settings, LENGTH_SHORT).show();
            activity.startActivityForResult(demoModeInitializer.demoModeScreenIntent(),
                REQUEST_CODE_ENABLE_DEMO_MODE);
            break;
        }
      }
    }.execute();
  }

  @TargetApi(M) static boolean handleActivityResult(Activity activity, int requestCode,
      ShowDemoModeSetting callback) {
    if (requestCode != REQUEST_CODE_ENABLE_DEMO_MODE) {
      return false;
    }
    if (DemoMode.initializer(activity).getDemoModeSetting() == ENABLED) {
      showDemoModeSetting(activity, callback);
    }
    return true;
  }
}
