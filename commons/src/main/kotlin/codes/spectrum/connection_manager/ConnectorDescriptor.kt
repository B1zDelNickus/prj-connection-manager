package codes.spectrum.connection_manager

import codes.spectrum.serialization.json.Json
import java.net.URI
import java.net.URL
import java.net.URLEncoder


/**
 * Описывает соединение с внешним сервисом
 */
data class ConnectorDescriptor(
    /**
     * Тип платформы
     */
    val platform: Platform = Platform.UNDEFINED,
    /**
     * Код коннекстора
     */
    val code: String = "",
    /**
     * Имя коннектора
     */
    val name: String = "",
    /**
     * Профили в которых задействован коннектор
     */
    val profiles: Set<Profile> = setOf(),
    /**
     * Теги, связанные с коннектором
     */
    val tags: Set<String> = setOf(),
    /**
     * Информация о типе и настройке аутертификации
     */
    val credentials: ConnectorCredentials = ConnectorCredentials.Empty,
    /**
     * Дополнительные настройки
     */
    val options: Map<String, String> = mapOf(),
    /**
     * Внутренние сегменты
     */
    val segments: Set<ConnectorSegment> = setOf(),

    val isSecure: Boolean = false,

    val hosts: Set<String> = setOf(),

    val port: Int = 0
) {
    override fun toString(): String {
        val schema = if (isSecure) platform.outSecureSchema else platform.outSchema
        val hosts = hosts.sortedBy { it }.joinToString(",")
        val path = segments.sortedBy { it.code }.map { it.toURIPathPart() }.joinToString(",")
        val queryMap = mutableMapOf("type" to platform.toString())
        if (code.isNotBlank()) queryMap[".code"] = code
        if (isSecure) queryMap[".secure"] = isSecure.toString()
        if (name.isNotBlank()) queryMap[".name"] = name
        if (profiles.isNotEmpty()) queryMap[".profiles"] = profiles.sortedBy { it.code }.map { it.code }.joinToString(",")
        if (tags.isNotEmpty()) queryMap[".tags"] = tags.sortedBy { it }.joinToString(",")
        if (options.isNotEmpty()) {
            queryMap[".options"] = Json.stringify(options, format = false)
        }
        for (s in segments.sortedBy { it.code }) {
            s.fillURIQueryMap(queryMap)
        }
        val queryString = queryMap.entries.sortedBy { it.key }.map { "${it.key}=${URLEncoder.encode(it.value, Charsets.UTF_8)}" }.joinToString("&")
        return "${schema}://${credentials.toUriHostPart()}${hosts}:${port}/${path}?${queryString}"
    }

    fun matches(query: Query) = query.matches(this)

    /**
     * Описывает запрос на ConnectorDescriptor
     * представляет собой коньюнкцию (AND) непустых дизъюнктов (OR)
     */
    data class Query(
        /**
         * Условие на требуемую платформу
         * пусто - нет условия
         * несколько - любая платформа из указанных
         */
        val platforms: Set<Platform> = emptySet(),
        /**
         * Условие на профиль
         * пусто - нет условия
         * несколько - любой профиль из указанных
         */
        val profiles: Set<Profile> = emptySet(),
        /**
         * Условие на тег
         * пусто - нет условия
         * несколько - любой профиль из указанных
         */
        val tags: Set<String> = emptySet(),
        /**
         * Условие на код
         * пусто - нет условия
         * несколько - любой код из указанных
         */
        val codes: Set<String> = emptySet(),
        /**
         * Маска опций
         * пусто - нет условия
         * проверяется, что все (при @see shouldMatchAll == true) или любой (при @see shouldMatchAll == false)
         * entries содержат в своем наборе значение
         */
        val options: Map<String, Set<String>> = emptyMap(),

        /**
         * Логическая функция условия на options
         * true - конъьюнкция - все опции должны одновременно совпасть
         * false - дизъюнкция - любая из опций должна просоответствовать
         */
        val shouldMatchAll: Boolean = true

    ) {
        fun matches(descriptor: ConnectorDescriptor) =
            ((platforms.isEmpty() || platforms.contains(descriptor.platform))
                &&
                (profiles.isEmpty() || profiles.intersect(descriptor.profiles).isNotEmpty())
                &&
                (tags.isEmpty() || tags.intersect(descriptor.tags).isNotEmpty())
                &&
                (codes.isEmpty() || codes.contains(descriptor.code))
                &&
                (options.isEmpty() ||
                    (
                        (shouldMatchAll && options.entries.all { it.value.contains(descriptor.options[it.key] ?: "") })
                            ||
                            (!shouldMatchAll && options.entries.any {
                                it.value.contains(descriptor.options[it.key] ?: "")
                            })
                        )
                    ))

        companion object {
            val Empty = Query()
        }
    }

    /**
     * Обеспечивает загрузку перечня  ConnectorDescriptor
     * из переданной карты настроек, например ENV
     */
    class Config(
        /**
         * Карта с сырыми конфигами (позже надо будет перейти на IConfig)
         */
        val map: Map<String, String> = emptyMap(),
        /**
         * Ключевое слово параметра для поиска в env,
         * по умолчанию `CONNECTIONS`
         */
        val keyword: String = KEYWORD,
        /**
         * В случае настройки для профиля - разделитель ключевого слова и имени профиля
         * по умолчанию `_`
         */
        val profile_splitter: String = PROFILE_SPLITTER,
        /**
         * Разделитель списка URL в случае если используется на JSON
         * по умолчанию `|`
         */
        val url_splitter: String = URL_SPLITTER
    ) {
        fun build(): Set<ConnectorDescriptor> {
            val entries = map.entries.filter { it.key == keyword || it.key.startsWith(keyword + profile_splitter) }
            val result = mutableListOf<ConnectorDescriptor>()
            for (e in entries) {
                var profile = Profile.Default
                if (e.key.contains(profile_splitter)) {
                    profile = Profile.get(e.key.split(profile_splitter).elementAt(1))
                }
                val urls = if (e.value.startsWith("[")) {
                    Json.read<List<String>>(e.value)
                } else e.value.split(url_splitter)
                for (u in urls) {
                    result.add(parse(u) {
                        this.profiles.add(profile)
                    })
                }
            }
            return SetOf().apply { addAll(result) }
        }

        companion object {
            const val KEYWORD = "CONNECTIONS"
            const val PROFILE_SPLITTER = "_"
            const val URL_SPLITTER = "|"
            fun buildDefault(): Config = Config(System.getenv(), KEYWORD, PROFILE_SPLITTER, URL_SPLITTER)
            val Default by lazy { buildDefault() }
        }
    }

    interface IConnect
    class SetOf : HashSet<ConnectorDescriptor>()

    /**
     * Билдер, отвечающий за конструирование объекта ConnectorDescriptor
     */
    class Builder {

        /**
         * Тип платформы
         */
        var platform: Platform = Platform.UNDEFINED
        /**
         * Код коннекстора
         */
        var code: String = ""
        /**
         * Имя коннектора
         */
        var name: String = ""
        /**
         * Профили в которых задействован коннектор
         */
        val profiles: MutableSet<Profile> = mutableSetOf()
        /**
         * Теги, связанные с коннектором
         */
        val tags: MutableSet<String> = mutableSetOf()
        /**
         * Информация о типе и настройке аутертификации
         */
        var credentials: ConnectorCredentials.Builder = ConnectorCredentials.Builder()
        /**
         * Дополнительные настройки
         */
        val options: MutableMap<String, String> = mutableMapOf()
        /**
         * Внутренние сегменты
         */
        val segments: MutableSet<ConnectorSegment.Builder> = mutableSetOf()

        var isSecure: Boolean = false


        val hosts: MutableSet<String> = mutableSetOf()

        var port: Int = 0

        var srcUrl: String = ""

        fun build(): ConnectorDescriptor {
            optimize()
            return ConnectorDescriptor(
                platform = platform,
                code = code,
                name = name,
                profiles = profiles.toSet(),
                tags = tags.toSet(),
                credentials = credentials.build(),
                options = options.toMap(),
                segments = segments.map { it.build() }.toSet(),
                isSecure = isSecure,
                hosts = hosts.toSet(),
                port = port
            )
        }

        fun optimize() {
            val self = this
            //обеспечиваем отсуствие дублирование тегов и профилей
            //сначала проброс общих профилей и тегов "вверх"
            segments.forEach {
                it.tags.forEach { tag ->
                    if (segments.all { tags.contains(tag) }) {
                        self.tags.add(tag)
                    }
                }
                it.profiles.forEach { profile ->
                    if (segments.all { profiles.contains(profile) }) {
                        self.profiles.add(profile)
                    }
                }
            }
            // теперь зачистка общих компонентов "вниз" и обеспечиваем, чтобы рут содержал все профили и все теги
            segments.forEach {
                it.tags.removeAll(this.tags);
                this.tags.addAll(it.tags)
                it.profiles.removeAll(this.profiles)
                this.profiles.addAll(it.profiles)
                if (it.credentials.build() == self.credentials.build()) {
                    it.credentials.clear()
                }
                this.tags
            }

        }

        fun setupFromUrl(url: String) {
            srcUrl = url
            val normal_url = url.replace("^(\\w+):(\\w+):".toRegex(), "$1-$2:")
            val uri = URI.create(normal_url)
            val query = if (uri.query == null) mapOf<String, String>() else uri.query.split("&").map { it.split("=") }.map { it.first() to it.last() }.toMap()
            fun q(name: String) = query["." + name] ?: query[name]
            if (query.contains(".type")) {
                platform = Platform.valueOf(query[".type"]!!.toUpperCase())
            } else {
                platform = Platform.values().first {
                    it.inSchemas.contains(uri.scheme)
                }
            }
            if (uri.scheme == platform.outSecureSchema || uri.scheme == "https" /*универсальный вариант*/) {
                isSecure = true
            }
            q("secure")?.let { isSecure = it == "true" }
            if (uri.authority.contains("@")) {
                val (cred, host) = uri.authority.split("@")
                credentials.setupFromUserInfo(cred)
                hosts.addAll(host.split(","))
            } else {
                hosts.addAll(uri.authority.split(","))
            }



            q("code")?.let { code = it }
            q("secure")?.let { isSecure = it == "true" }
            q("name")?.let { name = it }
            q("profiles")?.let { profiles.addAll(it.split(",").filter { it.isNotBlank() }.map { Profile.get(it) }) }
            q("tags")?.let { tags.addAll(it.split(",").filter { it.isNotBlank() }) }
            q("options")?.let { options.putAll(Json.read<Map<String, String>>(it)) }
            if (uri.port <= 0) {
                port = if (isSecure) platform.securePort else platform.port
            } else {
                port = uri.port
            }
            val path = uri.path.let { if (it.startsWith("/")) it.substring(1) else it }
            val pathparts = path.split(",").filter { it.isNotBlank() }
            for (part in pathparts) {
                val builder = ConnectorSegment.Builder()
                builder.setupFromUri(part, query)
                segments.add(builder)
            }
        }
    }


    companion object {
        fun parse(uri: URI, body: Builder.() -> Unit = {}) = parse(uri.toString(), body)
        fun parse(url: URL, body: Builder.() -> Unit = {}) = parse(url.toString(), body)
        fun parse(url: String, body: Builder.() -> Unit = {}) = build {
            setupFromUrl(url)
            body()
        }

        fun build(body: Builder.() -> Unit = {}): ConnectorDescriptor {
            return Builder().apply { body() }.build()
        }

        fun fromConfig(config: Map<String, String>) = Config(config).build()
        fun fromSystemConfig() = Config.Default.build()
        val SystemDefined by lazy { fromSystemConfig() }
    }
}