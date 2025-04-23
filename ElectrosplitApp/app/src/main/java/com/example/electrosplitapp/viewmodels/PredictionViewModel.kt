package com.example.electrosplitapp.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import com.example.electrosplitapp.PredictionRequest
import com.example.electrosplitapp.PredictionResponse
import com.example.electrosplitapp.PaymentHistoryEntry
import com.example.electrosplitapp.PredictionService
import com.example.electrosplitapp.PaymentHistoryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PredictionViewModel : ViewModel() {

    // Retrofit instances
    private val predictionRetrofit = Retrofit.Builder()
        .baseUrl("http://192.168.1.2:5000/")   // Flask server for prediction
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val backendRetrofit = Retrofit.Builder()
        .baseUrl("http://192.168.1.2:8080/")   // Example backend URL (adjust as needed)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    // Reuse interfaces
    private val predictionService = predictionRetrofit.create(PredictionService::class.java)
    private val historyService = backendRetrofit.create(PaymentHistoryService::class.java)

    private val _prediction = MutableLiveData<PredictionResponse?>()
    val prediction: LiveData<PredictionResponse?> = _prediction

    private val _latestPayment = MutableLiveData<PaymentHistoryEntry?>()
    val latestPayment: LiveData<PaymentHistoryEntry?> = _latestPayment

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun fetchPredictionWithHistory(phone: String, city: String, billingCycle: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val historyResponse = historyService.getPaymentHistory(phone).execute()
                if (historyResponse.isSuccessful) {
                    val historyList = historyResponse.body()
                    if (!historyList.isNullOrEmpty()) {
                        val latest = historyList.maxByOrNull { it.datetimePaid }
                        _latestPayment.postValue(latest)

                        val lastPaidDate = latest?.billGenerationDate ?: return@launch
                        val request = PredictionRequest(
                            city = city,
                            lastPaidUnits = latest.unitsPaidFor.toInt(),
                            lastPaidDate = lastPaidDate.substring(0, 10),
                            billingCycle = billingCycle
                        )

                        val predictionResponse = predictionService.getPrediction(request).execute()
                        if (predictionResponse.isSuccessful) {
                            _prediction.postValue(predictionResponse.body())
                        } else {
                            _error.postValue("Prediction error: ${predictionResponse.code()}")
                        }
                    } else {
                        _error.postValue("No payment history found.")
                    }
                } else {
                    _error.postValue("History fetch error: ${historyResponse.code()}")
                }
            } catch (e: Exception) {
                _error.postValue("Network error: ${e.message}")
            }
        }
    }
}
