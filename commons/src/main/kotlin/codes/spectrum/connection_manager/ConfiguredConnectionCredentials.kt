package codes.spectrum.connection_manager

import codes.spectrum.serialization.json.ExposeLevel
import codes.spectrum.serialization.json.Json
import codes.spectrum.serialization.json.extensibility.IJsonDeserializeInterceptor
import com.google.gson.*
import java.net.URI


/**
 * Описатель креденций в конфигурации
 */
data class ConfiguredConnectionCredentials(
    val host: String = "",
    val segment: String = "",
    val credentials: ConnectorCredentials = ConnectorCredentials.Empty
) {

    data class Config(
        val map: Map<String, String?> = emptyMap(),
        val prefix: String = "CONNECTION_CREDENTIALS",
        val secure_file_root: String = "/run/secrets"
    ) {


        private var fileSystem = SecretFileSystem(secure_file_root, prefix)

        constructor(map: Map<String, String?> = emptyMap(), fileSystem: SecretFileSystem) : this(map) {
            this.fileSystem = fileSystem
        }

        fun build(): IConfiguredConnectionCredentialsSet {
            val _cache = mutableMapOf<String, ConfiguredConnectionCredentials>()

            fun apply(m: String?) {
                if (m.isNullOrBlank()) return
                if (m.trim().matches("""^["\[{][\s\S]*""".toRegex())) {
                    val list = Json.read(m.trim(), SetOf::class.java, level = ExposeLevel.IGNORABLE)
                    for (i in list) {
                        _cache["${i.host}__${i.segment}"] = i
                    }
                } else {
                    val i = parse(m.trim())
                    _cache["${i.host}__${i.segment}"] = i
                }
            }


            val from_files = fileSystem.list()
            for (f in from_files) {
                apply(fileSystem.get(f))
            }

            val from_map = map.entries.filter { it.key == prefix || it.key.startsWith(prefix + "_") }.map { it.value }
            for (m in from_map) {
                apply(m)
            }

            return SetOf().apply { addAll(_cache.values) }
        }

        companion object {
            val Default = Config(System.getenv())


        }
    }


    fun match(descriptor: ConnectorDescriptor): Boolean {
        if (host.isEmpty()) return false
        if (descriptor.hosts.any { match(host, it) }) {
            if (segment.isEmpty()) return true
            return descriptor.segments.any { match(segment, it.code) }
        }
        return false

    }

    fun applyRequired(descriptor: ConnectorDescriptor): Boolean {
        if (!match(descriptor)) return false
        return if (segment.isNotBlank()) {
            descriptor.segments.filter { match(segment, it.code) }.any { !it.credentials.isDefined() }
        } else {
            !descriptor.credentials.isDefined()
        }
    }

    fun applyTo(descriptor: ConnectorDescriptor): ConnectorDescriptor {
        if (!applyRequired(descriptor)) return descriptor
        if (segment.isNotBlank()) {
            return descriptor.copy(segments = descriptor.segments.map { if (match(segment, it.code)) it.copy(credentials = credentials) else it }.toSet())
        } else {
            return descriptor.copy(credentials = this.credentials)
        }
    }


    companion object {
        private fun match(pattern: String, trg: String): Boolean {
            if (pattern.startsWith("/") && pattern.endsWith("/")) return pattern.substring(1, pattern.length - 1).toRegex().matches(trg)
            if (pattern.contains("*")) return pattern.replace("*", """[\w\d_\-]*""").toRegex().matches(trg)
            return pattern == trg
        }

        private val escapes = listOf(
            "_OQ_" to "[",
            "_CQ_" to "]",
            "_PL_" to "+",
            "_ST_" to "*",
            "_LN_" to "-",
            "_OB_" to "(",
            "_CB_" to ")",
            "_ESC_" to "\\"
        )

        private fun String.escape(): String {
            var result = this
            escapes.forEach {
                result = result.replace(it.second, it.first)
            }
            return result
        }

        private fun String.unescape(): String {
            var result = this
            escapes.forEach {
                result = result.replace(it.first, it.second)
            }
            return result
        }

        fun parse(url: String): ConfiguredConnectionCredentials {
            var baseurl = url.escape()
            if (!baseurl.contains(":/")) {
                baseurl = "http://" + baseurl
            }
            val uri = URI(baseurl)
            val host = uri.authority.split("@").last().replace("/", "").unescape()
            val segment = uri.path.replace("/", "").unescape()
            val creds = if (uri.authority.contains("@")) uri.authority.split("@").first() else ""
            val token = if (creds.isNotBlank() && !creds.contains(":")) creds else ""
            val user = if (creds.isNotBlank() && creds.contains(":")) creds.split(":").first() else ""
            val pass = if (creds.isNotBlank() && creds.contains(":")) creds.split(":").last() else ""
            val resolvedHost = if (uri.scheme == "pattern" || uri.scheme == "regex") "/$host/" else host
            return ConfiguredConnectionCredentials(resolvedHost, segment, ConnectorCredentials(token = token, user = user, password = pass))
        }
    }

    data class Query(val host: String = "", val segment: String = "") {
        companion object {
            val Empty = Query()
        }
    }

    interface IConfiguredConnectionCredentialsSet : Set<ConfiguredConnectionCredentials> {
        fun applyTo(descriptor: ConnectorDescriptor): ConnectorDescriptor
        fun resolve(query: Query = Query.Empty): Set<ConfiguredConnectionCredentials>
    }

    class SetOf() : HashSet<ConfiguredConnectionCredentials>(), IConfiguredConnectionCredentialsSet, IJsonDeserializeInterceptor {
        constructor(values: Collection<ConfiguredConnectionCredentials>) : this() {
            this.addAll(values)
        }

        constructor(vararg values: ConfiguredConnectionCredentials) : this() {
            this.addAll(values)
        }

        override fun jsonInterceptDeserialize(json: JsonElement, context: JsonDeserializationContext, level: ExposeLevel) {
            when {
                json is JsonPrimitive -> this.add(parse(json.asString))
                json is JsonArray -> json.asIterable().forEach {
                    when {
                        it is JsonPrimitive -> this.add(parse(it.asString))
                        it is JsonObject -> this.add(context.deserialize(it, ConfiguredConnectionCredentials::class.java))
                    }
                }
                json is JsonObject -> this.add(context.deserialize(json, ConfiguredConnectionCredentials::class.java))
            }
        }

        override fun applyTo(descriptor: ConnectorDescriptor): ConnectorDescriptor {
            var result = descriptor
            this.forEach { result = it.applyTo(result) }
            return result
        }

        override fun resolve(query: Query): Set<ConfiguredConnectionCredentials> {
            return this.filter {
                (query.host.isBlank() || match(query.host, it.host))
                    &&
                    (query.segment.isBlank() || match(query.segment, it.segment))

            }.toSet()
        }
    }
}



