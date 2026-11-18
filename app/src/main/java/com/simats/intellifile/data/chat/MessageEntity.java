package com.simats.intellifile.data.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "messages",
    indices = {@Index(value = {"conversationId"})}
)
public class MessageEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "messageId")
    private String messageId;

    @NonNull
    @ColumnInfo(name = "conversationId")
    private String conversationId;

    @NonNull
    @ColumnInfo(name = "messageText")
    private String messageText;

    @NonNull
    @ColumnInfo(name = "senderType")
    private String senderType; // "USER" or "AI"

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @Nullable
    @ColumnInfo(name = "filesJson")
    private String filesJson;

    public MessageEntity(
        @NonNull String messageId,
        @NonNull String conversationId,
        @NonNull String messageText,
        @NonNull String senderType,
        long timestamp,
        @Nullable String filesJson
    ) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.messageText = messageText;
        this.senderType = senderType;
        this.timestamp = timestamp;
        this.filesJson = filesJson;
    }

    @NonNull
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(@NonNull String messageId) {
        this.messageId = messageId;
    }

    @NonNull
    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(@NonNull String conversationId) {
        this.conversationId = conversationId;
    }

    @NonNull
    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(@NonNull String messageText) {
        this.messageText = messageText;
    }

    @NonNull
    public String getSenderType() {
        return senderType;
    }

    public void setSenderType(@NonNull String senderType) {
        this.senderType = senderType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Nullable
    public String getFilesJson() {
        return filesJson;
    }

    public void setFilesJson(@Nullable String filesJson) {
        this.filesJson = filesJson;
    }
}
