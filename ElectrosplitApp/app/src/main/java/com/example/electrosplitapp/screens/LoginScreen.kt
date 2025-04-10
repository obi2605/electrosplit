package com.example.electrosplitapp.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.electrosplitapp.BillService

@Composable
fun LoginScreen(
    billService: BillService,
    onLoginSuccess: () -> Unit
) {
    var consumerNumber by remember { mutableStateOf("") }
    var operator by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to Electrosplit",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = consumerNumber,
            onValueChange = { consumerNumber = it },
            label = { Text("Consumer Number") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = Color.Black)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = operator,
            onValueChange = { operator = it },
            label = { Text("Operator") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = Color.Black)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isLoading = true
                // Here you would call your bill service and save the login state
                // For now we'll just simulate a successful login
                onLoginSuccess()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && consumerNumber.isNotBlank() && operator.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text("Login")
            }
        }

        if (result.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = result)
        }
    }
}