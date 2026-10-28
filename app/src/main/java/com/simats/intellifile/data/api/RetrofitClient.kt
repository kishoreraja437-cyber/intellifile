package com.simats.intellifile.data.api

import android.content.Context
import com.simats.intellifile.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
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
            }

            chain.proceed(builder.build())
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
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
