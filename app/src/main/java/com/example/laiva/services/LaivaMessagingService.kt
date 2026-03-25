package com.example.laiva.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.laiva.DataStoreManager
import com.example.laiva.MainActivity
import com.example.laiva.data.AppDatabase
import com.example.laiva.data.MessageHandler
import com.example.laiva.data.MessengerRepository
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.*
import javax.mail.*
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent
import javax.mail.internet.MimeMessage

class LaivaMessagingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isListening = false
    private lateinit var repository: MessengerRepository
    private lateinit var dataStoreManager: DataStoreManager

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getDatabase(applicationContext)
        dataStoreManager = DataStoreManager(applicationContext)
        repository = MessengerRepository(db, dataStoreManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val email = intent?.getStringExtra("email") ?: ""
        val password = intent?.getStringExtra("password") ?: ""

        startForegroundServiceNotification()

        if (!isListening && email.isNotEmpty() && password.isNotEmpty()) {
            startListening(email, password)
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "LAIVA_SERVICE_SILENT"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Работа в фоне",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(null)
            .setContentText("Laiva работает в фоне")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        startForeground(1, notification)
    }

    private fun startListening(email: String, password: String) {
        isListening = true
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {

                val isPanic = dataStoreManager.isPanicActiveFlow.first()
                if (isPanic) {
                    Log.d("LAIVA_DEBUG", "Паника активна: сервис в режиме ожидания.")
                    delay(10000)
                    continue
                }

                var store: Store? = null
                try {
                    val props = Properties().apply {
                        setProperty("mail.store.protocol", "imaps")
                        setProperty("mail.imaps.host", email.substringAfter("@"))
                        setProperty("mail.imaps.port", "993")
                        setProperty("mail.imaps.ssl.enable", "true")
                        setProperty("mail.imaps.ssl.trust", "*")
                        setProperty("mail.imaps.ssl.checkserveridentity", "false")
                        setProperty("mail.imaps.timeout", "30000")
                        setProperty("mail.imaps.connectiontimeout", "15000")
                        setProperty("mail.imaps.peek", "true")
                    }

                    val session = Session.getInstance(props)
                    store = session.getStore("imaps")
                    store.connect(email, password)

                    val inbox = store.getFolder("INBOX") as IMAPFolder
                    inbox.open(Folder.READ_WRITE)

                    inbox.addMessageCountListener(object : MessageCountAdapter() {
                        override fun messagesAdded(e: MessageCountEvent) {
                            serviceScope.launch(Dispatchers.IO) {
                                if (dataStoreManager.isPanicActiveFlow.first()) return@launch

                                var hasDeletedAny = false

                                for (msg in e.messages) {
                                    try {
                                        val mimeMsg = msg as MimeMessage
                                        val isLaivaMessage = mimeMsg.getHeader("Laiva-App")?.isNotEmpty() ?: false

                                        if (isLaivaMessage) {
                                            handleIncomingMessage(mimeMsg)
                                            msg.setFlag(Flags.Flag.SEEN, true)
                                            msg.setFlag(Flags.Flag.DELETED, true)
                                            hasDeletedAny = true
                                        }
                                    } catch (ex: Exception) {
                                        Log.e("LAIVA_DEBUG", "Ошибка обработки: ${ex.message}")
                                    }
                                }

                                if (hasDeletedAny && inbox.isOpen) {
                                    inbox.expunge()
                                    Log.d("LAIVA_DEBUG", "Выполнена массовая очистка Laiva-сообщений.")
                                }
                            }
                        }
                    })

                    Log.d("LAIVA_DEBUG", "Подключено. Слушаем почту...")

                    while (isActive && inbox.isOpen) {
                        // Если во время работы активировали панику — немедленно рвем соединение
                        if (dataStoreManager.isPanicActiveFlow.first()) {
                            Log.d("LAIVA_DEBUG", "Паника сработала! Закрываем соединение.")
                            break
                        }

                        if (!store.isConnected) break

                        // Метод idle() блокирует поток до появления сообщения или таймаута
                        inbox.idle()
                    }
                } catch (e: Exception) {
                    Log.e("LAIVA_DEBUG", "Ошибка IMAP: ${e.message}. Реконнект...")
                    delay(5000)
                } finally {
                    try { store?.close() } catch (e: Exception) { /* ignore */ }
                }
            }
        }
    }

    private suspend fun handleIncomingMessage(msg: MimeMessage) {
        if (dataStoreManager.isPanicActiveFlow.first()) return

        MessageHandler.processIncomingMessage(
            context = this,
            msg = msg,
            repository = repository,
            activeChatEmail = MainActivity.activeChatEmail
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}