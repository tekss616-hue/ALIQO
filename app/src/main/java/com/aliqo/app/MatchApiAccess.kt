package com.aliqo.app

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
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

interface RpsLiveApi {
    @GET("matchmaking/status") suspend fun matchStatus(@Header("Authorization") auth:String):MatchStatusDto
    @POST("matchmaking/queue") suspend fun matchQueue(@Header("Authorization") auth:String,@Body body:MatchQueueRequest):MatchStatusDto
    @POST("rps/session/{sessionId}/move") suspend fun move(@Header("Authorization") auth:String,@Path("sessionId") sessionId:String,@Body body:RpsMoveRequest):RpsStateDto
    @GET("rps/session/{sessionId}/state") suspend fun state(@Header("Authorization") auth:String,@Path("sessionId") sessionId:String):RpsStateDto
    @POST("rps/session/{sessionId}/next") suspend fun next(@Header("Authorization") auth:String,@Path("sessionId") sessionId:String):RpsStateDto
    @POST("rps/session/{sessionId}/rematch") suspend fun rematch(@Header("Authorization") auth:String,@Path("sessionId") sessionId:String):RpsStateDto
}

private val matchHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(75, TimeUnit.SECONDS)
        .readTimeout(75, TimeUnit.SECONDS)
        .writeTimeout(75, TimeUnit.SECONDS)
        .build()
}

val matchApi: ChatApi by lazy {
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(matchHttpClient).addConverterFactory(GsonConverterFactory.create()).build().create(ChatApi::class.java)
}

val rpsLiveApi:RpsLiveApi by lazy {
    Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(matchHttpClient).addConverterFactory(GsonConverterFactory.create()).build().create(RpsLiveApi::class.java)
}
