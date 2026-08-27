package com.texto.sms.interfaces

import androidx.room.Dao
import androidx.room.Query
import com.texto.sms.models.Attachment

@Dao
interface AttachmentsDao {
    @Query("SELECT * FROM attachments")
    fun getAll(): List<Attachment>
}
