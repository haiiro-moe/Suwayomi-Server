package suwayomi.tachidesk.server.user

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.model.PermissionTable
import suwayomi.tachidesk.server.user.model.RolePermissionTable
import suwayomi.tachidesk.server.user.model.RoleTable
import suwayomi.tachidesk.server.user.model.UserTable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val OWNER_ROLE = "owner"
private const val HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
private const val HASH_ITERATIONS = 120_000
private const val HASH_BYTES = 32
private const val SALT_BYTES = 16

object UserService {
    fun ensureBootstrapUser(): Int {
        return transaction(DBManager.db) {
            val ownerRole =
                RoleTable
                    .selectAll()
                    .where { RoleTable.name eq OWNER_ROLE }
                    .firstOrNull()
                    ?.get(RoleTable.id)
                    ?: RoleTable.insertAndGetId {
                        it[name] = OWNER_ROLE
                        it[description] = "Instance owner"
                    }

            val username = serverConfig.authUsername.value
            val existingUser =
                UserTable
                    .selectAll()
                    .where { UserTable.username eq username }
                    .firstOrNull()

            if (existingUser == null) {
                UserTable.insertAndGetId {
                    it[UserTable.username] = username
                    it[passwordHash] = hashPassword(serverConfig.authPassword.value)
                    it[displayName] = username
                    it[role] = ownerRole
                }.value
            } else {
                existingUser[UserTable.id].value
            }
        }
    }

    fun findEnabledUserId(username: String): Int? =
        transaction(DBManager.db) {
            UserTable
                .selectAll()
                .where { (UserTable.username eq username) and (UserTable.enabled eq true) }
                .firstOrNull()
                ?.get(UserTable.id)
                ?.value
        }

    fun authenticate(username: String, password: String): Int? =
        transaction(DBManager.db) {
            val user =
                UserTable
                    .selectAll()
                    .where { (UserTable.username eq username) and (UserTable.enabled eq true) }
                    .firstOrNull()
                    ?: return@transaction null

            if (!verifyPassword(password, user[UserTable.passwordHash])) {
                return@transaction null
            }
            user[UserTable.id].value
        }

    fun hasPermission(userId: Int, node: String): Boolean =
        transaction(DBManager.db) {
            val userRole =
                UserTable
                    .innerJoin(RoleTable)
                    .selectAll()
                    .where { UserTable.id eq userId }
                    .firstOrNull()
                    ?.get(RoleTable.name)
            if (userRole == OWNER_ROLE) {
                return@transaction true
            }

            RolePermissionTable
                .innerJoin(UserTable)
                .innerJoin(PermissionTable)
                .selectAll()
                .where {
                    (UserTable.id eq userId) and
                        (PermissionTable.node eq node)
                }.count() > 0
        }

    fun hashPasswordForAdmin(password: String): String = hashPassword(password)

    private fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val hash = derive(password, salt)
        return listOf(
            "pbkdf2",
            HASH_ITERATIONS.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash),
        ).joinToString("$")
    }

    private fun verifyPassword(password: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val salt = runCatching { Base64.getDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, derive(password, salt, iterations))
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int = HASH_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BYTES * 8)
        return SecretKeyFactory.getInstance(HASH_ALGORITHM).generateSecret(spec).encoded
    }
}
