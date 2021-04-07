package com.r3.conclave.sample.enclave

import com.r3.conclave.client.EnclaveConstraint
import com.r3.conclave.enclave.Enclave
import com.r3.conclave.mail.EnclaveMail

/**
 *
 */
class KeyDistributionEnclave : Enclave() {
    private val keyStore = SimpleInMemoryKeyStore()
    private lateinit var masterKey: ByteArray // TODO that is for demo now

    // If we want to limit access to the keys produced by this KDE
    val constraintsList: List<EnclaveConstraint> = emptyList()

    init {
        // generate or read from self storage, I wish there was a initialisation handler...
        masterKey = TODO()
    }

    // EnclaveInfo of the KDE
    // TODO what to do with id
    override fun receiveMail(id: Long, mail: EnclaveMail, routingHint: String?) {
        if (routingHint == SELF_HINT) {
            handleMailToSelf(mail)
        } else if (routingHint == REQUEST_KEY_HINT){ // remember if we requested key at all ;)
            handleKeyRequest(mail)
        } else {
            // TODO handle this case
            throw IllegalArgumentException("this enclave only does key loading and sharing")
        }
        // This is used when the host delivers a message from the client.
        // First, decode mail body as a String.
//        val stringToReverse = String(mail.bodyAsBytes)
//        // Reverse it and re-encode to UTF-8 to send back.
//        val reversedEncodedString: ByteArray = reverse(stringToReverse).toByteArray()
//        // Get the post office object for responding back to this mail and use it to encrypt our response.
//        val responseBytes = postOffice(mail).encryptMail(reversedEncodedString)
//        postMail(responseBytes, routingHint)
    }

    // TODO this should go to the key derivation enclave, but for now i will implement it here
    private fun handleMailToSelf(mail: EnclaveMail) {
        mail.envelope
        val mailBody = mail.bodyAsBytes
        // store secret data for key derivation
        TODO()
    }

    // TODO invoke key storage
    private fun saveSecretData() {
        // construct mail with that secret data
        val responseBytes = ByteArray(0) // TODO
        postMail(responseBytes, SELF_HINT)
        TODO()
    }

    private fun handleKeyRequest(mail: EnclaveMail) {
        // 1. authenticate
        // 2. check that valid key request


        // if we have key derivation secret
        // authenticate the enclave that we are allowed to generate the key for them, btw we need storage as well
        // we can either generate key in case of KDE
        // or we can share ours
        TODO()
    }

    // This would be the identity sharing of Application Enclaves keys
    private fun proveKeyGeneration() {
        // TODO think of that case, we need to have transitive attestation
        //  this can be proved from KDE and from app, todo think about revocation
        TODO()
    }

    private fun attestKeyRequest() {
        TODO()
    }
}
