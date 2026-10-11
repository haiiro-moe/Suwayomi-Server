package suwayomi.tachidesk.manga.impl.backup.proto.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/** Snapshot of multi-user state owned by the instance owner. */
@Serializable
data class BackupUserData(
    @ProtoNumber(1) val roles: List<BackupUserRole> = emptyList(),
    @ProtoNumber(2) val permissions: List<BackupUserPermission> = emptyList(),
    @ProtoNumber(3) val users: List<BackupUser> = emptyList(),
    @ProtoNumber(4) val rolePermissions: List<BackupRolePermission> = emptyList(),
    @ProtoNumber(5) val categoryAccess: List<BackupUserCategoryAccess> = emptyList(),
    @ProtoNumber(6) val chapterStates: List<BackupUserChapterState> = emptyList(),
    @ProtoNumber(7) val profiles: List<BackupUserProfile> = emptyList(),
    @ProtoNumber(8) val favorites: List<BackupUserFavorite> = emptyList(),
    @ProtoNumber(9) val messages: List<BackupUserMessage> = emptyList(),
    @ProtoNumber(10) val settings: List<BackupUserSetting> = emptyList(),
)

@Serializable data class BackupUserRole(@ProtoNumber(1) val id: Int = 0, @ProtoNumber(2) val name: String = "", @ProtoNumber(3) val description: String = "")
@Serializable data class BackupUserPermission(@ProtoNumber(1) val id: Int = 0, @ProtoNumber(2) val node: String = "")
@Serializable data class BackupRolePermission(@ProtoNumber(1) val roleId: Int = 0, @ProtoNumber(2) val permissionId: Int = 0)
@Serializable data class BackupUser(@ProtoNumber(1) val id: Int = 0, @ProtoNumber(2) val username: String = "", @ProtoNumber(3) val passwordHash: String = "", @ProtoNumber(4) val displayName: String = "", @ProtoNumber(5) val avatarUrl: String? = null, @ProtoNumber(6) val roleId: Int? = null, @ProtoNumber(7) val enabled: Boolean = true)
@Serializable data class BackupUserCategoryAccess(@ProtoNumber(1) val userId: Int = 0, @ProtoNumber(2) val categoryId: Int = 0, @ProtoNumber(3) val canRead: Boolean = false, @ProtoNumber(4) val canEdit: Boolean = false)
@Serializable data class BackupUserChapterState(@ProtoNumber(1) val userId: Int = 0, @ProtoNumber(2) val chapterId: Int = 0, @ProtoNumber(3) val isRead: Boolean = false, @ProtoNumber(4) val isBookmarked: Boolean = false, @ProtoNumber(5) val lastPageRead: Int = 0, @ProtoNumber(6) val lastReadAt: Long = 0)
@Serializable data class BackupUserProfile(@ProtoNumber(1) val userId: Int = 0, @ProtoNumber(2) val description: String = "")
@Serializable data class BackupUserFavorite(@ProtoNumber(1) val userId: Int = 0, @ProtoNumber(2) val mangaId: Int = 0)
@Serializable data class BackupUserMessage(@ProtoNumber(1) val id: Int = 0, @ProtoNumber(2) val senderId: Int = 0, @ProtoNumber(3) val receiverId: Int = 0, @ProtoNumber(4) val parentId: Int? = null, @ProtoNumber(5) val content: String = "", @ProtoNumber(6) val createdAt: Long = 0, @ProtoNumber(7) val readAt: Long? = null)
@Serializable data class BackupUserSetting(@ProtoNumber(1) val userId: Int = 0, @ProtoNumber(2) val key: String = "", @ProtoNumber(3) val value: String = "")
