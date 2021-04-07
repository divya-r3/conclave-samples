package com.r3.conclave.sample.enclave

import com.r3.conclave.mail.Curve25519PrivateKey
import com.r3.conclave.mail.Curve25519PublicKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import java.lang.IllegalArgumentException
import java.security.KeyPair
import java.util.*

// TODO Think of proof of key generation, could be useful
@Serializable
class SignedData(val bytes: ByteArray, val signature: ByteArray)

private object Curve25519PublicKeySerializer : KSerializer<Curve25519PublicKey> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Curve25519PublicKey", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Curve25519PublicKey) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value.encoded))
    }
    override fun deserialize(decoder: Decoder): Curve25519PublicKey {
        return Curve25519PublicKey(Base64.getDecoder().decode(decoder.decodeString()))
    }
}

private object Curve25519PrivateKeySerializer : KSerializer<Curve25519PrivateKey> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Curve25519PrivateKey", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Curve25519PrivateKey) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value.encoded))
    }
    override fun deserialize(decoder: Decoder): Curve25519PrivateKey {
        return Curve25519PrivateKey(Base64.getDecoder().decode(decoder.decodeString()))
    }
}


internal object KeyPairSerializer: KSerializer<KeyPair> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("KeyPair") {
        element<String>("public")
        element<String>("private")
    }
    override fun serialize(encoder: Encoder, value: KeyPair) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, Base64.getEncoder().encodeToString(value.public.encoded))
            encodeStringElement(descriptor, 1, Base64.getEncoder().encodeToString(value.private.encoded))
        }
    }

    // todo reuse public/private serialisers
    override fun deserialize(decoder: Decoder): KeyPair {
        var public:Curve25519PublicKey? = null
        var private: Curve25519PrivateKey? = null
        decoder.decodeStructure(descriptor) {
            while(true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> public = Curve25519PublicKey(Base64.getDecoder().decode(decodeStringElement(descriptor, 0)))
                    1 -> private = Curve25519PrivateKey(Base64.getDecoder().decode(decodeStringElement(descriptor, 1)))
                    CompositeDecoder.DECODE_DONE -> break
                    else -> throw IllegalArgumentException("TODO something went wrong in deserialisaion")
                }
            }
        }
        //TODO check null
        return KeyPair(public, private)
    }
}

@Serializable
data class KeyDataResponse(
        val todo: Int,
        @Serializable(with = Curve25519PrivateKeySerializer::class)
        val privateKey: Curve25519PrivateKey
)