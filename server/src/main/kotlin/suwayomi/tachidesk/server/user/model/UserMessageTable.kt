package suwayomi.tachidesk.server.user.model

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object UserMessageTable : IntIdTable("user_messages") {
    val sender = reference("sender_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val receiver = reference("receiver_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val parent = reference("parent_id", this, onDelete = ReferenceOption.CASCADE).nullable()
    val content = varchar("content", 4000)
    val createdAt = long("created_at")
    val readAt = long("read_at").nullable()
}
