package com.ethred.panorama.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ethred.panorama.data.repository.CaptureSessionRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoomSetupScreen(
    propertyId: String,
    propertyTitle: String,
    sessionRepository: CaptureSessionRepository,
    onNavigateBack: () -> Unit,
    onStartCapture: (sessionId: String, nodeCount: Int) -> Unit
) {
    var roomName       by remember { mutableStateOf("") }
    var roomNameError  by remember { mutableStateOf<String?>(null) }
    var isLoading      by remember { mutableStateOf(false) }
    var errorMessage   by remember { mutableStateOf<String?>(null) }

    // Capture quality presets
    val qualityOptions = listOf(
        Triple("Quick", 12, "12 frames — Equatorial ring only. Fastest."),
        Triple("Standard", 20, "20 frames — Upper + equatorial rings."),
        Triple("Full", 28, "28 frames — Full sphere. Best for real estate."),
        Triple("Custom", -1, "Choose your own frame count (12–36).")
    )
    var selectedQuality    by remember { mutableIntStateOf(2) }   // default "Full"
    var customNodeCount    by remember { mutableFloatStateOf(28f) }

    val effectiveNodeCount = when (selectedQuality) {
        3    -> customNodeCount.toInt()
        else -> qualityOptions[selectedQuality].second
    }

    val predefinedRooms = listOf(
        "Living Room", "Master Bedroom", "Bedroom 2", "Kitchen",
        "Dining Room", "Bathroom", "Balcony", "Office", "Garage"
    )

    val coroutineScope = rememberCoroutineScope()
    val snackbarState  = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = { Text("Room Setup", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Property header ───────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Target Property",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(propertyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
            }

            // ── Room Name ─────────────────────────────────────────────────────
            OutlinedTextField(
                value     = roomName,
                onValueChange = {
                    if (it.length <= 50) {
                        roomName      = it
                        roomNameError = null
                    }
                },
                label         = { Text("Room Name") },
                leadingIcon   = { Icon(Icons.Default.MeetingRoom, null) },
                isError       = roomNameError != null,
                supportingText = {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(roomNameError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                        Text("${roomName.length}/50",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                },
                placeholder = { Text("e.g. Living Room") },
                singleLine  = true,
                modifier    = Modifier.fillMaxWidth(),
                shape       = MaterialTheme.shapes.medium
            )

            // ── Quick suggestion chips ────────────────────────────────────────
            Text("Quick suggestions:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                predefinedRooms.forEach { room ->
                    FilterChip(
                        selected = roomName == room,
                        onClick  = { roomName = room; roomNameError = null },
                        label    = { Text(room, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // ── Capture Quality ────────────────────────────────────────────────
            Text("Capture Quality",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Text("More frames = higher quality, but takes longer to capture and stitch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                qualityOptions.forEachIndexed { idx, (label, _, _) ->
                    FilterChip(
                        selected = selectedQuality == idx,
                        onClick  = { selectedQuality = idx },
                        label    = { Text(label) }
                    )
                }
            }

            // Quality description
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                )
            ) {
                Text(
                    text     = qualityOptions[selectedQuality].third,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.padding(12.dp)
                )
            }

            // Custom slider
            if (selectedQuality == 3) {
                Column {
                    Text("Custom: ${customNodeCount.toInt()} frames",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground)
                    Slider(
                        value         = customNodeCount,
                        onValueChange = { customNodeCount = it },
                        valueRange    = 12f..36f,
                        steps         = 23,  // 12 to 36 = 24 positions, 23 steps
                        colors = SliderDefaults.colors(
                            thumbColor       = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text("12 (min)", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                        Text("36 (max)", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Start button ──────────────────────────────────────────────────
            Button(
                onClick = {
                    val trimmed = roomName.trim()
                    if (trimmed.isBlank()) {
                        roomNameError = "Room name is required"
                        return@Button
                    }
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val session = sessionRepository.createSession(propertyId, trimmed)
                            isLoading = false
                            onStartCapture(session.id, effectiveNodeCount)
                        } catch (e: Exception) {
                            isLoading    = false
                            errorMessage = "Failed to create session: ${e.message}"
                            snackbarState.showSnackbar(errorMessage ?: "Error")
                        }
                    }
                },
                enabled  = !isLoading && roomName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = MaterialTheme.shapes.medium
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Start 360° Capture  ·  $effectiveNodeCount frames",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
