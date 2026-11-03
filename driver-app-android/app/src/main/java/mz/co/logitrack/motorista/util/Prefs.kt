package mz.co.logitrack.motorista.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import mz.co.logitrack.motorista.data.dto.DashboardDto
import mz.co.logitrack.motorista.data.dto.ReferenciasDto
import java.security.MessageDigest

/**
 * Preferências da sessão. Usa armazenamento cifrado quando o dispositivo o
 * permite e recorre a SharedPreferences normal caso o keystore falhe, para que
 * a aplicação nunca rebente no arranque.
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
            appContext.getSharedPreferences("logitrack_secure_fallback", Context.MODE_PRIVATE)
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
        if (!sessaoOfflineValida()) return false
        val offline = tokenOffline ?: return false
        prefs.edit()
            .putString("token", offline)
            .putLong("token_expira_em", tokenOfflineExpiraEm)
            .putLong("user_id", offlineUserId)
            .putString("user_name", offlineUserName)
            .putString("user_email", offlineUserEmail)
            .putBoolean("session_expired", false)
            .apply()
        return true
    }

    fun guardarCredencial(email: String, senha: String) {
        credencialHash = hashCredencial(email, senha)
    }

    fun credencialValida(email: String, senha: String): Boolean {
        val guardado = credencialHash ?: return false
        return guardado == hashCredencial(email, senha)
    }

    fun sessaoValida(): Boolean {
        val atual = token
        if (atual.isNullOrBlank()) return false
        val expira = tokenExpiraEm
        return expira <= 0L || expira > System.currentTimeMillis()
    }

    fun guardarDashboard(dto: DashboardDto?) = putJson("cache_dashboard", dto)
    fun lerDashboard(): DashboardDto? = readJson("cache_dashboard", DashboardDto::class.java)

    fun guardarReferencias(dto: ReferenciasDto?) = putJson("cache_referencias", dto)
    fun lerReferencias(): ReferenciasDto? = readJson("cache_referencias", ReferenciasDto::class.java)

    fun guardarDetalhe(id: Long, json: String?) {
        prefs.edit().putString(chaveDetalhe(id), json).apply()
    }

    fun lerDetalhe(id: Long): String? = prefs.getString(chaveDetalhe(id), null)

    /** IDs das notificações já lidas, guardados como texto para manter o ficheiro leve. */
    fun notificacoesLidas(): Set<String> =
        prefs.getString(CHAVE_NOTIF_LIDAS, null)
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
            .putString(CHAVE_NOTIF_LIDAS, lidas.toList().takeLast(MAX_NOTIF_LIDAS).joinToString("\n"))
            .apply()
    }

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

    fun limparSessao() {
        prefs.edit()
            .remove("token")
            .remove("token_expira_em")
            .remove("user_id")
            .remove("user_name")
            .remove("user_email")
            .remove("session_expired")
            .remove(CHAVE_NOTIF_LIDAS)
            .apply()
    }

    fun limparTudo() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val BASE_PADRAO = "http://192.168.137.1:8080"
        private const val CHAVE_NOTIF_LIDAS = "notif_lidas"
        private const val MAX_NOTIF_LIDAS = 300

        fun normalizarBase(valor: String): String {
            var base = valor.trim()
            if (base.isEmpty()) return BASE_PADRAO
            if (!base.startsWith("http://") && !base.startsWith("https://")) {
                base = "http://$base"
            }
            base = base.trimEnd('/')
            if (base.endsWith("/api/v1")) base = base.removeSuffix("/api/v1")
            if (base.endsWith("/api")) base = base.removeSuffix("/api")
            return base.trimEnd('/')
        }

        fun hashCredencial(email: String, senha: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest("${email.trim().lowercase()}:$senha".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
