package com.texto.sms.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.texto.sms.models.MessageAttachment

@Dao
interface MessageAttachmentsDao {
    @Query("SELECT * FROM message_attachments")
    fun getAll(): List<MessageAttachment>
}
