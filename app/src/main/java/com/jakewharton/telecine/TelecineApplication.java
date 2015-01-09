package com.jakewharton.telecine;

import android.app.Application;
import com.bugsnag.android.BeforeNotify;
import com.bugsnag.android.Bugsnag;
import com.bugsnag.android.Error;
import dagger.ObjectGraph;
import timber.log.Timber;

public final class TelecineApplication extends Application {
  private ObjectGraph objectGraph;

  @Override public void onCreate() {
    super.onCreate();

    if (BuildConfig.DEBUG) {
      Timber.plant(new Timber.DebugTree());
    } else {
      Bugsnag.init(this, BuildConfig.BUGSNAG_KEY);
      Bugsnag.setReleaseStage(BuildConfig.BUILD_TYPE);
      Bugsnag.setProjectPackages("com.jakewharton.telecine");

      final BugsnagTree tree = new BugsnagTree();
      Bugsnag.getClient().beforeNotify(new BeforeNotify() {
        @Override public boolean run(Error error) {
          tree.update(error);
          return true;
        }
      });

      Timber.plant(tree);
    }

    objectGraph = ObjectGraph.create(new TelecineModule(this));
  }

  public void inject(Object o) {
    objectGraph.inject(o);
  }
}
