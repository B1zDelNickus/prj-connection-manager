package codes.spectrum.connection_manager

/**
 * Описывает типы платформ коннекторов
 */
enum class Platform(
    val iscassandra: Boolean = false,
    val iselastic: Boolean = false,
    val issql: Boolean = false,
    val isbinary: Boolean = false,
    val isrest: Boolean = false,
    val isqueue: Boolean = false,
    val outSchema: String = this.toString().toLowerCase(),
    val outSecureSchema: String = outSchema + "s",
    val inSchemas: Set<String> = setOf(),
    val port: Int = 80,
    val securePort: Int = if (port == 80) 433 else port
) {
    POSTGRESQL(issql = true, inSchemas = setOf("jdbc-postgres", "postgres", "jdbc"), outSchema = "jdbc-postgres", port = 5432),
    CASSANDRA(iscassandra = true, inSchemas = setOf("cassandra", "cassandras"), outSchema = "cassandra", port = 9042),
    ELASTIC(iselastic = true, isrest = true, inSchemas = setOf("elastic", "elastics"), outSchema = "elastic", port = 9200),
    ELASSANDRA(iscassandra = true, iselastic = true, inSchemas = setOf("elassandra", "elassandras"), outSchema = "elassandra", port = 9042),
    S3(isbinary = true, outSchema = "http", inSchemas = setOf("s3", "s3s"), port = 9000),
    HDFS(isbinary = true, inSchemas = setOf("hdfs", "hdfss"), outSchema = "hdfs", port = 9020),
    REST(isrest = true, inSchemas = setOf("http", "https"), outSchema = "http"),
    RABBITMQ(isqueue = true, outSchema = "rabbit", inSchemas = setOf("rabbit", "rabbits", "rabbitmq", "rabbitmqs"), port = 5672),
    UNDEFINED;

    fun matches(other: Platform): Boolean {
        if (this == other) return true
        if (this == UNDEFINED) return true
        if (this == ELASTIC || this == CASSANDRA) return other == ELASSANDRA
        return false
    }

}