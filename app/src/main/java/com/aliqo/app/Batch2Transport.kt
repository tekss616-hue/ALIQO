package com.aliqo.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.security.MessageDigest
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

data class MediaCapabilitiesDto(
    val enabled:Boolean=false,
    val provider:String?=null,
    val kinds:List<String> = emptyList(),
    val maxBytes:Map<String,Int> = emptyMap(),
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
    val formFields:Map<String,String> = emptyMap(),
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

data class AttachMediaMessageRequest(
    val uploadId:String,
    val replyToId:String?=null,
    val caption:String?=null,
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

    @GET("media/capabilities")
    suspend fun mediaCapabilities(@Header("Authorization") auth:String):MediaCapabilitiesDto

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

    @POST("chats/{chatId}/media-message")
    suspend fun attachMediaMessage(
        @Header("Authorization") auth:String,
        @Path("chatId") chatId:String,
        @Body body:AttachMediaMessageRequest,
    ):MessageDto
}

private val batch2HttpClient:OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(75,TimeUnit.SECONDS)
        .readTimeout(75,TimeUnit.SECONDS)
        .writeTimeout(75,TimeUnit.SECONDS)
        .build()
}

val batch2TransportApi:Batch2TransportApi by lazy {
    Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(batch2HttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(Batch2TransportApi::class.java)
}

object MediaBinaryUploader {
    fun upload(prepared:PrepareMediaResponse,mimeType:String,bytes:ByteArray,fileName:String) {
        val url=prepared.uploadUrl?.trim().orEmpty()
        require(url.startsWith("https://")) { "Secure upload URL required" }
        val method=prepared.method?.uppercase() ?: "PUT"
        require(method=="PUT" || method=="POST") { "Unsupported upload method" }
        val builder=Request.Builder().url(url)
        prepared.headers.forEach{(name,value)->builder.header(name,value)}
        val binaryBody=bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val body=if(method=="POST" && prepared.formFields.isNotEmpty()){
            val multipart=MultipartBody.Builder().setType(MultipartBody.FORM)
            prepared.formFields.forEach{(name,value)->multipart.addFormDataPart(name,value)}
            multipart.addFormDataPart("file",fileName,binaryBody)
            multipart.build()
        }else binaryBody
        if(method=="POST") builder.post(body) else builder.put(body)
        batch2HttpClient.newCall(builder.build()).execute().use { response ->
            if(!response.isSuccessful) error("Media upload failed: ${response.code}")
        }
    }
}

object SecureMediaMessageSender {
    private fun sha256(bytes:ByteArray):String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(""){"%02x".format(it)}

    suspend fun available(auth:String):Boolean = try {
        batch2TransportApi.mediaCapabilities(auth).enabled
    } catch (_:Exception) {
        false
    }

    suspend fun send(
        auth:String,
        chatId:String,
        fileName:String,
        mimeType:String,
        bytes:ByteArray,
        replyToId:String?=null,
        caption:String?=null,
    ):MessageDto {
        require(bytes.isNotEmpty()) { "Empty media" }
        val capabilities=batch2TransportApi.mediaCapabilities(auth)
        require(capabilities.enabled) { "Media uploads are not enabled" }
        val prepared=batch2TransportApi.prepareMedia(
            auth,
            PrepareMediaRequest(
                chatId=chatId,
                fileName=fileName,
                mimeType=mimeType,
                byteSize=bytes.size,
                sha256=sha256(bytes),
            ),
        )
        withContext(Dispatchers.IO) { MediaBinaryUploader.upload(prepared,mimeType,bytes,fileName) }
        val completed=batch2TransportApi.completeMedia(auth,prepared.uploadId)
        require(completed.status=="UPLOADED") { "Upload confirmation failed" }
        return batch2TransportApi.attachMediaMessage(
            auth,
            chatId,
            AttachMediaMessageRequest(prepared.uploadId,replyToId,caption?.trim()?.ifBlank{null}),
        )
    }
}
