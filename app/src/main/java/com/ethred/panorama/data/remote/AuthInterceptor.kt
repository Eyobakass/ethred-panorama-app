package com.ethred.panorama.data.remote

import com.ethred.panorama.data.local.prefs.EncryptedTokenStorage
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStorage: EncryptedTokenStorage
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenStorage.getToken()

        val requestBuilder = originalRequest.newBuilder()
        if (!token.isNullOrEmpty() && !originalRequest.url.encodedPath.contains("/auth/login")) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        if (response.code == 401) {
            // Token expired or invalid, clear token to trigger re-auth
            tokenStorage.clearToken()
        }

        return response
    }
}
