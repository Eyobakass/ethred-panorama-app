package com.ethred.panorama.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TourManifestDto(
    @SerializedName("version") val version: String = "1.0",
    @SerializedName("property_id") val propertyId: String,
    @SerializedName("default_scene") val defaultScene: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("scenes") val scenes: List<SceneDto>
)

data class SceneDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("pitch_offset") val pitchOffset: Float = 0f,
    @SerializedName("yaw_offset") val yawOffset: Float = 0f,
    @SerializedName("hotspots") val hotspots: List<HotspotDto> = emptyList()
)

data class HotspotDto(
    @SerializedName("id") val id: String,
    @SerializedName("pitch") val pitch: Float,
    @SerializedName("yaw") val yaw: Float,
    @SerializedName("text") val text: String,
    @SerializedName("target_scene_id") val targetSceneId: String
)
