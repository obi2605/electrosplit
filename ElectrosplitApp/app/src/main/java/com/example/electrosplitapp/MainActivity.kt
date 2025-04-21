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
    private lateinit var authService: AuthService
    private lateinit var authManager: AuthManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize services
        visionService = VisionService(applicationContext)
        billService = createBillService()
        authService = createAuthService()
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
                    authService = authService,
                    authManager = authManager,
                    paymentHistoryService = createRetrofit().create(PaymentHistoryService::class.java)

                )
            }
        }
    }

    private fun createBillService(): BillService {
        return createRetrofit().create(BillService::class.java)
    }

    private fun createAuthService(): AuthService {
        return createRetrofit().create(AuthService::class.java)
    }

    private fun createRetrofit(): Retrofit {
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
    }

    override fun onDestroy() {
        visionService.shutdown()
        super.onDestroy()
    }
}