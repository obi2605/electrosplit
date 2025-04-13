package com.example.electrosplitapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "auth_prefs")

class AuthManager(private val context: Context) {
    companion object {
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val USER_ID = stringPreferencesKey("user_id")
        private val PHONE_NUMBER = stringPreferencesKey("phone_number")
        private val USER_NAME = stringPreferencesKey("user_name")
        private val CONSUMER_NUMBER = stringPreferencesKey("consumer_number")
        private val OPERATOR_NAME = stringPreferencesKey("operator_name")
    }

    suspend fun saveLoginDetails(
        userId: String,
        phoneNumber: String,
        name: String,
        consumerNumber: String,
        operator: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = true
            preferences[USER_ID] = userId
            preferences[PHONE_NUMBER] = phoneNumber
            preferences[USER_NAME] = name
            preferences[CONSUMER_NUMBER] = consumerNumber
            preferences[OPERATOR_NAME] = operator
        }
    }

    suspend fun logout() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[IS_LOGGED_IN] ?: false }

    val userId: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[USER_ID] }

    val phoneNumber: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PHONE_NUMBER] }

    val userName: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[USER_NAME] }

    val consumerNumber: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[CONSUMER_NUMBER] }

    val operatorName: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[OPERATOR_NAME] }

    // New helper function to get all user data at once
    suspend fun getUserData(): Map<String, String?> {
        return context.dataStore.data.map { preferences ->
            mapOf(
                "userId" to preferences[USER_ID],
                "phoneNumber" to preferences[PHONE_NUMBER],
                "userName" to preferences[USER_NAME],
                "consumerNumber" to preferences[CONSUMER_NUMBER],
                "operatorName" to preferences[OPERATOR_NAME]
            )
        }.first()
    }
}