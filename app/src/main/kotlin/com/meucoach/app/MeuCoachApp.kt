package com.meucoach.app

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meucoach.app.reminder.NotificationNavigation
import com.meucoach.app.ui.activities.ActivitiesScreen
import com.meucoach.app.ui.activitydetail.ActivityDetailScreen
import com.meucoach.app.ui.calibration.CalibrationScreen
import com.meucoach.app.ui.checkin.CheckInScreen
import com.meucoach.app.ui.data.DataManagementScreen
import com.meucoach.app.ui.metrics.MetricsImportScreen
import com.meucoach.app.ui.onboarding.OnboardingScreen
import com.meucoach.app.ui.profile.ProfileGateViewModel
import com.meucoach.app.ui.profile.ProfileScreen
import com.meucoach.app.ui.today.TodayRoute
import com.meucoach.app.ui.trends.TrendsScreen
import com.meucoach.app.ui.tutorial.TutorialScreen
import com.meucoach.app.ui.tutorial.TutorialViewModel
import com.meucoach.core.designsystem.component.McBottomBar
import com.meucoach.core.designsystem.component.McBottomBarItem
import com.meucoach.core.designsystem.theme.McTheme

private sealed class AppDestination(val route: String, val label: String, val icon: ImageVector) {
    data object Today : AppDestination("today", "Hoje", Icons.Outlined.WbSunny)
    data object Activities : AppDestination(
        "activities",
        "Corridas",
        Icons.AutoMirrored.Outlined.DirectionsRun,
    )
    data object Trends : AppDestination("trends", "Tendências", Icons.AutoMirrored.Outlined.ShowChart)
    data object Profile : AppDestination("profile", "Perfil", Icons.Outlined.Person)
}

private val destinations = listOf(
    AppDestination.Today,
    AppDestination.Activities,
    AppDestination.Trends,
    AppDestination.Profile,
)

private const val ACTIVITY_DETAIL_ROUTE = "activity/{activityId}"
private const val PROFILE_EDIT_ROUTE = "profile/edit"
private const val CALIBRATION_ROUTE = "profile/calibration?activityId={activityId}"
private const val CALIBRATION_BASE_ROUTE = "profile/calibration"
private const val CALIBRATION_ACTIVITY_ARG = "activityId"
private const val METRICS_IMPORT_ROUTE = "profile/metrics-import"
private const val DATA_MANAGEMENT_ROUTE = "profile/data"
private const val CHECK_IN_ROUTE = "check-in"
private const val TUTORIAL_ROUTE = "tutorial"

private fun activityDetailRoute(activityId: String): String = "activity/${Uri.encode(activityId)}"

private fun calibrationRoute(activityId: String? = null): String = if (activityId == null) {
    CALIBRATION_BASE_ROUTE
} else {
    "$CALIBRATION_BASE_ROUTE?$CALIBRATION_ACTIVITY_ARG=${Uri.encode(activityId)}"
}

@Composable
fun MeuCoachApp(gateViewModel: ProfileGateViewModel = hiltViewModel()) {
    val gate by gateViewModel.uiState.collectAsStateWithLifecycle()
    when {
        gate.isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = McTheme.colors.accent,
                    strokeWidth = 2.dp,
                )
            }
        }

        gate.profile == null -> OnboardingScreen(onDone = {})

        else -> MainApp()
    }
}

@Composable
private fun MainApp() {
    val tutorialViewModel: TutorialViewModel = hiltViewModel()
    val tutorialState by tutorialViewModel.uiState.collectAsStateWithLifecycle()
    if (!tutorialState.seen) {
        TutorialScreen(onDone = {})
    } else {
        MainContent()
    }
}

@Composable
private fun MainContent() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Today.route
    val showBottomBar = currentRoute != ACTIVITY_DETAIL_ROUTE &&
        currentRoute != PROFILE_EDIT_ROUTE &&
        currentRoute != CALIBRATION_ROUTE &&
        currentRoute != METRICS_IMPORT_ROUTE &&
        currentRoute != DATA_MANAGEMENT_ROUTE &&
        currentRoute != CHECK_IN_ROUTE &&
        currentRoute != TUTORIAL_ROUTE
    val context = LocalContext.current
    val showDemoMessage: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val requestedRoute by NotificationNavigation.route.collectAsStateWithLifecycle()
    LaunchedEffect(requestedRoute) {
        val route = requestedRoute ?: return@LaunchedEffect
        navController.navigate(route) { launchSingleTop = true }
        NotificationNavigation.consume()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = McTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                McBottomBar(
                    items = destinations.map { destination ->
                        McBottomBarItem(
                            route = destination.route,
                            label = destination.label,
                            icon = destination.icon,
                        )
                    },
                    selectedRoute = currentRoute,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Today.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(AppDestination.Today.route) {
                TodayRoute(
                    onDemoAction = showDemoMessage,
                    onCheckInClick = { navController.navigate(CHECK_IN_ROUTE) },
                    onTrendsClick = {
                        navController.navigate(AppDestination.Trends.route) { launchSingleTop = true }
                    },
                    onActivityClick = { activityId ->
                        navController.navigate(activityDetailRoute(activityId))
                    },
                )
            }
            composable(CHECK_IN_ROUTE) {
                CheckInScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
            composable(AppDestination.Activities.route) {
                ActivitiesScreen(
                    onActivityClick = { activityId ->
                        navController.navigate(activityDetailRoute(activityId))
                    },
                )
            }
            composable(ACTIVITY_DETAIL_ROUTE) {
                ActivityDetailScreen(
                    onBack = { navController.popBackStack() },
                    onCalibrate = { activityId ->
                        navController.navigate(calibrationRoute(activityId))
                    },
                )
            }
            composable(AppDestination.Trends.route) {
                TrendsScreen()
            }
            composable(AppDestination.Profile.route) {
                ProfileScreen(
                    onEdit = { navController.navigate(PROFILE_EDIT_ROUTE) },
                    onCalibrate = { navController.navigate(calibrationRoute()) },
                    onImportMetrics = { navController.navigate(METRICS_IMPORT_ROUTE) },
                    onManageData = { navController.navigate(DATA_MANAGEMENT_ROUTE) },
                    onShowTutorial = { navController.navigate(TUTORIAL_ROUTE) },
                )
            }
            composable(METRICS_IMPORT_ROUTE) {
                MetricsImportScreen(onBack = { navController.popBackStack() })
            }
            composable(DATA_MANAGEMENT_ROUTE) {
                DataManagementScreen(onBack = { navController.popBackStack() })
            }
            composable(TUTORIAL_ROUTE) {
                TutorialScreen(onDone = { navController.popBackStack() })
            }
            composable(PROFILE_EDIT_ROUTE) {
                OnboardingScreen(
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(
                route = CALIBRATION_ROUTE,
                arguments = listOf(
                    navArgument(CALIBRATION_ACTIVITY_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                CalibrationScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }
    }
}
