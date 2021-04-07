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
 * This class demonstrates how to load an enclave and exchange byte arrays with it.
 */
object KeyDistributionHost {
    lateinit var enclaveHost: EnclaveHost

    // TODO think of routing
    sealed class KeyDerivationAddress() {
        class URL(val address: java.net.URL) // this will be Azure case
        class KeyDerivationEnclave(KDEInstanceInfo: EnclaveInstanceInfo)
        class ClusterEnclave(instanceInfo: EnclaveInstanceInfo) // TODO to be fair these two are the same
    }

    // Host needs routing table for the key derivation
//    val keyDerivationAddress: KeyDerivationAddress = TODO()

    @JvmStatic
    fun main(args: Array<String>) {
        // TODO add enclave load exception handling
        try {
            EnclaveHost.checkPlatformSupportsEnclaves(true)
            println("This platform supports enclaves in simulation, debug and release mode.")
        } catch (e: MockOnlySupportedException) {
            println("This platform only supports mock enclaves: " + e.message)
            System.exit(1)
        } catch (e: EnclaveLoadException) {
            println("This platform does not support hardware enclaves: " + e.message)
        }

        enclaveHost = EnclaveHost.load("com.r3.conclave.sample.enclave.KeyDerivationEnclave")

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
                    } else if (command.routingHint == RESPONSE_KEY_HINT) { // TODO think if this could be done with separate mail command
                        handleResponseKey()
                        // Look up the address we want to request from
                    } else {
                        TODO()
                    }
                }
            }
        }

        // The attestation data must be provided to the client of the enclave, via whatever mechanism you like.
        val attestation = enclaveHost.enclaveInstanceInfo
        val attestationBytes = attestation.serialize()

        // It has a useful toString method.
        println(EnclaveInstanceInfo.deserialize(attestationBytes))

        loadStoredData()
        closeEnclave()
    }

    fun handleResponseKey() {
        // TODO pass mail to the the enclave that sent that
        TODO()
    }

    fun closeEnclave() {
        enclaveHost.close()
    }

    private fun loadStoredData() {
        try {
            val selfFile = Files.readAllBytes(Paths.get(SELF_FILE))
            enclaveHost.deliverMail(SELF_ID, selfFile, SELF_HINT)
        } catch (e: IOException) {
            println("Could not read persistent data: " + e.message)
        }
    }


    //ENDPOINTS
    //    @GetMapping("/attestation")
    fun attestation(): ByteArray = enclaveHost.enclaveInstanceInfo.serialize()

    fun keyInformation() {
        // distribution of the key information for clients
        TODO()
    }

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
}