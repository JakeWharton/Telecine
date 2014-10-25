package com.jakewharton.telecine;

import timber.log.Timber;

enum EncodingQuality {
  HIGH(20 * 1000 * 1000),
  NORMAL(8 * 1000 * 1000),
  LOW(3 * 1000 * 1000);

  public static EncodingQuality fromString(String name) {
    try {
      return EncodingQuality.valueOf(name);
    } catch (IllegalArgumentException e) {
      Timber.w(e, "Unable to get constant for name: %s", name);
      return NORMAL;
    }
  }

  private final int bitrate;

  private EncodingQuality(int bitrate) {
    this.bitrate = bitrate;
  }
}
