package com.jakewharton.telecine;

import android.app.Application;
import dagger.BindsInstance;
import dagger.Component;
import dagger.android.AndroidInjectionModule;
import javax.inject.Singleton;

@Singleton @Component(modules = { AndroidInjectionModule.class, TelecineModule.class })
interface TelecineComponent {
  void inject(TelecineApplication app);

  @Component.Builder interface Builder {
    @BindsInstance Builder application(Application application);

    TelecineComponent build();
  }
}
