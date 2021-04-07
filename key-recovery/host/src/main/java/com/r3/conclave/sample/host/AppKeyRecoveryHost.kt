package com.r3.conclave.sample.host

import com.r3.conclave.common.EnclaveInstanceInfo
import com.r3.conclave.host.AttestationParameters.DCAP
import com.r3.conclave.host.EnclaveHost
import com.r3.conclave.host.EnclaveLoadException
import com.r3.conclave.host.MailCommand
import com.r3.conclave.host.MockOnlySupportedException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * This class demonstrates how to do key recovery. It's proof of concept and by no means production ready. Extension in
 * EnclaveHost itself is kept minimal.
 * TODO
 */
object AppKeyRecoveryHost {
    lateinit var enclaveHost: EnclaveHost
    private val ENCLAVE_CLASS_NAME = "com.r3.conclave.sample.enclave.AppKeyRecoveryEnclave"
    // EnclaveHost needs to have handler for routing

    // Routing
    sealed class KeyDerivationAddress() {
        class URL(val address: java.net.URL): KeyDerivationAddress() // This will be Azure case
        class KeyDerivationEnclave(KDEInstanceInfo: EnclaveInstanceInfo): KeyDerivationAddress() // Another enclave
        class ClusterEnclave(instanceInfo: EnclaveInstanceInfo): KeyDerivationAddress() // TODO to be fair these two are the same
    }

    // Host needs routing table for the key derivation
//    val keyDerivationAddress: KeyDerivationAddress = TODO()

    // TODO add communication part with the KDE - spring boot rest api
    @JvmStatic
    fun main(args: Array<String>) {
        checkVersionSupport()
        initialiseEnclave()
        printAttestationData()

        // On startup read all persisted data that we may wish to provide to the enclave
        readSharedKey()
        loadStoredData()

        // TODO handle requests from the clients part
        closeEnclave()
    }

    ///////////////////////////////////////////// ENCLAVE BOILERPLATE
    private fun checkVersionSupport() {
        try {
            EnclaveHost.checkPlatformSupportsEnclaves(true)
            println("This platform supports enclaves in simulation, debug and release mode.")
        } catch (e: MockOnlySupportedException) {
            println("This platform only supports mock enclaves: " + e.message)
            System.exit(1)
        } catch (e: EnclaveLoadException) {
            println("This platform does not support hardware enclaves: " + e.message)
        }
    }

    private fun initialiseEnclave() {
        enclaveHost = EnclaveHost.load(ENCLAVE_CLASS_NAME)

        // Start up the enclave with a callback that will deliver the response. But remember: in a real app that can
        // handle multiple clients, you shouldn't start one enclave per client. That'd be wasteful and won't fit in
        // available encrypted memory. A real app should use the routingHint parameter to select the right connection
        // back to the client, here.
        enclaveHost.start(DCAP()) { commands: List<MailCommand?> ->
            for (command in commands) {
                if (command is MailCommand.PostMail) {
                    // This is just normal storage
                    if (command.routingHint == SELF_HINT) {
                        println("Request from enclave to store " + command.encryptedBytes.size + " bytes of persistent data.")
                        try {
                            // For showing that I can decrypt data after change of machine
                            // storage, encrypted with shared key
                            FileOutputStream(SELF_FILE).use { fos -> fos.write(command.encryptedBytes) }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                        // This is case where we have shared Key to store
                    } else if (command.routingHint == SHARED_KEY_HINT) {
                        println("Request from enclave to store shared key of size: " + command.encryptedBytes.size + " bytes.")
                        try {
                            FileOutputStream(SHARED_KEY_FILE).use { fos -> fos.write(command.encryptedBytes) }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                        // TODO there is no nice way of doing it, key request has to be done in coordination with host
                        //  this opens side channel because we leak that information here
                        // at the beginning when the enclave starts up
                    } else if (command.routingHint == REQUEST_KEY_HINT) { // TODO this could be done with separate mail command
                        routeKeyRequest()
                    } else { // Client request handling
                        TODO("Add demo code for sealing and unsealing some data")
                    }
                }
                else {
                    // it would be this: MailCommand.AcknowledgeMail, For now we don't support the ack
                    // we could have special command for key handling of it is part of the Conclave SDK, but it's not strictly necessary
                    // it could even be considered a side channel, although we have routing hint...
                    TODO()
                }
            }
        }
    }

    private fun printAttestationData() {
        // The attestation data must be provided to the client of the enclave, via whatever mechanism you like.
        val attestation = enclaveHost.enclaveInstanceInfo
        val attestationBytes = attestation.serialize() // TODO why do I have to serialise it and deserialise it
        // It has a useful toString method.
        println(EnclaveInstanceInfo.deserialize(attestationBytes))
    }

    private fun closeEnclave() {
        enclaveHost.close()
    }
    ///////////////////////////////////////////// END ENCLAVE BOILERPLATE

    ///////////////////////////////////////////// START COMMUNICATION FUNCTIONS
    // Spring boot routing to KDE/Azure
    private fun routeKeyRequest() {
        TODO("Add when implementing http stuff")
    }

    private fun requestKey() {
        val keyRequest = KeyRequest.createKeyRequest(KeyRequest.RequestType.KDE, "Hello")
//        routeKeyRequest(keyRequest)
    }

    // This will be used for any mail, also key response, but this shouldn't be visible to host
    private fun deliverMail() {
        TODO("Placeholder for delivering mail to enclave from the external source - KDE for example")
    }
    ///////////////////////////////////////////// END COMMUNICATION FUNCTIONS


    ///////////////////////////////////////////// START STORAGE FUNCTIONS
    // Read key on startup
    // TODO could connect it with self so it's not obvious that this is a shared key from the host perspective
    private fun readSharedKey() {
        try {
            val sharedKey = Files.readAllBytes(Paths.get(SHARED_KEY_FILE))
            enclaveHost.deliverMail(SHARED_KEY_ID, sharedKey, SHARED_KEY_HINT)
        } catch (e: IOException) { // TODO what is thrown when we can't decrypt it? There is no documentation on that :(
            // This is the case when we can't decrypt the key that was saved in the shared key file
            // This could be also a case when the constraints don't match
            //
            println("Could not read shared key: " + e.message)
            println("Key recovery started")
            requestKey()
        }
//        } catch () {
//            TODO()
//        }
    }

    private fun loadStoredData() {
        try {
            val selfFile = Files.readAllBytes(Paths.get(SELF_FILE))
            enclaveHost.deliverMail(SELF_ID, selfFile, SELF_HINT)
        } catch (e: IOException) {
            println("Could not read persistent data: " + e.message)
        }
    }
    ///////////////////////////////////////////// END STORAGE FUNCTIONS

    ///////////////////////////////////////////// ENDPOINTS
    //    @GetMapping("/attestation")
    fun attestation(): ByteArray = KeyDistributionHost.enclaveHost.enclaveInstanceInfo.serialize()

//    @PostMapping("/deliver-mail")
//    fun deliverMail(@RequestHeader("Correlation-ID") correlationId: String, @RequestBody encryptedMail: ByteArray) {
//        var signedDataBytes: ByteArray? = null
//        enclaveHost.deliverMail(idCounter.getAndIncrement(), encryptedMail, correlationId) {
//            signedDataBytes = it
//            null
//        }
//
//        if (signedDataBytes != null) {
//            val signedData = ProtoBuf.decodeFromByteArray(SignedData.serializer(), signedDataBytes!!)
//
//            //  Sanity check.
//            enclaveHost.enclaveInstanceInfo.verifier().apply {
//                update(signedData.bytes)
//                check(verify(signedData.signature))
//            }
//
//            val auctionResultHost = ProtoBuf.decodeFromByteArray(AuctionResultHost.serializer(), signedData.bytes)
//            println("Lot ${auctionResultHost.lotId} from seller ${auctionResultHost.seller} sold for ${auctionResultHost.price}")
//            // TODO Now chase up the seller for payment of fees, using the singature from the enclave as evidence!!
//        }
//    }
//    @PreDestroy
//    fun shutdown() {
//        if (::enclaveHost.isInitialized) {
//            enclaveHost.close()
//        }
//    }
    ///////////////////////////////////////////// END ENDPOINTS
}



sealed class KeyRequest {
    enum class RequestType {
        AZURE, // Jason format
        KDE
    }

    // Creates the keyRequest on the host side to trigger it from KDE
    // This function can return Azure format or KDE
    // For Azure we need to play with their attestation though... this will be part of a separate demo
    // TODO Make it factory
    companion object {
        fun createKeyRequest(type: RequestType, data: String): KeyRequest {
            return when (type) {
                RequestType.AZURE -> AzureKeyRequest.createKeyRequest(data)
                RequestType.KDE -> KeyDerivationEnclaveRequest.createKeyRequest(data)
            }
        }
    }

    data class AzureKeyRequest(val todo: String) : KeyRequest() {
        companion object {
            fun createKeyRequest(data: String): KeyRequest {
                val newKeyRequest = AzureKeyRequest("Hello Azure!")
                return newKeyRequest
            }
        }
    }

    data class KeyDerivationEnclaveRequest(val todo: String) : KeyRequest() {
        companion object {
            fun createKeyRequest(data: String): KeyRequest {
                val newKeyRequest = KeyDerivationEnclaveRequest("Hello KDE!")
                return newKeyRequest
            }
        }
    }
}
