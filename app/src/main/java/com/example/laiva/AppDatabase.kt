package com.example.laiva.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.paging.PagingSource

// ----------------------
// Entities
// ----------------------
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val email: String,
    val name: String,
    val encryptionKey: String, //теперь это публичный ключ контакта

    val hasNewMessages: Boolean = false,



    //аватарки
    val avatarPath: String? = null,
    val avatarHash: String? = null
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["messageId"], unique = true),
        Index(value = ["chatId", "timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val messageId: String,
    val chatId: String,       // Контакт или группа
    val senderEmail: String,
    val text: String,
    val timestamp: Long,
    val fromMe: Boolean,
    val status: String = "sent",
    val type: String = "text",
    val localPath: String? = null
)

// Групповые чаты
@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isGroup: Boolean,
    val encryptionKey: String
)

@Entity(
    tableName = "chat_members",
    primaryKeys = ["chatId", "email"]
)
data class ChatMemberEntity(
    val chatId: String,
    val email: String,
    val publicKey: String? = null // Публичный ключ участника для этой группы
)


@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addContact(contact: ContactEntity)

    @Query("SELECT * FROM contacts")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE email = :email LIMIT 1")
    suspend fun getContactByEmail(email: String): ContactEntity?

    @Delete
    suspend fun deleteContact(contact: ContactEntity)




    @Query("UPDATE contacts SET encryptionKey = :key WHERE email = :email")
    suspend fun updatePublicKey(email: String, key: String)

    @Query("UPDATE contacts SET avatarPath = :path, avatarHash = :hash WHERE email = :email")
    suspend fun updateAvatarData(email: String, path: String?, hash: String?)

    @Query("UPDATE contacts SET hasNewMessages = :hasNew WHERE email = :email")
    suspend fun updateNewMessagesFlag(email: String, hasNew: Boolean)


}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addMessage(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC")
    fun getMessagesPaging(chatId: String): PagingSource<Int, MessageEntity>

    @Query("UPDATE messages SET status = :newStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, newStatus: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChat(chatId: String)
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addChat(chat: ChatEntity)

    @Query("SELECT * FROM chats WHERE id = :chatId LIMIT 1")
    suspend fun getChatById(chatId: String): ChatEntity?

    @Query("SELECT * FROM chats")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Delete
    suspend fun deleteChat(chat: ChatEntity)
}

@Dao
interface ChatMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addMember(member: ChatMemberEntity)

    @Query("SELECT * FROM chat_members WHERE chatId = :chatId")
    suspend fun getMembers(chatId: String): List<ChatMemberEntity>

    @Query("SELECT email FROM chat_members WHERE chatId = :chatId")
    suspend fun getEmails(chatId: String): List<String>

    @Query("DELETE FROM chat_members WHERE chatId = :chatId AND email = :email")
    suspend fun removeMember(chatId: String, email: String)


    @Query("SELECT email, publicKey FROM chat_members WHERE chatId = :chatId AND publicKey IS NOT NULL")
    suspend fun getGroupKeys(chatId: String): List<MemberKeyProjection>

    @Query("SELECT publicKey FROM chat_members WHERE email = :email LIMIT 1")
    suspend fun getMemberPublicKey(email: String): String?
}

data class MemberKeyProjection(
    val email: String,
    val publicKey: String
)

@Database(
    entities = [ContactEntity::class, MessageEntity::class, ChatEntity::class, ChatMemberEntity::class],
    version = 19
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun chatMemberDao(): ChatMemberDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "messenger_db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}