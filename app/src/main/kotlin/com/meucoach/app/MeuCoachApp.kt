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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meucoach.app.ui.activities.ActivitiesScreen
import com.meucoach.app.ui.activitydetail.ActivityDetailScreen
import com.meucoach.app.ui.calibration.CalibrationScreen
import com.meucoach.app.ui.onboarding.OnboardingScreen
import com.meucoach.app.ui.placeholder.PlaceholderScreen
import com.meucoach.app.ui.profile.ProfileGateViewModel
import com.meucoach.app.ui.profile.ProfileScreen
import com.meucoach.app.ui.today.TodayScreen
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
private const val CALIBRATION_ROUTE = "profile/calibration"

private fun activityDetailRoute(activityId: String): String = "activity/${Uri.encode(activityId)}"

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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Today.route
    val showBottomBar = currentRoute != ACTIVITY_DETAIL_ROUTE &&
        currentRoute != PROFILE_EDIT_ROUTE &&
        currentRoute != CALIBRATION_ROUTE
    val context = LocalContext.current
    val showDemoMessage: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                TodayScreen(onDemoAction = showDemoMessage)
            }
            composable(AppDestination.Activities.route) {
                ActivitiesScreen(
                    onActivityClick = { activityId ->
                        navController.navigate(activityDetailRoute(activityId))
                    },
                )
            }
            composable(ACTIVITY_DETAIL_ROUTE) {
                ActivityDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(AppDestination.Trends.route) {
                PlaceholderScreen(
                    eyebrow = "EVOLUÇÃO",
                    title = "Tendências",
                    description = "Carga, sono, HRV e pace@FC vão aparecer aqui quando houver dados suficientes.",
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    emptyTitle = "Sem dados suficientes",
                )
            }
            composable(AppDestination.Profile.route) {
                ProfileScreen(
                    onEdit = { navController.navigate(PROFILE_EDIT_ROUTE) },
                    onCalibrate = { navController.navigate(CALIBRATION_ROUTE) },
                )
            }
            composable(PROFILE_EDIT_ROUTE) {
                OnboardingScreen(
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(CALIBRATION_ROUTE) {
                CalibrationScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }
    }
}
