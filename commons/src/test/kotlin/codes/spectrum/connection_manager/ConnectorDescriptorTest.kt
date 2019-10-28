package codes.spectrum.connection_manager

import io.kotlintest.assertSoftly
import io.kotlintest.matchers.collections.shouldHaveSize
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import java.net.URLEncoder


/**
 * Данный тест в основном проверят прямую и обратную совместимость
 * ConnectorDescriptor с URL и возможностью конфигурирования из них
 */
internal class ConnectorDescriptorTest : StringSpec({


    "Проверяем возможность загрузки из конфига (без профилей)"{
        val fromConfig = ConnectorDescriptor.Config(mapOf(ConnectorDescriptor.Config.KEYWORD to "jdbc:postgres://u:p@host1/db1|cassandra://host1,host2,host3")).build()
        val fromDirect = setOf(ConnectorDescriptor.parse("jdbc:postgres://u:p@host1/db1"), ConnectorDescriptor.parse("cassandra://host1,host2,host3"))
    }

    "Проверяем возможность загрузки из конфига (c профилями)"{
        val fromConfig = ConnectorDescriptor.Config(mapOf(ConnectorDescriptor.Config.KEYWORD + "_DEV" to "jdbc:postgres://u:p@host1/db1|cassandra://host1,host2,host3")).build()
        val fromDirect = setOf(ConnectorDescriptor.parse("jdbc:postgres://u:p@host1/db1?profiles=dev"), ConnectorDescriptor.parse("cassandra://host1,host2,host3?profiles=dev"))
    }

    "Проверяем матчинг по объекту запроса"{
        val desc = ConnectorDescriptor.build {
            platform = Platform.POSTGRESQL
            profiles.add(Profile.Stage)
            tags.add("test")
            tags.add("main")
            code = "mycode"
            options["x"] = "1"
            options["y"] = "2"
        }
        assertSoftly {
            // пустой запрос
            ConnectorDescriptor.Query().matches(desc) shouldBe true

            // условие на платформу
            ConnectorDescriptor.Query(platforms = setOf(Platform.POSTGRESQL)).matches(desc) shouldBe true
            ConnectorDescriptor.Query(platforms = setOf(Platform.POSTGRESQL, Platform.CASSANDRA)).matches(desc) shouldBe true
            ConnectorDescriptor.Query(platforms = setOf(Platform.CASSANDRA)).matches(desc) shouldBe false

            // условие на профиль
            ConnectorDescriptor.Query(profiles = setOf(Profile.Stage)).matches(desc) shouldBe true
            ConnectorDescriptor.Query(profiles = setOf(Profile.Stage, Profile.Prod)).matches(desc) shouldBe true
            ConnectorDescriptor.Query(profiles = setOf(Profile.Prod)).matches(desc) shouldBe false

            //условие на теги
            ConnectorDescriptor.Query(tags = setOf("test")).matches(desc) shouldBe true
            ConnectorDescriptor.Query(tags = setOf("test", "main")).matches(desc) shouldBe true
            ConnectorDescriptor.Query(tags = setOf("main")).matches(desc) shouldBe true
            ConnectorDescriptor.Query(tags = setOf("main", "best")).matches(desc) shouldBe true
            ConnectorDescriptor.Query(tags = setOf("best")).matches(desc) shouldBe false

            // условие на код
            ConnectorDescriptor.Query(codes = setOf("mycode")).matches(desc) shouldBe true
            ConnectorDescriptor.Query(codes = setOf("mycode", "othercode")).matches(desc) shouldBe true
            ConnectorDescriptor.Query(codes = setOf("othercode")).matches(desc) shouldBe false

            // условие на опции
            ConnectorDescriptor.Query(options = mapOf("x" to setOf("1"))).matches(desc) shouldBe true
            ConnectorDescriptor.Query(options = mapOf("x" to setOf("1", "2"))).matches(desc) shouldBe true
            ConnectorDescriptor.Query(options = mapOf("x" to setOf("2"))).matches(desc) shouldBe false
            ConnectorDescriptor.Query(options = mapOf("x" to setOf("1"), "y" to setOf("2"))).matches(desc) shouldBe true
            ConnectorDescriptor.Query(options = mapOf("x" to setOf("1"), "y" to setOf("1"))).matches(desc) shouldBe false
            ConnectorDescriptor.Query(options = mapOf("x" to setOf("1"), "y" to setOf("1")), shouldMatchAll = false).matches(desc) shouldBe true
            ConnectorDescriptor.Query(options = mapOf("x" to setOf("2"), "y" to setOf("1")), shouldMatchAll = false).matches(desc) shouldBe false
        }
    }


    "Проверяем чтение профилей из .profiles и profiles"{
        assertSoftly {
            ConnectorDescriptor.parse("http://host").profiles shouldHaveSize 0
            ConnectorDescriptor.parse("http://host?.profiles=").profiles shouldHaveSize 0
            ConnectorDescriptor.parse("http://host?profiles=").profiles shouldHaveSize 0
            ConnectorDescriptor.parse("http://host?.profiles=dev").profiles shouldBe setOf(Profile.Dev)
            ConnectorDescriptor.parse("http://host?profiles=dev").profiles shouldBe setOf(Profile.Dev)
            ConnectorDescriptor.parse("http://host?.profiles=dev,prod").profiles shouldBe setOf(Profile.Dev, Profile.Prod)
            ConnectorDescriptor.parse("http://host?profiles=dev,prod").profiles shouldBe setOf(Profile.Dev, Profile.Prod)
        }
    }


    "Проверяем чтение .code, .name и code, name"{
        assertSoftly {
            ConnectorDescriptor.parse("http://host?.code=x").code shouldBe "x"
            ConnectorDescriptor.parse("http://host?code=x").code shouldBe "x"
            ConnectorDescriptor.parse("http://host?.name=x").name shouldBe "x"
            ConnectorDescriptor.parse("http://host?name=x").name shouldBe "x"
            ConnectorDescriptor.parse("http://host?.code=y&.name=x").let {
                it.code shouldBe "y"
                it.name shouldBe "x"
            }

        }
    }

    "Проверяем чтение .tags и tags"{
        assertSoftly {
            ConnectorDescriptor.parse("http://host").tags shouldHaveSize 0
            ConnectorDescriptor.parse("http://host?.tags=").tags shouldHaveSize 0
            ConnectorDescriptor.parse("http://host?tags=").tags shouldHaveSize 0
            ConnectorDescriptor.parse("http://host?.tags=meta").tags shouldBe setOf("meta")
            ConnectorDescriptor.parse("http://host?tags=meta").tags shouldBe setOf("meta")
            ConnectorDescriptor.parse("http://host?.tags=meta,ref").tags shouldBe setOf("meta", "ref")
            ConnectorDescriptor.parse("http://host?tags=meta,ref").tags shouldBe setOf("meta", "ref")

        }
    }

    "Проверяем чтение доп опций в формате JSON" {
        assertSoftly {
            ConnectorDescriptor.parse("http://host?options=${URLEncoder.encode("{x:'1',y:'2'}")}").options shouldBe mapOf("x" to "1", "y" to "2")
            ConnectorDescriptor.parse("http://host?.options=${URLEncoder.encode("{x:'1',y:'2'}")}").options shouldBe mapOf("x" to "1", "y" to "2")
        }
    }

    "Проверяем чтение доп опций прямо из query" {
        assertSoftly {
            ConnectorDescriptor.parse("http://host?options=${URLEncoder.encode("{x:'1',y:'2'}")}").options shouldBe mapOf("x" to "1", "y" to "2")
            ConnectorDescriptor.parse("http://host/x,y?.options=${URLEncoder.encode("{x:'1',y:'2'}")}").options shouldBe mapOf("x" to "1", "y" to "2")
        }
    }



    "Проверяем, что .secure в query имеет старший приоритет"{
        assertSoftly {
            ConnectorDescriptor.parse("https://host").isSecure shouldBe true
            ConnectorDescriptor.parse("http://host").isSecure shouldBe false
            ConnectorDescriptor.parse("https://host?secure=false").isSecure shouldBe false
            ConnectorDescriptor.parse("http://host?secure=true").isSecure shouldBe true
            ConnectorDescriptor.parse("https://host?.secure=false").isSecure shouldBe false
            ConnectorDescriptor.parse("http://host?.secure=true").isSecure shouldBe true
        }
    }


    // Блок тестов проверки правильной обработки параметра "платформа"
    for (p in Platform.values().filter { it != Platform.UNDEFINED }) {
        // проверяем, что при явном указании типа платформа и secure определяется правильно
        "Если явно указан .type=$p то это $p даже при дефолтной схеме при этом isSecure соблюдается, кейс не важен"{
            assertSoftly {
                ConnectorDescriptor.parse("http://host?.type=$p").let {
                    it.platform shouldBe p
                    it.isSecure shouldBe false
                }
                ConnectorDescriptor.parse("https://host?.type=$p").let {
                    it.platform shouldBe p
                    it.isSecure shouldBe true
                }
                ConnectorDescriptor.parse("http://host?.type=${p.toString().toLowerCase()}").let {
                    it.platform shouldBe p
                    it.isSecure shouldBe false
                }
                ConnectorDescriptor.parse("https://host?.type=${p.toString().toLowerCase()}").let {
                    it.platform shouldBe p
                    it.isSecure shouldBe true
                }
            }
        }

        for (s in p.inSchemas) {
            // проверяем, что он умеет по схеме определять
            "Если схема $s то платформа $p"{
                ConnectorDescriptor.parse("$s://host").platform shouldBe p
            }

            // проверяем, что он умеет по схеме определять
            "Если схема $s то порт ${if (s == p.outSecureSchema) p.securePort else p.port}"{
                val desc = ConnectorDescriptor.parse("$s://host")
                if (desc.isSecure) {
                    desc.port shouldBe desc.platform.securePort
                } else {
                    desc.port shouldBe desc.platform.port
                }
            }
            // отдельная проверка распознавания isSecure
            "Если схема $s то isSecure - ${s == p.outSecureSchema}"{
                ConnectorDescriptor.parse("$s://host").isSecure shouldBe (s == p.outSecureSchema)
            }


            // специальный кейс для составных псевдосхем типа `jdbc:postgres:`, несовместимых с обычным URI
            if (s.contains("-")) {
                "Если схема ${s.replace("-", ":")} то платформа $p"{
                    ConnectorDescriptor.parse("${s.replace("-", ":")}://host").platform shouldBe p
                }
            }
        }
    }

    "issue 1 - was invalid parse url"{
        ConnectorDescriptor.parse("jdbc:postgres://pguser:pgpass@pg-1/db").toString() shouldBe "jdbc-postgres://pguser:pgpass@pg-1:5432/db?type=POSTGRESQL"
    }
})


