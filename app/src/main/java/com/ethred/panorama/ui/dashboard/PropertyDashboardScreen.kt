package com.ethred.panorama.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethred.panorama.data.repository.AuthRepository

data class PropertyItem(
    val id: String,
    val title: String,
    val location: String,
    val panoramaCount: Int
)

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val properties: List<PropertyItem>, val userRole: String) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
    object Empty : DashboardUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyDashboardScreen(
    authRepository: AuthRepository,
    onSelectProperty: (propertyId: String, propertyTitle: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onLogout: () -> Unit
) {
    var uiState by remember { mutableStateOf<DashboardUiState>(DashboardUiState.Loading) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadProperties() {
        val role = authRepository.getUserRole() ?: "AGENT"
        val sampleProperties = listOf(
            PropertyItem("prop_101", "Luxury Villa #307", "Bole, Addis Ababa", 2),
            PropertyItem("prop_102", "Modern Apartment #402", "CMC, Addis Ababa", 0),
            PropertyItem("prop_103", "Commercial Office Suite B", "Kazanchis, Addis Ababa", 1)
        )
        uiState = if (sampleProperties.isEmpty()) {
            DashboardUiState.Empty
        } else {
            DashboardUiState.Success(sampleProperties, role)
        }
        isRefreshing = false
    }

    LaunchedEffect(Unit) { loadProperties() }

    val canCapture = (uiState as? DashboardUiState.Success)?.let {
        it.userRole == "AGENT" || it.userRole == "SELLER"
    } ?: true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Properties", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenLibrary) {
                        Icon(Icons.Default.CollectionsBookmark, contentDescription = "Panorama Library")
                    }
                    IconButton(onClick = { isRefreshing = true; loadProperties() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = {
                        authRepository.logout()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(3) { ShimmerPropertyCard() }
                    }
                }

                is DashboardUiState.Empty -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Home, contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No properties assigned yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Text(
                                "Contact your admin to assign properties.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                is DashboardUiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { uiState = DashboardUiState.Loading; loadProperties() }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Retry")
                            }
                        }
                    }
                }

                is DashboardUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        if (!canCapture) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Lock, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "360° Capture is restricted to Agent or Seller accounts.",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Your Assigned Properties",
                            style    = MaterialTheme.typography.titleMedium,
                            color    = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding      = PaddingValues(bottom = 80.dp)
                        ) {
                            items(state.properties) { property ->
                                PropertyCard(
                                    property       = property,
                                    canCapture     = canCapture,
                                    onSelectProperty = onSelectProperty
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyCard(
    property: PropertyItem,
    canCapture: Boolean,
    onSelectProperty: (String, String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canCapture) { onSelectProperty(property.id, property.title) },
        shape  = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Home, contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = property.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text     = property.location,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp, start = 26.dp)
                )
                Text(
                    text     = "${property.panoramaCount} panorama(s) attached",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp, start = 26.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            Button(
                onClick         = { onSelectProperty(property.id, property.title) },
                enabled         = canCapture,
                shape           = MaterialTheme.shapes.small,
                contentPadding  = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Capture", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ShimmerPropertyCard() {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val alpha by shimmer.animateFloat(
        initialValue = 0.3f,
        targetValue  = 0.7f,
        animationSpec = infiniteRepeatable(
            animation  = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Card(
        modifier  = Modifier.fillMaxWidth().height(88.dp),
        shape     = MaterialTheme.shapes.medium,
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = alpha)
        )
    ) {}
}
