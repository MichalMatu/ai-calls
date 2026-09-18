package pl.michalmatu.aicallbridge.agent;

import java.time.ZonedDateTime;
import java.util.Objects;

/** A half-open allowed/preferred wall-clock interval: startInclusive <= time < endExclusive. */
public record CallTimeWindow(ZonedDateTime startInclusive, ZonedDateTime endExclusive) {
    public CallTimeWindow {
        Objects.requireNonNull(startInclusive, "startInclusive");
        Objects.requireNonNull(endExclusive, "endExclusive");
        if (!endExclusive.toInstant().isAfter(startInclusive.toInstant())) {
            throw new IllegalArgumentException("endExclusive must be after startInclusive");
        }
    }
}
