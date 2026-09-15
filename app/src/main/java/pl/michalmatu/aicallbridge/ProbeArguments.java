package pl.michalmatu.aicallbridge;

/** Pure parser for bounded shell-probe modes. */
public final class ProbeArguments {
    static final int MAX_DURATION_MS = 5_000;
    static final int MAX_FREQUENCY_HZ = 8_000;
    private static final String TEMP_OUTPUT_PREFIX = "/data/local/tmp/";

    public enum Mode {
        INVENTORY,
        CAPTURE_DOWNLINK,
        INJECT_TONE
    }

    private final Mode mode;
    private final int durationMs;
    private final int frequencyHz;
    private final double amplitude;
    private final String outputPath;

    private ProbeArguments(
        Mode mode,
        int durationMs,
        int frequencyHz,
        double amplitude,
        String outputPath
    ) {
        this.mode = mode;
        this.durationMs = durationMs;
        this.frequencyHz = frequencyHz;
        this.amplitude = amplitude;
        this.outputPath = outputPath;
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
                requireArgumentCountRange(
                    args,
                    2,
                    3,
                    "capture-downlink <durationMs> [/data/local/tmp/<file>.pcm]"
                );
                int durationMs = parseBoundedInt(args[1], "durationMs", 1, MAX_DURATION_MS);
                String outputPath = args.length == 3 ? validateTemporaryOutput(args[2]) : null;
                yield new ProbeArguments(Mode.CAPTURE_DOWNLINK, durationMs, 0, 0.0, outputPath);
            }
            case "inject-tone" -> {
                requireArgumentCount(args, 4, "inject-tone <durationMs> <frequencyHz> <amplitude>");
                int durationMs = parseBoundedInt(args[1], "durationMs", 1, MAX_DURATION_MS);
                int frequencyHz = parseBoundedInt(args[2], "frequencyHz", 1, MAX_FREQUENCY_HZ);
                double amplitude = parseDouble(args[3], "amplitude");
                if (!(amplitude > 0.0 && amplitude <= 1.0)) {
                    throw new IllegalArgumentException("amplitude must be > 0 and <= 1");
                }
                yield new ProbeArguments(Mode.INJECT_TONE, durationMs, frequencyHz, amplitude, null);
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

    public String outputPath() {
        return outputPath;
    }

    private static ProbeArguments inventory() {
        return new ProbeArguments(Mode.INVENTORY, 0, 0, 0.0, null);
    }

    private static String validateTemporaryOutput(String path) {
        if (path == null || !path.startsWith(TEMP_OUTPUT_PREFIX)) {
            throw new IllegalArgumentException("output path must be under " + TEMP_OUTPUT_PREFIX);
        }
        String fileName = path.substring(TEMP_OUTPUT_PREFIX.length());
        if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("..")) {
            throw new IllegalArgumentException("output path must name one temporary file");
        }
        if (!fileName.endsWith(".pcm")) {
            throw new IllegalArgumentException("output file must end with .pcm");
        }
        return path;
    }

    private static void requireArgumentCount(String[] args, int expected, String usage) {
        if (args.length != expected) {
            throw new IllegalArgumentException("usage: " + usage);
        }
    }

    private static void requireArgumentCountRange(
        String[] args,
        int min,
        int max,
        String usage
    ) {
        if (args.length < min || args.length > max) {
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
