package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.user.model.UserMessageTable
import suwayomi.tachidesk.server.user.model.UserTable
import java.time.Instant

object UserMessageService {
    data class Message(
        val id: Int,
        val senderId: Int,
        val receiverId: Int,
        val parentId: Int?,
        val content: String,
        val createdAt: Long,
        val readAt: Long?,
    )

    fun send(senderId: Int, receiverId: Int, content: String, parentId: Int? = null): Message {
        val normalized = content.trim()
        require(normalized.isNotEmpty()) { "Message content must not be empty" }
        require(normalized.length <= 4000) { "Message content is too long" }
        require(senderId != receiverId) { "Users cannot message themselves" }
        return transaction(DBManager.db) {
            require(UserTable.selectAll().where { UserTable.id eq receiverId }.any()) { "Receiver does not exist" }
            val parent = parentId?.let {
                UserMessageTable.selectAll().where { UserMessageTable.id eq it }.firstOrNull()
                    ?: error("Parent message does not exist")
            }
            if (parent != null) {
                require(parent[UserMessageTable.parent] == null) { "Replies may only target top-level messages" }
                require(
                    (parent[UserMessageTable.sender].value == senderId && parent[UserMessageTable.receiver].value == receiverId) ||
                        (parent[UserMessageTable.sender].value == receiverId && parent[UserMessageTable.receiver].value == senderId),
                ) { "Parent message is outside this conversation" }
            }
            val id = UserMessageTable.insertAndGetId {
                it[sender] = senderId
                it[receiver] = receiverId
                it[UserMessageTable.parent] = parentId
                it[UserMessageTable.content] = normalized
                it[createdAt] = Instant.now().epochSecond
            }
            toMessage(UserMessageTable.selectAll().where { UserMessageTable.id eq id }.single())
        }
    }

    fun conversation(userId: Int, otherUserId: Int): List<Message> = transaction(DBManager.db) {
        UserMessageTable.selectAll().where {
            ((UserMessageTable.sender eq userId) and (UserMessageTable.receiver eq otherUserId)) or
                ((UserMessageTable.sender eq otherUserId) and (UserMessageTable.receiver eq userId))
        }.orderBy(UserMessageTable.createdAt).map(::toMessage)
    }

    fun markRead(userId: Int, messageId: Int) {
        transaction(DBManager.db) {
            UserMessageTable.update({ (UserMessageTable.id eq messageId) and (UserMessageTable.receiver eq userId) }) {
                it[readAt] = Instant.now().epochSecond
            }
        }
    }

    fun unreadCount(userId: Int): Long = transaction(DBManager.db) {
        UserMessageTable.selectAll().where { (UserMessageTable.receiver eq userId) and UserMessageTable.readAt.isNull() }.count()
    }

    private fun toMessage(row: org.jetbrains.exposed.v1.core.ResultRow) = Message(
        id = row[UserMessageTable.id].value,
        senderId = row[UserMessageTable.sender].value,
        receiverId = row[UserMessageTable.receiver].value,
        parentId = row[UserMessageTable.parent]?.value,
        content = row[UserMessageTable.content],
        createdAt = row[UserMessageTable.createdAt],
        readAt = row[UserMessageTable.readAt],
    )
}
