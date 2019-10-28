package codes.spectrum.connection_manager

import codes.spectrum.serialization.json.ExposeLevel
import codes.spectrum.serialization.json.Json
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec


internal class ConfiguredConnectionCredentialsTest : StringSpec({
    val sample1 = ConfiguredConnectionCredentials("host", "x", ConnectorCredentials(user = "u", password = "p"))
    val sample2 = ConfiguredConnectionCredentials("host2", "x2", ConnectorCredentials(user = "u2", password = "p2"))
    "can parse from string user pass" {
        ConfiguredConnectionCredentials.parse("u:p@host/x") shouldBe sample1

    }

    "can parse from string token" {
        ConfiguredConnectionCredentials.parse("t@host/x") shouldBe
            ConfiguredConnectionCredentials("host", "x", ConnectorCredentials(token = "t"))
    }

    "can use asterix" {
        ConfiguredConnectionCredentials.parse("t@host*/x") shouldBe
            ConfiguredConnectionCredentials("host*", "x", ConnectorCredentials(token = "t"))
    }
    "can be regex" {
        ConfiguredConnectionCredentials.parse("pattern://t@host[x+]/x") shouldBe
            ConfiguredConnectionCredentials("/host[x+]/", "x", ConnectorCredentials(token = "t"))
    }

    "collection deserialization from single object" {
        Json.read<ConfiguredConnectionCredentials.SetOf>(Json.stringify(sample1, level = ExposeLevel.IGNORABLE), level = ExposeLevel.IGNORABLE) shouldBe
            ConfiguredConnectionCredentials.SetOf(sample1)
    }

    "collection deserialization from string" {
        Json.read<ConfiguredConnectionCredentials.SetOf>("\"u:p@host/x\"", level = ExposeLevel.IGNORABLE) shouldBe
            ConfiguredConnectionCredentials.SetOf(ConfiguredConnectionCredentials("host", "x", ConnectorCredentials(user = "u", password = "p")))
    }

    "collection deserialization from string array" {
        Json.read<ConfiguredConnectionCredentials.SetOf>("[\"u:p@host/x\",\"u2:p2@host2/x2\"]", level = ExposeLevel.IGNORABLE) shouldBe
            ConfiguredConnectionCredentials.SetOf(
                sample1,
                sample2
            )
    }

    "collection deserialization from obj array" {
        Json.read<ConfiguredConnectionCredentials.SetOf>(Json.stringify(listOf(sample1, sample2), level = ExposeLevel.IGNORABLE), level = ExposeLevel.IGNORABLE) shouldBe
            ConfiguredConnectionCredentials.SetOf(
                sample1,
                sample2
            )
    }

    "collection deserialization from mix array" {
        Json.read<ConfiguredConnectionCredentials.SetOf>(Json.stringify(listOf("u:p@host/x", sample2), level = ExposeLevel.IGNORABLE), level = ExposeLevel.IGNORABLE) shouldBe
            ConfiguredConnectionCredentials.SetOf(
                sample1,
                sample2
            )
    }


})