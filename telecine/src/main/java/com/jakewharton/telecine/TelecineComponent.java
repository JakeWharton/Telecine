package com.jakewharton.telecine;

import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component(modules = TelecineModule.class)
interface TelecineComponent {
  void inject(TelecineActivity activity);
  void inject(TelecineService service);
  void inject(TelecineShortcutConfigureActivity activity);
  void inject(TelecineShortcutLaunchActivity activity);
}
