package co.golink.tester.domain.user

import co.golink.tester.domain.asUrl
import co.golink.tester.domain.browse.BrowseEntryEnvelope
import co.golink.tester.domain.browse.BrowseItem
import co.golink.tester.domain.browse.toItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class UserResponse(
    val data: UserData,
)

@Serializable
data class UserData(
    val id: String,
    val type: String,
    val attributes: UserAttributes,
    val relationships: UserRelationships? = null,
)

@Serializable
data class UserAttributes(
    val color: String? = null,
    val avatar: JsonElement? = null,
    val email: String,
    val role: String? = null,
    val two_factor_authentication: Boolean = false,
    val two_factor_confirmed_at: String? = null,
    val socialite_account: Boolean = false,
    val email_verified_at: String? = null,
)

@Serializable
data class UserRelationships(
    val settings: SettingsEnvelope? = null,
    val favourites: List<BrowseEntryEnvelope>? = null,
    val subscription: SubscriptionEnvelope? = null,
)

@Serializable
data class SettingsEnvelope(
    val data: SettingsData? = null,
)

@Serializable
data class SettingsData(
    val id: String? = null,
    val type: String? = null,
    val attributes: SettingsAttributes? = null,
)

@Serializable
data class SettingsAttributes(
    val name: String? = null,
    val first_name: String? = null,
    val last_name: String? = null,
    val avatar: JsonElement? = null,
    val timezone: String? = null,
    val language: String? = null,
    val phone_number: String? = null,
    val address: String? = null,
    val city: String? = null,
    val postal_code: String? = null,
    val country: String? = null,
    val state: String? = null,
)

@Serializable
data class SubscriptionEnvelope(
    val data: SubscriptionData? = null,
)

@Serializable
data class SubscriptionData(
    val id: String? = null,
    val type: String? = null,
    val attributes: SubscriptionAttributes? = null,
    val relationships: SubscriptionRelationships? = null,
)

@Serializable
data class SubscriptionAttributes(
    val name: String? = null,
    val status: String? = null,
    val renews_at: String? = null,
    val ends_at: String? = null,
)

@Serializable
data class SubscriptionRelationships(
    val plan: PlanEnvelope? = null,
)

@Serializable
data class PlanEnvelope(
    val data: PlanData? = null,
)

@Serializable
data class PlanData(
    val id: String? = null,
    val type: String? = null,
    val attributes: PlanAttributes? = null,
)

@Serializable
data class PlanAttributes(
    val name: String? = null,
    val price: String? = null,
    val currency: String? = null,
    val interval: String? = null,
)

data class User(
    val id: String,
    val email: String,
    val role: String,
    val name: String,
    val firstName: String?,
    val lastName: String?,
    val avatar: String?,
    val emailVerified: Boolean,
    val twoFactorEnabled: Boolean,
    val socialiteAccount: Boolean,
    val favouriteFolders: List<BrowseItem.Folder>,
    val planName: String?,
    val timezone: String?,
    val phoneNumber: String?,
    val address: String?,
    val city: String?,
    val postalCode: String?,
    val country: String?,
    val state: String?,
) {
    companion object {
        fun fromResponse(response: UserResponse): User {
            val attrs = response.data.attributes
            val settings = response.data.relationships?.settings?.data?.attributes
            val displayName = settings?.name
                ?: listOfNotNull(settings?.first_name, settings?.last_name).joinToString(" ").ifBlank { null }
                ?: attrs.email
            val favourites = response.data.relationships?.favourites
                .orEmpty()
                .mapNotNull { (it.data.toItem() as? BrowseItem.Folder) }
            val planName = response.data.relationships?.subscription
                ?.data?.relationships?.plan?.data?.attributes?.name
            return User(
                id = response.data.id,
                email = attrs.email,
                role = attrs.role ?: "user",
                name = displayName,
                firstName = settings?.first_name,
                lastName = settings?.last_name,
                avatar = attrs.avatar.asUrl() ?: settings?.avatar.asUrl(),
                emailVerified = attrs.email_verified_at != null,
                twoFactorEnabled = attrs.two_factor_authentication,
                socialiteAccount = attrs.socialite_account,
                favouriteFolders = favourites,
                planName = planName,
                timezone = settings?.timezone,
                phoneNumber = settings?.phone_number,
                address = settings?.address,
                city = settings?.city,
                postalCode = settings?.postal_code,
                country = settings?.country,
                state = settings?.state,
            )
        }
    }
}
