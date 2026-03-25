package com.example.laiva
import java.io.File
import java.security.MessageDigest


fun getAvatarHash(file: File): String? {
    return if (file.exists()) {
        val bytes = file.readBytes()
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    } else null
}
