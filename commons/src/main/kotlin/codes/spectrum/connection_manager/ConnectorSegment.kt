package codes.spectrum.connection_manager

import codes.spectrum.serialization.json.Json

/**
 * Описывает отдельный сегмент внутри коннектора (например имя базы данных в RDBMS)
 */
data class ConnectorSegment(
    /**
     * Код сегмента
     */
    val code: String = "",
    /**
     * Название сегмента
     */
    val name: String = "",
    /**
     * Профили, в которых задействован сегмент
     */
    val profiles: Set<Profile> = setOf(),
    /**
     * Теги, которые связаны с сегментом
     */
    val tags: Set<String> = setOf(),
    /**
     * Информация о типе и настройке аутертификации
     */
    val credentials: ConnectorCredentials = ConnectorCredentials.Empty,
    /**
     * Дополнительные настройки
     */
    val options: Map<String, String> = mapOf()
) {
    fun toURIPathPart(tagsToPath: Boolean = true): String {
        val tagString = if (!tagsToPath || tags.isEmpty()) "" else ":${tags.sortedBy { it }.joinToString(":")}"
        return "${code}${tagString}";
    }

    fun fillURIQueryMap(queryMap: MutableMap<String, String>, tagsToPath: Boolean = true) {
        if (name.isNotBlank()) {
            queryMap["${code}.name"] = name
        }
        if (profiles.isNotEmpty()) {
            queryMap["${code}.profiles"] = profiles.sortedBy { it.code }.map { it.code }.joinToString(",")
        }
        if (tagsToPath && tags.isNotEmpty()) {
            queryMap["${code}.tags"] = tags.sortedBy { it }.joinToString(",")
        }
        if (credentials != ConnectorCredentials.Empty) {
            queryMap["${code}.credentials"] = credentials.toUriQueryPart()
        }
        if (options.isNotEmpty()) {
            queryMap["${code}.options"] = Json.stringify(options, format = false)
        }
    }

    class Builder {

        /**
         * Код сегмента
         */
        var code: String = ""
        /**
         * Название сегмента
         */
        var name: String = ""
        /**
         * Профили, в которых задействован сегмент
         */
        val profiles: MutableSet<Profile> = mutableSetOf()
        /**
         * Теги, которые связаны с сегментом
         */
        val tags: MutableSet<String> = mutableSetOf()
        /**
         * Информация о типе и настройке аутертификации
         */
        val credentials: ConnectorCredentials.Builder = ConnectorCredentials.Builder()
        /**
         * Дополнительные настройки
         */
        val options: MutableMap<String, String> = mutableMapOf()

        fun build(): ConnectorSegment {
            return ConnectorSegment(
                code = code,
                name = name,
                profiles = profiles.toSet(),
                tags = tags.toSet(),
                credentials = credentials.build(),
                options = options.toMap()
            )
        }

        fun setupFromUri(part: String, query: Map<String, String>) {
            val split = part.split(":")
            code = split.first()
            tags.addAll(split.drop(1))
            query["${code}.profiles"]?.let { profiles.addAll(it.split(",").map { Profile.get(it) }) }
            query["${code}.credential"]?.let { credentials.setupFromUserInfo(it) }
            query["${code}.name"]?.let { name = it }
            query["${code}.options"]?.let { options.putAll(Json.read<Map<String, String>>(it)) }
            query["${code}.tags"]?.let { tags.addAll(it.split(",")) }
        }
    }
}