package com.example.electrosplitapp.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun GroupDrawerContent(
    groupName: String,
    groupCode: String,
    groupQr: String,
    isCreator: Boolean,
    onEditGroup: (String) -> Unit,
    onLeaveGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onLogout: () -> Unit, // 👈 new
    onDismiss: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(groupName) }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount > 20) onDismiss()
                }
            }
            .verticalScroll(rememberScrollState()),
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Column {
            Text("Group Options", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            val qrBitmap = try {
                val decoded = Base64.decode(groupQr, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decoded, 0, decoded.size).asImageBitmap()
            } catch (e: Exception) {
                null
            }

            qrBitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = "Group QR",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(180.dp)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "Scan this QR to join",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Code: $groupCode", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(groupName, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Group Name")
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isCreator) {
                TextButton(
                    onClick = {
                        onDeleteGroup()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Group", tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Group", color = MaterialTheme.colorScheme.error)
                }
            }

            TextButton(
                onClick = {
                    onLeaveGroup()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Leave Group", tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Leave Group", color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))

            // 👇 ADD THIS LOGOUT OPTION
            TextButton(
                onClick = {
                    onLogout()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Logout", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showEditDialog) {
        Dialog(onDismissRequest = { showEditDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Edit Group Name", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Group Name") }
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showEditDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            onEditGroup(newName)
                            showEditDialog = false
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
