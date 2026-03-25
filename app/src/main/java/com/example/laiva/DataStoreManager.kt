package com.example.laiva

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore("user_prefs")

class DataStoreManager(private val context: Context) {

    companion object {
        val EMAIL_KEY = stringPreferencesKey("email")
        val PASSWORD_KEY = stringPreferencesKey("password")
        val PRIVATE_KEY = stringPreferencesKey("private_key")
        val PUBLIC_KEY = stringPreferencesKey("public_key")

        val IS_PIN_ENABLED = booleanPreferencesKey("is_pin_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PANIC_PIN_HASH = stringPreferencesKey("panic_pin_hash")

        val AVATAR_PATH = stringPreferencesKey("avatar_path")
        val AVATAR_HASH = stringPreferencesKey("avatar_hash")

        val IS_PANIC_ACTIVE = booleanPreferencesKey("is_panic_active")



        val IGNORE_NON_CONTACT_MESSAGES = booleanPreferencesKey("ignore_non_contact_messages")
        val IGNORE_UNKNOWN_GROUPS = booleanPreferencesKey("ignore_unknown_groups")



    }

    val ignoreNonContactMessagesFlow: Flow<Boolean> = context.dataStore.data
        .map { it[IGNORE_NON_CONTACT_MESSAGES] ?: false }

    val ignoreUnknownGroupsFlow: Flow<Boolean> = context.dataStore.data
        .map { it[IGNORE_UNKNOWN_GROUPS] ?: false }
    suspend fun setIgnoreNonContactMessages(ignore: Boolean) {
        context.dataStore.edit { it[IGNORE_NON_CONTACT_MESSAGES] = ignore }
    }

    suspend fun setIgnoreUnknownGroups(ignore: Boolean) {
        context.dataStore.edit { it[IGNORE_UNKNOWN_GROUPS] = ignore }
    }

    val emailFlow: Flow<String?> = context.dataStore.data.map { it[EMAIL_KEY] }
    val passwordFlow: Flow<String?> = context.dataStore.data.map { it[PASSWORD_KEY] }
    val privateKeyFlow = context.dataStore.data.map { it[PRIVATE_KEY] }
    val publicKeyFlow = context.dataStore.data.map { it[PUBLIC_KEY] }

    val isPinEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_PIN_ENABLED] ?: false }
    val pinHashFlow: Flow<String?> = context.dataStore.data.map { it[PIN_HASH] }
    val panicPinHashFlow: Flow<String?> = context.dataStore.data.map { it[PANIC_PIN_HASH] }


    val isPanicActiveFlow: Flow<Boolean> = context.dataStore.data.map { it[IS_PANIC_ACTIVE] ?: false }

    suspend fun setPanicActive(active: Boolean) {
        context.dataStore.edit { it[IS_PANIC_ACTIVE] = active }
    }

    suspend fun savePinSettings(enabled: Boolean, pin: String?, panicPin: String?) {
        context.dataStore.edit { prefs ->
            prefs[IS_PIN_ENABLED] = enabled
            pin?.let { if (it.isNotEmpty()) prefs[PIN_HASH] = hashPin(it) }
            panicPin?.let { if (it.isNotEmpty()) prefs[PANIC_PIN_HASH] = hashPin(it) }
        }
    }



    private fun hashPin(pin: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    suspend fun saveKeys(privateKey: String, publicKey: String) {
        context.dataStore.edit {
            it[PRIVATE_KEY] = privateKey
            it[PUBLIC_KEY] = publicKey
        }
    }

    suspend fun saveUser(email: String, password: String) {
        context.dataStore.edit {
            it[EMAIL_KEY] = email
            it[PASSWORD_KEY] = password
        }
    }

}