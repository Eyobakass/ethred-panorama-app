package com.ethred.panorama.data.remote

import com.ethred.panorama.data.remote.dto.AttachMediaRequest
import com.ethred.panorama.data.remote.dto.LoginRequest
import com.ethred.panorama.data.remote.dto.LoginResponse
import com.ethred.panorama.data.remote.dto.PropertyMediaDto
import com.ethred.panorama.data.remote.dto.UploadResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Multipart
import retrofit2.http.Part

interface EthredApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @Multipart
    @POST("upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>

    @POST("properties/{id}/media")
    suspend fun attachMedia(
        @Path("id") propertyId: String,
        @Body request: AttachMediaRequest
    ): Response<PropertyMediaDto>
}
