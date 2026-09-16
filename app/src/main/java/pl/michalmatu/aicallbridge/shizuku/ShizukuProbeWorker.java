package pl.michalmatu.aicallbridge.shizuku;

import java.util.Objects;

/** Runs blocking Shizuku diagnostics away from the app main Looper. */
final class ShizukuProbeWorker {
    private ShizukuProbeWorker() {}

    static Thread start(String threadName, Runnable task) {
        Objects.requireNonNull(threadName, "threadName");
        Objects.requireNonNull(task, "task");
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
