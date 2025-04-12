package com.example.electrosplitapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.electrosplitapp.data.AuthManager
import com.example.electrosplitapp.ui.theme.ElectrosplitAppTheme
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    private lateinit var visionService: VisionService
    private lateinit var billService: BillService
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize services
        visionService = VisionService(applicationContext)
        billService = createBillService()
        authManager = AuthManager(applicationContext)

        setContent {
            AppContent()
        }
    }

    @Composable
    private fun AppContent() {
        ElectrosplitAppTheme {
            Surface(color = Color.White) {
                MainApp(
                    visionService = visionService,
                    billService = billService,
                    authManager = authManager
                )
            }
        }
    }

    private fun createBillService(): BillService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl("http://192.168.1.2:8080/") // Verify this IP!
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BillService::class.java)
    }
    override fun onDestroy() {
        visionService.shutdown()
        super.onDestroy()
    }
}