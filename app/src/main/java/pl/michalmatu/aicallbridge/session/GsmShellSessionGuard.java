package pl.michalmatu.aicallbridge.session;

/**
 * Fail-closed one-owner lifecycle for a privileged GSM shell connection.
 * Pure Java so replay, STOP and generation ownership can be unit-tested
 * without Android, ADB, telephony or private call data.
 */
public final class GsmShellSessionGuard {
    private enum Phase { NEW, PREPARED, ACTIVE, CLOSED }

    private final long generation;
    private Phase phase = Phase.NEW;

    public GsmShellSessionGuard(long generation) {
        if (!GsmShellMediaProtocol.validGeneration(generation))
            throw new IllegalArgumentException("gsm_shell_invalid_generation");
        this.generation = generation;
    }

    public synchronized boolean prepare(long expected, int rate) {
        if (expected != generation || phase != Phase.NEW ||
            rate != GsmShellMediaProtocol.SAMPLE_RATE_HZ) return false;
        phase = Phase.PREPARED;
        return true;
    }

    public synchronized boolean start(long expected) {
        if (expected != generation || phase != Phase.PREPARED) return false;
        phase = Phase.ACTIVE;
        return true;
    }

    public synchronized boolean heartbeat(long expected) {
        return expected == generation && phase == Phase.ACTIVE;
    }

    public synchronized void stop() { phase = Phase.CLOSED; }
}
