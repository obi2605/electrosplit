package com.example.electrosplitapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {
    @POST("register")
    fun register(@Body request: UserRequest): Call<AuthResponse>

    @POST("login")
    fun login(@Body request: UserRequest): Call<AuthResponse>
}