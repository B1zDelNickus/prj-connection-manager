package codes.spectrum.connection_manager

import java.io.File

open class SecretFileSystem(val root: String = "/run/secrets", val prefix: String = "") {
    open operator fun get(code: String): String? {
        var resolved = code.toLowerCase()
        if (prefix.isNotBlank()) {
            if (resolved != prefix.toLowerCase() && !resolved.startsWith("${prefix.toLowerCase()}_")) {
                resolved = prefix.toLowerCase() + "_" + resolved
            }
        }
        val file_lower = File(root, resolved.toLowerCase())
        if (file_lower.exists()) return file_lower.readText()
        val file_upper = File(root, resolved.toUpperCase())
        if (file_upper.exists()) return file_upper.readText()
        return null
    }

    open fun list(): List<String> {
        if (File(root).exists()) return File(root).listFiles().map { it.name }.filter {
            when {
                prefix.isBlank() -> true
                it.toLowerCase() == prefix.toLowerCase() -> true
                it.toLowerCase().startsWith(prefix.toLowerCase() + "_") -> true
                else -> false
            }
        }
        return emptyList()
    }
}