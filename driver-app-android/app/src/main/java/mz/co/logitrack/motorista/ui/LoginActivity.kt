package mz.co.logitrack.motorista.ui

import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.dto.LoginRequest
import mz.co.logitrack.motorista.databinding.ActivityLoginBinding
import mz.co.logitrack.motorista.network.ApiErrors
import mz.co.logitrack.motorista.network.NetworkUtils
import mz.co.logitrack.motorista.network.RetrofitClient
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Prefs
import java.io.IOException

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: Prefs
    private var senhaVisivel = false
    private var aEntrar = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        val mensagemInicial = intent?.getStringExtra(MainActivity.EXTRA_MENSAGEM)
        if (mensagemInicial.isNullOrBlank() && prefs.sessaoValida()) {
            validarSessaoOnline()
            return
        }
        if (!mensagemInicial.isNullOrBlank()) {
            mostrarErro(mensagemInicial)
            intent?.removeExtra(MainActivity.EXTRA_MENSAGEM)
        }

        binding.etEmail.setText(prefs.emailRecordado.orEmpty())
        binding.cbLembrar.isChecked = !prefs.emailRecordado.isNullOrBlank()
        if (binding.etEmail.text.isNullOrBlank()) {
            binding.etEmail.requestFocus()
        } else {
            binding.etSenha.requestFocus()
        }

        binding.btnMostrarSenha.setOnClickListener { alternarSenha() }
        binding.btnEntrar.setOnClickListener { submeter() }
        binding.wrapServidor.setOnClickListener { editarServidor() }
        atualizarServidor()
        binding.btnEsqueci.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.login_forgot)
                .setMessage(R.string.login_forgot_message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        prepararBiometria()
        prepararAnimacoes()
    }

    /** Mostra o servidor ativo no ecrã de login. */
    private fun atualizarServidor() {
        binding.tvServidor.text = prefs.apiBase
    }

    /** Permite ao motorista apontar a app para outra API sem reinstalar o APK. */
    private fun editarServidor() {
        val campo = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(prefs.apiBase)
            setSelection(text.length)
            hint = getString(R.string.server_dialog_hint)
            setPadding(48, 32, 48, 32)
        }
        val caixa = FrameLayout(this).apply { addView(campo) }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.server_dialog_title)
            .setView(caixa)
            .setPositiveButton(R.string.save) { _, _ ->
                var valor = campo.text.toString().trim()
                if (valor.isEmpty()) {
                    valor = Prefs.BASE_PADRAO
                }
                if (valor.startsWith("http://")) {
                    valor = "https://" + valor.removePrefix("http://")
                } else if (!valor.startsWith("https://")) {
                    valor = "https://$valor"
                }
                if (!Regex("^https://[^\\s]+$").matches(valor)) {
                    Toast.makeText(this, R.string.server_invalid, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.apiBase = valor
                atualizarServidor()
                Toast.makeText(this, R.string.server_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun prepararAnimacoes() {
        Animacoes.cascata(
            binding.loginContent,
            atraso = 80L,
            intervalo = 45L,
            deslocamentoY = 20f,
            duracao = 340L
        )
        Animacoes.pressionar(binding.btnEntrar, binding.btnBiometria, binding.btnMostrarSenha)
    }

    private fun prepararBiometria() {
        val disponivel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            prefs.credencialGuardada() && prefs.sessaoOfflineValida()
        binding.wrapBiometria.visibility = if (disponivel) View.VISIBLE else View.GONE
        if (!disponivel) return
        binding.btnBiometria.setOnClickListener { pedirBiometria() }
    }

    private fun pedirBiometria() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            mostrarErro(getString(R.string.login_biometric_unavailable))
            return
        }
        val executor = ContextCompat.getMainExecutor(this)
        try {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle(getString(R.string.login_biometric_title))
                .setSubtitle(getString(R.string.login_biometric_subtitle))
                .setNegativeButton(
                    getString(R.string.login_biometric_cancel),
                    executor
                ) { _, _ -> }
                .build()

            prompt.authenticate(
                CancellationSignal(),
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        if (prefs.restaurarSessaoOffline()) {
                            abrirPrincipal(getString(R.string.offline_login_welcome))
                        } else {
                            mostrarErro(getString(R.string.login_biometric_no_session))
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence
                    ) {
                        // 5 = cancelado, 10 = cancelado pelo utilizador, 13 = botão negativo.
                        if (errorCode != 5 && errorCode != 10 && errorCode != 13) {
                            mostrarErro(getString(R.string.login_biometric_failed))
                        }
                    }
                }
            )
        } catch (e: Exception) {
            mostrarErro(getString(R.string.login_biometric_unavailable))
        }
    }

    private fun alternarSenha() {
        senhaVisivel = !senhaVisivel
        binding.etSenha.inputType = if (senhaVisivel) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        binding.etSenha.setSelection(binding.etSenha.text?.length ?: 0)
        binding.btnMostrarSenha.setText(
            if (senhaVisivel) R.string.hide_password_short else R.string.show_password_short
        )
    }

    private fun submeter() {
        if (aEntrar) return
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val senha = binding.etSenha.text?.toString().orEmpty()
        if (email.isBlank() || senha.isBlank()) {
            mostrarErro(getString(R.string.error_fill_fields))
            return
        }
        esconderErro()
        aEntrar = true
        definirCarregando(true)
        lifecycleScope.launch {
            try {
                if (NetworkUtils.estaLigado(this@LoginActivity)) {
                    val online = tentarOnline(email, senha)
                    if (online) return@launch
                }
                tentarOffline(email, senha)
            } finally {
                aEntrar = false
                definirCarregando(false)
            }
        }
    }

    /** @return true se a sessão foi iniciada com o servidor. */
    private suspend fun tentarOnline(email: String, senha: String): Boolean {
        return try {
            val resp = RetrofitClient.getApi(this).login(LoginRequest(email, senha))
            if (!resp.isSuccessful) {
                val corpo = resp.body()
                if (resp.code() == 401 || resp.code() == 400 || resp.code() == 403) {
                    mostrarErro(ApiErrors.mensagem(resp))
                    true
                } else if (corpo != null) {
                    concluirSessao(corpo, email, senha, false)
                    true
                } else {
                    mostrarErro(ApiErrors.mensagem(resp))
                    true
                }
            } else {
                val corpo = resp.body()
                if (corpo == null || corpo.token.isBlank()) {
                    mostrarErro(getString(R.string.error_generic))
                    true
                } else if (!perfilMotorista(corpo.utilizador.perfil, corpo.utilizador.perfilEtiqueta)
                    || !perfilConfirmadoOnline(corpo.token)
                ) {
                    prefs.limparTudo()
                    RetrofitClient.invalidar()
                    mostrarErro(getString(R.string.error_driver_only))
                    true
                } else {
                    concluirSessao(corpo, email, senha, false)
                    true
                }
            }
        } catch (e: IOException) {
            // Sem resposta do servidor: tenta validar a credencial guardada.
            false
        } catch (e: Exception) {
            false
        }
    }

    /** Confirma o perfil no arranque; a sessão guardada não basta para RBAC. */
    private fun validarSessaoOnline() {
        if (!NetworkUtils.estaLigado(this)) {
            entrarNaApp(null)
            return
        }
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.getApi(this@LoginActivity).me()
                when {
                    resp.code() == 401 -> {
                        prefs.limparSessao()
                        RetrofitClient.invalidar()
                        mostrarErro(getString(R.string.session_expired_message))
                    }
                    resp.isSuccessful && resp.body()?.let {
                        perfilMotorista(it.perfil, it.perfilEtiqueta)
                    } != true -> {
                        prefs.limparTudo()
                        RetrofitClient.invalidar()
                        mostrarErro(getString(R.string.error_driver_only))
                    }
                    else -> entrarNaApp(null)
                }
            } catch (e: Exception) {
                entrarNaApp(null)
            }
        }
    }

    private suspend fun perfilConfirmadoOnline(token: String): Boolean = try {
        val resp = RetrofitClient.getApi(this).meComToken("Bearer $token")
        resp.isSuccessful && resp.body()?.let {
            perfilMotorista(it.perfil, it.perfilEtiqueta)
        } == true
    } catch (e: Exception) {
        false
    }

    private fun perfilMotorista(perfil: String?, etiqueta: String?): Boolean {
        val valor = "${perfil.orEmpty()} ${etiqueta.orEmpty()}".uppercase()
        return valor.contains("MOTORISTA") || valor.contains("DRIVER")
    }

    private fun tentarOffline(email: String, senha: String) {
        if (!prefs.credencialGuardada()) {
            mostrarErro(getString(R.string.error_first_login_online))
            return
        }
        if (!prefs.credencialValida(email, senha)) {
            mostrarErro(getString(R.string.error_offline_credentials))
            return
        }
        if (!prefs.restaurarSessaoOffline()) {
            mostrarErro(getString(R.string.error_offline_session_expired))
            return
        }
        prefs.emailRecordado = if (binding.cbLembrar.isChecked) email else null
        abrirPrincipal(getString(R.string.offline_login_welcome))
    }

    private fun concluirSessao(
        auth: mz.co.logitrack.motorista.data.dto.AuthResponse,
        email: String,
        senha: String,
        offline: Boolean
    ) {
        val expiraEm = if (auth.expiresInSegundos > 0) {
            System.currentTimeMillis() + auth.expiresInSegundos * 1000L
        } else {
            0L
        }
        prefs.guardarSessao(
            token = auth.token,
            expiraEm = expiraEm,
            id = auth.utilizador.id,
            nome = auth.utilizador.nome,
            email = auth.utilizador.email.ifBlank { email }
        )
        prefs.guardarCredencial(email, senha)
        prefs.emailRecordado = if (binding.cbLembrar.isChecked) email else null
        abrirPrincipal(
            if (offline) getString(R.string.offline_login_welcome)
            else getString(R.string.login_success, auth.utilizador.nome)
        )
    }

    private fun abrirPrincipal(mensagem: String?) {
        startActivity(
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_MENSAGEM, mensagem)
        )
        finish()
    }

    private fun entrarNaApp(mensagem: String?) {
        startActivity(
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_MENSAGEM, mensagem)
        )
        finish()
    }

    private fun definirCarregando(carregando: Boolean) {
        binding.btnEntrar.isEnabled = !carregando
        binding.btnEntrar.text = getString(if (carregando) R.string.login_loading else R.string.login)
        binding.progress.visibility = if (carregando) View.VISIBLE else View.GONE
        binding.etEmail.isEnabled = !carregando
        binding.etSenha.isEnabled = !carregando
        binding.cbLembrar.isEnabled = !carregando
        binding.btnBiometria.isEnabled = !carregando
        binding.btnBiometria.alpha = if (carregando) 0.5f else 1f
    }

    private fun mostrarErro(texto: String) {
        binding.tvErro.text = texto
        binding.tvErro.visibility = View.VISIBLE
        Animacoes.tremer(binding.tvErro)
    }

    private fun esconderErro() {
        binding.tvErro.visibility = View.GONE
        binding.tvErro.text = null
    }
}
