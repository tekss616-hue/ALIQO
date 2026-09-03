package com.aliqo.app

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class RegisterDeviceRequest(
    val token:String,
    val platform:String="ANDROID",
    val appVersion:String?=null,
)

data class DeviceDto(
    val id:String,
    val platform:String,
    val appVersion:String?=null,
    val lastSeenAt:String?=null,
    val createdAt:String?=null,
)

data class PrepareMediaRequest(
    val chatId:String,
    val fileName:String,
    val mimeType:String,
    val byteSize:Int,
    val sha256:String?=null,
)

data class PrepareMediaResponse(
    val uploadId:String,
    val kind:String,
    val objectKey:String?=null,
    val uploadUrl:String?=null,
    val method:String?=null,
    val headers:Map<String,String> = emptyMap(),
    val expiresAt:String?=null,
)

data class CompleteMediaResponse(
    val id:String,
    val status:String,
    val chatId:String,
    val fileName:String,
    val mimeType:String,
    val byteSize:Int,
    val publicUrl:String?=null,
    val uploadedAt:String?=null,
)

interface Batch2TransportApi {
    @POST("devices/register")
    suspend fun registerDevice(
        @Header("Authorization") auth:String,
        @Body body:RegisterDeviceRequest,
    ):DeviceDto

    @GET("devices")
    suspend fun devices(@Header("Authorization") auth:String):List<DeviceDto>

    @DELETE("devices/{id}")
    suspend fun revokeDevice(
        @Header("Authorization") auth:String,
        @Path("id") id:String,
    ):OkResponse

    @POST("media/prepare")
    suspend fun prepareMedia(
        @Header("Authorization") auth:String,
        @Body body:PrepareMediaRequest,
    ):PrepareMediaResponse

    @POST("media/{id}/complete")
    suspend fun completeMedia(
        @Header("Authorization") auth:String,
        @Path("id") id:String,
    ):CompleteMediaResponse
}

val batch2TransportApi:Batch2TransportApi by lazy {
    val client=OkHttpClient.Builder()
        .connectTimeout(75,TimeUnit.SECONDS)
        .readTimeout(75,TimeUnit.SECONDS)
        .writeTimeout(75,TimeUnit.SECONDS)
        .build()
    Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(Batch2TransportApi::class.java)
}
