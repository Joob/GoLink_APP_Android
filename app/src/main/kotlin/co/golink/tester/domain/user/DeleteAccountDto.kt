package co.golink.tester.domain.user

import kotlinx.serialization.Serializable

@Serializable
data class SendDeletionCodeRequest(
    val email_confirmation: String,
)

@Serializable
data class DeleteAccountRequest(
    val verification_code: String,
)

/** Espelha a cache delete_account_progress_{id} do backend. */
@Serializable
data class DeletionProgressResponse(
    val percentage: Int = 0,
    val current_step: String? = null,
    val completed: Boolean = false,
    val details: String? = null,
)

@Serializable
data class SimpleMessageResponse(
    val message: String? = null,
)
