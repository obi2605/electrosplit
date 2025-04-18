package com.example.electrosplitapp.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.electrosplitapp.AuthService
import com.example.electrosplitapp.BillService
import com.example.electrosplitapp.UserRequest
import com.example.electrosplitapp.data.AuthManager
import com.example.electrosplitapp.viewmodels.GroupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    authService: AuthService,
    billService: BillService,
    authManager: AuthManager,
    groupViewModel: GroupViewModel,
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
                    errorMessage = "Please enter phone number and password"
                    return@Button
                }

                isLoading = true
                errorMessage = ""

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val response = authService.login(
                            UserRequest(phoneNumber, password, consumerNumber = "", operator = "")
                        ).execute()

                        withContext(Dispatchers.Main) {
                            if (response.isSuccessful) {
                                response.body()?.let { authResponse ->
                                    if (authResponse.success) {
                                        val userName = authResponse.name ?: ""
                                        authManager.saveLoginDetails(
                                            userId = authResponse.userId.toString(),
                                            phoneNumber = phoneNumber,
                                            name = userName,
                                            consumerNumber = authResponse.consumerNumber ?: "",
                                            operator = authResponse.operator ?: ""
                                        )

                                        Log.d("LoginScreen", "Calling getGroupForUser with phone: $phoneNumber")

                                        val groupResponse = withContext(Dispatchers.IO) {
                                            try {
                                                val call = billService.getGroupForUser(phoneNumber)
                                                val apiResponse = call.execute()
                                                if (apiResponse.isSuccessful) {
                                                    Log.d("LoginScreen", "✅ API success, code: ${apiResponse.code()}")
                                                    apiResponse.body()
                                                } else {
                                                    Log.e("LoginScreen", "❌ API failed, code: ${apiResponse.code()}")
                                                    null
                                                }
                                            } catch (e: Exception) {
                                                Log.e("LoginScreen", "🔥 API exception: ${e.message}", e)
                                                null
                                            }
                                        }

                                        Log.d("LoginScreen", "Group response from API: $groupResponse")

                                        groupResponse?.let { group ->
                                            Log.d("LoginScreen", "Saving group: ID=${group.groupId}, code=${group.groupCode}")
                                            authManager.saveGroupDetails(
                                                groupId = group.groupId,
                                                groupName = group.groupName,
                                                groupCode = group.groupCode,
                                                groupQr = group.groupQr,
                                                isCreator = group.creatorPhone == phoneNumber
                                            )
                                        }

                                        groupViewModel.restoreGroupIfExists()
                                        onLoginSuccess()
                                    } else {
                                        errorMessage = authResponse.message ?: "Login failed"
                                    }
                                } ?: run {
                                    errorMessage = "Unknown error occurred"
                                }
                            } else {
                                errorMessage = "Login failed: ${response.code()}"
                            }
                            isLoading = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Network error: ${e.message ?: "Unknown error"}"
                            isLoading = false
                            Log.e("LoginScreen", "Login failed", e)
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
                Text("Login")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onRegisterClick,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Don't have an account? Register")
        }
    }
}
