package com.jakewharton.telecine;

import android.app.Activity;
import android.app.Application;
import android.app.Service;
import com.bugsnag.android.BeforeNotify;
import com.bugsnag.android.Bugsnag;
import com.bugsnag.android.Error;
import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasActivityInjector;
import dagger.android.HasServiceInjector;
import javax.inject.Inject;
import timber.log.Timber;

public final class TelecineApplication extends Application
    implements HasActivityInjector, HasServiceInjector {
  @Inject DispatchingAndroidInjector<Activity> dispatchingActivityInjector;
  @Inject DispatchingAndroidInjector<Service> dispatchingServiceInjector;

  @Override public void onCreate() {
    DaggerTelecineComponent.builder().application(this).build().inject(this);
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
  }

  @Override public AndroidInjector<Activity> activityInjector() {
    return dispatchingActivityInjector;
  }

  @Override public AndroidInjector<Service> serviceInjector() {
    return dispatchingServiceInjector;
  }
}
