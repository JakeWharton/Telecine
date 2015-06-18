package com.jakewharton.telecine;

import android.util.Log;
import com.bugsnag.android.Bugsnag;
import com.bugsnag.android.Error;
import java.util.ArrayDeque;
import java.util.Deque;
import timber.log.Timber;

/**
 * A logging implementation which buffers the last 200 messages and notifies on error exceptions.
 */
final class BugsnagTree extends Timber.Tree {
  private static final int BUFFER_SIZE = 200;

  // Adding one to the initial size accounts for the add before remove.
  private final Deque<String> buffer = new ArrayDeque<>(BUFFER_SIZE + 1);

  @Override
  protected void log(int priority, String tag, String message, Throwable t) {
    message = System.currentTimeMillis() + " " + priorityToString(priority) + " " + message;
    synchronized (buffer) {
      buffer.addLast(message);
      if (buffer.size() > BUFFER_SIZE) {
        buffer.removeFirst();
      }
    }
    if (t != null && priority == Log.ERROR) {
      Bugsnag.notify(t);
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
