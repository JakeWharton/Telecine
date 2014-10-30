/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jakewharton.telecine;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.Surface;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import timber.log.Timber;

import static android.media.MediaFormat.KEY_HEIGHT;
import static android.media.MediaFormat.KEY_MIME;
import static android.media.MediaFormat.KEY_WIDTH;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

final class Mp4FrameExtractor {
  private Mp4FrameExtractor() {
    throw new AssertionError("No instances.");
  }

  /**
   * Tests extraction from an MP4 to a series of PNG files.
   * <p>
   * We scale the video to 640x480 for the PNG just to demonstrate that we can scale the
   * video with the GPU.  If the input video has a different aspect ratio, we could preserve
   * it by adjusting the GL viewport to get letterboxing or pillarboxing, but generally if
   * you're extracting frames you don't want black bars.
   */
  public static void extractFrames(File mp4File, File gifFile, int width, int height) throws IOException {
    MediaCodec decoder = null;
    CodecOutputSurface outputSurface = null;
    MediaExtractor extractor = null;

    try {
      // The MediaExtractor error messages aren't very useful.  Check to see if the input
      // file exists so we can throw a better one if it's not there.
      if (!mp4File.canRead()) {
        throw new FileNotFoundException("Unable to read " + mp4File);
      }

      extractor = new MediaExtractor();
      extractor.setDataSource(mp4File.toString());
      int trackIndex = selectTrack(extractor);
      if (trackIndex < 0) {
        throw new RuntimeException("No video track found in " + mp4File);
      }
      extractor.selectTrack(trackIndex);

      MediaFormat format = extractor.getTrackFormat(trackIndex);
      Timber.d("Video size is %sx%s", format.getInteger(KEY_WIDTH), format.getInteger(KEY_HEIGHT));

      // Could use width/height from the MediaFormat to get full-size frames.
      outputSurface = new CodecOutputSurface(width, height);

      // Create a MediaCodec decoder, and configure it with the MediaFormat from the
      // extractor.  It's very important to use the format from the extractor because
      // it contains a copy of the CSD-0/CSD-1 codec-specific data chunks.
      String mime = format.getString(KEY_MIME);
      decoder = MediaCodec.createDecoderByType(mime);
      decoder.configure(format, outputSurface.getSurface(), null, 0);
      decoder.start();

      try (OutputStream gifOut = new BufferedOutputStream(new FileOutputStream(gifFile))) {
        AnimatedGifEncoder.Builder builder =
            new AnimatedGifEncoder.Builder().delay(1000 / 30, MILLISECONDS)
                .repeat(0)
                .size(width, height)
                .output(gifOut);
        try (AnimatedGifEncoder encoder = builder.build()) {
          doExtract(extractor, trackIndex, decoder, outputSurface, encoder);
        }
      }
    } finally {
      // release everything we grabbed
      if (outputSurface != null) {
        outputSurface.release();
      }
      if (decoder != null) {
        decoder.stop();
        decoder.release();
      }
      if (extractor != null) {
        extractor.release();
      }
    }
  }

  /**
   * Selects the video track, if any.
   *
   * @return the track index, or -1 if no video track is found.
   */
  private static int selectTrack(MediaExtractor extractor) {
    // Select the first video track we find, ignore the rest.
    int numTracks = extractor.getTrackCount();
    for (int i = 0; i < numTracks; i++) {
      MediaFormat format = extractor.getTrackFormat(i);
      String mime = format.getString(KEY_MIME);
      if (mime.startsWith("video/")) {
        Timber.d("Extractor selected track %s (%s): %s", i, mime, format);
        return i;
      }
    }

    return -1;
  }

  private static void doExtract(MediaExtractor extractor, int trackIndex, MediaCodec decoder,
      CodecOutputSurface outputSurface, AnimatedGifEncoder gifEncoder) throws IOException {
    final int TIMEOUT_USEC = 10000;
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    int inputChunk = 0;
    int decodeCount = 0;
    long frameSaveTime = 0;

    boolean outputDone = false;
    boolean inputDone = false;
    while (!outputDone) {
      // Feed more data to the decoder.
      if (!inputDone) {
        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
        if (inputBufIndex >= 0) {
          ByteBuffer inputBuf = decoder.getInputBuffer(inputBufIndex);
          // Read the sample data into the ByteBuffer.  This neither respects nor
          // updates inputBuf's position, limit, etc.
          int chunkSize = extractor.readSampleData(inputBuf, 0);
          if (chunkSize < 0) {
            // End of stream -- send empty frame with EOS flag set.
            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            inputDone = true;
            Timber.d("sent input EOS");
          } else {
            if (extractor.getSampleTrackIndex() != trackIndex) {
              Timber.w("WEIRD: got sample from track %s, expected %s",
                  extractor.getSampleTrackIndex(), trackIndex);
            }
            long presentationTimeUs = extractor.getSampleTime();
            decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, presentationTimeUs, 0 /*flags*/);
            Timber.d("submitted frame %s to dec, size=%s", inputChunk, chunkSize);
            inputChunk++;
            extractor.advance();
          }
        } else {
          Timber.d("input buffer not available");
        }
      }

      int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
      if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
        // no output available yet
        Timber.d("no output from decoder available");
      } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
        // not important for us, since we're using Surface
        Timber.d("decoder output buffers changed");
      } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        MediaFormat newFormat = decoder.getOutputFormat();
        Timber.d("decoder output format changed: %s", newFormat);
      } else if (decoderStatus < 0) {
        throw new RuntimeException(
            "unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
      } else { // decoderStatus >= 0
        Timber.d("surface decoder given buffer %s (size=%s)", decoderStatus, info.size);
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
          Timber.d("output EOS");
          outputDone = true;
        }

        boolean doRender = (info.size != 0);

        // As soon as we call releaseOutputBuffer, the buffer will be forwarded
        // to SurfaceTexture to convert to a texture.  The API doesn't guarantee
        // that the texture will be available before the call returns, so we
        // need to wait for the onFrameAvailable callback to fire.
        decoder.releaseOutputBuffer(decoderStatus, doRender);
        if (doRender) {
          Timber.d("awaiting decode of frame %s", decodeCount);
          outputSurface.awaitNewImage();
          outputSurface.drawImage(true);

          long startWhen = System.nanoTime();
          outputSurface.saveFrame(gifEncoder);
          frameSaveTime += System.nanoTime() - startWhen;
          decodeCount++;
        }
      }
    }

    long decodeAverage = frameSaveTime / decodeCount / 1000;
    Timber.d("Saving %s frames took %s us per frame", decodeCount, decodeAverage);
  }

  /**
   * Holds state associated with a Surface used for MediaCodec decoder output.
   * <p>
   * The constructor for this class will prepare GL, create a SurfaceTexture,
   * and then create a Surface for that SurfaceTexture.  The Surface can be passed to
   * MediaCodec.configure() to receive decoder output.  When a frame arrives, we latch the
   * texture with updateTexImage(), then render the texture with GL to a pbuffer.
   * <p>
   * By default, the Surface will be using a BufferQueue in asynchronous mode, so we
   * can potentially drop frames.
   */
  private static class CodecOutputSurface implements SurfaceTexture.OnFrameAvailableListener {
    private STextureRender textureRender;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private EGL10 egl;

    private EGLDisplay eglDisplay = EGL10.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL10.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL10.EGL_NO_SURFACE;
    int width;
    int height;

    private final Object frameSyncObject = new Object();     // guards frameAvailable
    private boolean frameAvailable;

    private ByteBuffer pixelBuf;                       // used by saveFrame()

    /**
     * Creates a CodecOutputSurface backed by a pbuffer with the specified dimensions.  The
     * new EGL context and surface will be made current.  Creates a Surface that can be passed
     * to MediaCodec.configure().
     */
    public CodecOutputSurface(int width, int height) {
      if (width <= 0 || height <= 0) {
        throw new IllegalArgumentException();
      }
      egl = (EGL10) EGLContext.getEGL();
      this.width = width;
      this.height = height;

      eglSetup();
      makeCurrent();
      setup();
    }

    /**
     * Creates interconnected instances of TextureRender, SurfaceTexture, and Surface.
     */
    private void setup() {
      textureRender = new STextureRender();
      textureRender.surfaceCreated();

      Timber.d("textureID=%s", textureRender.getTextureId());
      surfaceTexture = new SurfaceTexture(textureRender.getTextureId());

      // This doesn't work if this object is created on the thread that CTS started for
      // these test cases.
      //
      // The CTS-created thread has a Looper, and the SurfaceTexture constructor will
      // create a Handler that uses it.  The "frame available" message is delivered
      // there, but since we're not a Looper-based thread we'll never see it.  For
      // this to do anything useful, CodecOutputSurface must be created on a thread without
      // a Looper, so that SurfaceTexture uses the main application Looper instead.
      //
      // Java language note: passing "this" out of a constructor is generally unwise,
      // but we should be able to get away with it here.
      surfaceTexture.setOnFrameAvailableListener(this);

      surface = new Surface(surfaceTexture);

      pixelBuf = ByteBuffer.allocateDirect(width * height * 4);
      pixelBuf.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports pbuffer.
     */
    private void eglSetup() {
      final int EGL_OPENGL_ES2_BIT = 0x0004;
      final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

      eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
      if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
        throw new RuntimeException("unable to get EGL14 display");
      }
      int[] version = new int[2];
      if (!egl.eglInitialize(eglDisplay, version)) {
        eglDisplay = null;
        throw new RuntimeException("unable to initialize EGL14");
      }

      // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
      int[] attribList = {
          EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
          EGL10.EGL_ALPHA_SIZE, 8, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
          EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT, EGL10.EGL_NONE
      };
      EGLConfig[] configs = new EGLConfig[1];
      int[] numConfigs = new int[1];
      if (!egl.eglChooseConfig(eglDisplay, attribList, configs, configs.length, numConfigs)) {
        throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
      }

      // Configure context for OpenGL ES 2.0.
      int[] attrib_list = {
          EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE
      };
      eglContext =
          egl.eglCreateContext(eglDisplay, configs[0], EGL10.EGL_NO_CONTEXT, attrib_list);
      checkEglError("eglCreateContext");
      if (eglContext == null) {
        throw new RuntimeException("null context");
      }

      // Create a pbuffer surface.
      int[] surfaceAttribs = {
          EGL10.EGL_WIDTH, width, EGL10.EGL_HEIGHT, height, EGL10.EGL_NONE
      };
      eglSurface = egl.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs);
      checkEglError("eglCreatePbufferSurface");
      if (eglSurface == null) {
        throw new RuntimeException("surface was null");
      }
    }

    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    public void release() {
      if (eglDisplay != EGL10.EGL_NO_DISPLAY) {
        egl.eglDestroySurface(eglDisplay, eglSurface);
        egl.eglDestroyContext(eglDisplay, eglContext);
        //egl.eglReleaseThread();
        egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
            EGL10.EGL_NO_CONTEXT);
        egl.eglTerminate(eglDisplay);
      }
      eglDisplay = EGL10.EGL_NO_DISPLAY;
      eglContext = EGL10.EGL_NO_CONTEXT;
      eglSurface = EGL10.EGL_NO_SURFACE;

      surface.release();

      // this causes a bunch of warnings that appear harmless but might confuse someone:
      //  W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!
      //surfaceTexture.release();

      textureRender = null;
      surface = null;
      surfaceTexture = null;
    }

    /**
     * Makes our EGL context and surface current.
     */
    public void makeCurrent() {
      if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
        throw new RuntimeException("eglMakeCurrent failed");
      }
    }

    /**
     * Returns the Surface.
     */
    public Surface getSurface() {
      return surface;
    }

    /**
     * Latches the next buffer into the texture.  Must be called from the thread that created
     * the CodecOutputSurface object.  (More specifically, it must be called on the thread
     * with the EGLContext that contains the GL texture object used by SurfaceTexture.)
     */
    public void awaitNewImage() {
      final int TIMEOUT_MS = 2500;

      synchronized (frameSyncObject) {
        while (!frameAvailable) {
          try {
            // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
            // stalling the test if it doesn't arrive.
            frameSyncObject.wait(TIMEOUT_MS);
            if (!frameAvailable) {
              // TODO: if "spurious wakeup", continue while loop
              throw new RuntimeException("frame wait timed out");
            }
          } catch (InterruptedException ie) {
            // shouldn't happen
            throw new RuntimeException(ie);
          }
        }
        frameAvailable = false;
      }

      // Latch the data.
      textureRender.checkGlError("before updateTexImage");
      surfaceTexture.updateTexImage();
    }

    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     *
     * @param invert if set, render the image with Y inverted (0,0 in top left)
     */
    public void drawImage(boolean invert) {
      textureRender.drawFrame(surfaceTexture, invert);
    }

    // SurfaceTexture callback
    @Override public void onFrameAvailable(SurfaceTexture st) {
      Timber.d("new frame available");
      synchronized (frameSyncObject) {
        if (frameAvailable) {
          throw new RuntimeException("frameAvailable already set, frame could be dropped");
        }
        frameAvailable = true;
        frameSyncObject.notifyAll();
      }
    }

    public void saveFrame(AnimatedGifEncoder gifEncoder) throws IOException {
      // glReadPixels gives us a ByteBuffer filled with what is essentially big-endian RGBA
      // data (i.e. a byte of red, followed by a byte of green...).  To use the Bitmap
      // constructor that takes an int[] array with pixel data, we need an int[] filled
      // with little-endian ARGB data.
      //
      // If we implement this as a series of buf.get() calls, we can spend 2.5 seconds just
      // copying data around for a 720p frame.  It's better to do a bulk get() and then
      // rearrange the data in memory.  (For comparison, the PNG compress takes about 500ms
      // for a trivial frame.)
      //
      // So... we set the ByteBuffer to little-endian, which should turn the bulk IntBuffer
      // get() into a straight memcpy on most Android devices.  Our ints will hold ABGR data.
      // Swapping B and R gives us ARGB.  We need about 30ms for the bulk get(), and another
      // 270ms for the color swap.
      //
      // We can avoid the costly B/R swap here if we do it in the fragment shader (see
      // http://stackoverflow.com/questions/21634450/ ).
      //
      // Having said all that... it turns out that the Bitmap#copyPixelsFromBuffer()
      // method wants RGBA pixels, not ARGB, so if we create an empty bitmap and then
      // copy pixel data in we can avoid the swap issue entirely, and just copy straight
      // into the Bitmap from the ByteBuffer.
      //
      // Making this even more interesting is the upside-down nature of GL, which means
      // our output will look upside-down relative to what appears on screen if the
      // typical GL conventions are used.  (For ExtractMpegFrameTest, we avoid the issue
      // by inverting the frame when we render it.)
      //
      // Allocating large buffers is expensive, so we really want pixelBuf to be
      // allocated ahead of time if possible.  We still get some allocations from the
      // Bitmap / PNG creation.

      pixelBuf.rewind();
      GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);

      Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
      pixelBuf.rewind();
      bmp.copyPixelsFromBuffer(pixelBuf);
      gifEncoder.addFrame(bmp);
      bmp.recycle();
    }

    /** Checks for EGL errors. */
    private void checkEglError(String msg) {
      int error;
      if ((error = egl.eglGetError()) != EGL10.EGL_SUCCESS) {
        throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
      }
    }
  }

  /**
   * Code for rendering a texture onto a surface using OpenGL ES 2.0.
   */
  private static class STextureRender {
    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
    private final float[] mTriangleVerticesData = {
        // X, Y, Z, U, V
        -1.0f, -1.0f, 0, 0.f, 0.f, 1.0f, -1.0f, 0, 1.f, 0.f, -1.0f, 1.0f, 0, 0.f, 1.f, 1.0f, 1.0f,
        0, 1.f, 1.f,
    };

    private FloatBuffer mTriangleVertices;

    private static final String VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
        "uniform mat4 uSTMatrix;\n" +
        "attribute vec4 aPosition;\n" +
        "attribute vec4 aTextureCoord;\n" +
        "varying vec2 vTextureCoord;\n" +
        "void main() {\n" +
        "    gl_Position = uMVPMatrix * aPosition;\n" +
        "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
        "}\n";

    private static final String FRAGMENT_SHADER =
        "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +      // highp here doesn't seem to matter
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";

    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];

    private int mProgram;
    private int mTextureID = -12345;
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;

    public STextureRender() {
      mTriangleVertices = ByteBuffer.allocateDirect(mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
          .order(ByteOrder.nativeOrder())
          .asFloatBuffer();
      mTriangleVertices.put(mTriangleVerticesData).position(0);

      Matrix.setIdentityM(mSTMatrix, 0);
    }

    public int getTextureId() {
      return mTextureID;
    }

    /**
     * Draws the external texture in SurfaceTexture onto the current EGL surface.
     */
    public void drawFrame(SurfaceTexture st, boolean invert) {
      checkGlError("onDrawFrame start");
      st.getTransformMatrix(mSTMatrix);
      if (invert) {
        mSTMatrix[5] = -mSTMatrix[5];
        mSTMatrix[13] = 1.0f - mSTMatrix[13];
      }

      // (optional) clear to green so we can see if we're failing to set pixels
      GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

      GLES20.glUseProgram(mProgram);
      checkGlError("glUseProgram");

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);

      mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
      GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
          TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
      checkGlError("glVertexAttribPointer maPosition");
      GLES20.glEnableVertexAttribArray(maPositionHandle);
      checkGlError("glEnableVertexAttribArray maPositionHandle");

      mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
      GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
          TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
      checkGlError("glVertexAttribPointer maTextureHandle");
      GLES20.glEnableVertexAttribArray(maTextureHandle);
      checkGlError("glEnableVertexAttribArray maTextureHandle");

      Matrix.setIdentityM(mMVPMatrix, 0);
      GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
      GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
      checkGlError("glDrawArrays");

      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    public void surfaceCreated() {
      mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
      if (mProgram == 0) {
        throw new RuntimeException("failed creating program");
      }

      maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
      checkLocation(maPositionHandle, "aPosition");
      maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
      checkLocation(maTextureHandle, "aTextureCoord");

      muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
      checkLocation(muMVPMatrixHandle, "uMVPMatrix");
      muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
      checkLocation(muSTMatrixHandle, "uSTMatrix");

      int[] textures = new int[1];
      GLES20.glGenTextures(1, textures, 0);

      mTextureID = textures[0];
      GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
      checkGlError("glBindTexture mTextureID");

      GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
          GLES20.GL_NEAREST);
      GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
          GLES20.GL_LINEAR);
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
          GLES20.GL_CLAMP_TO_EDGE);
      GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
          GLES20.GL_CLAMP_TO_EDGE);
      checkGlError("glTexParameter");
    }

    private int loadShader(int shaderType, String source) {
      int shader = GLES20.glCreateShader(shaderType);
      checkGlError("glCreateShader type=" + shaderType);
      GLES20.glShaderSource(shader, source);
      GLES20.glCompileShader(shader);
      int[] compiled = new int[1];
      GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
      if (compiled[0] == 0) {
        Timber.e("Could not compile shader %s:", shaderType);
        Timber.e(" " + GLES20.glGetShaderInfoLog(shader));
        GLES20.glDeleteShader(shader);
        shader = 0;
      }
      return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
      int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
      if (vertexShader == 0) {
        return 0;
      }
      int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
      if (pixelShader == 0) {
        return 0;
      }

      int program = GLES20.glCreateProgram();
      if (program == 0) {
        Timber.e("Could not create program");
      }
      GLES20.glAttachShader(program, vertexShader);
      checkGlError("glAttachShader");
      GLES20.glAttachShader(program, pixelShader);
      checkGlError("glAttachShader");
      GLES20.glLinkProgram(program);
      int[] linkStatus = new int[1];
      GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
      if (linkStatus[0] != GLES20.GL_TRUE) {
        Timber.e("Could not link program: ");
        Timber.e(GLES20.glGetProgramInfoLog(program));
        GLES20.glDeleteProgram(program);
        program = 0;
      }
      return program;
    }

    public void checkGlError(String op) {
      int error;
      if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
        Timber.e(op + ": glError " + error);
        throw new RuntimeException(op + ": glError " + error);
      }
    }

    public static void checkLocation(int location, String label) {
      if (location < 0) {
        throw new RuntimeException("Unable to locate '" + label + "' in program");
      }
    }
  }
}
