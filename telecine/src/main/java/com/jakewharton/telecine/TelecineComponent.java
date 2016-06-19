package com.jakewharton.telecine;

import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component(modules = TelecineModule.class)
interface TelecineComponent {
  void inject(final TelecineActivity activity);
  void inject(final TelecineService service);
  void inject(final TelecineShortcutConfigureActivity activity);
  void inject(final TelecineShortcutLaunchActivity activity);
}
