package com.example.electrosplitapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import com.example.electrosplitapp.ui.theme.ElectrosplitAppTheme
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    private lateinit var visionService: VisionService
    private lateinit var billService: BillService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize services
        visionService = VisionService(applicationContext)
        billService = createBillService()

        setContent {
            ElectrosplitAppTheme {
                Surface(color = Color.White) {
                    MainApp(visionService, billService)
                }
            }
        }
    }

    private fun createBillService(): BillService {
        return Retrofit.Builder()
            .baseUrl("http://192.168.1.2:8080/") // Replace with your actual base URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BillService::class.java)
    }

    override fun onDestroy() {
        visionService.shutdown()
        super.onDestroy()
    }
}