package com.simats.intellifile.data.api

import android.content.Context
import android.util.Log
import com.simats.intellifile.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val TAG = "RetrofitClient"

    @Volatile
    private var apiService: IntelliFileApiService? = null
    private var initializedBaseUrl: String = ""

    fun getApiService(context: Context): IntelliFileApiService {
        val activeBaseUrl = NetworkConfig.getBaseUrl(context)

        return apiService?.takeIf { initializedBaseUrl == activeBaseUrl } ?: synchronized(this) {
            apiService?.takeIf { initializedBaseUrl == activeBaseUrl } ?: buildRetrofit(context, activeBaseUrl).also {
                apiService = it
                initializedBaseUrl = activeBaseUrl
            }
        }
    }

    private fun buildRetrofit(context: Context, baseUrl: String): IntelliFileApiService {
        val sessionManager = SessionManager(context)

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Accept", "application/json")

            val token = sessionManager.getToken()
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
                builder.header("X-Authorization", "Bearer $token")
            }

            chain.proceed(builder.build())
        }

        // Network error handling interceptor: intercepts low-level IO/timeout errors and surfaces clear messages
        val errorInterceptor = Interceptor { chain ->
            val request = chain.request()
            try {
                chain.proceed(request)
            } catch (e: IOException) {
                val currentHost = request.url.host
                val failureReason = when (e) {
                    is SocketTimeoutException -> "Connection timed out connecting to $currentHost"
                    is ConnectException -> "Connection refused by $currentHost"
                    is UnknownHostException -> "Server host $currentHost not found"
                    else -> e.message ?: "Network error"
                }

                throw IOException(
                    "Cannot reach backend ($failureReason). Please ensure your server or devtunnel is running.",
                    e
                )
            }
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(errorInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IntelliFileApiService::class.java)
    }

    /**
     * Resets the client if base URL or credentials change.
     */
    fun resetClient() {
        synchronized(this) {
            apiService = null
            initializedBaseUrl = ""
        }
    }
}
