package com.r3.conclave.sample.host

// TODO refactor this and move to common, even better, these hints should be reserved hints in conclave itself
//  also there is no enclave storage support in the conclave sdk, so for now I do PoC on files
//  think of
const val SHARED_KEY_FILE = "sharedKey.dat"
const val SELF_FILE = "self.dat"
const val SELF_HINT = "self"
const val SHARED_KEY_HINT = "sharedKey"
const val REQUEST_KEY_HINT = "requestKey"
const val RESPONSE_KEY_HINT = "responseKey"
const val SHARED_KEY_ID = 1L
const val SELF_ID = 2L