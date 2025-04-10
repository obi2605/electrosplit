package com.example.electrosplitapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "auth_prefs")

class AuthManager(private val context: Context) {
    companion object {
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val CONSUMER_NUMBER = stringPreferencesKey("consumer_number")
        private val OPERATOR_NAME = stringPreferencesKey("operator_name")
        private val ACCOUNT_NAME = stringPreferencesKey("account_name")
    }

    suspend fun saveLoginDetails(
        consumerNumber: String,
        operator: String,
        accountName: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = true
            preferences[CONSUMER_NUMBER] = consumerNumber
            preferences[OPERATOR_NAME] = operator
            preferences[ACCOUNT_NAME] = accountName
        }
    }

    suspend fun logout() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[IS_LOGGED_IN] ?: false }

    val consumerNumber: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[CONSUMER_NUMBER] }

    val operatorName: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[OPERATOR_NAME] }

    val accountName: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[ACCOUNT_NAME] }
}