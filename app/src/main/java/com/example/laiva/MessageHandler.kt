package com.example.laiva.data

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.laiva.MainActivity
import com.example.laiva.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.mail.BodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

import com.example.laiva.sendAvatarRequest
import com.example.laiva.sendAvatarUpdate
import com.example.laiva.DataStoreManager
import kotlinx.coroutines.flow.first

object MessageHandler {

    private val pendingAvatarRequests = mutableSetOf<String>()

    suspend fun processIncomingMessage(
        context: Context,
        msg: MimeMessage,
        repository: MessengerRepository,
        activeChatEmail: String? = null
    ) {
        try {
            val dataStoreManager = DataStoreManager(context)

            val senderEmailRaw = msg.from[0].toString()
            val senderEmail = if (senderEmailRaw.contains("<")) {
                senderEmailRaw.substringAfter("<").substringBefore(">")
            } else senderEmailRaw



            val ignoreNonContact = dataStoreManager.ignoreNonContactMessagesFlow.first()
            val ignoreUnknownGroups = dataStoreManager.ignoreUnknownGroupsFlow.first()

            val contact = repository.getContact(senderEmail)
            val isKnownContact = contact != null


            val groupId = msg.getHeader("X-Laiva-Group-Id")?.firstOrNull()
            val isGroupMessage = !groupId.isNullOrEmpty()

            if (!isGroupMessage && ignoreNonContact && !isKnownContact) {
                Log.d("MESSAGE_HANDLER", "ФИЛЬТР: Проигнорировано ЛС от незнакомца: $senderEmail")
                return
            }

            if (isGroupMessage && ignoreUnknownGroups) {
                val existingChat = repository.getChat(groupId!!)
                if (existingChat == null) {
                    Log.d("MESSAGE_HANDLER", "ФИЛЬТР: Проигнорирована новая группа от незнакомца: $senderEmail")
                    return
                }
            }








            val serverMessageId = msg.messageID ?: "${msg.sentDate?.time}_$senderEmail"
            val mymail: String = repository.getMyEmail() ?: ""
            val laivaTypeHeader = msg.getHeader("X-Laiva-Type", null)?.lowercase()


            if (laivaTypeHeader == "avatar_request") {
                val myPassword = repository.getMyEmailPassword() ?: ""
                sendAvatarUpdate(
                    repository = repository,
                    context = context,
                    smtpHost = MailConfig.getSmtpHost(mymail),
                    smtpPort = 587,
                    fromEmail = mymail,
                    password = myPassword,
                    toEmail = senderEmail
                )
                return
            }

            if (laivaTypeHeader == "avatar_update") {
                saveIncomingAvatar(context, msg, senderEmail, repository)
                pendingAvatarRequests.remove(senderEmail)
                return
            }


            val avatarHashHeader = msg.getHeader("X-Laiva-Avatar-Hash")?.firstOrNull()
            if (avatarHashHeader != null) {

                val contact = repository.getContact(senderEmail)
                val savedHash = contact?.avatarHash
                val localFileExists = contact?.avatarPath?.let { File(it).exists() } ?: false







                val needsUpdate = !localFileExists || savedHash != avatarHashHeader

                if (needsUpdate && !pendingAvatarRequests.contains(senderEmail)) {
                    Log.d("MESSAGE_HANDLER", "Запрашиваем аватар для $senderEmail (хэш не совпал)")
                    pendingAvatarRequests.add(senderEmail)

                    sendAvatarRequest(
                        repository = repository,
                        smtpHost = MailConfig.getSmtpHost(mymail),
                        smtpPort = 587,
                        fromEmail = mymail,
                        password = repository.getMyEmailPassword() ?: "",
                        toEmail = senderEmail
                    )
                }
            }

            val groupIdHeaders: Array<String>? = msg.getHeader("X-Laiva-Group-Id")



            val chatId = if (isGroupMessage) groupId!! else senderEmail

            val pubKeyHeaders: Array<String>? = msg.getHeader("X-Laiva-PubKey")
            val senderPubKey: String? = pubKeyHeaders?.firstOrNull()

            val existingChat = repository.getChat(chatId)
            if (existingChat == null) {
                if (isGroupMessage) {
                    val membersHeaders: Array<String>? = msg.getHeader("X-Laiva-Members")
                    val membersList = membersHeaders?.firstOrNull()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                    repository.createGroup(name = "Новая группа", members = membersList, id = chatId)
                } else {
                    repository.addContact(email = senderEmail, name = senderEmail.substringBefore("@"), encryptionKey = senderPubKey ?: "")
                }
            }



















            val existingContact = repository.getContact(senderEmail)

            if (existingContact == null) {
                repository.addContact(
                    email = senderEmail,
                    name = senderEmail.substringBefore("@"),
                    encryptionKey = senderPubKey ?: ""
                )
            } else {
                if (senderPubKey != null && senderPubKey != existingContact.encryptionKey) {
                    Log.d("MESSAGE_HANDLER", "Обнаружен новый публичный ключ для $senderEmail. Обновляем...")

                    repository.updateContactPublicKey(senderEmail, senderPubKey)
                }
            }


            if (isGroupMessage) {
                if (senderPubKey != null) {
                    repository.updateMemberPublicKey(chatId, senderEmail, senderPubKey)
                }

                val allHeaders = msg.allHeaders.asSequence()
                allHeaders.filter { it.name.startsWith("X-Laiva-Member-PubKey-", ignoreCase = true) }
                    .forEach { header ->
                        val participantEmail = header.name.substringAfter("X-Laiva-Member-PubKey-").trim()
                        val participantPubKey = header.value // value у Header всегда String

                        repository.updateMemberPublicKey(chatId, participantEmail, participantPubKey)
                    }
            }
//            else {
//                if (senderPubKey != null) {
//                    repository.addContact(senderEmail, senderEmail, senderPubKey)
//                }
//            }

//проблема, что если у человека изменится публичный ключ, то у собеседника он не изменится, нужно фиксить




            val displayName = contact?.name ?: senderEmail
            val privateKey = repository.getMyPrivateKey()

            when (laivaTypeHeader) {
                "text" -> {
                    val content = extractTextFromMessage(msg)
                    val headerName = "X-Laiva-Key-$mymail"
                    val encryptedKey = msg.getHeader(headerName, null)?.toString() ?: ""
                    val decrypted = if (privateKey != null && encryptedKey.isNotEmpty()) {
                        CryptoManager.decryptHybridText(content, encryptedKey, privateKey)
                    } else content

                    addMessageToDb(repository, isGroupMessage, chatId, senderEmail, decrypted, serverMessageId, "text", null)

                    if (senderEmail != activeChatEmail) {
                        val title = if (isGroupMessage) repository.getChatName(chatId) ?: "Группа" else displayName
                        showNotification(context, chatId, decrypted, title)
                    }
                }
                "image", "audio", "file" -> {
                    if (msg.isMimeType("multipart/*")) {
                        val multipart = msg.content as MimeMultipart
                        for (i in 0 until multipart.count) {
                            val part = multipart.getBodyPart(i)
                            saveAndDecryptAttachment(context, repository, part, senderEmail, serverMessageId, chatId, msg, isGroupMessage, privateKey)
                        }
                    }
                }
                else -> fallbackProcess(context, msg, repository, senderEmail, serverMessageId, privateKey, displayName, activeChatEmail, chatId, isGroupMessage)
            }
        } catch (e: Exception) {
            Log.e("MESSAGE_HANDLER", "Error processing message: ${e.message}")
        }
    }

    private suspend fun saveAndDecryptAttachment(context: Context, repository: MessengerRepository, part: BodyPart, senderEmail: String, serverMessageId: String, chatId: String, msg: MimeMessage, isGroupMessage: Boolean, key: String?) {
        withContext(Dispatchers.IO) {
            val fileName = part.fileName ?: "${System.currentTimeMillis()}"
            val tempFile = File(context.cacheDir, fileName)
            part.inputStream.use { it.copyTo(tempFile.outputStream()) }

            val myEmail = repository.getMyEmail() ?: ""
            val decryptedFile = if (key != null && fileName.endsWith(".enc")) {
                val outFile = File(context.filesDir, fileName.removeSuffix(".enc"))
                try {
                    val encryptedKey = msg.getHeader("X-Laiva-Key-$myEmail", null)?.toString() ?: ""
                    CryptoManager.decryptHybridFile(tempFile, encryptedKey, key, outFile)
                    outFile
                } catch (e: Exception) { tempFile } finally { tempFile.delete() }
            } else tempFile

            val typeHeader = msg.getHeader("X-Laiva-Type", null) ?: "file"
            addMessageToDb(repository, isGroupMessage, chatId, senderEmail, decryptedFile.name, serverMessageId, typeHeader, decryptedFile.absolutePath)
        }
    }

    private suspend fun addMessageToDb(repository: MessengerRepository, isGroup: Boolean, chatId: String, senderEmail: String, text: String, serverMessageId: String, type: String, localPath: String?) {
        if (isGroup) {
            repository.addIncomingMessageToChat(chatId, senderEmail, text, serverMessageId, type, localPath)
        } else {
            repository.addIncomingMessage(chatId, text, serverMessageId, type, localPath)
        }
    }

    private suspend fun saveIncomingAvatar(
        context: Context,
        msg: MimeMessage,
        senderEmail: String,
        repository: MessengerRepository
    ) {
        withContext(Dispatchers.IO) {
            try {
                val myEmail = repository.getMyEmail() ?: ""
                val privateKey = repository.getMyPrivateKey()

                val encryptedKeyHeader = msg.getHeader("X-Laiva-Key-$myEmail")?.firstOrNull()

                val avatarFileDir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }

                if (msg.content is MimeMultipart) {
                    val multipart = msg.content as MimeMultipart
                    for (i in 0 until multipart.count) {
                        val part = multipart.getBodyPart(i)
                        if (part.isMimeType("text/plain")) continue

                        val tempFile = File(context.cacheDir, "inc_avatar_${System.currentTimeMillis()}.enc")
                        part.inputStream.use { it.copyTo(tempFile.outputStream()) }

                        val savedFile = File(avatarFileDir, "$senderEmail.jpg")

                        if (!privateKey.isNullOrEmpty() && !encryptedKeyHeader.isNullOrEmpty()) {
                            try {
                                CryptoManager.decryptHybridFile(tempFile, encryptedKeyHeader, privateKey, savedFile)
                                Log.d("MESSAGE_HANDLER", "Аватар от $senderEmail расшифрован")
                            } catch (e: Exception) {
                                Log.e("MESSAGE_HANDLER", "Ошибка дешифровки: ${e.message}")
                                tempFile.copyTo(savedFile, overwrite = true)
                            }
                        } else {
                            tempFile.copyTo(savedFile, overwrite = true)
                        }

                        tempFile.delete()

                        val newHash = msg.getHeader("X-Laiva-Avatar-Hash")?.firstOrNull()

                        repository.updateContactAvatar(senderEmail, savedFile.absolutePath, newHash)
                        Log.d("MESSAGE_HANDLER", "Данные аватара для $senderEmail обновлены точечно")
                    }
                }
            } catch (e: Exception) {
                Log.e("MESSAGE_HANDLER", "Ошибка сохранения аватара: ${e.message}")
            }
        }
    }

    private fun extractTextFromMessage(msg: MimeMessage): String {
        if (msg.isMimeType("text/plain")) return msg.content.toString()
        if (msg.isMimeType("multipart/*")) {
            val multipart = msg.content as MimeMultipart
            for (i in 0 until multipart.count) {
                val part = multipart.getBodyPart(i)
                if (part.isMimeType("text/plain")) return part.content.toString()
            }
        }
        return ""
    }

    private fun showNotification(context: Context, chatId: String, text: String, title: String) {
        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_CHAT_EMAIL", chatId)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(context, chatId.hashCode(), intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "LAIVA_MESSAGES")
            .setContentTitle(title).setContentText(text).setSmallIcon(R.drawable.grok_image_52e1455a_).setContentIntent(pendingIntent).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        NotificationManagerCompat.from(context).notify(chatId.hashCode(), notification)
    }

    private suspend fun fallbackProcess(context: Context, msg: MimeMessage, repository: MessengerRepository, senderEmail: String, serverMessageId: String, key: String?, displayName: String, activeChatEmail: String?, chatId: String, isGroupMessage: Boolean) {
        if (msg.isMimeType("text/plain")) {
            val text = msg.content.toString()
            addMessageToDb(repository, isGroupMessage, chatId, senderEmail, text, serverMessageId, "text", null)
        }
    }
}