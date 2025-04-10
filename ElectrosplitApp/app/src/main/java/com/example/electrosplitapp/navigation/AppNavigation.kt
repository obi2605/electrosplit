package com.example.electrosplitapp.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.electrosplitapp.BillService
import com.example.electrosplitapp.VisionService
import com.example.electrosplitapp.components.BottomNavigationBar
import com.example.electrosplitapp.screens.HistoryScreen
import com.example.electrosplitapp.screens.HomeScreen
import com.example.electrosplitapp.screens.LoginScreen
import com.example.electrosplitapp.screens.PredictionScreen

@Composable
fun AppNavigation(
    visionService: VisionService,
    billService: BillService
) {
    val navController = rememberNavController()
    var isLoggedIn by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (isLoggedIn && currentRoute(navController) != Screen.Login.route) {
                BottomNavigationBar(navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isLoggedIn) Screen.Home.route else Screen.Login.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    billService = billService,
                    onLoginSuccess = {
                        isLoggedIn = true
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    visionService = visionService,
                    billService = billService,
                    onLogout = {
                        isLoggedIn = false
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.History.route) {
                HistoryScreen()
            }
            composable(Screen.Prediction.route) {
                PredictionScreen()
            }
        }
    }
}

@Composable
private fun currentRoute(navController: NavController): String? {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    return navBackStackEntry?.destination?.route
}

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object History : Screen("history")
    object Prediction : Screen("prediction")

    companion object {
        val bottomNavItems = listOf(Home, History, Prediction)
    }
}