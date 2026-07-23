package com.ethred.panorama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.work.WorkManager
import com.ethred.panorama.data.repository.AuthRepository
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.data.repository.UploadQueueRepository
import com.ethred.panorama.domain.usecase.GenerateTourManifestUseCase
import com.ethred.panorama.ui.navigation.AppNavGraph
import com.ethred.panorama.ui.theme.Ethred360Theme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var sessionRepository: CaptureSessionRepository
    @Inject lateinit var uploadQueueRepository: UploadQueueRepository
    @Inject lateinit var generateTourManifestUseCase: GenerateTourManifestUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val workManager = WorkManager.getInstance(applicationContext)

        setContent {
            Ethred360Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(
                        authRepository = authRepository,
                        sessionRepository = sessionRepository,
                        uploadQueueRepository = uploadQueueRepository,
                        generateTourManifestUseCase = generateTourManifestUseCase,
                        workManager = workManager
                    )
                }
            }
        }
    }
}
