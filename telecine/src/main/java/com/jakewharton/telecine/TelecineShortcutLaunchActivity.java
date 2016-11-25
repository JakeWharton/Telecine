package com.jakewharton.telecine;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.google.android.gms.analytics.HitBuilders;
import javax.inject.Inject;

public final class TelecineShortcutLaunchActivity extends Activity {
  private static final String KEY_ACTION = "launch-action";
  static final String EXTRA_AUTO_RECORDING = "auto-recording";

  static Intent createQuickTileIntent(Context context) {
    Intent intent = new Intent(context, TelecineShortcutLaunchActivity.class);
    intent.putExtra(KEY_ACTION, Analytics.ACTION_QUICK_TILE_LAUNCHED);
    return intent;
  }

  @Inject Analytics analytics;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ((TelecineApplication) getApplication()).injector().inject(this);

    String launchAction = getIntent().getStringExtra(KEY_ACTION);
    if (launchAction == null) {
      launchAction = Analytics.ACTION_SHORTCUT_LAUNCHED;
    }

    analytics.send(new HitBuilders.EventBuilder() //
        .setCategory(Analytics.CATEGORY_SHORTCUT)
        .setAction(launchAction)
        .build());

    CaptureHelper.fireScreenCaptureIntent(this, analytics);
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (getIntent().getBooleanExtra(EXTRA_AUTO_RECORDING, false) && data != null) {
      // Add the auto-recording extra from the original intent to the result data.
      data.putExtra(EXTRA_AUTO_RECORDING, true);
    }
    if (!CaptureHelper.handleActivityResult(this, requestCode, resultCode, data, analytics)) {
      super.onActivityResult(requestCode, resultCode, data);
    }
    finish();
  }

  @Override protected void onStop() {
    if (!isFinishing()) {
      finish();
    }
    super.onStop();
  }
}
