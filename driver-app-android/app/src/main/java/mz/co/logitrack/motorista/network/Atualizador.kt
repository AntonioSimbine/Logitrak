package mz.co.logitrack.motorista.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mz.co.logitrack.motorista.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.Serializable
import java.util.concurrent.TimeUnit

/** Dados do aviso de atualização publicados no site da Logitrack. */
data class InfoVersao(
    var versionCode: Int = 0,
    var versionName: String = "",
    var obrigatoria: Boolean = false,
    var mensagem: String? = null,
    var url: String? = null,
    var sha256: String? = null
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** Resultado da verificação feita ao abrir a aplicação. */
data class Verificacao(
    val info: InfoVersao?,
    val disponivel: Boolean,
    val erro: Boolean = false
)

/**
 * Verifica se existe uma versão nova e descarrega o APK sem sair da aplicação.
 * O ficheiro é guardado no espaço privado da app; a instalação é feita pelo
 * instalador do Android através de um FileProvider.
 */
object Atualizador {
    private val gson = Gson()
    private val cliente: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseWeb: String
        get() = BuildConfig.WEB_BASE_PADRAO.trimEnd('/')

    private val urlVersao: String
        get() = "$baseWeb/downloads/versao.json"

    private val apkOficial: String
        get() = "$baseWeb/downloads/Logitrack-Motorista.apk"

    suspend fun verificar(): Verificacao = withContext(Dispatchers.IO) {
        try {
            val pedido = Request.Builder()
                .url(urlVersao)
                .header("Cache-Control", "no-cache")
                .build()

            cliente.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) return@withContext Verificacao(null, false, true)
                val json = resposta.body?.string().orEmpty()
                val info = gson.fromJson(json, InfoVersao::class.java)
                    ?: return@withContext Verificacao(null, false, true)
                if (info.versionCode <= 0) return@withContext Verificacao(null, false, true)
                Verificacao(info, info.versionCode > BuildConfig.VERSION_CODE, false)
            }
        } catch (e: Exception) {
            Verificacao(null, false, true)
        }
    }

    suspend fun descarregar(
        context: Context,
        info: InfoVersao,
        aoProgressar: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val url = urlApk(info)
        val pasta = File(context.cacheDir, "atualizacoes").apply { mkdirs() }
        val destino = File(pasta, "Logitrack-Motorista-${info.versionCode}.apk")
        val esperado = info.sha256?.trim()?.takeIf { it.isNotBlank() }

        if (destino.exists() && destino.length() > 0 && esperado == null) return@withContext destino
        if (destino.exists() && destino.length() > 0 && esperado != null &&
            sha256(destino).equals(esperado, ignoreCase = true)
        ) {
            return@withContext destino
        }

        val temporario = File(pasta, "Logitrack-Motorista-${info.versionCode}.part")
        if (temporario.exists()) temporario.delete()

        try {
            val pedido = Request.Builder().url(url).header("Cache-Control", "no-cache").build()
            cliente.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) {
                    throw IOException("Não foi possível descarregar a atualização.")
                }
                val corpo = resposta.body ?: throw IOException("Ficheiro vazio.")
                val total = corpo.contentLength()
                var lidos = 0L
                var ultimoPercento = -1

                FileOutputStream(temporario).use { saida ->
                    corpo.byteStream().use { entrada ->
                        val bloco = ByteArray(8 * 1024)
                        while (true) {
                            val n = entrada.read(bloco)
                            if (n <= 0) break
                            saida.write(bloco, 0, n)
                            lidos += n
                            val percento = if (total > 0) {
                                (lidos * 100 / total).toInt().coerceIn(0, 100)
                            } else {
                                -1
                            }
                            if (percento != ultimoPercento) {
                                ultimoPercento = percento
                                withContext(Dispatchers.Main) { aoProgressar(percento) }
                            }
                        }
                    }
                }
            }

            if (temporario.length() <= 0) throw IOException("Ficheiro vazio.")
            if (esperado != null && !sha256(temporario).equals(esperado, ignoreCase = true)) {
                throw IOException("O ficheiro descarregado não passou a verificação.")
            }
            if (destino.exists()) destino.delete()
            if (!temporario.renameTo(destino)) throw IOException("Não foi possível guardar o ficheiro.")
            withContext(Dispatchers.Main) { aoProgressar(100) }
            destino
        } catch (e: Exception) {
            temporario.delete()
            throw e
        }
    }

    private fun urlApk(info: InfoVersao): String {
        val candidata = info.url?.trim()
        return if (!candidata.isNullOrBlank() &&
            candidata.startsWith(baseWeb, ignoreCase = true) &&
            candidata.startsWith("https://", ignoreCase = true)
        ) {
            candidata
        } else {
            apkOficial
        }
    }

    private fun sha256(arquivo: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        arquivo.inputStream().use { entrada ->
            val bloco = ByteArray(8 * 1024)
            while (true) {
                val n = entrada.read(bloco)
                if (n <= 0) break
                digest.update(bloco, 0, n)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    fun podeInstalar(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun intentInstalacao(context: Context, arquivo: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            arquivo
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun intentDefinicoesInstalacao(context: Context): Intent {
        val principal = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
        return if (principal.resolveActivity(context.packageManager) != null) {
            principal
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }
}
