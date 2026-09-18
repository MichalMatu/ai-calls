package pl.michalmatu.aicallbridge.agent;

/** Machine-readable reasons why a proposal cannot proceed autonomously. */
public enum CallPolicyReason {
    TIME_UNKNOWN,
    TIME_OUTSIDE_ALLOWED,
    PRICE_UNKNOWN,
    PRICE_CURRENCY_MISMATCH,
    PRICE_ABOVE_MAX,
    PAYMENT_MODE_UNKNOWN,
    PAYMENT_MODE_NOT_ALLOWED,
    PREFERRED_TIME_UNKNOWN,
    PREFERRED_TIME_MISSED,
    PREFERRED_PROVIDER_UNKNOWN,
    PREFERRED_PROVIDER_MISSED,
    PREFERRED_LOCATION_UNKNOWN,
    PREFERRED_LOCATION_MISSED,
}
