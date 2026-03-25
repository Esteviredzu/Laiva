package com.example.laiva.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID
import androidx.paging.*
import com.example.laiva.DataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.flow.map
import javax.mail.*

import java.util.Properties


class MessengerRepository(    private val db: AppDatabase,
                              private val dataStoreManager: DataStoreManager
) {

    private val contactDao = db.contactDao()
    private val messageDao = db.messageDao()



    suspend fun getMyEmail(): String? {
        return dataStoreManager.emailFlow.first()
    }
    suspend fun getMyEmailPassword(): String? {
        return dataStoreManager.passwordFlow.first()
    }

    suspend fun getMyPrivateKey(): String? {
        return dataStoreManager.privateKeyFlow.first()
    }


    suspend fun getMyPublicKey(): String? {
        return dataStoreManager.publicKeyFlow.first()
    }

    suspend fun getChatName(chatId: String): String? {
        val chat = chatDao.getChatById(chatId)
        return chat?.name
    }
    suspend fun getEncryptionKey(chatId: String): String? {


        val contact = contactDao.getContactByEmail(chatId)
        val key = contact?.encryptionKey

        if(key != null) {
            Log.d("GetEncyptionKey", "Я получил ключ из таблицы контактов")
            return contact?.encryptionKey
        } else{
            val member_key = chatMemberDao.getMemberPublicKey(chatId)
            Log.d("GetEncyptionKey", "Я получил ключ из таблицы участников чатов")
            return member_key
        }


    }
    private val chatDao = db.chatDao()
    private val chatMemberDao = db.chatMemberDao()

    private var persistentTransport: Transport? = null
    private var mailSession: Session? = null

    suspend fun getConnectedTransport(
        smtpHost: String,
        smtpPort: Int,
        fromEmail: String,
        pass: String
    ): Pair<Transport, Session> {
        return withContext(Dispatchers.IO) {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", smtpHost)
                put("mail.smtp.port", smtpPort.toString())
                put("mail.smtp.ssl.trust", "*")
                put("mail.smtp.timeout", "15000")
                put("mail.smtp.connectiontimeout", "15000")
            }

            val session = mailSession ?: Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(fromEmail, pass)
            })
            mailSession = session

            val transport = persistentTransport ?: session.getTransport("smtp")

            if (!transport.isConnected) {
                Log.d("LAIVA_SMTP", "SMTP: Установка нового соединения с $smtpHost...")
                transport.connect(smtpHost, fromEmail, pass)
                persistentTransport = transport
            }
            Pair(transport, session)
        }
    }

    fun closeSmtpConnection() {
        try {
            persistentTransport?.close()
            persistentTransport = null
            mailSession = null
        } catch (e: Exception) {
            Log.e("LAIVA_SMTP", "Ошибка при закрытии SMTP", e)
        }
    }


    suspend fun addContact(email: String, name: String, encryptionKey: String? = null) {
        contactDao.addContact(
            ContactEntity(
                email = email,
                name = name,
                encryptionKey = encryptionKey ?: ""
            )
        )
    }

    fun getAllContacts(): Flow<List<ContactEntity>> =
        contactDao.getAllContacts()

    suspend fun getContact(email: String): ContactEntity? =
        contactDao.getContactByEmail(email)

    suspend fun removeContact(email: String) {
        contactDao.getContactByEmail(email)?.let { contactDao.deleteContact(it) }
    }

    suspend fun deleteContactCompletely(email: String) {
        val contact = contactDao.getContactByEmail(email)
        if (contact != null) {
            try {
                contact.avatarPath?.let { path ->
                    try {
                        val avatarFile = File(path)
                        if (avatarFile.exists()) {
                            if (avatarFile.delete()) {
                                Log.d("MESSENGER_REPO", "Файл аватарки удален: $path")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MESSENGER_REPO", "Ошибка удаления аватарки $path", e)
                    }
                }

                val messages = messageDao.getMessagesByChat(email).first()
                messages.forEach { msg ->
                    msg.localPath?.let { path ->
                        try {
                            val file = File(path)
                            if (file.exists()) {
                                file.delete()
                            }
                        } catch (e: Exception) {
                            Log.e("MESSENGER_REPO", "Ошибка удаления файла сообщения $path", e)
                        }
                    }
                }

                messageDao.deleteMessagesByChat(email)
                contactDao.deleteContact(contact)

                Log.d("MESSENGER_REPO", "Контакт $email, его аватарка и переписка полностью удалены")
            } catch (e: Exception) {
                Log.e("MESSENGER_REPO", "Ошибка при полном удалении контакта $email", e)
            }
        } else {
            Log.w("MESSENGER_REPO", "Контакт $email не найден")
        }
    }

    suspend fun renameContact(email: String, newName: String) {
        val contact = contactDao.getContactByEmail(email)
        if (contact != null) {
            contactDao.addContact(contact.copy(name = newName))
            Log.d("MESSENGER_REPO", "Контакт $email переименован в $newName")
        } else {
            Log.w("MESSENGER_REPO", "Контакт $email не найден для переименования")
        }
    }


    fun getMessages(contactEmail: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesByChat(contactEmail)

    suspend fun addIncomingMessage(
        contactEmail: String,
        text: String,
        serverMessageId: String,
        type: String = "text",
        localPath: String? = null
    ) {
        Log.d("MESSENGER_REPO", "ДОБАВЛЕНИЕ СООБЩЕНИЯ: contact=$contactEmail, serverId=$serverMessageId, text=$text, type=$type, localPath=$localPath")
        messageDao.addMessage(
            MessageEntity(
                messageId = serverMessageId,
                chatId = contactEmail,
                senderEmail = contactEmail,
                text = text,
                timestamp = System.currentTimeMillis(),
                fromMe = false,
                type = type,
                localPath = localPath
            )
        )

        val contact = contactDao.getContactByEmail(contactEmail)
        contact?.let {
            contactDao.addContact(it.copy(hasNewMessages = true))
        }
    }

    suspend fun markAsRead(contactEmail: String) {
        val contact = contactDao.getContactByEmail(contactEmail)
        if (contact?.hasNewMessages == true) {
            contactDao.addContact(contact.copy(hasNewMessages = false))
        }
    }


    suspend fun hasEncryptionKey(chatId: String): Boolean {
        val chat = chatDao.getChatById(chatId)
        if (chat != null) return chat.encryptionKey.isNotEmpty()

        val contact = contactDao.getContactByEmail(chatId)
        return contact?.encryptionKey?.isNotEmpty() == true
    }
    suspend fun getChat(chatId: String): ChatEntity? {

        val chat = chatDao.getChatById(chatId)
        if (chat != null) return chat

        val contact = contactDao.getContactByEmail(chatId)
        return contact?.let { c ->
            ChatEntity(
                id = c.email,
                name = c.name,
                isGroup = false,
                encryptionKey = c.encryptionKey ?: ""
            )
        }
    }
    suspend fun saveEncryptionKey(chatId: String, key: String, isGroup: Boolean) {
        if (isGroup) {
            val chat = chatDao.getChatById(chatId)
            if (chat != null) chatDao.addChat(chat.copy(encryptionKey = key))
        } else {
            val contact = contactDao.getContactByEmail(chatId)
            if (contact != null) contactDao.addContact(contact.copy(encryptionKey = key))
        }
    }
    suspend fun addOutgoingMessage(
        contactEmail: String,
        text: String,
        type: String? = null,
        localPath: String? = null
    ): Long {
        val myEmail = dataStoreManager.emailFlow.first()
        val tempId = UUID.randomUUID().toString()
        val entity = MessageEntity(
            messageId = tempId,
            chatId = contactEmail,
            senderEmail = myEmail ?: "",
            text = text,
            timestamp = System.currentTimeMillis(),
            fromMe = true,
            status = "pending",
            type = type ?: "text",
            localPath = localPath
        )
        return messageDao.addMessage(entity)
    }

    suspend fun updateStatus(id: Long, status: String) {
        messageDao.updateStatus(id, status)
    }

    suspend fun syncMessages(
        context: Context,
        imapHost: String,
        imapPort: Int,
        email: String,
        password: String,
        contactEmail: String,
    ) {
        val contact = getContact(contactEmail)

        withContext(Dispatchers.IO) {
            val props = java.util.Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", imapHost)
                put("mail.imaps.port", imapPort.toString())
                put("mail.imaps.ssl.trust", "*")
                put("mail.imaps.timeout", "10000")
                put("mail.imaps.connectiontimeout", "10000")
            }

            val session = javax.mail.Session.getInstance(props)
            var store: Store? = null
            var inbox: Folder? = null

            try {
                Log.d("MESSENGER_REPO", "Подключение к IMAP для $contactEmail...")
                store = session.getStore("imaps")
                store.connect(imapHost, email, password)

                inbox = store.getFolder("INBOX")

                inbox.open(javax.mail.Folder.READ_WRITE)

                try {
                    var hasNew = false
                    var hasDeletedAny = false

                    val allMsgs = inbox.messages

                    val msgs = allMsgs

                        .asSequence()
                        .filter {
                            try {
                                it.from?.firstOrNull()?.toString()?.contains(contactEmail, ignoreCase = true) == true
                            } catch (e: Exception) { false }
                        }
                        .take(30)
                        .toList()

                    for (msg in msgs) {
                        try {
                            if (msg is MimeMessage) {

                                val isLaiva = msg.getHeader("Laiva-App")?.isNotEmpty() ?: false

                                if (isLaiva) {
                                    MessageHandler.processIncomingMessage(
                                        context,
                                        msg,
                                        this@MessengerRepository,
                                        contactEmail
                                    )

                                    msg.setFlag(Flags.Flag.SEEN, true)
                                    msg.setFlag(Flags.Flag.DELETED, true)

                                    hasNew = true
                                    hasDeletedAny = true
                                    Log.d("MESSENGER_REPO", "Сообщение от $contactEmail обработано и помечено к удалению")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("MESSENGER_REPO", "Ошибка обработки письма: ${e.message}")
                        }
                    }

                    if (hasDeletedAny) {
                        inbox.expunge()
                    }

                    if (hasNew) {
                        contact?.let {
                            contactDao.addContact(it.copy(hasNewMessages = true))
                        }
                    }
                } finally {
                    if (inbox != null && inbox.isOpen) {
                        inbox.close(true)
                    }
                }
            } catch (e: Exception) {
                Log.e("MESSENGER_REPO", "Ошибка IMAP синхронизации: ${e.message}")
            } finally {
                try {
                    if (store != null && store.isConnected) {
                        store.close()
                    }
                } catch (e: Exception) {
                    Log.e("MESSENGER_REPO", "Ошибка при закрытии store: ${e.message}")
                }
            }
        }
    }

    fun getMessagesPaged(chatId: String): Flow<PagingData<MessageEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                prefetchDistance = 10,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { messageDao.getMessagesPaging(chatId) }
        ).flow
    }



    suspend fun getChatMemberEmails(chatId: String): List<String> {
        return chatMemberDao.getEmails(chatId)
    }

    suspend fun addIncomingMessageToChat(
        chatId: String,
        senderEmail: String,
        text: String,
        serverMessageId: String,
        type: String = "text",
        localPath: String? = null
    ) {
        messageDao.addMessage(
            MessageEntity(
                messageId = serverMessageId,
                chatId = chatId,
                senderEmail = senderEmail,
                text = text,
                timestamp = System.currentTimeMillis(),
                fromMe = false,
                type = type,
                localPath = localPath
            )
        )
    }
    suspend fun addOutgoingMessageToChat(
        chatId: String,
        text: String,
        type: String? = null,
        localPath: String? = null
    ): Long {
        val tempId = UUID.randomUUID().toString()
        val entity = MessageEntity(
            messageId = tempId,
            chatId = chatId,
            senderEmail = "",
            text = text,
            timestamp = System.currentTimeMillis(),
            fromMe = true,
            status = "pending",
            type = type ?: "text",
            localPath = localPath
        )
        return messageDao.addMessage(entity)
    }



    suspend fun createGroup(
        name: String,
        members: List<String>,
        encryptionKey: String? = null,
        id: String? = null
    ): String {
        val chatId = id ?: UUID.randomUUID().toString()

        chatDao.addChat(
            ChatEntity(
                id = chatId,
                name = name,
                isGroup = true,
                encryptionKey = encryptionKey ?: ""
            )
        )

        members.forEach { email ->
            chatMemberDao.addMember(ChatMemberEntity(chatId, email))
        }

        return chatId
    }

    suspend fun getGroupMembers(chatId: String): List<String> {
        return db.chatMemberDao().getMembers(chatId).map { it.email }
    }


    fun getAllGroups(): Flow<List<ChatEntity>> =
        db.chatDao().getAllChats().map { it.filter { chat -> chat.isGroup } }



    suspend fun renameChat(chatId: String, newName: String) {
        val chat = chatDao.getChatById(chatId)
        if (chat != null) {
            chatDao.addChat(chat.copy(name = newName))
            Log.d("MESSENGER_REPO", "Чат $chatId переименован в $newName")
        } else {
            Log.w("MESSENGER_REPO", "Чат $chatId не найден для переименования")
        }
    }

    suspend fun deleteChat(chatId: String) {
        val chat = chatDao.getChatById(chatId)
        if (chat != null) {
            messageDao.deleteMessagesByChat(chatId)

            val members = chatMemberDao.getMembers(chatId)
            members.forEach { member ->
                chatMemberDao.removeMember(chatId, member.email)
            }

            chatDao.deleteChat(chat)
            Log.d("MESSENGER_REPO", "Чат $chatId и все данные удалены")
        } else {
            Log.w("MESSENGER_REPO", "Чат $chatId не найден для удаления")
        }
    }

    suspend fun addChatMember(chatId: String, email: String) {
        val chat = chatDao.getChatById(chatId)
        if (chat != null && chat.isGroup) {
            chatMemberDao.addMember(ChatMemberEntity(chatId, email))
            Log.d("MESSENGER_REPO", "Пользователь $email добавлен в чат $chatId")
        } else {
            Log.w("MESSENGER_REPO", "Невозможно добавить участника, чат $chatId не найден или это не группа")
        }
    }

    suspend fun removeChatMember(chatId: String, email: String) {
        val chat = chatDao.getChatById(chatId)
        if (chat != null && chat.isGroup) {
            chatMemberDao.removeMember(chatId, email)
            Log.d("MESSENGER_REPO", "Пользователь $email удален из чата $chatId")
        } else {
            Log.w("MESSENGER_REPO", "Невозможно удалить участника, чат $chatId не найден или это не группа")
        }
    }



    suspend fun getGroupMemberKeys(chatId: String): Map<String, String> {
        return chatMemberDao.getGroupKeys(chatId).associate { it.email to it.publicKey }
    }

    suspend fun updateMemberPublicKey(chatId: String, email: String, publicKey: String) {
        chatMemberDao.addMember(ChatMemberEntity(chatId, email, publicKey))
    }



    suspend fun updateContactPublicKey(email: String, newKey: String) {
        contactDao.updatePublicKey(email, newKey)
    }

    suspend fun updateContactAvatar(email: String, path: String, hash: String?) {
        contactDao.updateAvatarData(email, path, hash)
    }

    fun getContactDao() = contactDao

}








