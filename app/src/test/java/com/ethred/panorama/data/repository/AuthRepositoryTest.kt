package com.ethred.panorama.data.repository

import com.ethred.panorama.data.local.prefs.EncryptedTokenStorage
import com.ethred.panorama.data.remote.EthredApiService
import com.ethred.panorama.data.remote.dto.LoginRequest
import com.ethred.panorama.data.remote.dto.LoginResponse
import com.ethred.panorama.data.remote.dto.UserDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import retrofit2.Response

class AuthRepositoryTest {

    private lateinit var mockApi: EthredApiService
    private lateinit var mockTokenStorage: EncryptedTokenStorage
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        mockApi = mock(EthredApiService::class.java)
        mockTokenStorage = mock(EncryptedTokenStorage::class.java)
        repository = AuthRepository(mockApi, mockTokenStorage)
    }

    @Test
    fun testIsLoggedIn_returnsTrueWhenTokenExists() {
        `when`(mockTokenStorage.getToken()).thenReturn("valid_jwt_token")
        assertTrue(repository.isLoggedIn())
    }

    @Test
    fun testIsLoggedIn_returnsFalseWhenTokenNull() {
        `when`(mockTokenStorage.getToken()).thenReturn(null)
        assertFalse(repository.isLoggedIn())
    }

    @Test
    fun testLogin_successfulResponse_savesTokenAndReturnsUser() = runBlocking {
        val user = UserDto("usr_1", "agent@ethred.com", "AGENT")
        val loginResponse = Response.success(LoginResponse("token_123", 3600, user))

        `when`(mockApi.login(LoginRequest("agent@ethred.com", "pass123"))).thenReturn(loginResponse)

        val result = repository.login("agent@ethred.com", "pass123")

        assertTrue(result.isSuccess)
        assertEquals(user, result.getOrNull())
        verify(mockTokenStorage).saveToken("token_123")
    }

    @Test
    fun testLogout_clearsToken() {
        repository.logout()
        verify(mockTokenStorage).clearToken()
    }
}
