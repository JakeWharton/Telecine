package com.jakewharton.telecine;

import android.util.Log;
import com.bugsnag.Error;
import com.bugsnag.android.Bugsnag;
import java.util.ArrayDeque;
import java.util.Deque;
import timber.log.Timber;

/**
 * A logging implementation which buffers the last 200 messages and notifies on error exceptions.
 */
final class BugsnagTree extends Timber.HollowTree {
  private static final int BUFFER_SIZE = 200;

  // Adding one to the initial size accounts for the add before remove.
  private final Deque<String> buffer = new ArrayDeque<>(BUFFER_SIZE + 1);

  @Override public void d(String message, Object... args) {
    logMessage(Log.DEBUG, message, args);
  }

  @Override public void d(Throwable t, String message, Object... args) {
    logMessage(Log.DEBUG, message, args);
  }

  @Override public void i(String message, Object... args) {
    logMessage(Log.INFO, message, args);
  }

  @Override public void i(Throwable t, String message, Object... args) {
    logMessage(Log.INFO, message, args);
  }

  @Override public void w(String message, Object... args) {
    logMessage(Log.WARN, message, args);
  }

  @Override public void w(Throwable t, String message, Object... args) {
    logMessage(Log.WARN, message, args);
  }

  @Override public void e(String message, Object... args) {
    logMessage(Log.ERROR, message, args);
  }

  @Override public void e(Throwable t, String message, Object... args) {
    logMessage(Log.ERROR, message, args);
    Bugsnag.notify(t);
  }

  private void logMessage(int priority, String message, Object... args) {
    if (args.length > 0) {
      message = String.format(message, args);
    }
    message = System.currentTimeMillis() + " " + priorityToString(priority) + " " + message;
    synchronized (buffer) {
      buffer.addLast(message);
      if (buffer.size() > BUFFER_SIZE) {
        buffer.removeFirst();
      }
    }
  }

  public void update(Error error) {
    synchronized (buffer) {
      int i = 1;
      for (String message : buffer) {
        error.addToTab("Log", String.format("%03d", i++), message);
      }
    }
  }

  private static String priorityToString(int priority) {
    switch (priority) {
      case Log.ERROR:
        return "E";
      case Log.WARN:
        return "W";
      case Log.INFO:
        return "I";
      case Log.DEBUG:
        return "D";
      default:
        return String.valueOf(priority);
    }
  }
}
