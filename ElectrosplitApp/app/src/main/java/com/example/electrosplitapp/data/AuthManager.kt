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
        private val CURRENT_GROUP_ID = stringPreferencesKey("current_group_id")
        private val CURRENT_GROUP_NAME = stringPreferencesKey("current_group_name")
        private val CURRENT_GROUP_CODE = stringPreferencesKey("current_group_code")
        private val CURRENT_GROUP_QR = stringPreferencesKey("current_group_qr")
        private val IS_GROUP_CREATOR = booleanPreferencesKey("is_group_creator")
    }

    suspend fun saveLoginDetails(
        userId: String,
        phoneNumber: String,
        name: String

    ) {
        context.dataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = true
            preferences[USER_ID] = userId
            preferences[PHONE_NUMBER] = phoneNumber
            preferences[USER_NAME] = name

        }
    }

    suspend fun logout() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    suspend fun saveGroupDetails(
        groupId: Int,
        groupName: String,
        groupCode: String,
        groupQr: String,
        isCreator: Boolean
    ) {
        context.dataStore.edit { preferences ->
            preferences[CURRENT_GROUP_ID] = groupId.toString()
            preferences[CURRENT_GROUP_NAME] = groupName
            preferences[CURRENT_GROUP_CODE] = groupCode
            preferences[CURRENT_GROUP_QR] = groupQr
            preferences[IS_GROUP_CREATOR] = isCreator
        }
    }

    suspend fun clearGroupDetails() {
        context.dataStore.edit { preferences ->
            preferences.remove(CURRENT_GROUP_ID)
            preferences.remove(CURRENT_GROUP_NAME)
            preferences.remove(CURRENT_GROUP_CODE)
            preferences.remove(CURRENT_GROUP_QR)
            preferences.remove(IS_GROUP_CREATOR)
        }
    }

    // Group related flows
    val currentGroupId: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[CURRENT_GROUP_ID] }

    val currentGroupName: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[CURRENT_GROUP_NAME] }

    val currentGroupCode: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[CURRENT_GROUP_CODE] }

    val currentGroupQr: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[CURRENT_GROUP_QR] }

    val isGroupCreator: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[IS_GROUP_CREATOR] ?: false }

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

    suspend fun getGroupData(): Map<String, String?> {
        return context.dataStore.data.map { preferences ->
            mapOf(
                "groupId" to preferences[CURRENT_GROUP_ID],
                "groupName" to preferences[CURRENT_GROUP_NAME],
                "groupCode" to preferences[CURRENT_GROUP_CODE],
                "groupQr" to preferences[CURRENT_GROUP_QR],
                "isCreator" to (preferences[IS_GROUP_CREATOR]?.toString() ?: "false")
            )
        }.first()
    }
}