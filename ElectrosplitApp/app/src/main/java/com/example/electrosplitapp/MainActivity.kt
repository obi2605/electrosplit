package com.example.electrosplitapp
import com.example.electrosplitapp.ui.theme.ElectrosplitAppTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    private val billService by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/") // Use 10.0.2.2 for Android emulator to connect to localhost
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BillService::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ElectrosplitAppTheme {
                ElectrosplitApp(billService)
            }
        }
    }
}

@Composable
fun ElectrosplitApp(billService: BillService) {
    var consumerNumber by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = consumerNumber,
            onValueChange = { consumerNumber = it },
            label = { Text("Consumer Number") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = operator,
            onValueChange = { operator = it },
            label = { Text("Operator") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val request = BillRequest(consumerNumber, operator)
                billService.fetchBill(request).enqueue(object : Callback<BillResponse> {
                    override fun onResponse(call: Call<BillResponse>, response: Response<BillResponse>) {
                        if (response.isSuccessful) {
                            val bill = response.body()
                            result = "Total Units: ${bill?.totalUnits}, Total Amount: ₹${bill?.totalAmount}"
                        } else {
                            result = "Bill not found"
                        }
                    }

                    override fun onFailure(call: Call<BillResponse>, t: Throwable) {
                        result = "Error: ${t.message}"
                    }
                })
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Fetch Bill")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(result)
    }
}