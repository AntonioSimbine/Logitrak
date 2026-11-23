package mz.co.logitrack.motorista.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.room.TypeConverter
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra os campos sensíveis guardados pelo Room com uma chave AES-256 do
 * Android Keystore. Os valores antigos (sem prefixo) continuam a ser lidos;
 * são reescritos cifrados na próxima atualização do registo.
 */
object CriptoLocal {
    private const val ALIAS = "logitrack_room_cipher"
    private const val PREFIXO = "enc:v1:"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    @Volatile
    private var chave: SecretKey? = null

    fun init(context: Context) {
        if (chave != null) return
        try {
            val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existente = keystore.getKey(ALIAS, null) as? SecretKey
            chave = existente ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                            ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                        )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                }.generateKey()
        } catch (e: Exception) {
            Log.w("LogitrackCripto", "Keystore indisponível para a BD; valor mantido como recebido.", e)
        }
    }

    fun cifrar(valor: String): String {
        val segredo = chave ?: return valor
        return try {
            val iv = ByteArray(IV_BYTES).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, segredo, GCMParameterSpec(TAG_BITS, iv))
            val cifrado = cipher.doFinal(valor.toByteArray(Charsets.UTF_8))
            val pacote = ByteArray(iv.size + cifrado.size)
            System.arraycopy(iv, 0, pacote, 0, iv.size)
            System.arraycopy(cifrado, 0, pacote, iv.size, cifrado.size)
            PREFIXO + Base64.encodeToString(pacote, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w("LogitrackCripto", "Falha a cifrar campo; valor mantido.", e)
            valor
        }
    }

    fun decifrar(valor: String): String {
        if (!valor.startsWith(PREFIXO)) return valor // registos anteriores à cifragem
        val segredo = chave ?: return valor
        return try {
            val pacote = Base64.decode(valor.removePrefix(PREFIXO), Base64.NO_WRAP)
            val iv = pacote.copyOfRange(0, IV_BYTES)
            val cifrado = pacote.copyOfRange(IV_BYTES, pacote.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, segredo, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(cifrado), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("LogitrackCripto", "Falha a decifrar campo; valor devolvido tal como está.", e)
            valor
        }
    }
}

/** Aplicada apenas à fotografia guardada no Room (o campo maior e mais sensível). */
class CriptoConverters {
    @TypeConverter
    fun cifrar(valor: String?): String? = valor?.let { CriptoLocal.cifrar(it) }

    @TypeConverter
    fun decifrar(valor: String?): String? = valor?.let { CriptoLocal.decifrar(it) }
}
