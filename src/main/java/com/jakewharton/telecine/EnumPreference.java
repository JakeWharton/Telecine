package com.jakewharton.telecine;

import android.content.SharedPreferences;
import timber.log.Timber;

final class EnumPreference<T extends Enum<T>> {
  private final StringPreference delegate;
  private final Class<T> cls;
  private final T defaultValue;

  public EnumPreference(SharedPreferences preferences, String key, Class<T> cls, T defaultValue) {
    this.delegate = new StringPreference(preferences, key, defaultValue.name());
    this.cls = cls;
    this.defaultValue = defaultValue;
  }

  public T get() {
    String value = delegate.get();
    try {
      return Enum.valueOf(cls, value);
    } catch (IllegalArgumentException e) {
      Timber.w(e, "Unable to get constant for value: %s", value);
      return defaultValue;
    }
  }

  public boolean isSet() {
    return delegate.isSet();
  }

  public void set(T value) {
    delegate.set(value.name());
  }

  public void delete() {
    delegate.delete();
  }
}
