package com.pulseplayer.app.aipulse

import java.io.File
import java.security.MessageDigest

/** Local, offline, multi-project storage. Existing legacy project remains readable. */
internal class AiPulseProjectLibrary(private val filesDir: File) {
    private val directory = File(filesDir, "ai_pulse_projects")

    private fun key(title: String): String {
        val clean = title.trim().ifBlank { "Untitled" }
        val prefix = clean.replace(Regex("[^a-zA-Z0-9 _-]"), "_").take(36).ifBlank { "Song" }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(clean.lowercase().toByteArray(Charsets.UTF_8))
            .take(8).joinToString("") { "%02x".format(it) }
        return "$prefix-$digest.json"
    }

    fun save(project: AiPulseProject): File {
        require(directory.isDirectory || directory.mkdirs()) { "Cannot create projects folder" }
        val target = File(directory, key(project.title))
        val temporary = File.createTempFile("pulse-", ".tmp", directory)
        try {
            temporary.writeText(project.toJson())
            // Verify serialization before replacing the previous project.
            AiPulseProject.fromJson(temporary.readText())
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
            }
            return target
        } finally {
            temporary.delete()
        }
    }

    fun list(): List<File> {
        val saved = directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        val legacy = File(filesDir, "ai_pulse_project.json")
        return saved + if (legacy.isFile) listOf(legacy) else emptyList()
    }

    fun load(file: File): AiPulseProject {
        require(list().any { it.canonicalFile == file.canonicalFile }) { "Project is not in the library" }
        return AiPulseProject.fromJson(file.readText())
    }

    fun delete(file: File): Boolean {
        require(list().any { it.canonicalFile == file.canonicalFile }) { "Project is not in the library" }
        return file.delete()
    }
}
