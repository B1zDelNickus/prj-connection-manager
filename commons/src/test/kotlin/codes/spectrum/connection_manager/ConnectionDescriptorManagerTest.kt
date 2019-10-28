package codes.spectrum.connection_manager

import codes.spectrum.serialization.json.Json
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

internal class ConnectionDescriptorManagerTest : StringSpec({

    "configures both from descriptors and passwordss"{
        val manager = ConnectionDescriptorManager(
            descriptorConfig = ConnectorDescriptor.Config(map = mapOf(
                "CONNECTIONS" to Json.stringify(listOf("jdbc:postgres://pg-1/db", "rabbit://rbt-1"))
            )),
            credenditalsConfig = ConfiguredConnectionCredentials.Config(
                map = mapOf(
                    "CONNECTION_CREDENTIALS" to Json.stringify(listOf("pguser:pgpass@pg-*", "rbuser:rbpass@rbt-*"))
                )
            )
        )
        manager.resolve(ConnectorDescriptor.Query(platforms = setOf(Platform.POSTGRESQL))).single() shouldBe
            ConnectorDescriptor.parse("jdbc:postgres://pguser:pgpass@pg-1/db?.profiles=default")
        manager.resolve(ConnectorDescriptor.Query(platforms = setOf(Platform.RABBITMQ))).single() shouldBe
            ConnectorDescriptor.parse("rabbit://rbuser:rbpass@rbt-1?.profiles=default")
    }
})