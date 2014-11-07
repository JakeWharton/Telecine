package com.jakewharton.telecine;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.google.android.gms.analytics.Tracker;
import javax.inject.Inject;

public final class TelecineWidgetLaunchActivity extends Activity {
  @Inject Tracker tracker;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ((TelecineApplication) getApplication()).inject(this);
    CaptureHelper.fireScreenCaptureIntent(this, tracker);
  }

  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (!CaptureHelper.handleActivityResult(this, requestCode, resultCode, data, tracker)) {
      super.onActivityResult(requestCode, resultCode, data);
    }
    finish();
  }
}
