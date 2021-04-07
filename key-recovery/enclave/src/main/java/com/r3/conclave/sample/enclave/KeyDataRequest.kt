package com.r3.conclave.sample.enclave

import kotlinx.serialization.Serializable

// todo signed
@Serializable
data class KeyDataRequest(val id: Long)