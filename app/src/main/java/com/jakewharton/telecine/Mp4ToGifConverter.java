package com.jakewharton.telecine;

import java.io.File;
import java.io.IOException;
import timber.log.Timber;

final class Mp4ToGifConverter {
  public static void convert(File mp4File, File gifFile, int width, int height) {
    if (width > 800 || height > 800) {
      float widthRatio = width / 800f;
      float heightRatio = height / 800f;
      if (widthRatio > heightRatio) {
        width = 800;
        height = (int) (height / widthRatio);
      } else {
        height = 800;
        width = (int) (width / heightRatio);
      }
    }

    try {
      Mp4FrameExtractor.extractFrames(mp4File, gifFile, width, height);
    } catch (IOException e) {
      Timber.e(e, "Oops!");
    }
  }
}
