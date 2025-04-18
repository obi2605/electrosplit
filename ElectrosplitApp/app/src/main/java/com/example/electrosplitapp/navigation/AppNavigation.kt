package com.example.electrosplitapp.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.electrosplitapp.AuthService
import com.example.electrosplitapp.BillService
import com.example.electrosplitapp.VisionService
import com.example.electrosplitapp.components.BottomNavigationBar
import com.example.electrosplitapp.data.AuthManager
import com.example.electrosplitapp.screens.HistoryScreen
import com.example.electrosplitapp.screens.HomeScreen
import com.example.electrosplitapp.screens.LoginScreen
import com.example.electrosplitapp.screens.PredictionScreen
import com.example.electrosplitapp.screens.RegisterScreen
import com.example.electrosplitapp.viewmodels.BillViewModel
import com.example.electrosplitapp.viewmodels.GroupViewModel

@Composable
fun AppNavigation(
    visionService: VisionService,
    billService: BillService,
    authService: AuthService,
    authManager: AuthManager
) {
    val navController = rememberNavController()
    val groupViewModel: GroupViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return GroupViewModel(billService, authManager) as T
            }
        }
    )
    var isLoggedIn by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authManager.isLoggedIn.collect { loggedIn ->
            isLoggedIn = loggedIn
        }
    }

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
                    authService = authService,
                    billService = billService, // ✅ Add this
                    authManager = authManager,
                    groupViewModel = groupViewModel, // ✅ Add this
                    onLoginSuccess = {
                        isLoggedIn = true
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onRegisterClick = {
                        navController.navigate(Screen.Register.route)
                    }
                )
            }

            composable(Screen.Register.route) {
                RegisterScreen(
                    authService = authService,
                    authManager = authManager,
                    onRegisterSuccess = {
                        isLoggedIn = true
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Home.route) {
                val billViewModel: BillViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return BillViewModel(billService, authManager) as T
                        }
                    }
                )

                HomeScreen(
                    visionService = visionService,
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    billViewModel = billViewModel,
                    groupViewModel = groupViewModel
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
    object Register : Screen("register")
    object Home : Screen("home")
    object History : Screen("history")
    object Prediction : Screen("prediction")

    companion object {
        val bottomNavItems = listOf(Home, History, Prediction)
    }
}
