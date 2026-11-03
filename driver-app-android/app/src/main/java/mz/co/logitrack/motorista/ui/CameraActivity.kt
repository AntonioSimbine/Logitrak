package mz.co.logitrack.motorista.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.databinding.ActivityCameraBinding
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.FotoProcessada
import mz.co.logitrack.motorista.util.Imagem
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var executor: ExecutorService = Executors.newSingleThreadExecutor()

    private var foto: FotoProcessada? = null
    private var processando = false
    private var cameraPronta = false
    private var animouBotoes = false

    private val permissaoCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) {
            iniciarCamera()
        } else {
            mostrarMensagem(
                getString(R.string.camera_unavailable),
                getString(R.string.camera_permission_denied)
            )
        }
    }

    private val escolhaGaleria = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) processarGaleria(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnFechar.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        binding.btnCapturar.setOnClickListener { tirarFoto() }
        binding.btnRepetir.setOnClickListener { reiniciarCamera() }
        binding.btnUsarFoto.setOnClickListener { usarFoto() }
        binding.btnGaleria.setOnClickListener { abrirGaleria() }

        Animacoes.surgir(binding.root.getChildAt(0), atraso = 40L, deslocamentoY = 12f)
        Animacoes.pressionar(binding.btnFechar, binding.btnCapturar, binding.btnRepetir, binding.btnUsarFoto, binding.btnGaleria)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            iniciarCamera()
        } else {
            permissaoCamera.launch(Manifest.permission.CAMERA)
        }
    }

    /* ------------------------- Câmara ------------------------- */

    private fun iniciarCamera() {
        reiniciarUi()
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            try {
                val provider = futuro.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.preview.surfaceProvider)
                }
                val captura = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(binding.preview.display.rotation)
                    .build()
                val seletor = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                provider.unbindAll()
                provider.bindToLifecycle(this, seletor, preview, captura)
                imageCapture = captura
                cameraPronta = true
                revelarBotoes()
            } catch (e: Exception) {
                cameraPronta = false
                mostrarMensagem(
                    getString(R.string.camera_unavailable),
                    getString(R.string.camera_open_failed)
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun tirarFoto() {
        if (processando) return
        if (!cameraPronta) {
            avisar(getString(R.string.camera_wait_title), getString(R.string.camera_wait_text))
            return
        }
        val captura = imageCapture ?: return
        val ficheiro = File(cacheDir, "guia_${System.currentTimeMillis()}.jpg")
        val opcoes = ImageCapture.OutputFileOptions.Builder(ficheiro).build()
        processando = true
        binding.btnCapturar.isEnabled = false
        captura.takePicture(
            opcoes,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        processarFicheiro(ficheiro)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        processando = false
                        binding.btnCapturar.isEnabled = true
                        ficheiro.delete()
                        avisar(getString(R.string.camera_error), exception.message.orEmpty())
                    }
                }
            }
        )
    }

    private fun processarFicheiro(ficheiro: File) {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { Imagem.carregar(ficheiro.absolutePath) }
                ficheiro.delete()
                if (bitmap == null) {
                    avisar(getString(R.string.photo_invalid), getString(R.string.camera_photo_invalid_hint))
                    return@launch
                }
                processarBitmap(bitmap)
            } finally {
                processando = false
                binding.btnCapturar.isEnabled = true
            }
        }
    }

    private fun abrirGaleria() {
        try {
            escolhaGaleria.launch("image/*")
        } catch (e: Exception) {
            avisar(getString(R.string.camera_gallery_unavailable), getString(R.string.camera_photo_invalid_hint))
        }
    }

    private fun processarGaleria(uri: Uri) {
        if (processando) return
        processando = true
        lifecycleScope.launch {
            try {
                val tipo = contentResolver.getType(uri)
                if (tipo != null && !tipo.startsWith("image/")) {
                    avisar(getString(R.string.camera_file_invalid), getString(R.string.camera_photo_invalid_hint))
                    return@launch
                }
                val tamanho = withContext(Dispatchers.IO) {
                    try {
                        contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                    } catch (e: Exception) {
                        -1L
                    }
                }
                if (tamanho > 12L * 1024L * 1024L) {
                    avisar(getString(R.string.photo_too_large), getString(R.string.camera_file_too_large))
                    return@launch
                }
                val bitmap = withContext(Dispatchers.IO) { Imagem.carregar(this@CameraActivity, uri) }
                if (bitmap == null) {
                    avisar(getString(R.string.photo_invalid), getString(R.string.camera_photo_invalid_hint))
                    return@launch
                }
                processarBitmap(bitmap)
            } finally {
                processando = false
            }
        }
    }

    private suspend fun processarBitmap(original: Bitmap) {
        val largura = original.width
        val altura = original.height
        if (maxOf(largura, altura) < Imagem.MIN_LARGURA ||
            minOf(largura, altura) < Imagem.MIN_ALTURA
        ) {
            avisar(
                getString(R.string.camera_unavailable),
                getString(R.string.camera_resolution_received, largura, altura)
            )
            return
        }
        val processada = withContext(Dispatchers.IO) { Imagem.processar(original) }
        if (processada == null) {
            avisar(getString(R.string.photo_too_large), getString(R.string.camera_photo_invalid_hint))
            return
        }
        foto = processada
        withContext(Dispatchers.Main) {
            piscarObturador()
        }
        mostrarFoto(processada)
    }

    /* ------------------------- Interface ------------------------- */

    private fun mostrarFoto(processada: FotoProcessada) {
        cameraProvider?.unbindAll()
        cameraPronta = false
        val bitmap = Imagem.deBase64(processada.base64)
        binding.imgPreview.setImageBitmap(bitmap)
        binding.imgPreview.visibility = View.VISIBLE
        binding.preview.visibility = View.GONE
        binding.frameCamera.visibility = View.GONE
        binding.tvDicaCamera.visibility = View.GONE
        binding.btnCapturar.visibility = View.GONE
        binding.wrapMensagem.visibility = View.GONE
        binding.btnRepetir.visibility = View.VISIBLE
        binding.btnUsarFoto.visibility = View.VISIBLE
        Animacoes.pop(binding.imgPreview, 0.96f)
        Animacoes.entrada(
            binding.btnRepetir,
            binding.btnUsarFoto,
            atraso = 120L,
            intervalo = 70L,
            deslocamentoY = 10f
        )
    }

    /** Clarão rápido do obturador ao concluir a captura. */
    private fun piscarObturador() {
        if (!Animacoes.ativas(this)) return
        val flash = binding.flashShutter
        flash.animate().cancel()
        flash.alpha = 0f
        flash.animate()
            .alpha(0.85f)
            .setDuration(70L)
            .withEndAction {
                flash.animate()
                    .alpha(0f)
                    .setDuration(200L)
                    .start()
            }
            .start()
    }

    /** Revela a moldura e os botões quando a câmara fica pronta. */
    private fun revelarBotoes() {
        if (animouBotoes) return
        animouBotoes = true
        Animacoes.pop(binding.btnCapturar, 0.82f)
        Animacoes.surgir(binding.frameCamera, atraso = 40L, deslocamentoY = 8f, duracao = 320L)
    }

    private fun reiniciarCamera() {
        foto = null
        iniciarCamera()
    }

    private fun reiniciarUi() {
        binding.wrapMensagem.visibility = View.GONE
        binding.frameCamera.visibility = View.VISIBLE
        binding.tvDicaCamera.visibility = View.VISIBLE
        binding.btnCapturar.visibility = View.VISIBLE
        binding.btnCapturar.isEnabled = true
        binding.btnRepetir.visibility = View.GONE
        binding.btnUsarFoto.visibility = View.GONE
        binding.imgPreview.visibility = View.GONE
        binding.imgPreview.setImageDrawable(null)
        binding.preview.visibility = View.VISIBLE
        binding.btnGaleria.visibility = View.VISIBLE
    }

    private fun mostrarMensagem(titulo: String, texto: String) {
        cameraProvider?.unbindAll()
        cameraPronta = false
        binding.wrapMensagem.visibility = View.VISIBLE
        binding.tvMsgTitulo.text = titulo
        binding.tvMsgTexto.text = texto
        binding.frameCamera.visibility = View.GONE
        binding.tvDicaCamera.visibility = View.GONE
        binding.btnCapturar.visibility = View.GONE
        binding.btnRepetir.visibility = View.GONE
        binding.btnUsarFoto.visibility = View.GONE
        binding.btnGaleria.visibility = View.VISIBLE
    }

    private fun usarFoto() {
        val processada = foto
        if (processada == null) {
            avisar(getString(R.string.photo_required), getString(R.string.camera_capture_missing))
            return
        }
        // Não transportar a imagem em Base64 no Intent: imagens grandes podem
        // exceder o limite Binder e fechar a aplicação ao devolver o resultado.
        try {
            val ficheiro = File(cacheDir, "foto_${System.currentTimeMillis()}.b64")
            ficheiro.writeText(processada.base64, Charsets.UTF_8)
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(EXTRA_FICHEIRO, ficheiro.absolutePath)
                    .putExtra(EXTRA_LARGURA, processada.largura)
                    .putExtra(EXTRA_ALTURA, processada.altura)
            )
            Toast.makeText(this, R.string.camera_ready, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            avisar(getString(R.string.photo_required), e.localizedMessage ?: "Não foi possível preparar a fotografia.")
        }
    }

    private fun avisar(titulo: String, mensagem: String) {
        val texto = if (mensagem.isBlank() || mensagem == titulo) titulo else "$titulo — $mensagem"
        Toast.makeText(this, texto, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FICHEIRO = "foto_ficheiro_cache"
        const val EXTRA_LARGURA = "foto_largura"
        const val EXTRA_ALTURA = "foto_altura"
    }
}
