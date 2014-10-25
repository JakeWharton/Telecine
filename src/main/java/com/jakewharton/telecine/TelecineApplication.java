package com.jakewharton.telecine;

import android.app.Application;
import com.crashlytics.android.Crashlytics;
import dagger.ObjectGraph;
import io.fabric.sdk.android.Fabric;
import timber.log.Timber;

public final class TelecineApplication extends Application {
  private ObjectGraph objectGraph;

  @Override public void onCreate() {
    super.onCreate();

    objectGraph = ObjectGraph.create(new TelecineModule(this));

    if (BuildConfig.DEBUG) {
      Timber.plant(new Timber.DebugTree());
    } else {
      Fabric.with(this, new Crashlytics());
      Timber.plant(new CrashlyticsTree());
    }
  }

  public void inject(Object o) {
    objectGraph.inject(o);
  }
}
