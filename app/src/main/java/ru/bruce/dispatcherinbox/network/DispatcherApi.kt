package ru.bruce.dispatcherinbox.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface DispatcherApi {
    @Multipart
    @POST("api/mobile-inbox/items")
    suspend fun uploadItem(
        @Header("Authorization") authorization: String,
        @Part("payload") payload: RequestBody,
        @Part files: List<MultipartBody.Part>,
    ): Response<DispatcherResponse>
}

data class DispatcherResponse(
    val ok: Boolean,
    @SerializedName("dispatcherItemId") val dispatcherItemId: String?,
)
