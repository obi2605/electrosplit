package com.example.electrosplitapp.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.electrosplitapp.R
import com.example.electrosplitapp.navigation.Screen

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        Screen.bottomNavItems.forEach { screen ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = getIconResource(screen)),
                        contentDescription = screen.route
                    )
                },
                label = { Text(text = getLabel(screen)) },
                selected = currentRoute == screen.route,
                onClick = {
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

private fun getIconResource(screen: Screen): Int {
    return when (screen) {
        Screen.Home -> R.drawable.ic_home
        Screen.History -> R.drawable.ic_history
        Screen.Prediction -> R.drawable.ic_prediction
        else -> R.drawable.ic_home
    }
}

private fun getLabel(screen: Screen): String {
    return when (screen) {
        Screen.Home -> "Home"
        Screen.History -> "History"
        Screen.Prediction -> "Prediction"
        else -> ""
    }
}