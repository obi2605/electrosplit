package com.example.electrosplitapp.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.electrosplitapp.BillService
import com.example.electrosplitapp.VisionService

@Composable
fun HomeScreen(
    visionService: VisionService,
    billService: BillService,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Text(
            text = "Your Current Bill",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Placeholder for bill information
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Total Amount: ₹XXX")
                Text("Units Consumed: XXX")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Add your meter reading options here
        Button(
            onClick = { /* Open camera */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan Meter")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { /* Open gallery */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select from Gallery")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { /* Manual entry */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enter Reading Manually")
        }
    }
}