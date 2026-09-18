package pl.michalmatu.aicallbridge.session;

/** Schedules the app-side heartbeat refresh independently of model/network work. */
public interface CallMediaHeartbeatScheduler {
    AutoCloseable start(long generation, Runnable heartbeat);
}
