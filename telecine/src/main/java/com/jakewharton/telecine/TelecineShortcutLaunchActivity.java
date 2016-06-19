package com.jakewharton.telecine;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.google.android.gms.analytics.HitBuilders;
import javax.inject.Inject;

public final class TelecineShortcutLaunchActivity extends Activity {
  @Inject Analytics analytics;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ((TelecineApplication) getApplication()).injector().inject(this);

    analytics.send(new HitBuilders.EventBuilder() //
        .setCategory(Analytics.CATEGORY_SHORTCUT)
        .setAction(Analytics.ACTION_SHORTCUT_LAUNCHED)
        .build());

    CaptureHelper.fireScreenCaptureIntent(this, analytics);
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
