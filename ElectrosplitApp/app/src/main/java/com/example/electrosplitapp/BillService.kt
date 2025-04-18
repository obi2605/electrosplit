package com.example.electrosplitapp

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path


interface BillService {
    @POST("fetchBill")
    fun fetchBill(@Body request: BillRequest): Call<BillResponse>

    // Add to BillService.kt
    @POST("createGroup")
    fun createGroup(@Body request: GroupRequest): Call<GroupResponse>

    @POST("joinGroup")
    fun joinGroup(@Body request: JoinGroupRequest): Call<GroupDetailsResponse>

    @GET("groupDetails/{groupId}")
    fun getGroupDetails(@Path("groupId") groupId: Int): Call<GroupDetailsResponse>

    @POST("updateGroup")
    fun updateGroup(@Body request: UpdateGroupRequest): Call<AuthResponse>

    @POST("leaveGroup")
    fun leaveGroup(@Body request: LeaveGroupRequest): Call<AuthResponse>

    @POST("deleteGroup")
    fun deleteGroup(@Body request: DeleteGroupRequest): Call<AuthResponse>

    @POST("submitReading")
    fun submitReading(@Body request: SubmitReadingRequest): Call<AuthResponse>

    @GET("getGroupForUser/{phone}")
    fun getGroupForUser(@Path("phone") phone: String): Call<GroupDetailsResponse?>

    @POST("updateGroupBill/{groupId}")
    fun updateGroupBill(@Path("groupId") groupId: Int, @Body request: BillRequest): Call<AuthResponse>

    @POST("markAsPaid")
    fun markAsPaid(@Body request: MarkPaidRequest): Call<AuthResponse>

    @POST("resetPaymentStatus")
    fun resetPaymentStatus(@Body request: ResetPaymentRequest): Call<AuthResponse>





}

