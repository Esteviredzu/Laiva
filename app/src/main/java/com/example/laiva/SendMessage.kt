package com.example.laiva

import com.example.laiva.data.MessengerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.*
import android.content.Context
import com.example.laiva.data.CryptoManager


suspend fun sendEmail(
    repository: MessengerRepository,
    smtpHost: String,
    smtpPort: Int,
    fromEmail: String,
    password: String,
    type: String? = "unknown",
    myPublicKey: String? = null,
    toEmails: List<String>,
    subject: String,
    body: String?,
    recipientKeys: Map<String, String> = emptyMap(),
    filePath: String? = null,
    group_id: String? = null,


    allMemberKeys: Map<String, String>? = null,


): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val (transport, session) = repository.getConnectedTransport(
                smtpHost, smtpPort, fromEmail, password
            )

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(fromEmail))
                setRecipients(
                    javax.mail.Message.RecipientType.TO,
                    InternetAddress.parse(toEmails.joinToString(","))
                )
                setSubject(subject)

                val avatarFile = File("/data/data/com.example.laiva/files/avatars/me.jpg")
                val avatarHash = getAvatarHash(avatarFile)
                avatarHash?.let { hash -> setHeader("X-Laiva-Avatar-Hash", hash) }

                setHeader("Laiva-App", "true")
                setHeader("X-Laiva", "1")
                setHeader("X-Laiva-Type", type)
                setHeader("X-Laiva-Version", "1")

                recipientKeys.forEach { (email, encKey) ->
                    setHeader("X-Laiva-Key-$email", encKey)
                }
                myPublicKey?.let { key -> setHeader("X-Laiva-PubKey", key) }
                setHeader("X-Laiva-Group-Id", group_id)

                if (!group_id.isNullOrEmpty()) {
                    setHeader("X-Laiva-Group-Id", group_id)

                    allMemberKeys?.forEach { (email, pubKey) ->
                        setHeader("X-Laiva-Member-PubKey-$email", pubKey)
                    }
                }

                if (!group_id.isNullOrEmpty() && toEmails.size > 1) {
                    val membersHeader = (toEmails + fromEmail).distinct().joinToString(",")
                    setHeader("X-Laiva-Members", membersHeader)
                }
            }

            if (filePath == null) {
                message.setText(body ?: "")
            } else {
                val multipart = MimeMultipart()
                if (!body.isNullOrEmpty()) {
                    val textPart = MimeBodyPart()
                    textPart.setText(body)
                    multipart.addBodyPart(textPart)
                }

                val filePart = MimeBodyPart()
                val file = File(filePath)
                val source = FileDataSource(file)
                filePart.dataHandler = DataHandler(source)
                filePart.fileName = file.name
                filePart.disposition = MimeBodyPart.ATTACHMENT

                val mimeType = when (file.extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "pdf" -> "application/pdf"
                    "txt" -> "text/plain"
                    else -> "application/octet-stream"
                }
                filePart.setHeader("Content-Type", mimeType)
                multipart.addBodyPart(filePart)
                message.setContent(multipart)
            }

            transport.sendMessage(message, message.allRecipients)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            repository.closeSmtpConnection()
            false
        }
    }
}

suspend fun sendAvatarRequest(
    repository: MessengerRepository,
    smtpHost: String,
    smtpPort: Int,
    fromEmail: String,
    password: String,
    toEmail: String
): Boolean {
    return sendEmail(
        repository = repository,
        smtpHost = smtpHost,
        smtpPort = smtpPort,
        fromEmail = fromEmail,
        password = password,
        type = "avatar_request",
        toEmails = listOf(toEmail),
        subject = "Avatar request",
        body = null
    )
}

suspend fun sendAvatarUpdate(
    repository: MessengerRepository,
    context: Context,
    smtpHost: String,
    smtpPort: Int,
    fromEmail: String,
    password: String,
    toEmail: String
): Boolean {
    val avatarFile = File(context.filesDir, "avatars/me.jpg")

    if (!avatarFile.exists()) return false

    val contact = repository.getContact(toEmail)
    val recipientPubKey = contact?.encryptionKey

    if (recipientPubKey.isNullOrEmpty()) {
        return sendEmail(
            repository = repository,
            smtpHost = smtpHost,
            smtpPort = smtpPort,
            fromEmail = fromEmail,
            password = password,
            type = "avatar_update",
            toEmails = listOf(toEmail),
            subject = "Avatar update",
            body = null,
            filePath = avatarFile.absolutePath
        )
    }

    val tempEncFile = File(context.cacheDir, "sent_avatar_${System.currentTimeMillis()}.enc")

    return try {
        val encryptedAESKey = CryptoManager.encryptHybridFile(avatarFile, recipientPubKey, tempEncFile)

        sendEmail(
            repository = repository,
            smtpHost = smtpHost,
            smtpPort = smtpPort,
            fromEmail = fromEmail,
            password = password,
            type = "avatar_update",
            toEmails = listOf(toEmail),
            subject = "Avatar update",
            body = null,
            filePath = tempEncFile.absolutePath,
            recipientKeys = mapOf(toEmail to encryptedAESKey)
        )
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        if (tempEncFile.exists()) {
            tempEncFile.delete()
        }
    }
}