package com.jakewharton.telecine;

import android.app.Application;
import com.bugsnag.BeforeNotify;
import com.bugsnag.android.Bugsnag;
import dagger.ObjectGraph;
import timber.log.Timber;

public final class TelecineApplication extends Application {
  private ObjectGraph objectGraph;

  @Override public void onCreate() {
    super.onCreate();

    objectGraph = ObjectGraph.create(new TelecineModule(this));

    if (BuildConfig.DEBUG) {
      Timber.plant(new Timber.DebugTree());
    } else {
      Bugsnag.register(this, "8c9c38a766416720b3ede7518c54e522");
      Bugsnag.setReleaseStage(BuildConfig.BUILD_TYPE);
      Bugsnag.setProjectPackages("com.jakewharton.telecine");

      final BugsnagTree tree = new BugsnagTree();
      Bugsnag.getClient().addBeforeNotify(new BeforeNotify() {
        @Override public boolean run(com.bugsnag.Error error) {
          tree.update(error);
          return true;
        }
      });

      Timber.plant(tree);
    }
  }

  public void inject(Object o) {
    objectGraph.inject(o);
  }
}
