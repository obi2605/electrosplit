package com.example.electrosplitapp.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.electrosplitapp.AuthService
import com.example.electrosplitapp.UserRequest
import com.example.electrosplitapp.data.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RegisterScreen(
    authService: AuthService,
    authManager: AuthManager,
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
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
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (phoneNumber.isBlank() || password.isBlank()) {
                    errorMessage = "Phone and password are required"
                    return@Button
                }

                isLoading = true
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val response = authService.register(
                            UserRequest(
                                phoneNumber = phoneNumber,
                                password = password,
                                name = name
                            )
                        ).execute()

                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful) {
                                response.body()?.let { authResponse ->
                                    if (authResponse.success) {
                                        authManager.saveLoginDetails(
                                            userId = authResponse.userId.toString(),
                                            phoneNumber = phoneNumber,
                                            name = name
                                        )
                                        onRegisterSuccess()
                                    } else {
                                        errorMessage = authResponse.message ?: "Registration failed"
                                    }
                                } ?: run {
                                    errorMessage = "Unknown error occurred"
                                }
                            } else {
                                errorMessage = "Registration failed: ${response.code()}"
                            }
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Network error: ${e.message ?: "Unknown error"}"
                            isLoading = false
                            Log.e("RegisterScreen", "Registration failed", e)
                        }
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
                Text("Register")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Already have an account? Login")
        }
    }
}
