package co.golink.tester.domain.settings

import kotlinx.serialization.Serializable

@Serializable
data class StorageResponse(
    val data: StorageData,
)

@Serializable
data class StorageData(
    val id: String,
    val type: String,
    val attributes: StorageAttributes,
    val meta: StorageMeta? = null,
)

@Serializable
data class StorageMeta(
    val traffic: StorageTraffic? = null,
    val images: StorageTypeUsage? = null,
    val audios: StorageTypeUsage? = null,
    val videos: StorageTypeUsage? = null,
    val documents: StorageTypeUsage? = null,
    val others: StorageTypeUsage? = null,
)

@Serializable
data class StorageTraffic(
    val upload: String? = null,
    val download: String? = null,
    val chart: StorageTrafficChart? = null,
)

@Serializable
data class StorageTrafficChart(
    val upload: List<TrafficChartPoint>? = null,
    val download: List<TrafficChartPoint>? = null,
)

@Serializable
data class TrafficChartPoint(
    val created_at: String? = null,
    val amount: String? = null,
    val percentage: Float? = null,
)

@Serializable
data class StorageTypeUsage(
    val used: String? = null,
    val percentage: Float? = null,
)

@Serializable
data class StorageAttributes(
    val used: String,
    val capacity: String,
    val percentage: Float,
)

@Serializable
data class SessionsResponse(
    val data: List<SessionItem> = emptyList(),
    val table_exists: Boolean = true,
)

@Serializable
data class SessionItem(
    val id: String,
    val ip_address: String? = null,
    val browser: String? = null,
    val device: String? = null,
    val platform: String? = null,
    val location: String? = null,
    val is_current: Boolean = false,
    val login_at: String? = null,
    val last_activity_at: String? = null,
)

@Serializable
data class UpdatePasswordRequest(
    val current: String,
    val password: String,
    val password_confirmation: String,
)

@Serializable
data class UpdateProfileFieldRequest(
    val name: String,
    val value: String,
)

@Serializable
data class MobileBackupSettingRequest(
    val enabled: Boolean,
)

@Serializable
data class AccessToken(
    val id: Long,
    val name: String,
    val abilities: List<String> = emptyList(),
    val last_used_at: String? = null,
    val created_at: String? = null,
)

@Serializable
data class CreateTokenRequest(
    val name: String,
)

@Serializable
data class CreateTokenResponse(
    val plainTextToken: String? = null,
)

data class StorageUsage(
    val used: String,
    val capacity: String,
    val percentage: Float,
    val trafficUpload: String?,
    val trafficDownload: String?,
    val trafficChartUpload: List<TrafficChartPoint>?,
    val trafficChartDownload: List<TrafficChartPoint>?,
    val images: StorageTypeUsage?,
    val audios: StorageTypeUsage?,
    val videos: StorageTypeUsage?,
    val documents: StorageTypeUsage?,
    val others: StorageTypeUsage?,
) {
    companion object {
        fun fromResponse(response: StorageResponse) = StorageUsage(
            used = response.data.attributes.used,
            capacity = response.data.attributes.capacity,
            percentage = response.data.attributes.percentage,
            trafficUpload = response.data.meta?.traffic?.upload,
            trafficDownload = response.data.meta?.traffic?.download,
            trafficChartUpload = response.data.meta?.traffic?.chart?.upload,
            trafficChartDownload = response.data.meta?.traffic?.chart?.download,
            images = response.data.meta?.images,
            audios = response.data.meta?.audios,
            videos = response.data.meta?.videos,
            documents = response.data.meta?.documents,
            others = response.data.meta?.others,
        )
    }
}

@Serializable
data class TransactionsResponse(
    val data: List<TransactionEnvelope> = emptyList(),
)

@Serializable
data class TransactionEnvelope(
    val data: TransactionData,
)

@Serializable
data class TransactionData(
    val id: String,
    val type: String,
    val attributes: TransactionAttributes,
)

@Serializable
data class TransactionAttributes(
    val type: String? = null,
    val status: String? = null,
    val note: String? = null,
    val price: String? = null,
    val currency: String? = null,
    val driver: String? = null,
    val created_at: String? = null,
)

data class TransactionItem(
    val id: String,
    val type: String?,
    val status: String?,
    val note: String?,
    val price: String?,
    val driver: String?,
    val createdAt: String?,
)
