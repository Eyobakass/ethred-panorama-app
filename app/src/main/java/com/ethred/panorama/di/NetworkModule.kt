package com.ethred.panorama.di

import com.ethred.panorama.BuildConfig
import com.ethred.panorama.data.remote.AuthInterceptor
import com.ethred.panorama.data.remote.EthredApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val BASE_URL = "https://ethred-backend.onrender.com/api/v1/"
    private const val API_HOST  = "ethred-backend.onrender.com"

    /**
     * SEC-02: Certificate pinning for Ethred API.
     * SHA-256 pins should be updated whenever the server certificate rotates.
     * Obtain the current pin via:
     *   openssl s_client -connect ethred-backend.onrender.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
     *
     * TWO pins are provided: current cert + backup rotation cert.
     * Update these values in the gradle.properties / secrets file and inject via BuildConfig.
     */
    private val certificatePinner = CertificatePinner.Builder()
        // Primary pin — live leaf cert for CN=onrender.com (fetched 2026-07-23)
        // Render.com rotates leaf certs every ~90 days — update this when it expires.
        .add(API_HOST, "sha256/svZQ+GWVBhyE3yjb5e+PnpdDsH4n9mfjjx4Alk3gH5o=")
        // Backup pin — Google Trust Services WE1 intermediate CA (stable, long-lived)
        // This pin will survive leaf cert rotation so the app keeps working after renewal.
        .add(API_HOST, "sha256/oof/q3Ysxpom1IIDft9wH2U86JkCXGKn5cuIu5tBnLs=")
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            // Only log full body in debug builds — never log tokens in release
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            // SEC-02: SSL certificate pinning
            .certificatePinner(certificatePinner)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)   // Longer for large panorama uploads
            .writeTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideEthredApiService(retrofit: Retrofit): EthredApiService {
        return retrofit.create(EthredApiService::class.java)
    }
}
