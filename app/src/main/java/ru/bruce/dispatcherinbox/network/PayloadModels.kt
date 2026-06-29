package ru.bruce.dispatcherinbox.network

import com.google.gson.Gson
import ru.bruce.dispatcherinbox.BuildConfig
import ru.bruce.dispatcherinbox.data.InboxItemEntity
import java.time.Instant

data class DispatcherPayload(
    val clientItemId: String,
    val type: String,
    val text: String?,
    val createdAt: String,
    val source: String,
    val metadata: PayloadMetadata,
)

data class PayloadMetadata(
    val appVersion: String,
    val platform: String = "android",
)

class PayloadSerializer(private val gson: Gson = Gson()) {
    fun from(item: InboxItemEntity, appVersion: String = BuildConfig.VERSION_NAME): DispatcherPayload =
        DispatcherPayload(
            clientItemId = item.id,
            type = item.type,
            text = item.text,
            createdAt = Instant.ofEpochMilli(item.createdAt).toString(),
            source = item.source,
            metadata = PayloadMetadata(appVersion = appVersion),
        )

    fun serialize(payload: DispatcherPayload): String = gson.toJson(payload)
}
