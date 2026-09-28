@file:Suppress("RedundantNullableReturnType", "unused")

package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import suwayomi.tachidesk.global.impl.util.Jwt
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermission
import suwayomi.tachidesk.graphql.server.getAttribute
import suwayomi.tachidesk.server.JavalinSetup.Attribute
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.PermissionNodes
import suwayomi.tachidesk.server.user.UserMessageService
import suwayomi.tachidesk.server.user.UserProfileService
import suwayomi.tachidesk.server.user.UserService
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.server.user.requireUser


class UserMutation {
    data class LoginInput(
        val clientMutationId: String? = null,
        val username: String,
        val password: String,
    )

    data class LoginPayload(
        val clientMutationId: String?,
        val accessToken: String,
        val refreshToken: String,
    )

    data class SetupOwnerInput(
        val clientMutationId: String? = null,
        val username: String,
        val password: String,
    )

    data class SetupOwnerPayload(
        val clientMutationId: String?,
        val accessToken: String,
        val refreshToken: String,
    )

    fun setupOwner(dataFetchingEnvironment: DataFetchingEnvironment, input: SetupOwnerInput): SetupOwnerPayload {
        if (dataFetchingEnvironment.getAttribute(Attribute.TachideskUser) !is UserType.Visitor) {
            throw IllegalArgumentException("Cannot setup while already logged-in")
        }
        val userId = UserService.setupOwner(input.username, input.password)
        val jwt = Jwt.generateJwt(userId)
        return SetupOwnerPayload(input.clientMutationId, jwt.accessToken, jwt.refreshToken)
    }

    fun login(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: LoginInput,
    ): LoginPayload {
        if (dataFetchingEnvironment.getAttribute(Attribute.TachideskUser) !is UserType.Visitor) {
            throw IllegalArgumentException("Cannot login while already logged-in")
        }
        val userId =
            if (serverConfig.authMode.value == suwayomi.tachidesk.graphql.types.AuthMode.UI_LOGIN) {
                UserService.authenticate(input.username, input.password)
            } else {
                if (input.username == serverConfig.authUsername.value && input.password == serverConfig.authPassword.value) 1 else null
            }
        if (userId != null) {
            val jwt = Jwt.generateJwt(userId)
            return LoginPayload(
                clientMutationId = input.clientMutationId,
                accessToken = jwt.accessToken,
                refreshToken = jwt.refreshToken,
            )
        } else {
            throw Exception("Incorrect username or password.")
        }
    }

    data class RefreshTokenInput(
        val clientMutationId: String? = null,
        val refreshToken: String,
    )

    data class UpdateProfileInput(
        val clientMutationId: String? = null,
        val displayName: String? = null,
        val avatarUrl: String? = null,
        val bannerUrl: String? = null,
        val description: String,
    )

    data class ProfileMutationPayload(
        val clientMutationId: String?,
        val updated: Boolean,
    )

    @RequireAuth
    fun updateProfile(dataFetchingEnvironment: DataFetchingEnvironment, input: UpdateProfileInput): ProfileMutationPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        UserProfileService.updateProfile(userId, input.displayName, input.avatarUrl, input.bannerUrl, input.description)
        return ProfileMutationPayload(input.clientMutationId, true)
    }

    data class FavoriteMangaInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
    )

    @RequireAuth
    fun addFavorite(dataFetchingEnvironment: DataFetchingEnvironment, input: FavoriteMangaInput): ProfileMutationPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        UserProfileService.addFavorite(userId, input.mangaId)
        return ProfileMutationPayload(input.clientMutationId, true)
    }

    @RequireAuth
    fun removeFavorite(dataFetchingEnvironment: DataFetchingEnvironment, input: FavoriteMangaInput): ProfileMutationPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        UserProfileService.removeFavorite(userId, input.mangaId)
        return ProfileMutationPayload(input.clientMutationId, true)
    }

    data class SetMangaNoteInput(
        val clientMutationId: String? = null,
        val mangaId: Int,
        val note: String,
    )

    @RequirePermission(PermissionNodes.MANGA_NOTES_WRITE)
    fun setMangaNote(dataFetchingEnvironment: DataFetchingEnvironment, input: SetMangaNoteInput): ProfileMutationPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        UserProfileService.setMangaNote(userId, input.mangaId, input.note)
        return ProfileMutationPayload(input.clientMutationId, true)
    }

    data class SendMessageInput(
        val clientMutationId: String? = null,
        val receiverId: Int,
        val content: String,
        val parentId: Int? = null,
    )

    data class MessageMutationPayload(
        val clientMutationId: String?,
        val messageId: Int,
    )

    @RequireAuth
    fun sendMessage(dataFetchingEnvironment: DataFetchingEnvironment, input: SendMessageInput): MessageMutationPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        val message = UserMessageService.send(userId, input.receiverId, input.content, input.parentId)
        return MessageMutationPayload(input.clientMutationId, message.id)
    }

    data class MarkMessageReadInput(
        val clientMutationId: String? = null,
        val messageId: Int,
    )

    @RequireAuth
    fun markMessageRead(dataFetchingEnvironment: DataFetchingEnvironment, input: MarkMessageReadInput): ProfileMutationPayload {
        val userId = dataFetchingEnvironment.getAttribute(Attribute.TachideskUser).requireUser()
        UserMessageService.markRead(userId, input.messageId)
        return ProfileMutationPayload(input.clientMutationId, true)
    }

    data class RefreshTokenPayload(
        val clientMutationId: String?,
        val accessToken: String,
    )

    fun refreshToken(input: RefreshTokenInput): RefreshTokenPayload {
        val accessToken = Jwt.refreshJwt(input.refreshToken)

        return RefreshTokenPayload(
            clientMutationId = input.clientMutationId,
            accessToken = accessToken,
        )
    }
}
