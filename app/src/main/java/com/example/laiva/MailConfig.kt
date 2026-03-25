package com.example.laiva.data

object MailConfig {

    fun getImapHost(email: String): String {
        val domain = email.substringAfter("@")
        return domain
    }

    fun getSmtpHost(email: String): String {
        val domain = email.substringAfter("@")
        return "$domain"
    }
}