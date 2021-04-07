//package com.r3.conclave.sample.client
//
//import com.fasterxml.jackson.databind.ObjectMapper
//import com.r3.conclave.client.EnclaveConstraint
//import com.r3.conclave.common.EnclaveInstanceInfo
//import com.r3.conclave.mail.Curve25519PrivateKey
//import com.r3.conclave.mail.PostOffice
////import com.r3.conclave.sample.common.*
//import kotlinx.serialization.ExperimentalSerializationApi
//import kotlinx.serialization.KSerializer
//import kotlinx.serialization.protobuf.ProtoBuf
//import org.apache.http.client.entity.EntityBuilder
//import org.apache.http.client.methods.HttpGet
//import org.apache.http.client.methods.HttpPost
//import org.apache.http.impl.client.HttpClients
//import java.nio.file.Files
//import java.nio.file.Paths
//import java.security.PrivateKey
//import java.time.Duration
//import java.time.Instant
//import java.util.*
//import kotlin.collections.HashMap
//
//@ExperimentalSerializationApi
//object Client {
//    private val httpClient = HttpClients.createDefault()
//    private val remainingArgs = LinkedList<String>()
//    private val bidRequests = HashMap<String, String>()
//
//    private lateinit var domain: String
//    private lateinit var attestation: EnclaveInstanceInfo
//    private lateinit var name: String
//    private lateinit var identityKey: PrivateKey
//    private lateinit var postOffice: PostOffice
//
//    @JvmStatic
//    fun main(args: Array<String>) {
//        remainingArgs += args
//
//        domain = remainingArgs.remove()
//        val constraint = EnclaveConstraint.parse(remainingArgs.remove())
//
//        attestation = httpClient.execute(HttpGet("$domain/attestation")).use {
//            EnclaveInstanceInfo.deserialize(it.entity.content.readBytes())
//        }
//
//        // First make sure we're talking to the right enclave.
//        constraint.check(attestation)
//
//        name = remainingArgs.remove().toLowerCase()
//
//        restoreState()
//
//        val cmd = remainingArgs.remove().toLowerCase()
//        when (cmd) {
//            "create-lot" -> createLot()
//            "expire-lot" -> expireLot()
//            "list-auctions" -> listAuctions()
//            "submit-bid" -> submitBid()
//            "query-auction" -> queryAuction()
//            else -> throw IllegalArgumentException(cmd)
//        }
//    }
//
//    private fun createLot() {
//        val description = remainingArgs.remove()
//        val duration = Duration.parse(remainingArgs.remove())
//        val id = UUID.randomUUID().toString()
//        val createLot = CreateLot(
//                Lot(
//                        id = id,
//                        description = description,
//                        auctionExpiration = Instant.now() + duration
//                )
//        )
//        enclaveRequest(createLot, EnclaveResponse.serializer())
//        println("Lot ID = $id")
//    }
//
//    private fun expireLot() {
//        val lotId = remainingArgs.remove()
//        val result = enclaveRequest(ExpireLot(lotId), AuctionResultForSeller.serializer()).firstOrNull()
//        println(result ?: "Unable to expire lot.")
//    }
//
//    private fun listAuctions() {
//        val auctions = enclaveRequest(ListAuctions, Auctions.serializer()).firstOrNull() ?: return
//        for (lot in auctions.lots) {
//            println(lot)
//        }
//    }
//
//    private fun submitBid() {
//        val lotId = remainingArgs.remove()
//        val price = remainingArgs.remove().toDouble()
//        val bidRequest = BidRequest(lotId, price)
//        val correlationId = UUID.randomUUID().toString()
//        val result = enclaveRequest(bidRequest, BidResult.serializer(), correlationId).first()
//        bidRequests[lotId] = correlationId
//        persistState()
//        println(result)
//    }
//
//    private fun queryAuction() {
//        val lotId = remainingArgs.remove()
//        val correlationId = bidRequests[lotId]
//        if (correlationId == null) {
//            println("Bid for lot $lotId was never made.")
//            return
//        }
//        val result = inbox(correlationId, AuctionResultForBuyer.serializer()).lastOrNull()
//        println(result ?: "Auction has not expired.")
//    }
//
//    private fun <T : EnclaveResponse> enclaveRequest(
//            request: ClientRequest,
//            responseSerializer: KSerializer<T>,
//            correlationId: String = UUID.randomUUID().toString()
//    ): List<T> {
//        deliverMail(request, correlationId)
//        return inbox(correlationId, responseSerializer)
//    }
//
//    private fun deliverMail(request: ClientRequest, correlationId: String) {
//        val requestBody = ProtoBuf.encodeToByteArray(ClientRequest.serializer(), request)
//        val requestMail = postOffice.encryptMail(requestBody)
//        val post = HttpPost("$domain/deliver-mail").apply {
//            addHeader("Correlation-ID", correlationId)
//            entity = EntityBuilder.create().setBinary(requestMail).build()
//        }
//        httpClient.execute(post)
//        persistState()
//    }
//
//    private fun <T : EnclaveResponse> inbox(correlationId: String, serializer: KSerializer<T>): List<T> {
//        return httpClient.execute(HttpGet("$domain/inbox/$correlationId")).use {
//            val json = ObjectMapper().readTree(it.entity.content)
//            json.map { child ->
//                val mailBytes = child.binaryValue()
//                val responseBytes = postOffice.decryptMail(mailBytes).bodyAsBytes
//                ProtoBuf.decodeFromByteArray(serializer, responseBytes)
//            }
//        }
//    }
//
//    private fun restoreState() {
//        val file = Paths.get(name)
//        if (Files.exists(file)) {
//            val lines = Files.readAllLines(file)
//            identityKey = Curve25519PrivateKey(Base64.getDecoder().decode(lines[0]))
//            postOffice = attestation.createPostOffice(identityKey, lines[1])
//            postOffice.nextSequenceNumber = lines[2].toLong()
//            for (bidLine in lines.drop(3)) {
//                val (lotId, correlationId) = bidLine.split(",")
//                bidRequests[lotId] = correlationId
//            }
//        } else {
//            println("Creating new identity key...")
//            identityKey = Curve25519PrivateKey.random()
//            postOffice = attestation.createPostOffice(identityKey, UUID.randomUUID().toString())
//            persistState()
//        }
//    }
//
//    private fun persistState() {
//        Files.write(
//                Paths.get(name),
//                listOf(
//                        Base64.getEncoder().encodeToString(identityKey.encoded),
//                        postOffice.topic,
//                        postOffice.nextSequenceNumber.toString(),
//                ) + bidRequests.map { "${it.key},${it.value}" }
//        )
//    }
//}
