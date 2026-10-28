package com.meucoach.app

import android.widget.Toast
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.meucoach.app.ui.placeholder.PlaceholderScreen
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

@Composable
fun MeuCoachApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Today.route
    val context = LocalContext.current
    val showDemoMessage: (String) -> Unit = { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = McTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
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
                PlaceholderScreen(
                    eyebrow = "ATIVIDADES",
                    title = "Suas corridas",
                    description = "Importe FIT, TCX ou GPX para começar. O detalhe da atividade com splits, " +
                        "zonas e mapa chega na Fase 0.",
                    icon = Icons.AutoMirrored.Outlined.DirectionsRun,
                    emptyTitle = "Nenhuma corrida importada",
                    actionLabel = "Importar arquivo",
                    onAction = { showDemoMessage("Importação chega na Fase 0.") },
                )
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
                PlaceholderScreen(
                    eyebrow = "PERFIL",
                    title = "Seu perfil",
                    description = "Calibração, fontes de dados e preferências ficam aqui. Nenhuma conta é necessária.",
                    icon = Icons.Outlined.Person,
                    emptyTitle = "Perfil ainda não configurado",
                    actionLabel = "Configurar perfil",
                    onAction = { showDemoMessage("Perfil chega na Fase 1.") },
                )
            }
        }
    }
}
