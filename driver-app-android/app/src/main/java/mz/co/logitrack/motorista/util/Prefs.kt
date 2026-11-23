package mz.co.logitrack.motorista.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import mz.co.logitrack.motorista.BuildConfig
import mz.co.logitrack.motorista.data.dto.DashboardDto
import mz.co.logitrack.motorista.data.dto.NotificacaoDto
import mz.co.logitrack.motorista.data.dto.ReferenciasDto
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Preferências da sessão. Usa sempre AES/Keystore; se o dispositivo não o
 * permitir, mantém os dados apenas em memória. Nunca grava token, sessão ou
 * credencial em SharedPreferences normais (texto claro).
 */
class Prefs(context: Context) {
    private val appContext = context.applicationContext
    private val gson = Gson()

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "logitrack_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            Log.w("LogitrackPrefs", "Keystore indisponível; sessão apenas em memória.", t)
            MemoriaSharedPreferences.INSTANCIA
        }
    }

    var token: String?
        get() = prefs.getString("token", null)
        set(value) = prefs.edit().putString("token", value).apply()

    var tokenExpiraEm: Long
        get() = prefs.getLong("token_expira_em", 0L)
        set(value) = prefs.edit().putLong("token_expira_em", value).apply()

    var userId: Long
        get() = prefs.getLong("user_id", 0L)
        set(value) = prefs.edit().putLong("user_id", value).apply()

    var userName: String?
        get() = prefs.getString("user_name", null)
        set(value) = prefs.edit().putString("user_name", value).apply()

    var userEmail: String?
        get() = prefs.getString("user_email", null)
        set(value) = prefs.edit().putString("user_email", value).apply()

    var credencialHash: String?
        get() = prefs.getString("credencial_hash", null)
        set(value) = prefs.edit().putString("credencial_hash", value).apply()

    var emailRecordado: String?
        get() = prefs.getString("email_recordado", null)
        set(value) = prefs.edit().putString("email_recordado", value).apply()

    // Instantâneo guardado para permitir iniciar sessão sem rede.
    private var tokenOffline: String?
        get() = prefs.getString("token_offline", null)
        set(value) = prefs.edit().putString("token_offline", value).apply()

    private var tokenOfflineExpiraEm: Long
        get() = prefs.getLong("token_offline_expira_em", 0L)
        set(value) = prefs.edit().putLong("token_offline_expira_em", value).apply()

    private var offlineUserId: Long
        get() = prefs.getLong("offline_user_id", 0L)
        set(value) = prefs.edit().putLong("offline_user_id", value).apply()

    private var offlineUserName: String?
        get() = prefs.getString("offline_user_name", null)
        set(value) = prefs.edit().putString("offline_user_name", value).apply()

    private var offlineUserEmail: String?
        get() = prefs.getString("offline_user_email", null)
        set(value) = prefs.edit().putString("offline_user_email", value).apply()

    var ultimaSync: Long
        get() = prefs.getLong("ultima_sync", 0L)
        set(value) = prefs.edit().putLong("ultima_sync", value).apply()

    var sessionExpired: Boolean
        get() = prefs.getBoolean("session_expired", false)
        set(value) = prefs.edit().putBoolean("session_expired", value).apply()

    // Migra apenas os endereços antigos conhecidos. Endereços personalizados
    // definidos pelo utilizador são preservados.
    init {
        val enderecoGuardado = prefs.getString("api_base", null)?.trimEnd('/')
        val enderecosAntigos = setOf(
            "http://10.110.110.107:8080",
            "http://10.0.2.2:8080",
            "http://localhost:8080"
        )
        if (enderecoGuardado.isNullOrBlank() || enderecoGuardado in enderecosAntigos) {
            prefs.edit().putString("api_base", BASE_PADRAO).apply()
        }
    }

    var apiBase: String
        get() = normalizarBase(prefs.getString("api_base", BASE_PADRAO) ?: BASE_PADRAO)
        set(value) = prefs.edit().putString("api_base", normalizarBase(value)).apply()

    val apiRoot: String
        get() = "${apiBase.trimEnd('/')}/api/v1/"

    fun guardarSessao(token: String, expiraEm: Long, id: Long, nome: String, email: String) {
        prefs.edit()
            .putString("token", token)
            .putLong("token_expira_em", expiraEm)
            .putLong("user_id", id)
            .putString("user_name", nome)
            .putString("user_email", email)
            .putBoolean("session_expired", false)
            // Guarda também o instantâneo usado pelo acesso offline.
            .putString("token_offline", token)
            .putLong("token_offline_expira_em", expiraEm)
            .putLong("offline_user_id", id)
            .putString("offline_user_name", nome)
            .putString("offline_user_email", email)
            .putString("email_recordado", email)
            .apply()
    }

    fun credencialGuardada(): Boolean = !credencialHash.isNullOrBlank()

    fun sessaoOfflineValida(): Boolean {
        val offline = tokenOffline
        if (offline.isNullOrBlank()) return false
        val expira = tokenOfflineExpiraEm
        return expira <= 0L || expira > System.currentTimeMillis() + 30_000L
    }

    /** Repõe os dados do utilizador a partir do instantâneo offline guardado. */
    fun restaurarSessaoOffline(): Boolean {
        val offline = tokenOffline ?: return false
        if (offlineUserId <= 0L) return false
        val expirada = !(tokenOfflineExpiraEm <= 0L || tokenOfflineExpiraEm > System.currentTimeMillis() + 30_000L)
        prefs.edit()
            .putString("token", offline)
            .putLong("token_expira_em", tokenOfflineExpiraEm)
            .putLong("user_id", offlineUserId)
            .putString("user_name", offlineUserName)
            .putString("user_email", offlineUserEmail)
            .putBoolean("session_expired", expirada)
            .apply()
        return true
    }

    fun guardarCredencial(email: String, senha: String) {
        credencialHash = hashCredencial(email, senha)
    }

    fun credencialValida(email: String, senha: String): Boolean {
        val guardado = credencialHash ?: return false
        val valido = if (guardado.startsWith(PBKDF2_PREFIX)) {
            verificarCredencial(guardado, email, senha)
        } else {
            // Migração do formato antigo: o valor guardado é um SHA-256, nunca a senha.
            MessageDigest.isEqual(
                guardado.toByteArray(Charsets.UTF_8),
                hashSha256(email, senha).toByteArray(Charsets.UTF_8)
            )
        }
        if (valido && !guardado.startsWith(PBKDF2_PREFIX)) {
            guardarCredencial(email, senha)
        }
        return valido
    }

    fun sessaoValida(): Boolean {
        val atual = token
        if (atual.isNullOrBlank()) return false
        val expira = tokenExpiraEm
        return expira <= 0L || expira > System.currentTimeMillis()
    }

    // Os caches partilhados passam a ter o utilizador no nome da chave.
    private fun chaveCache(nome: String) = "${nome}_${userId}"

    fun guardarDashboard(dto: DashboardDto?) = putJson(chaveCache("cache_dashboard"), dto)
    fun lerDashboard(): DashboardDto? = readJson(chaveCache("cache_dashboard"), DashboardDto::class.java)

    fun guardarReferencias(dto: ReferenciasDto?) = putJson(chaveCache("cache_referencias"), dto)
    fun lerReferencias(): ReferenciasDto? = readJson(chaveCache("cache_referencias"), ReferenciasDto::class.java)

    fun guardarDetalhe(id: Long, json: String?) {
        prefs.edit().putString(chaveDetalhe(id), json).apply()
    }

    fun lerDetalhe(id: Long): String? = prefs.getString(chaveDetalhe(id), null)

    /** Cache das notificações recebidas da API, isolada por utilizador. */
    fun guardarNotificacoes(dto: List<NotificacaoDto>?) =
        putJson(chaveNotificacoes(userId), dto)

    fun lerNotificacoes(): List<NotificacaoDto> {
        val texto = prefs.getString(chaveNotificacoes(userId), null) ?: return emptyList()
        return try {
            val tipo = com.google.gson.reflect.TypeToken
                .getParameterized(List::class.java, NotificacaoDto::class.java).type
            gson.fromJson<List<NotificacaoDto>>(texto, tipo) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** IDs das notificações já lidas, guardados como texto para manter o ficheiro leve. */
    fun notificacoesLidas(): Set<String> =
        prefs.getString(chaveNotifLidas(userId), null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    fun marcarNotificacaoLida(id: String) = marcarNotificacoesLidas(listOf(id))

    fun marcarNotificacoesLidas(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val lidas = linkedSetOf<String>()
        lidas.addAll(notificacoesLidas())
        lidas.addAll(ids)
        prefs.edit()
            .putString(chaveNotifLidas(userId),
                lidas.toList().takeLast(MAX_NOTIF_LIDAS).joinToString("\n"))
            .apply()
    }

    private fun chaveNotificacoes(id: Long): String = "cache_notificacoes_$id"

    private fun chaveNotifLidas(id: Long): String = "notif_lidas_$id"

    private fun chaveDetalhe(id: Long): String = "cache_detalhe_${userId}_$id"

    private fun putJson(chave: String, valor: Any?) {
        prefs.edit().putString(chave, if (valor == null) null else gson.toJson(valor)).apply()
    }

    private fun <T> readJson(chave: String, tipo: Class<T>): T? {
        val texto = prefs.getString(chave, null) ?: return null
        return try {
            gson.fromJson(texto, tipo)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Limpa a sessão ativa e caches locais, mas preserva a credencial guardada e
     * o instantâneo offline para que o mesmo motorista possa voltar a entrar sem
     * perder pendentes. A configuração do servidor também é preservada.
     */
    fun limparSessao() {
        val fixas = setOf(
            "token", "token_expira_em", "user_id", "user_name", "user_email",
            "session_expired", "ultima_sync"
        )
        val editor = prefs.edit()
        prefs.all.keys
            .filter {
                it in fixas || it.startsWith("cache_") ||
                    it.startsWith("notif_lidas_") || it.startsWith("rascunho_")
            }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    fun limparTudo() {
        prefs.edit().clear().apply()
    }

    fun guardarRascunhoNovo(json: String?) {
        prefs.edit().putString("rascunho_novo_${userId}", json).apply()
    }

    fun lerRascunhoNovo(): String? = prefs.getString("rascunho_novo_${userId}", null)

    companion object {
        val BASE_PADRAO: String = BuildConfig.API_BASE_PADRAO
        private const val MAX_NOTIF_LIDAS = 300
        private const val PBKDF2_PREFIX = "pbkdf2"
        private const val ITERACOES_PBKDF2 = 120_000

        fun normalizarBase(valor: String): String {
            var base = valor.trim()
            if (base.isEmpty()) return BASE_PADRAO
            if (base.startsWith("http://")) {
                base = "https://" + base.removePrefix("http://")
            } else if (!base.startsWith("https://")) {
                base = "https://$base"
            }
            base = base.trimEnd('/')
            if (base.endsWith("/api/v1")) base = base.removeSuffix("/api/v1")
            if (base.endsWith("/api")) base = base.removeSuffix("/api")
            return base.trimEnd('/')
        }

        fun hashCredencial(email: String, senha: String): String {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val chave = derivarChave(email, senha, salt, ITERACOES_PBKDF2)
            return listOf(
                PBKDF2_PREFIX,
                ITERACOES_PBKDF2.toString(),
                Base64.encodeToString(salt, Base64.NO_WRAP),
                Base64.encodeToString(chave, Base64.NO_WRAP)
            ).joinToString(":")
        }

        private fun verificarCredencial(guardado: String, email: String, senha: String): Boolean {
            val partes = guardado.split(":")
            if (partes.size != 4 || partes[0] != PBKDF2_PREFIX) return false
            return try {
                val iteracoes = partes[1].toInt()
                val salt = Base64.decode(partes[2], Base64.DEFAULT)
                val esperado = Base64.decode(partes[3], Base64.DEFAULT)
                MessageDigest.isEqual(derivarChave(email, senha, salt, iteracoes), esperado)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * PBKDF2-HMAC-SHA256 implementado com HmacSHA256 (disponível desde a API 1).
         * O SecretKeyFactory "PBKDF2WithHmacSHA256" só existe a partir da API 26;
         * esta implementação produz exatamente a mesma chave e funciona na API 24.
         */
        internal fun derivarChave(
            email: String,
            senha: String,
            salt: ByteArray,
            iteracoes: Int
        ): ByteArray {
            val palavraPasse = "${email.trim().lowercase()}:$senha".toByteArray(Charsets.UTF_8)
            val tamanhoBloco = 32 // HmacSHA256
            val tamanhoChave = 32  // 256 bits
            val blocos = (tamanhoChave + tamanhoBloco - 1) / tamanhoBloco
            val resultado = ByteArray(tamanhoChave)
            var deslocamento = 0

            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(palavraPasse, "HmacSHA256"))
            for (bloco in 1..blocos) {
                mac.update(salt)
                mac.update(
                    byteArrayOf(
                        (bloco ushr 24).toByte(),
                        (bloco ushr 16).toByte(),
                        (bloco ushr 8).toByte(),
                        bloco.toByte()
                    )
                )
                var u = mac.doFinal()
                val t = u.copyOf()
                for (i in 2..iteracoes) {
                    mac.update(u)
                    u = mac.doFinal()
                    for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                }
                val tamanho = minOf(tamanhoBloco, tamanhoChave - deslocamento)
                System.arraycopy(t, 0, resultado, deslocamento, tamanho)
                deslocamento += tamanho
            }
            return resultado
        }

        private fun hashSha256(email: String, senha: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest("${email.trim().lowercase()}:$senha".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
