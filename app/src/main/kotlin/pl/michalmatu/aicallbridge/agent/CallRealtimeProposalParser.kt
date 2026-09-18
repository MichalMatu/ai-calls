package pl.michalmatu.aicallbridge.agent

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.StringReader
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** Strict, side-effect-free decoder for untrusted evaluate_proposal arguments. */
internal object CallRealtimeProposalParser {
    fun parse(argumentsJson: String): CallProposal {
        require(argumentsJson.isNotBlank()) { "evaluate_proposal arguments must not be blank" }
        try {
            JsonReader(StringReader(argumentsJson)).use { reader ->
                reader.setStrictness(Strictness.STRICT)
                reader.beginObject()

                var scheduledSeen = false
                var priceSeen = false
                var paymentModeSeen = false
                var providerSeen = false
                var locationSeen = false

                var scheduledAt: ZonedDateTime? = null
                var price: MoneyAmount? = null
                var paymentMode: CallPaymentMode? = null
                var provider: String? = null
                var location: String? = null

                while (reader.hasNext()) {
                    when (val name = reader.nextName()) {
                        "scheduled_at" -> {
                            checkNotDuplicate(scheduledSeen, name)
                            scheduledSeen = true
                            scheduledAt = readZonedDateTime(reader, name)
                        }

                        "price" -> {
                            checkNotDuplicate(priceSeen, name)
                            priceSeen = true
                            price = readPrice(reader)
                        }

                        "payment_mode" -> {
                            checkNotDuplicate(paymentModeSeen, name)
                            paymentModeSeen = true
                            paymentMode = readPaymentMode(reader)
                        }

                        "provider" -> {
                            checkNotDuplicate(providerSeen, name)
                            providerSeen = true
                            provider = readNullableNonBlankString(reader, name)
                        }

                        "location" -> {
                            checkNotDuplicate(locationSeen, name)
                            locationSeen = true
                            location = readNullableNonBlankString(reader, name)
                        }

                        else -> throw IllegalArgumentException("unexpected evaluate_proposal field: $name")
                    }
                }
                reader.endObject()
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw IllegalArgumentException("evaluate_proposal arguments contain trailing JSON")
                }

                if (!scheduledSeen || !priceSeen || !paymentModeSeen || !providerSeen || !locationSeen) {
                    throw IllegalArgumentException("evaluate_proposal requires all proposal fields explicitly")
                }

                return CallProposal(scheduledAt, price, paymentMode, provider, location)
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: IOException) {
            throw IllegalArgumentException("invalid evaluate_proposal JSON", error)
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("invalid evaluate_proposal JSON", error)
        }
    }

    private fun readPrice(reader: JsonReader): MoneyAmount? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw IllegalArgumentException("price must be an object or null")
        }
        reader.beginObject()
        var amountSeen = false
        var currencySeen = false
        var amount: String? = null
        var currency: String? = null
        while (reader.hasNext()) {
            when (val name = reader.nextName()) {
                "amount" -> {
                    checkNotDuplicate(amountSeen, "price.amount")
                    amountSeen = true
                    amount = readRequiredString(reader, "price.amount")
                }

                "currency" -> {
                    checkNotDuplicate(currencySeen, "price.currency")
                    currencySeen = true
                    currency = readRequiredString(reader, "price.currency")
                }

                else -> throw IllegalArgumentException("unexpected price field: $name")
            }
        }
        reader.endObject()
        if (!amountSeen || !currencySeen) {
            throw IllegalArgumentException("price requires amount and currency")
        }
        val parsedAmount = try {
            BigDecimal(amount)
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("price.amount must be a decimal string", error)
        }
        return MoneyAmount(parsedAmount, currency!!)
    }

    private fun readPaymentMode(reader: JsonReader): CallPaymentMode? {
        val raw = readNullableNonBlankString(reader, "payment_mode") ?: return null
        return try {
            CallPaymentMode.valueOf(raw)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("unsupported payment_mode: $raw", error)
        }
    }

    private fun readZonedDateTime(reader: JsonReader, name: String): ZonedDateTime? {
        val raw = readNullableNonBlankString(reader, name) ?: return null
        return try {
            ZonedDateTime.parse(raw)
        } catch (error: DateTimeParseException) {
            throw IllegalArgumentException("$name must be an ISO-8601 zoned timestamp", error)
        }
    }

    private fun readNullableNonBlankString(reader: JsonReader, name: String): String? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return readRequiredString(reader, name)
    }

    private fun readRequiredString(reader: JsonReader, name: String): String {
        if (reader.peek() != JsonToken.STRING) {
            throw IllegalArgumentException("$name must be a string")
        }
        val value = reader.nextString().trim()
        if (value.isEmpty()) {
            throw IllegalArgumentException("$name must not be blank")
        }
        return value
    }

    private fun checkNotDuplicate(seen: Boolean, name: String) {
        if (seen) {
            throw IllegalArgumentException("duplicate evaluate_proposal field: $name")
        }
    }
}
