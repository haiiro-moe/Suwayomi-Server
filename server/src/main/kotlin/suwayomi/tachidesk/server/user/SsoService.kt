package suwayomi.tachidesk.server.user

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.server.database.DBManager
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.model.RoleTable
import suwayomi.tachidesk.server.user.model.UserTable
import suwayomi.tachidesk.server.util.ServerSubpath
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

object SsoService {
    private val logger = KotlinLogging.logger {}
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .build()

    private val discoveryCache = ConcurrentHashMap<String, OidcDiscovery>()

    data class OidcDiscovery(
        val authorizationEndpoint: String,
        val tokenEndpoint: String,
        val userinfoEndpoint: String,
    )

    fun isConfigured(): Boolean =
        serverConfig.authMode.value.name == "SSO" &&
            serverConfig.ssoIssuerUrl.value.isNotBlank() &&
            serverConfig.ssoClientId.value.isNotBlank()

    fun issuerUrl(): String = serverConfig.ssoIssuerUrl.value.trim().trimEnd('/')

    fun clientId(): String = serverConfig.ssoClientId.value.trim()

    fun scope(): String = serverConfig.ssoScope.value.ifBlank { "openid profile email" }

    fun publicUrl(): String = serverConfig.ssoPublicUrl.value.trim().trimEnd('/')

    /**
     * The redirect_uri used for the OIDC authorization code flow, sent both to the authorization
     * endpoint and in the token exchange request. Per RFC 6749/OIDC Core, this must be an absolute
     * URI that exactly matches what's registered with the identity provider (e.g. Tinyauth's
     * TRUSTEDREDIRECTURIS) - a bare path is invalid and most spec-compliant providers reject it
     * outright with something like "The provided redirect URI is not trusted".
     *
     * Falls back to a relative path if SUWAIRO_SSO_PUBLIC_URL isn't configured, but SSO will not
     * actually work against a real OIDC provider without it set.
     */
    fun redirectUri(): String {
        val callbackPath = ServerSubpath.maybeAddAsPrefix("/sso/callback")
        val base = publicUrl()
        if (base.isBlank()) {
            logger.warn {
                "SSO is configured but SUWAIRO_SSO_PUBLIC_URL is not set - the redirect_uri sent " +
                    "to the identity provider will be a relative path, which most OIDC providers " +
                    "(including Tinyauth) will reject as untrusted."
            }
            return callbackPath
        }
        return "$base$callbackPath"
    }

    suspend fun discovery(): OidcDiscovery {
        val issuer = issuerUrl()
        discoveryCache[issuer]?.let { return it }

        val configurationUrl =
            if (issuer.endsWith("/.well-known/openid-configuration")) {
                issuer
            } else {
                "$issuer/.well-known/openid-configuration"
            }

        val body =
            withContext(Dispatchers.IO) {
                httpClient
                    .newCall(Request.Builder().url(configurationUrl).build())
                    .execute()
                    .use { response ->
                        require(response.isSuccessful) { "OIDC discovery failed: HTTP ${response.code}" }
                        response.body?.string() ?: error("OIDC discovery returned an empty body")
                    }
            }

        val parsed = json.parseToJsonElement(body).jsonObject
        val discovery =
            OidcDiscovery(
                authorizationEndpoint = parsed.getValue("authorization_endpoint").jsonPrimitive.content,
                tokenEndpoint = parsed.getValue("token_endpoint").jsonPrimitive.content,
                userinfoEndpoint =
                    parsed["userinfo_endpoint"]?.jsonPrimitive?.content
                        ?: error("OIDC discovery response has no userinfo_endpoint"),
            )
        discoveryCache[issuer] = discovery
        return discovery
    }

    fun clientSecret(): String = serverConfig.ssoClientSecret.value

    data class SsoUser(
        val subject: String,
        val username: String,
        val displayName: String,
    )

    suspend fun exchangeCode(code: String, redirectUri: String): SsoUser {
        val discovery = discovery()

        val form =
            FormBody
                .Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", redirectUri)
                .add("client_id", clientId())
                .apply {
                    val secret = clientSecret()
                    if (secret.isNotBlank()) {
                        add("client_secret", secret)
                    }
                }.build()

        val tokenResponseBody =
            withContext(Dispatchers.IO) {
                httpClient
                    .newCall(
                        Request
                            .Builder()
                            .url(discovery.tokenEndpoint)
                            .post(form)
                            .build(),
                    ).execute()
                    .use { response ->
                        val body = response.body?.string().orEmpty()
                        require(response.isSuccessful) {
                            logger.warn { "OIDC token exchange failed: HTTP ${response.code}: $body" }
                            "OIDC token exchange failed: HTTP ${response.code}"
                        }
                        body
                    }
            }

        val accessToken =
            json.parseToJsonElement(tokenResponseBody).jsonObject["access_token"]?.jsonPrimitive?.content
                ?: error("OIDC token response has no access_token")

        val userinfoBody =
            withContext(Dispatchers.IO) {
                httpClient
                    .newCall(
                        Request
                            .Builder()
                            .url(discovery.userinfoEndpoint)
                            .header("Authorization", "Bearer $accessToken")
                            .build(),
                    ).execute()
                    .use { response ->
                        require(response.isSuccessful) { "OIDC userinfo failed: HTTP ${response.code}" }
                        response.body?.string() ?: error("OIDC userinfo returned an empty body")
                    }
            }

        val userinfo = json.parseToJsonElement(userinfoBody).jsonObject
        val subject = userinfo["sub"]?.jsonPrimitive?.content ?: error("OIDC userinfo has no sub claim")
        val preferredUsername =
            userinfo["preferred_username"]?.jsonPrimitive?.content
                ?: userinfo["email"]?.jsonPrimitive?.content?.substringBefore('@')
                ?: subject
        val name = userinfo["name"]?.jsonPrimitive?.content ?: preferredUsername

        return SsoUser(
            subject = subject,
            username = preferredUsername,
            displayName = name,
        )
    }

    /**
     * Finds an existing user by username, or auto-provisions one with the configured default role.
     * Returns the local user id, or null when provisioning is not possible (no default role configured).
     */
    fun resolveUser(ssoUser: SsoUser): Int? =
        transaction {
            val existing =
                UserTable
                    .selectAll()
                    .where { UserTable.username eq ssoUser.username }
                    .firstOrNull()

            if (existing != null) {
                return@transaction if (existing[UserTable.enabled]) existing[UserTable.id].value else null
            }

            val defaultRoleName = serverConfig.ssoDefaultRole.value.trim()
            if (defaultRoleName.isBlank()) {
                logger.warn { "SSO user ${ssoUser.username} has no local account and no ssoDefaultRole is configured" }
                return@transaction null
            }

            val roleId =
                RoleTable
                    .selectAll()
                    .where { RoleTable.name eq defaultRoleName }
                    .firstOrNull()
                    ?.get(RoleTable.id)
                    ?: run {
                        logger.warn { "SSO default role \"$defaultRoleName\" does not exist" }
                        return@transaction null
                    }

            UserTable
                .insertAndGetId {
                    it[UserTable.username] = ssoUser.username
                    // SSO users do not use the local password login; store an unusable hash
                    it[UserTable.passwordHash] = "!sso"
                    it[UserTable.displayName] = ssoUser.displayName
                    it[UserTable.role] = roleId
                }.value
                .also { logger.info { "Auto-provisioned SSO user ${ssoUser.username} (id=$it, role=$defaultRoleName)" } }
        }
}
