package com.ethred.panorama.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomSetupScreen(
    propertyId: String,
    propertyTitle: String,
    sessionRepository: CaptureSessionRepository,
    onNavigateBack: () -> Unit,
    onStartCapture: (sessionId: String) -> Unit
) {
    var roomName by remember { mutableStateOf("Living Room") }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val predefinedRooms = listOf("Living Room", "Master Bedroom", "Kitchen", "Dining Room", "Bathroom", "Balcony", "Office")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room Setup", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Target Property", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Text(propertyTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Enter Room Name", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    leadingIcon = { Icon(Icons.Default.MeetingRoom, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Quick Suggestions:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    predefinedRooms.forEach { room ->
                        FilterChip(
                            selected = roomName == room,
                            onClick = { roomName = room },
                            label = { Text(room) }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (roomName.isBlank()) return@Button
                    isLoading = true
                    coroutineScope.launch {
                        val session = sessionRepository.createSession(propertyId, roomName)
                        isLoading = false
                        onStartCapture(session.id)
                    }
                },
                enabled = !isLoading && roomName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Start 360° Capture", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
