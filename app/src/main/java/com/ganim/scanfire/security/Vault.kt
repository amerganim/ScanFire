package com.ganim.scanfire.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.withTransaction
import com.ganim.scanfire.data.FileStore
import com.ganim.scanfire.data.db.DocumentSummary
import com.ganim.scanfire.data.db.PageEntity
import com.ganim.scanfire.data.db.ScanFireDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Private vault. Moving a document in encrypts every file in its folder with AES-256-GCM under a key that
 * lives in the Android Keystore (hardware-backed where the phone has it, never exportable), hides it from
 * the library and search, and drops its recognised text. Opening the vault is gated by the phone's own
 * fingerprint/face/screen lock (see [AppLock]); decrypted pages are only ever held in memory.
 *
 * The key is deliberately not bound to biometric enrolment: adding a fingerprint or changing the PIN would
 * otherwise destroy the key and every vault document with it.
 */
class Vault(private val db: ScanFireDatabase, private val files: FileStore) {

    fun documents(): Flow<List<DocumentSummary>> = db.documents().observeVault()

    /** Hide first, then encrypt, so an interruption never leaves plaintext showing in the library. */
    suspend fun moveIn(docId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.documents().setVault(docId, true)
            db.pages().forDocument(docId).forEach { db.pages().update(it.copy(ocrText = null)) }
        }
        files.docDir(docId).listFiles()?.filter { it.isFile }?.forEach(::encryptInPlace)
    }

    /** Decrypt first, then show, so the library never lists a document it can't display. */
    suspend fun moveOut(docId: Long) = withContext(Dispatchers.IO) {
        files.docDir(docId).listFiles()?.filter { it.isFile }?.forEach(::decryptInPlace)
        db.documents().setVault(docId, false)
    }

    suspend fun pages(docId: Long): List<PageEntity> = db.pages().forDocument(docId)

    /** The page image, decrypted into memory. */
    suspend fun read(page: PageEntity): ByteArray = withContext(Dispatchers.IO) {
        val bytes = files.file(page.documentId, page.imageFile).readBytes()
        if (isEncrypted(bytes)) decrypt(bytes) else bytes
    }

    private fun encryptInPlace(file: File) {
        val plain = file.readBytes()
        if (isEncrypted(plain)) return
        writeAtomically(file, encrypt(plain))
    }

    private fun decryptInPlace(file: File) {
        val bytes = file.readBytes()
        if (!isEncrypted(bytes)) return
        writeAtomically(file, decrypt(bytes))
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val tmp = File(file.parentFile, file.name + ".part")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Couldn't replace ${file.name}" }
        }
    }

    companion object {
        private const val ALIAS = "scanfire-vault"
        private val MAGIC = "SFV1".toByteArray()
        private const val IV_SIZE = 12

        fun isEncrypted(bytes: ByteArray): Boolean =
            bytes.size > MAGIC.size + IV_SIZE && MAGIC.indices.all { bytes[it] == MAGIC[it] }

        private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
        }

        /** Layout: "SFV1" | 12-byte IV | ciphertext with GCM tag. */
        fun encrypt(plain: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            return MAGIC + cipher.iv + cipher.doFinal(plain)
        }

        fun decrypt(bytes: ByteArray): ByteArray {
            val iv = bytes.copyOfRange(MAGIC.size, MAGIC.size + IV_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
            return cipher.doFinal(bytes, MAGIC.size + IV_SIZE, bytes.size - MAGIC.size - IV_SIZE)
        }
    }
}
