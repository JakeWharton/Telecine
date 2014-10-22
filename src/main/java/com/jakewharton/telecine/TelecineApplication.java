package com.jakewharton.telecine;

import android.app.Application;
import timber.log.Timber;

public final class TelecineApplication extends Application {
  @Override public void onCreate() {
    super.onCreate();

    if (BuildConfig.DEBUG) {
      Timber.plant(new Timber.DebugTree());
    }
  }
}
