package com.jakewharton.telecine;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.google.android.gms.analytics.HitBuilders;
import javax.inject.Inject;

import static android.content.Intent.ShortcutIconResource;

public final class TelecineShortcutConfigureActivity extends Activity {
  @Inject Analytics analytics;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    ((TelecineApplication) getApplication()).injector().inject(this);
    analytics.send(new HitBuilders.EventBuilder() //
        .setCategory(Analytics.CATEGORY_SHORTCUT) //
        .setAction(Analytics.ACTION_SHORTCUT_ADDED) //
        .build());

    Intent launchIntent = new Intent(this, TelecineShortcutLaunchActivity.class);
    ShortcutIconResource icon = ShortcutIconResource.fromContext(this, R.drawable.ic_launcher);

    Intent intent = new Intent();
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.launch));
    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, icon);
    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, launchIntent);

    setResult(RESULT_OK, intent);
    finish();
  }
}
