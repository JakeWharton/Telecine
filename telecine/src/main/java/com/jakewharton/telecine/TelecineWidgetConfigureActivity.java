package com.jakewharton.telecine;

import android.app.Activity;
import android.os.Bundle;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import javax.inject.Inject;

/**
 * A blank, invisible activity whose sole purpose is reporting when a widget is added to the
 * home screen to analytics. Sneaky sneaky.
 */
public final class TelecineWidgetConfigureActivity extends Activity {
  @Inject Tracker tracker;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ((TelecineApplication) getApplication()).inject(this);
    tracker.send(new HitBuilders.EventBuilder() //
        .setCategory(Analytics.CATEGORY_WIDGET) //
        .setAction(Analytics.ACTION_WIDGET_ADDED) //
        .build());

    setResult(RESULT_OK, getIntent());
    finish();
  }
}
