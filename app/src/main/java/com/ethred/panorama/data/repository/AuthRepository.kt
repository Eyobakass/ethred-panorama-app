package com.ethred.panorama.data.repository

import com.ethred.panorama.data.local.prefs.EncryptedTokenStorage
import com.ethred.panorama.data.remote.EthredApiService
import com.ethred.panorama.data.remote.dto.LoginRequest
import com.ethred.panorama.data.remote.dto.UserDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: EthredApiService,
    private val tokenStorage: EncryptedTokenStorage
) {
    fun isLoggedIn(): Boolean {
        return !tokenStorage.getToken().isNullOrEmpty()
    }

    suspend fun login(email: String, password: String): Result<UserDto?> {
        return try {
            val response = apiService.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenStorage.saveToken(body.accessToken)
                Result.success(body.user)
            } else {
                Result.failure(Exception("Authentication failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveDemoSession() {
        tokenStorage.saveToken("mock_demo_token_123")
    }

    fun logout() {
        tokenStorage.clearToken()
    }
}
