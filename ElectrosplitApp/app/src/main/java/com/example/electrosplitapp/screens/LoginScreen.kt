package com.example.electrosplitapp.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.electrosplitapp.BillRequest
import com.example.electrosplitapp.BillService
import com.example.electrosplitapp.data.AuthManager
import kotlinx.coroutines.launch
import retrofit2.awaitResponse

@Composable
fun LoginScreen(
    billService: BillService,
    authManager: AuthManager,
    onLoginSuccess: () -> Unit
) {
    var consumerNumber by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Electrosplit",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Input fields with error states
        OutlinedTextField(
            value = consumerNumber,
            onValueChange = { consumerNumber = it },
            label = { Text("Consumer Number") },
            isError = errorMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = operator,
            onValueChange = { operator = it },
            label = { Text("Operator") },
            isError = errorMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = accountName,
            onValueChange = { accountName = it },
            label = { Text("Account Name (Optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage.isNotBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (consumerNumber.isBlank() || operator.isBlank()) {
                    errorMessage = "Please fill all required fields"
                    return@Button
                }

                isLoading = true
                coroutineScope.launch {
                    try {
                        val response = billService.fetchBill(
                            BillRequest(consumerNumber, operator)
                        ).awaitResponse()

                        if (response.isSuccessful) {
                            authManager.saveLoginDetails(
                                consumerNumber,
                                operator,
                                accountName.ifBlank { "My Account" }
                            )
                            onLoginSuccess()
                        } else {
                            errorMessage = "Invalid credentials or service error"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Network error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text("Login")
            }
        }
    }
}