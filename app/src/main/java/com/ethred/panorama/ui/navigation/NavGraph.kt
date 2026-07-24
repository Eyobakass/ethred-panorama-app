package com.ethred.panorama.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.ethred.panorama.data.repository.AuthRepository
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.data.repository.UploadQueueRepository
import com.ethred.panorama.domain.usecase.GenerateTourManifestUseCase
import com.ethred.panorama.ui.auth.LoginScreen
import com.ethred.panorama.ui.capture.CaptureScreen
import com.ethred.panorama.ui.capture.CaptureViewModel
import com.ethred.panorama.ui.dashboard.PropertyDashboardScreen
import com.ethred.panorama.ui.onboarding.OnboardingScreen
import com.ethred.panorama.ui.preview.PreviewScreen
import com.ethred.panorama.ui.setup.RoomSetupScreen
import com.ethred.panorama.ui.stitching.StitchingProgressScreen
import com.ethred.panorama.ui.tour.TourEditorScreen
import com.ethred.panorama.ui.upload.UploadStatusScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Login      : Screen("login")
    object Dashboard  : Screen("dashboard")

    object RoomSetup : Screen("room_setup/{propertyId}/{propertyTitle}/{nodeCount}") {
        fun createRoute(propertyId: String, propertyTitle: String, nodeCount: Int = 28) =
            "room_setup/${Uri.encode(propertyId)}/${Uri.encode(propertyTitle)}/$nodeCount"
    }

    object Capture : Screen("capture/{sessionId}/{nodeCount}") {
        fun createRoute(sessionId: String, nodeCount: Int = 28) = "capture/$sessionId/$nodeCount"
    }

    object Stitching : Screen("stitching/{sessionId}?nadirOption={nadirOption}") {
        fun createRoute(sessionId: String, nadirOption: Int = 0) =
            "stitching/$sessionId?nadirOption=$nadirOption"
    }

    object Preview : Screen("preview/{sessionId}") {
        fun createRoute(sessionId: String) = "preview/$sessionId"
    }

    object TourEditor : Screen("tour_editor/{propertyId}") {
        fun createRoute(propertyId: String) = "tour_editor/$propertyId"
    }

    object UploadStatus : Screen("upload_status/{propertyId}") {
        fun createRoute(propertyId: String) = "upload_status/$propertyId"
    }
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    authRepository: AuthRepository,
    sessionRepository: CaptureSessionRepository,
    uploadQueueRepository: UploadQueueRepository,
    generateTourManifestUseCase: GenerateTourManifestUseCase,
    workManager: WorkManager
) {
    // Determine start destination: onboarding → login → dashboard
    val isLoggedIn by produceState(initialValue = false) {
        value = authRepository.isLoggedIn()
    }
    val startDestination = if (isLoggedIn) Screen.Dashboard.route else Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                authRepository = authRepository,
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            PropertyDashboardScreen(
                authRepository = authRepository,
                onSelectProperty = { propId, propTitle ->
                    navController.navigate(
                        Screen.RoomSetup.createRoute(propId, propTitle, 28)
                    )
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.RoomSetup.route) { backStackEntry ->
            val propertyId    = Uri.decode(backStackEntry.arguments?.getString("propertyId") ?: "")
            val propertyTitle = Uri.decode(backStackEntry.arguments?.getString("propertyTitle") ?: "")
            val nodeCount     = backStackEntry.arguments?.getString("nodeCount")?.toIntOrNull() ?: 28
            RoomSetupScreen(
                propertyId    = propertyId,
                propertyTitle = propertyTitle,
                sessionRepository = sessionRepository,
                onNavigateBack = { navController.popBackStack() },
                onStartCapture = { sessionId, chosenNodeCount ->
                    navController.navigate(Screen.Capture.createRoute(sessionId, chosenNodeCount)) {
                        popUpTo(Screen.RoomSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Capture.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: run {
                navController.popBackStack()
                return@composable
            }
            val nodeCount = backStackEntry.arguments?.getString("nodeCount")?.toIntOrNull() ?: 28
            val captureViewModel: CaptureViewModel = hiltViewModel()
            CaptureScreen(
                sessionId  = sessionId,
                nodeCount  = nodeCount,
                viewModel  = captureViewModel,
                onFinishCapture = {
                    navController.navigate(Screen.Stitching.createRoute(sessionId)) {
                        popUpTo(Screen.Capture.route) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Stitching.route) { backStackEntry ->
            val sessionId   = backStackEntry.arguments?.getString("sessionId") ?: ""
            val nadirOption = backStackEntry.arguments?.getString("nadirOption")?.toIntOrNull() ?: 0
            StitchingProgressScreen(
                sessionId      = sessionId,
                nadirOption    = nadirOption,
                workManager    = workManager,
                sessionRepository = sessionRepository,
                onStitchingComplete = {
                    navController.navigate(Screen.Preview.createRoute(sessionId)) {
                        popUpTo(Screen.Stitching.route) { inclusive = true }
                    }
                },
                onStitchingFailed = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Preview.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            PreviewScreen(
                sessionId          = sessionId,
                sessionRepository  = sessionRepository,
                uploadQueueRepository = uploadQueueRepository,
                onRetake           = { nadirOption ->
                    navController.navigate(Screen.Stitching.createRoute(sessionId, nadirOption))
                },
                onAddAnotherRoom   = {
                    navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                },
                onLinkRooms        = { propertyId ->
                    navController.navigate(Screen.TourEditor.createRoute(propertyId))
                },
                onUploadNow        = { propertyId ->
                    navController.navigate(Screen.UploadStatus.createRoute(propertyId))
                }
            )
        }

        composable(Screen.TourEditor.route) { backStackEntry ->
            val propertyId = backStackEntry.arguments?.getString("propertyId") ?: ""
            TourEditorScreen(
                propertyId             = propertyId,
                sessionRepository      = sessionRepository,
                uploadQueueRepository  = uploadQueueRepository,
                generateTourManifestUseCase = generateTourManifestUseCase,
                onNavigateBack         = { navController.popBackStack() },
                onPublishSuccess       = {
                    navController.navigate(Screen.UploadStatus.createRoute(propertyId))
                }
            )
        }

        composable(Screen.UploadStatus.route) { backStackEntry ->
            val propertyId = backStackEntry.arguments?.getString("propertyId") ?: ""
            UploadStatusScreen(
                propertyId            = propertyId,
                workManager           = workManager,
                uploadQueueRepository = uploadQueueRepository,
                onDashboardReturn     = {
                    navController.navigate(Screen.Dashboard.route) {
                        // inclusive=false: preserves dashboard state and scroll position
                        popUpTo(Screen.Dashboard.route) { inclusive = false }
                    }
                }
            )
        }
    }
}
