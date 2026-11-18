package com.simats.intellifile.data.chat;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertChat(ChatEntity chat);

    @Update
    void updateChat(ChatEntity chat);

    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    List<ChatEntity> getAllChats();

    @Query("SELECT * FROM chats WHERE conversationId = :conversationId LIMIT 1")
    ChatEntity getChatById(String conversationId);

    @Query("SELECT * FROM chats WHERE isActive = 1 ORDER BY updatedAt DESC LIMIT 1")
    ChatEntity getActiveChat();

    @Query("UPDATE chats SET isActive = 0")
    void deactivateAllChats();

    @Query("UPDATE chats SET isActive = 1 WHERE conversationId = :conversationId")
    void setActiveChat(String conversationId);

    @Query("UPDATE chats SET title = :newTitle, updatedAt = :updatedAt WHERE conversationId = :conversationId")
    void updateChatTitle(String conversationId, String newTitle, long updatedAt);

    @Query("UPDATE chats SET updatedAt = :updatedAt WHERE conversationId = :conversationId")
    void updateChatTimestamp(String conversationId, long updatedAt);

    @Query("DELETE FROM chats WHERE conversationId = :conversationId")
    void deleteChatById(String conversationId);

    @Query("DELETE FROM chats")
    void deleteAllChats();
}
