package com.ethred.panorama.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UploadResponse(
    @SerializedName("file_url") val fileUrl: String
)

data class AttachMediaRequest(
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("media_category") val mediaCategory: String, // "IMAGE" or "DOCUMENT"
    @SerializedName("sort_order") val sortOrder: Int
)

data class PropertyMediaDto(
    @SerializedName("id") val id: String,
    @SerializedName("property_id") val propertyId: String,
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("media_category") val mediaCategory: String,
    @SerializedName("sort_order") val sortOrder: Int
)
