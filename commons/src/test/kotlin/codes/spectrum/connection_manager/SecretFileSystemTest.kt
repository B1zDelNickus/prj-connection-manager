package codes.spectrum.connection_manager

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import java.io.File


internal class SecretFileSystemTest : StringSpec({
    "can get list no prefix and data"{
        val root = "./tmp/" + this.description().name.replace(" ", "_")
        File(root).let {
            if (it.exists()) {
                it.deleteRecursively()
            }
            it.mkdirs()
            File(it, "a").writeText("x")
            File(it, "b").writeText("y")
            File(it, "c").writeText("z")
        }
        val secrets = SecretFileSystem(root)
        secrets.list().sortedBy { it } shouldBe listOf("a", "b", "c")
        secrets.get("a") shouldBe "x"
        //ignore case
        secrets.get("A") shouldBe "x"
        secrets.get("no") shouldBe null
    }

    "prefixed files" {
        val root = "./tmp/" + this.description().name.replace(" ", "_")
        File(root).let {
            if (it.exists()) {
                it.deleteRecursively()
            }
            it.mkdirs()
            File(it, "z_a").writeText("x")
            File(it, "b").writeText("y")
            File(it, "Z_c").writeText("z")
            File(it, "z").writeText("z1")
        }
        val secrets = SecretFileSystem(root, "Z")
        secrets.list().sortedBy { it } shouldBe listOf("Z_c", "z", "z_a")
        secrets.get("a") shouldBe "x"
        secrets.get("z_a") shouldBe "x"
        secrets.get("A") shouldBe "x"

        secrets.get("c") shouldBe "z"
        secrets.get("z_c") shouldBe "z"
        secrets.get("Z_c") shouldBe "z"
        secrets.get("Z_C") shouldBe "z"
        secrets.get("C") shouldBe "z"

        secrets.get("Z") shouldBe "z1"
        secrets.get("z") shouldBe "z1"
    }
})