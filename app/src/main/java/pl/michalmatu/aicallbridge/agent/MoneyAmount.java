package pl.michalmatu.aicallbridge.agent;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

/** Immutable non-negative monetary amount with an ISO-4217 currency code. */
public record MoneyAmount(BigDecimal amount, String currencyCode) {
    public MoneyAmount {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyCode, "currencyCode");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be >= 0");
        }

        String normalizedCode = currencyCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("currencyCode must not be blank");
        }
        try {
            normalizedCode = Currency.getInstance(normalizedCode).getCurrencyCode();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("invalid ISO-4217 currencyCode: " + currencyCode, error);
        }
        currencyCode = normalizedCode;
    }
}
