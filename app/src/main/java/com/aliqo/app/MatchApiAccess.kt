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

data class RpsMoveRequest(val move:String)
data class RpsStateDto(
    val phase:String="PLAY",
    val round:Int=1,
    val myScore:Int=0,
    val opponentScore:Int=0,
    val myMove:String?=null,
    val opponentMove:String?=null,
    val roundResult:String?=null,
    val finished:Boolean=false,
    val wonMatch:Boolean=false
)

private interface RpsService {
    @GET("matchmaking/status") suspend fun matchStatus(@Header("Authorization") auth:String):MatchStatusDto
    @POST("matchmaking/queue") suspend fun queue(@Header("Authorization") auth:String,@Body body:MatchQueueRequest):MatchStatusDto
    @DELETE("matchmaking/queue") suspend fun cancelQueue(@Header("Authorization") auth:String):MatchStatusDto
    @POST("matchmaking/session/{sessionId}/leave") suspend fun leave(@Header("Authorization") auth:String,@Path("sessionId") sessionId:String):OkResponse
    @POST("rps/session/{sessionId}/move") suspend fun move(@Header("Authorization") auth:String,@Path("sessionId") sessionId:String,@Body body:RpsMoveRequest):RpsStateDto
    @GET("rps/session/{sessionId}/state") suspend fun state(@Header("Authorization") auth:String,@Path("sessionId") sessionId:String):RpsStateDto
    @POST("rps/session/{sessionId}/next") suspend fun next(@Header("Authorization") auth:String,@Path("sessionId") sessionId:String):RpsStateDto
    @POST("rps/session/{sessionId}/rematch") suspend fun rematch(@Header("Authorization") auth:String,@Path("sessionId") sessionId:String):RpsStateDto
}

class RpsLiveClient(private val service:RpsService) {
    suspend fun matchStatus(auth:String)=service.matchStatus(auth)
    suspend fun matchQueue(auth:String,body:MatchQueueRequest):MatchStatusDto {
        // A previous test session can remain ACTIVE in the database. RPS must never
        // reuse it because the other player may already be gone.
        val old=try{service.matchStatus(auth)}catch(_:Exception){null}
        if(old?.state=="MATCHED"&&old.sessionId!=null){
            try{service.leave(auth,old.sessionId)}catch(_:Exception){}
        } else if(old?.state=="WAITING") {
            try{service.cancelQueue(auth)}catch(_:Exception){}
        }
        return service.queue(auth,body)
    }
    suspend fun move(auth:String,sessionId:String,body:RpsMoveRequest)=service.move(auth,sessionId,body)
    suspend fun state(auth:String,sessionId:String)=service.state(auth,sessionId)
    suspend fun next(auth:String,sessionId:String)=service.next(auth,sessionId)
    suspend fun rematch(auth:String,sessionId:String)=service.rematch(auth,sessionId)
}

private val matchHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(75, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS)
        .build()
}
private val matchRetrofit by lazy {
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(matchHttpClient).addConverterFactory(GsonConverterFactory.create()).build()
}
val matchApi: ChatApi by lazy { matchRetrofit.create(ChatApi::class.java) }
val rpsLiveApi:RpsLiveClient by lazy { RpsLiveClient(matchRetrofit.create(RpsService::class.java)) }
