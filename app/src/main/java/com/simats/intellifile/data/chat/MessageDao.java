package com.simats.intellifile.data.chat;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(MessageEntity message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessages(List<MessageEntity> messages);

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    List<MessageEntity> getMessagesForConversation(String conversationId);

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    MessageEntity getLastMessageForConversation(String conversationId);

    @Query("UPDATE messages SET messageText = :newText WHERE messageId = :messageId")
    void updateMessageText(String messageId, String newText);

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    void deleteMessagesForConversation(String conversationId);

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    void deleteMessageById(String messageId);

    @Query("DELETE FROM messages")
    void deleteAllMessages();
}
