package com.hackerlauncher.chat

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Database
import androidx.room.RoomDatabase

@Entity(tableName = "chat_messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "default"
)

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: Message): Long

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesBySession(sessionId: String): List<Message>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 100): List<Message>

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun getCount(): Int
}

@Database(entities = [Message::class], version = 1, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
