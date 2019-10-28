package codes.spectrum.connection_manager

import codes.spectrum.serialization.json.ExposeLevel
import codes.spectrum.serialization.json.JsonLevel

data class ConnectorCredentials(
    @JsonLevel(ExposeLevel.IGNORABLE)
    val token: String = "",
    @Transient
    val secureToken: String = "",
    val user: String = "",
    @JsonLevel(ExposeLevel.IGNORABLE)
    val password: String = "",
    @Transient
    val securePassword: String = "",
    val tokenType: String = ""
) {

    fun hasUser() = user.isNotBlank()
    fun hasPassword() = password.isNotBlank() || securePassword.isNotBlank()
    fun isUserPassword() = hasUser() && hasPassword()
    fun isToken() = token.isNotBlank() || secureToken.isNotBlank()
    fun resolveToken() = if (secureToken.isBlank()) token else secureToken
    fun resolvePassword() = if (securePassword.isBlank()) password else securePassword
    fun isTokenDefined() = isToken() && resolveToken().let { it.isNotBlank() && it != MaskedToken && it != MaskedPassword }
    fun isPasswordDefined() = isUserPassword() && resolvePassword().let { it.isNotBlank() && it != MaskedToken && it != MaskedPassword }
    fun isDefined() = isTokenDefined() || isPasswordDefined()
    fun toUriHostPart(): String {
        return if (isUserPassword()) {
            if (securePassword.isNotBlank()) {
                "${user}:${MaskedPassword}@"
            } else {
                "${user}:${password}@"
            }
        } else if (isToken()) {
            val type = if (tokenType.isBlank()) "BEARER" else tokenType
            if (secureToken.isNotBlank()) {
                "token-${type}:${MaskedToken}@"
            } else {
                "token-${type}:${token}@"
            }
        } else "";
    }

    fun toUriQueryPart() = toUriHostPart().replace("@", "").replace(":", ",")

    class Builder {
        var token: String = ""
        var secureToken: String = ""
        var user: String = ""
        var password: String = ""
        var securePassword: String = ""
        var tokenType: String = ""
        fun build(): ConnectorCredentials {
            return ConnectorCredentials(
                token = token,
                secureToken = secureToken,
                tokenType = tokenType,
                user = user,
                password = password,
                securePassword = securePassword
            )
        }

        fun clear() {
            token = ""
            secureToken = ""
            user = ""
            password = ""
            securePassword = ""
            tokenType = ""
        }

        fun setupFromUserInfo(userInfo: String) {
            val (user, pass) = userInfo.replace(",", ":").split(":")
            if (user.startsWith("token-")) {
                tokenType = user.split("-").drop(1).joinToString("")
                token = pass
            } else {
                this.user = user
                password = pass
            }
        }
    }

    companion object {
        val Empty = ConnectorCredentials()
        val MaskedPassword = "********"
        val MaskedToken = "XXXXXXXX"
    }
}