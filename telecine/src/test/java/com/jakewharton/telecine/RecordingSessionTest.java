package com.jakewharton.telecine;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.jakewharton.telecine.RecordingSession.RecordingInfo;
import static com.jakewharton.telecine.RecordingSession.calculateRecordingInfo;

public final class RecordingSessionTest {
  @Test public void videoSizeNoCamera() {
    RecordingInfo size = calculateRecordingInfo(1080, 1920, 160, false, -1, -1, 30, 100);
    assertThat(size.width).isEqualTo(1080);
    assertThat(size.height).isEqualTo(1920);
    assertThat(size.density).isEqualTo(160);
  }

  @Test public void videoSizeResize() {
    RecordingInfo size = calculateRecordingInfo(1080, 1920, 160, false, -1, -1, 30, 75);
    assertThat(size.width).isEqualTo(810);
    assertThat(size.height).isEqualTo(1440);
    assertThat(size.density).isEqualTo(160);
  }

  @Test public void videoSizeFitsInCamera() {
    RecordingInfo size = calculateRecordingInfo(1080, 1920, 160, false, 1920, 1080, 30, 100);
    assertThat(size.width).isEqualTo(1080);
    assertThat(size.height).isEqualTo(1920);
    assertThat(size.density).isEqualTo(160);
  }

  @Test public void videoSizeFitsInCameraLandscape() {
    RecordingInfo size = calculateRecordingInfo(1920, 1080, 160, true, 1920, 1080, 30, 100);
    assertThat(size.width).isEqualTo(1920);
    assertThat(size.height).isEqualTo(1080);
    assertThat(size.density).isEqualTo(160);
  }

  @Test public void videoSizeLargerThanCamera() {
    RecordingInfo size = calculateRecordingInfo(2160, 3840, 160, false, 1920, 1080, 30, 100);
    assertThat(size.width).isEqualTo(1080);
    assertThat(size.height).isEqualTo(1920);
    assertThat(size.density).isEqualTo(160);
  }

  @Test public void videoSizeLargerThanCameraLandscape() {
    RecordingInfo size = calculateRecordingInfo(3840, 2160, 160, true, 1920, 1080, 30, 100);
    assertThat(size.width).isEqualTo(1920);
    assertThat(size.height).isEqualTo(1080);
    assertThat(size.density).isEqualTo(160);
  }

  @Test public void videoSizeLargerThanCameraScaling() {
    RecordingInfo size = calculateRecordingInfo(1200, 1920, 160, false, 1920, 1080, 30, 100);
    assertThat(size.width).isEqualTo(1080);
    assertThat(size.height).isEqualTo(1728);
    assertThat(size.density).isEqualTo(160);
  }

  @Test public void videoSizeLargerThanCameraScalingResizesFirst() {
    RecordingInfo size = calculateRecordingInfo(1200, 1920, 160, false, 1920, 1080, 30, 75);
    assertThat(size.width).isEqualTo(900);
    assertThat(size.height).isEqualTo(1440);
    assertThat(size.density).isEqualTo(160);
  }

  @Test public void videoSizeLargerThanCameraScalingLandscape() {
    RecordingInfo size = calculateRecordingInfo(1920, 1200, 160, true, 1920, 1080, 30, 100);
    assertThat(size.width).isEqualTo(1728);
    assertThat(size.height).isEqualTo(1080);
    assertThat(size.density).isEqualTo(160);
  }
}
