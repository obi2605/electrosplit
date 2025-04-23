package com.example.electrosplitapp.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.example.electrosplitapp.*
import com.example.electrosplitapp.components.GroupDrawerContent
import com.example.electrosplitapp.components.PieChart
import com.example.electrosplitapp.utils.BillCalculator
import com.example.electrosplitapp.viewmodels.BillViewModel
import com.example.electrosplitapp.viewmodels.GroupViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.NavController
import com.example.electrosplitapp.viewmodels.HistoryViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    visionService: VisionService,
    onLogout: () -> Unit,
    billViewModel: BillViewModel,
    groupViewModel: GroupViewModel,
    historyViewModel: HistoryViewModel
) {
    val context = LocalContext.current

    var showCamera by remember { mutableStateOf(false) }
    var manualReading by remember { mutableStateOf("") }
    var showManualDialog by remember { mutableStateOf(false) }
    var showBreakdownDialog by remember { mutableStateOf(false) }
    var selectedBreakdown by remember { mutableStateOf<BillCalculator.MemberBill?>(null) }

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                processImageFromUri(context, visionService, it) { reading ->
                    manualReading = reading
                    showManualDialog = true
                }
            }
        }
    )

    val billResponse by billViewModel.billResponse.collectAsState(initial = null)
    val billLoading by billViewModel.isLoading.collectAsState(initial = false)
    val billErrorMessage by billViewModel.errorMessage.collectAsState(initial = null)

    val groupDetails by groupViewModel.groupDetails.collectAsState(initial = null)
    val currentGroupIdState = groupViewModel.currentGroupId.collectAsState(initial = null)
    val currentGroupId = currentGroupIdState.value
    val isGroupCreator by groupViewModel.isGroupCreator.collectAsState(initial = false)
    val groupLoading by groupViewModel.isLoading.collectAsState(initial = false)
    val groupRestored by groupViewModel.groupRestored.collectAsState(initial = false)
    val isAnyMemberPaid = groupDetails?.members?.any { it.paymentStatus == "Paid" } == true


    val adjustedReadings = groupDetails?.members?.mapNotNull { member ->
        val reading = member.reading ?: return@mapNotNull null
        val offset = when (member.paymentStatus) {
            "Paid" -> member.previousOffsetValue ?: 0f
            else -> member.offsetValue ?: 0f
        }
        reading - offset
    }

    val calculatedResult = remember(adjustedReadings, billResponse) {
        val bill = billResponse
        if (!adjustedReadings.isNullOrEmpty() && bill != null) {
            BillCalculator.calculateSplit(
                totalBillAmount = bill.totalAmount.toFloat(),
                totalUnits = bill.totalUnits.toFloat(),
                individualReadings = adjustedReadings,
                groupSize = adjustedReadings.size
            )
        } else null
    }


    val calculatedBills = remember(calculatedResult, groupDetails) {
        groupDetails?.members?.mapIndexedNotNull { index, member ->
            val bill = calculatedResult?.individualBills?.getOrNull(index)
            if (bill != null) member.name to bill else null
        }?.toMap()
    }

    LaunchedEffect(currentGroupId) {
        currentGroupId?.toIntOrNull()?.let { groupId ->
            groupViewModel.fetchGroupDetails(groupId)
        }
    }
    LaunchedEffect(groupDetails?.consumerNumber, groupDetails?.operator) {
        val consumer = groupDetails?.consumerNumber
        val operator = groupDetails?.operator
        if (!consumer.isNullOrBlank() && !operator.isNullOrBlank()) {
            billViewModel.fetchBill(consumer, operator)
        }
    }



    if (!groupRestored) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Electrosplit") },
                    actions = {
                        if (currentGroupId != null) {
                            IconButton(onClick = { showDrawer = true }) {
                                Icon(Icons.Default.Menu, contentDescription = "Group menu")
                            }
                        } else {
                            IconButton(onClick = {
                                billViewModel.viewModelScope.launch {
                                    billViewModel.logout()
                                    onLogout()
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier
                .padding(padding)
                .fillMaxSize()
            ) {
                if (currentGroupId == null) {
                    GroupOptionsScreen(
                        onCreateGroupClick = { showCreateGroupDialog = true },
                        onJoinGroupClick = { showJoinGroupDialog = true },
                        onJoinWithQrClick = { showQrScanner = true }
                    )
                } else {
                    val swipeState = rememberSwipeRefreshState(isRefreshing = groupLoading)
                    SwipeRefresh(
                        state = swipeState,
                        onRefresh = {
                            val consumer = groupDetails?.consumerNumber
                            val operator = groupDetails?.operator

                            if (!consumer.isNullOrBlank() && !operator.isNullOrBlank()) {
                                groupViewModel.updateGroupBill(consumer, operator) {
                                    billViewModel.fetchBill(consumer, operator)
                                }
                            }
                        }

                        ,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(groupDetails?.groupName ?: "Group", style = MaterialTheme.typography.headlineSmall)
                                        Text("Created by ${groupDetails?.creatorName ?: "Creator"}")

                                        Spacer(Modifier.height(8.dp))

                                        when {
                                            billLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Loading bill data...")
                                            }
                                            billErrorMessage != null -> Text("Error: $billErrorMessage", color = MaterialTheme.colorScheme.error)
                                            billResponse != null -> {
                                                Text("Total Units: ${billResponse!!.totalUnits} kWh")
                                                Text("Total Amount: ₹${"%.2f".format(billResponse!!.totalAmount)}")
                                            }
                                            else -> Text("No bill data available", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }

                            groupDetails?.members?.let { members ->
                                itemsIndexed(
                                    items = members,
                                    key = { _, member -> member.phone + (calculatedBills?.get(member.name)?.amountToPay ?: "") }
                                ) { _, member ->
                                    val memberBill = calculatedBills?.get(member.name)
                                    val isCurrentUser =
                                        groupViewModel.phoneNumber.collectAsState(initial = "").value == member.phone

                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        member.name,
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    val adjustedReading = member.reading?.let { r ->
                                                        val offset = when (member.paymentStatus) {
                                                            "Paid" -> member.previousOffsetValue
                                                                ?: 0f

                                                            else -> member.offsetValue ?: 0f
                                                        }
                                                        (r - offset).coerceAtLeast(0f) // ✅ Clamp to zero
                                                    }
                                                    Text(
                                                        when {
                                                            member.reading == null -> "No reading submitted"
                                                            else -> "Reading: ${
                                                                "%.2f".format(
                                                                    adjustedReading ?: 0f
                                                                )
                                                            } kWh"
                                                        }
                                                    )


                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("₹${"%.2f".format(memberBill?.amountToPay ?: member.amountToPay)}")
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        TextButton(onClick = {
                                                            selectedBreakdown = memberBill
                                                            showBreakdownDialog = true
                                                        }) {
                                                            Text("How")
                                                        }
                                                    }
                                                    Text(
                                                        member.paymentStatus,
                                                        color = if (member.paymentStatus == "Paid") Color.Green else Color.Red
                                                    )
                                                }
                                            }

                                            if (isCurrentUser) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceEvenly
                                                ) {
                                                    if (member.paymentStatus == "Pending") {
                                                        Button(
                                                            onClick = {
                                                                navController.navigate("payment")
                                                            }
                                                        ) {
                                                            Text("Pay")
                                                        }

                                                        Button(
                                                            onClick = {
                                                                groupDetails?.let { gd ->
                                                                    groupViewModel.markAsPaid(
                                                                        groupId = gd.groupId,
                                                                        amountPaid = memberBill?.amountToPay
                                                                            ?: member.amountToPay
                                                                    )

                                                                    // Add small delay before fetching history
                                                                    CoroutineScope(Dispatchers.Main).launch {
                                                                        kotlinx.coroutines.delay(
                                                                            1000
                                                                        )
                                                                        historyViewModel.fetchPaymentHistory()
                                                                    }
                                                                }
                                                            }
                                                        ) {
                                                            Text("Mark as Paid")
                                                        }


                                                    } else if (member.paymentStatus == "Paid") {
                                                        Button(
                                                            onClick = {
                                                                groupDetails?.let { gd ->
                                                                    val currentReading =
                                                                        member.reading ?: 0f
                                                                    val currentOffset =
                                                                        member.offsetValue ?: 0f
                                                                    val restoredOffset =
                                                                        (currentReading - currentOffset).coerceAtLeast(
                                                                            0f
                                                                        )

                                                                    groupViewModel.submitReading(
                                                                        currentReading.toString(),
                                                                        restoredOffset.toString()
                                                                    ) {
                                                                        groupViewModel.resetPaymentStatus(
                                                                            groupId = gd.groupId,
                                                                            amountPaid = (memberBill?.amountToPay
                                                                                ?: member.amountToPay).toDouble()
                                                                        )
                                                                        CoroutineScope(Dispatchers.Main).launch {
                                                                            kotlinx.coroutines.delay(
                                                                                1000
                                                                            )
                                                                            historyViewModel.fetchPaymentHistory()
                                                                        }
                                                                    }

                                                                    CoroutineScope(Dispatchers.Main).launch {
                                                                        kotlinx.coroutines.delay(
                                                                            1000
                                                                        )
                                                                        historyViewModel.fetchPaymentHistory()
                                                                    }
                                                                }
                                                            },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = Color.Red.copy(
                                                                    alpha = 0.1f
                                                                )
                                                            )
                                                        ) {
                                                            Text("Reset", color = Color.Red)
                                                        }

                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                            }

                            item(key = calculatedBills?.hashCode() ?: groupDetails?.pieChartData?.hashCode()) {
                                calculatedBills?.takeIf { it.isNotEmpty() }?.let { data ->
                                    PieChart(data = data.mapValues { it.value.amountToPay })
                                } ?: groupDetails?.pieChartData?.takeIf { it.isNotEmpty() }?.let { data ->
                                    PieChart(data = data)
                                }
                            }

                            item {
                                Column {

                                    if (isAnyMemberPaid) {
                                        Text(
                                            text = "Reading cannot be changed: 1 or more users have paid already",
                                            color = Color.Red,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                    }
                                    
                                    FilledTonalButton(
                                        onClick = { showCamera = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isAnyMemberPaid,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isAnyMemberPaid) Color.LightGray else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (isAnyMemberPaid) Color.DarkGray else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Scan Meter")
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    FilledTonalButton(
                                        onClick = { galleryLauncher.launch("image/*") },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isAnyMemberPaid,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isAnyMemberPaid) Color.LightGray else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (isAnyMemberPaid) Color.DarkGray else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("From Gallery")
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    FilledTonalButton(
                                        onClick = { showManualDialog = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isAnyMemberPaid,
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = if (isAnyMemberPaid) Color.LightGray else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (isAnyMemberPaid) Color.DarkGray else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Icon(Icons.Filled.Keyboard, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Enter Manually")
                                    }

                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showDrawer = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.TopEnd)
                ) {
                    GroupDrawerContent(
                        groupName = groupDetails?.groupName ?: "",
                        groupCode = groupViewModel.authManager.currentGroupCode.collectAsState(initial = "").value ?: "",
                        groupQr = groupViewModel.authManager.currentGroupQr.collectAsState(initial = "").value ?: "",
                        isCreator = isGroupCreator,
                        onEditGroup = {
                            groupViewModel.updateGroupName(it) {
                                showDrawer = false
                            }
                        },
                        onEditBill = { newConsumerNumber, newOperator ->
                            groupViewModel.updateGroupBill(newConsumerNumber, newOperator) {
                                showDrawer = false
                            }
                        },
                        onLeaveGroup = {
                            groupViewModel.leaveGroup { showDrawer = false }
                        },
                        onDeleteGroup = {
                            groupViewModel.deleteGroup { showDrawer = false }
                        },
                        onLogout = {
                            billViewModel.viewModelScope.launch {
                                billViewModel.logout()
                                onLogout()
                            }
                        },
                        onDismiss = { showDrawer = false }
                    )
                }
            }
        }
    }

    if (showCamera) {
        CameraScreen(
            visionService = visionService,
            onTextRecognized = { reading ->
                showCamera = false
                manualReading = reading
                showManualDialog = true
            },
            onClose = {
                showCamera = false
            }
        )
    }


    if (showManualDialog) {
        val offsetInfo by produceState<Pair<Float?, String?>>(initialValue = null to null) {
            value = groupViewModel.getCurrentUserOffset()
        }

        val (offsetValue, offsetOrigin) = offsetInfo
        val offsetDisplay = if (offsetValue != null) "Using offset: ${"%.2f".format(offsetValue)}"
        else "No offset set"
        val originDisplay = offsetOrigin?.let {
            if (it == "manual") "(Manually entered)"
            else "(Auto-set after payment)"
        } ?: ""

        val offsetInput = remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Enter Meter Reading") },
            text = {
                Column {
                    OutlinedTextField(
                        value = manualReading,
                        onValueChange = { manualReading = it },
                        label = { Text("Current Reading (kWh)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = offsetInput.value,
                        onValueChange = { offsetInput.value = it },
                        label = { Text("Manual Offset (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("$offsetDisplay $originDisplay", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val response = billResponse ?: return@Button

                        CoroutineScope(Dispatchers.Main).launch {
                            val (existingOffset, _) = groupViewModel.getCurrentUserOffset()
                            val enteredOffset = offsetInput.value.toFloatOrNull() ?: existingOffset ?: 0f
                            val enteredReading = manualReading.toFloatOrNull() ?: 0f
                            val adjustedReading = enteredReading - enteredOffset

                            val result = BillCalculator.calculateSplit(
                                totalBillAmount = response.totalAmount.toFloat(),
                                totalUnits = response.totalUnits.toFloat(),
                                individualReadings = listOf(adjustedReading),
                                groupSize = groupDetails?.members?.size ?: 1
                            )

                            selectedBreakdown = result.individualBills.firstOrNull()
                            showManualDialog = false

                            currentGroupId?.toIntOrNull()?.let { groupId ->
                                groupViewModel.submitReading(
                                    reading = manualReading,
                                    offset = offsetInput.value.takeIf { it.isNotBlank() }
                                ) {
                                    groupViewModel.fetchGroupDetails(groupId)
                                }
                            }
                        }
                    },
                    enabled = manualReading.isNotBlank()
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBreakdownDialog && selectedBreakdown != null && calculatedResult != null) {
        val breakdownText = calculatedResult.getFormattedBreakdown(selectedBreakdown)
        AlertDialog(
            onDismissRequest = { showBreakdownDialog = false },
            title = { Text("Calculation Breakdown") },
            text = { Text(breakdownText) },
            confirmButton = {
                Button(onClick = { showBreakdownDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name, consumerNumber, operator ->
                groupViewModel.createGroup(name, consumerNumber, operator) {
                    showCreateGroupDialog = false
                }
            },
            isLoading = groupLoading
        )
    }

    if (showJoinGroupDialog) {
        JoinGroupDialog(
            onDismiss = { showJoinGroupDialog = false },
            onJoin = { code -> groupViewModel.joinGroup(code) { showJoinGroupDialog = false } },
            isLoading = groupLoading
        )
    }

    if (showQrScanner) {
        QRScannerScreen(
            onCodeScanned = { scannedCode ->
                showQrScanner = false
                groupViewModel.joinGroup(scannedCode) {
                    // Group joined successfully
                }
            },
            onClose = { showQrScanner = false }
        )
    }
}

private fun processImageFromUri(
    context: Context,
    visionService: VisionService,
    uri: Uri,
    onResult: (String) -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)!!
        val jpegBytes = inputStream.readBytes()
        inputStream.close()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reading = visionService.detectDigitsFromJpegBytes(jpegBytes)
                withContext(Dispatchers.Main) {
                    onResult(reading)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("Error: ${e.message}")
                }
            }
        }
    } catch (e: Exception) {
        onResult("Error: ${e.message}")
    }
}
