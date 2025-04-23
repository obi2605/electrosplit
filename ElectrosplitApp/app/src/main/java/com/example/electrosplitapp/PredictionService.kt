package com.example.electrosplitapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface PredictionService {
    @Headers("Content-Type: application/json")
    @POST("/predict")
    fun getPrediction(@Body request: PredictionRequest): Call<PredictionResponse>
}
