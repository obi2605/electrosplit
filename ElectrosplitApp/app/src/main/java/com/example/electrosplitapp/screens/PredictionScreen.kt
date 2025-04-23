package com.example.electrosplitapp.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.electrosplitapp.viewmodels.PredictionViewModel
import com.example.electrosplitapp.components.LineChartView
import com.example.electrosplitapp.utils.BillCalculator
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PredictionScreen(
    phone: String,
    predictionViewModel: PredictionViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val prediction by predictionViewModel.prediction.observeAsState()
    val latestPayment by predictionViewModel.latestPayment.observeAsState()
    val historyList by predictionViewModel.historyData.observeAsState(emptyList())
    val error by predictionViewModel.error.observeAsState()

    var city by remember { mutableStateOf<String?>(null) }
    var selectedCycle by rememberSaveable { mutableIntStateOf(1) }
    var expanded by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) {
            fetchCityFromLocation(context, fusedLocationClient) { cityName ->
                city = cityName
            }
        }
    }

    val isRefreshing = remember { mutableStateOf(false) }

    fun refreshPrediction() {
        if (city != null) {
            isRefreshing.value = true
            scope.launch {
                predictionViewModel.fetchPredictionWithHistory(phone, city!!, selectedCycle)
                isRefreshing.value = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            fetchCityFromLocation(context, fusedLocationClient) { cityName ->
                city = cityName
            }
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(city, selectedCycle) {
        if (city != null) {
            refreshPrediction()
        }
    }

    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing.value),
        onRefresh = { refreshPrediction() }
    ) {
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {

            Text("Electricity Split Prediction", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedCycle.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Billing cycle (months ahead)") },
                    trailingIcon = {
                        Icon(
                            if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    (1..6).forEach { cycle ->
                        DropdownMenuItem(
                            text = { Text("$cycle") },
                            onClick = {
                                selectedCycle = cycle
                                expanded = false
                                refreshPrediction()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                error != null -> {
                    Text("Error: $error", color = MaterialTheme.colorScheme.error)
                }
                prediction != null && latestPayment != null -> {
                    val predictedUnits = prediction!!.predictedUnits
                    val lastUnits = latestPayment!!.unitsPaidFor
                    val delta = predictedUnits - lastUnits
                    val isIncrease = delta > 0

                    val totalAmount = BillCalculator.calculateSplit(
                        totalBillAmount = 0f,
                        totalUnits = predictedUnits.toFloat(),
                        individualReadings = listOf(predictedUnits.toFloat()),
                        groupSize = 1
                    ).individualBills.first().amountToPay

                    val deltaText = if (delta >= 0)
                        "+%.2f units".format(delta)
                    else
                        "%.2f units".format(delta)

                    val deltaColor = if (delta >= 0) Color.Red else Color.Green
                    val seasonalHint = getSeasonalHint(prediction!!.targetMonth)

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Last Paid: ${"%.2f".format(lastUnits)} units")
                            Text("Predicted: ${"%.2f".format(predictedUnits)} units")
                            Text(deltaText, color = deltaColor)
                            Text("Estimated Amount: ₹${"%.2f".format(totalAmount)}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(seasonalHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Prepare chart data
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val historyPoints = historyList.mapNotNull {
                        try {
                            val date = dateFormat.parse(it.datetimePaid.substring(0, 19))
                            date?.let { d -> d to it.unitsPaidFor.toDouble() }   // Ensures type matches LineChartView
                        } catch (e: Exception) { null }
                    }

                    val predictedDate = Calendar.getInstance().apply {
                        time = dateFormat.parse(latestPayment!!.datetimePaid.substring(0, 19))!!
                        add(Calendar.MONTH, selectedCycle)
                    }.time

                    LineChartView(
                        historyData = historyPoints,
                        predictedPoint = predictedDate to predictedUnits,
                        isIncrease = isIncrease,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
fun fetchCityFromLocation(
    context: Context,
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onCityResolved: (String?) -> Unit
) {
    fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
        if (location != null) {
            val geocoder = Geocoder(context, Locale.getDefault())
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                val rawCity = addresses.firstOrNull()?.locality
                                val normalizedCity = normalizeCity(rawCity)
                                onCityResolved(normalizedCity)
                            }

                            override fun onError(errorMessage: String?) {
                                onCityResolved(null)
                            }
                        }
                    )
                } else {
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    val rawCity = addresses?.firstOrNull()?.locality
                    val normalizedCity = normalizeCity(rawCity)
                    onCityResolved(normalizedCity)
                }
            } catch (e: Exception) {
                onCityResolved(null)
            }
        } else {
            onCityResolved(null)
        }
    }.addOnFailureListener {
        onCityResolved(null)
    }
}

fun normalizeCity(rawCity: String?): String? {
    return when {
        rawCity.isNullOrBlank() -> null
        rawCity.contains("Kattankulathur", ignoreCase = true) -> "Chennai"
        rawCity.contains("Tambaram", ignoreCase = true) -> "Chennai"
        rawCity.contains("Noida", ignoreCase = true) -> "Delhi"
        rawCity.contains("Thane", ignoreCase = true) -> "Mumbai"
        else -> rawCity
    }
}

fun getSeasonalHint(month: Int): String {
    return when (month) {
        in 4..6 -> "Note: Higher usage expected due to summer months."
        in 11..12, 1 -> "Typically lower usage in winter."
        else -> "Moderate seasonal impact."
    }
}
