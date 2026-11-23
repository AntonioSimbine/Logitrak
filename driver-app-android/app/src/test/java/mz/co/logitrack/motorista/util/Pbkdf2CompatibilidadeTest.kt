package mz.co.logitrack.motorista.util

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Garante que a derivação usada na API 24 produz exatamente a mesma chave do
 * PBKDF2WithHmacSHA256 da plataforma (API 26+), preservando as credenciais já
 * guardadas.
 */
class Pbkdf2CompatibilidadeTest {
    @Test
    fun derivacaoCompativelComPlatforma() {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iteracoes = 120_000
        val esperado = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(
                PBEKeySpec(
                    "motorista@logitrack.co.mz:Senha@123".toCharArray(),
                    salt,
                    iteracoes,
                    256
                )
            ).encoded
        val obtido = Prefs.derivarChave(
            "motorista@logitrack.co.mz",
            "Senha@123",
            salt,
            iteracoes
        )
        assertArrayEquals(esperado, obtido)
    }
}
