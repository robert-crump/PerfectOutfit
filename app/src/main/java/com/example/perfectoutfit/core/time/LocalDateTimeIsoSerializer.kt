package com.example.perfectoutfit.core.time

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/** A [LocalDateTime] as its ISO-8601 string; an unparsable string is a [SerializationException]. */
object LocalDateTimeIsoSerializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): LocalDateTime {
        val raw = decoder.decodeString()
        return try {
            LocalDateTime.parse(raw)
        } catch (e: DateTimeParseException) {
            throw SerializationException("Not an ISO local date-time: $raw", e)
        }
    }
}
