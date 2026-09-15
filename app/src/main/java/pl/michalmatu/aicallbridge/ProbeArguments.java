package pl.michalmatu.aicallbridge;

/** Pure parser for bounded shell-probe modes. */
public final class ProbeArguments {
    static final int MAX_DURATION_MS = 5_000;
    static final int MAX_FREQUENCY_HZ = 8_000;

    public enum Mode {
        INVENTORY,
        CAPTURE_DOWNLINK,
        INJECT_TONE
    }

    private final Mode mode;
    private final int durationMs;
    private final int frequencyHz;
    private final double amplitude;

    private ProbeArguments(Mode mode, int durationMs, int frequencyHz, double amplitude) {
        this.mode = mode;
        this.durationMs = durationMs;
        this.frequencyHz = frequencyHz;
        this.amplitude = amplitude;
    }

    public static ProbeArguments parse(String[] args) {
        if (args == null || args.length == 0) {
            return inventory();
        }

        return switch (args[0]) {
            case "inventory" -> {
                requireArgumentCount(args, 1, "inventory");
                yield inventory();
            }
            case "capture-downlink" -> {
                requireArgumentCount(args, 2, "capture-downlink <durationMs>");
                int durationMs = parseBoundedInt(args[1], "durationMs", 1, MAX_DURATION_MS);
                yield new ProbeArguments(Mode.CAPTURE_DOWNLINK, durationMs, 0, 0.0);
            }
            case "inject-tone" -> {
                requireArgumentCount(args, 4, "inject-tone <durationMs> <frequencyHz> <amplitude>");
                int durationMs = parseBoundedInt(args[1], "durationMs", 1, MAX_DURATION_MS);
                int frequencyHz = parseBoundedInt(args[2], "frequencyHz", 1, MAX_FREQUENCY_HZ);
                double amplitude = parseDouble(args[3], "amplitude");
                if (!(amplitude > 0.0 && amplitude <= 1.0)) {
                    throw new IllegalArgumentException("amplitude must be > 0 and <= 1");
                }
                yield new ProbeArguments(Mode.INJECT_TONE, durationMs, frequencyHz, amplitude);
            }
            default -> throw new IllegalArgumentException("unknown mode: " + args[0]);
        };
    }

    public Mode mode() {
        return mode;
    }

    public int durationMs() {
        return durationMs;
    }

    public int frequencyHz() {
        return frequencyHz;
    }

    public double amplitude() {
        return amplitude;
    }

    private static ProbeArguments inventory() {
        return new ProbeArguments(Mode.INVENTORY, 0, 0, 0.0);
    }

    private static void requireArgumentCount(String[] args, int expected, String usage) {
        if (args.length != expected) {
            throw new IllegalArgumentException("usage: " + usage);
        }
    }

    private static int parseBoundedInt(String raw, String label, int min, int max) {
        final int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + " must be an integer", error);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static double parseDouble(String raw, String label) {
        final double value;
        try {
            value = Double.parseDouble(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + " must be numeric", error);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
        return value;
    }
}
