package com.example.laiva

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.laiva.data.AppDatabase
import com.example.laiva.data.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store

class MainViewModel(private val dataStoreManager: DataStoreManager) : ViewModel() {

    private val _email = MutableStateFlow<String?>(null)
    val email: StateFlow<String?> = _email

    private val _password = MutableStateFlow<String?>(null)
    val password: StateFlow<String?> = _password

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked

    init {
        viewModelScope.launch {
            val pinEnabled = dataStoreManager.isPinEnabledFlow.first()
            if (pinEnabled) {
                _isAppLocked.value = true
            }

            combine(
                dataStoreManager.emailFlow,
                dataStoreManager.passwordFlow
            ) { savedEmail, savedPassword ->
                savedEmail to savedPassword
            }.collect { (savedEmail, savedPassword) ->
                _email.value = savedEmail
                _password.value = savedPassword
                _isLoggedIn.value = !savedEmail.isNullOrEmpty() && !savedPassword.isNullOrEmpty()
            }
        }
    }


    fun triggerPanic(context: Context) {
        viewModelScope.launch {
            val mail = _email.value
            val pass = _password.value

            withContext(Dispatchers.IO) {
                try {
                    dataStoreManager.setPanicActive(true)

                    val db = AppDatabase.getDatabase(context)

                    db.clearAllTables()


                    val avatarsDir = File(context.filesDir, "avatars")
                    if (avatarsDir.exists()) {
                        avatarsDir.deleteRecursively()
                        avatarsDir.mkdirs()
                    }

                    context.filesDir.listFiles()?.forEach { file ->

                        if (file.name != "datastore" && file.isDirectory.not()) {
                            file.delete()
                        }
                    }

                    // Если были кэшированные файлы
                    context.cacheDir.deleteRecursively()

                    // Удаление писем с сервера
                    if (!mail.isNullOrEmpty() && !pass.isNullOrEmpty()) {
                        wipeRemoteEmails(mail, pass)
                    }

                    Log.d("LAIVA_DEBUG", "Паника: База и файлы (включая аватарки) удалены.")

                } catch (e: Exception) {
                    Log.e("LAIVA_DEBUG", "Ошибка при панике: ${e.message}")
                }
            }

            delay(100)
            _isAppLocked.value = false
        }
    }

    fun unlockApp() {
        viewModelScope.launch {
            dataStoreManager.setPanicActive(false)
            _isAppLocked.value = false
        }
    }

    private suspend fun wipeRemoteEmails(email: String, pass: String) {
        withContext(Dispatchers.IO) {
            var store: Store? = null
            try {
                val props = Properties().apply {
                    setProperty("mail.store.protocol", "imaps")
                    setProperty("mail.imaps.host", email.substringAfter("@"))
                    setProperty("mail.imaps.port", "993")
                    setProperty("mail.imaps.ssl.enable", "true")
                    setProperty("mail.imaps.timeout", "10000")
                }

                val session = Session.getInstance(props)
                store = session.getStore("imaps")
                store.connect(email, pass)

                val inbox = store.getFolder("INBOX")
                inbox.open(Folder.READ_WRITE)
                val messages = inbox.messages
                if (messages.isNotEmpty()) {
                    inbox.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                }
                inbox.close(true) // true выполняет удаление (expunge)

                Log.d("LAIVA_DEBUG", "Письма на сервере помечены на удаление")

            } catch (e: Exception) {
                Log.e("LAIVA_DEBUG", "Ошибка очистки сервера: ${e.message}")
            } finally {
                try { store?.close() } catch (e: Exception) {}
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            dataStoreManager.saveUser(email, password)
            val existingPub = dataStoreManager.publicKeyFlow.first()
            if (existingPub.isNullOrEmpty()) {
                val (priv, pub) = CryptoManager.generateKeyPair()
                dataStoreManager.saveKeys(priv, pub)
            }
            _isLoggedIn.value = true
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(DataStoreManager(context)) as T
    }
}