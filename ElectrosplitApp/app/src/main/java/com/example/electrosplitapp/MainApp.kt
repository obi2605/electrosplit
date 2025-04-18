package com.example.electrosplitapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.electrosplitapp.data.AuthManager
import com.example.electrosplitapp.navigation.AppNavigation
import com.example.electrosplitapp.ui.theme.ElectrosplitAppTheme

@Composable
fun MainApp(
    visionService: VisionService,
    billService: BillService,
    authService: AuthService,  // Added this parameter
    authManager: AuthManager,
    paymentHistoryService: PaymentHistoryService // <-- Add this

) {
    ElectrosplitAppTheme {
        MaterialTheme {
            Surface(color = Color.White) {
                AppNavigation(
                    visionService = visionService,
                    billService = billService,
                    authService = authService,  // Passing it down
                    authManager = authManager,
                    paymentHistoryService = paymentHistoryService
                )
            }
        }
    }
}