package com.example.electrosplitapp

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.example.electrosplitapp.navigation.AppNavigation
import com.example.electrosplitapp.ui.theme.ElectrosplitAppTheme

@Composable
fun MainApp(visionService: VisionService, billService: BillService) {
    ElectrosplitAppTheme {
        Surface(color = Color.White) {
            AppNavigation(visionService, billService)
        }
    }
}