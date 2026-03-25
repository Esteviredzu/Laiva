package com.example.laiva.data

import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private const val NONCE_SIZE = 12
private const val GCM_TAG_SIZE = 128

object CryptoManager {



    fun generateKeyPair(): Pair<String, String> {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        val pair = keyGen.generateKeyPair()

        val privateKey = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP)
        val publicKey = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        return privateKey to publicKey
    }

    fun encryptWithPublicKey(data: ByteArray, publicKeyBase64: String): ByteArray {
        val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey: PublicKey = keyFactory.generatePublic(keySpec)

        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    fun decryptWithPrivateKey(data: ByteArray, privateKeyBase64: String): ByteArray {
        val keyBytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey: PrivateKey = keyFactory.generatePrivate(keySpec)

        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(data)
    }

    private fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    private fun encryptAES(plaintext: ByteArray, secretKey: SecretKey): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(NONCE_SIZE).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val cipherText = cipher.doFinal(plaintext)
        return cipherText to nonce
    }

    private fun decryptAES(ciphertext: ByteArray, nonce: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }


    fun decryptHybridText(cipherTextBase64: String, encryptedAESKeyBase64: String, myPrivateKey: String): String {
        val encryptedAESKey = Base64.decode(encryptedAESKeyBase64, Base64.NO_WRAP)
        val aesKeyBytes = decryptWithPrivateKey(encryptedAESKey, myPrivateKey)
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")

        val data = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        val nonce = data.sliceArray(0 until NONCE_SIZE)
        val cipherText = data.sliceArray(NONCE_SIZE until data.size)

        val plainBytes = decryptAES(cipherText, nonce, secretKey)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun encryptHybridFile(inputFile: File, recipientPublicKey: String, outputFile: File): String {
        val aesKey = generateAESKey()
        val plaintext = inputFile.readBytes()
        val (cipherText, nonce) = encryptAES(plaintext, aesKey)

        val encryptedAESKey = encryptWithPublicKey(aesKey.encoded, recipientPublicKey)
        val encryptedAESKeyBase64 = Base64.encodeToString(encryptedAESKey, Base64.NO_WRAP)

        val buffer = ByteBuffer.allocate(nonce.size + cipherText.size)
        buffer.put(nonce)
        buffer.put(cipherText)

        FileOutputStream(outputFile).use { it.write(buffer.array()) }

        return encryptedAESKeyBase64
    }

    fun decryptHybridFile(inputFile: File, encryptedAESKeyBase64: String, myPrivateKey: String, outputFile: File) {
        val encryptedAESKey = Base64.decode(encryptedAESKeyBase64, Base64.NO_WRAP)
        val aesKeyBytes = decryptWithPrivateKey(encryptedAESKey, myPrivateKey)
        val secretKey = SecretKeySpec(aesKeyBytes, "AES")

        val data = inputFile.readBytes()
        val nonce = data.sliceArray(0 until NONCE_SIZE)
        val cipherText = data.sliceArray(NONCE_SIZE until data.size)

        val plainBytes = decryptAES(cipherText, nonce, secretKey)

        FileOutputStream(outputFile).use { it.write(plainBytes) }
    }



    fun encryptTextWithAES(message: String): Pair<String, ByteArray> {
        val aesKey = generateAESKey()
        val (cipherText, nonce) = encryptAES(message.toByteArray(Charsets.UTF_8), aesKey)

        val combined = ByteBuffer.allocate(nonce.size + cipherText.size).apply {
            put(nonce)
            put(cipherText)
        }.array()

        val cipherTextBase64 = Base64.encodeToString(combined, Base64.NO_WRAP)

        return cipherTextBase64 to aesKey.encoded
    }

    fun encryptAESKey(aesKeyBytes: ByteArray, recipientPublicKey: String): String {
        val encrypted = encryptWithPublicKey(aesKeyBytes, recipientPublicKey)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }







    fun encryptFileWithAES(inputFile: File, outputFile: File): ByteArray {
        val aesKey = generateAESKey()
        val plaintext = inputFile.readBytes()
        val (cipherText, nonce) = encryptAES(plaintext, aesKey)

        val buffer = ByteBuffer.allocate(nonce.size + cipherText.size)
        buffer.put(nonce)
        buffer.put(cipherText)

        FileOutputStream(outputFile).use { it.write(buffer.array()) }

        return aesKey.encoded
    }






}