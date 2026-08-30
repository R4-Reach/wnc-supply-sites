package org.r4reach.util;

import java.util.Map;
import org.slf4j.MDC;

/** Util to run new threads and copy over MDC to the new thread. */
public class ThreadRunner {

  public static void run(Runnable runnable) {
    final Map<String, String> mdcCopy = MDC.getCopyOfContextMap();

    new Thread(
            () -> {
              try {
                MDC.setContextMap(mdcCopy);
                runnable.run();
              } finally {
                MDC.clear();
              }
            })
        .start();
  }
}
