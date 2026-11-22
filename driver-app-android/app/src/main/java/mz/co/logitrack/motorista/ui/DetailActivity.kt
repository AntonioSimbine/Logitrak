package mz.co.logitrack.motorista.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.dto.CarregamentoDto
import mz.co.logitrack.motorista.data.local.FilaEntity
import mz.co.logitrack.motorista.databinding.ActivityDetailBinding
import mz.co.logitrack.motorista.databinding.ItemTimelineBinding
import mz.co.logitrack.motorista.network.NetworkUtils
import mz.co.logitrack.motorista.network.RetrofitClient
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Prefs
import mz.co.logitrack.motorista.util.Sessao
import mz.co.logitrack.motorista.worker.FaseOperacional
import mz.co.logitrack.motorista.worker.FasesManager

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private lateinit var prefs: Prefs
    private val gson = Gson()

    private var id: Long = 0L
    private var carregando = false
    private var guiaJob: Job? = null
    private var detalheAtual: CarregamentoDto? = null
    private var animouConteudo = false
    private var animouTimeline = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)
        id = intent.getLongExtra(EXTRA_ID, 0L)
        if (id <= 0L) {
            finish()
            return
        }

        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnAtualizar.setOnClickListener { carregar(forcar = true) }
        binding.btnTentar.setOnClickListener { carregar(forcar = true) }
        binding.btnFaseInicioCarregamento.setOnClickListener {
            registarFase(FaseOperacional.INICIO_CARREGAMENTO)
        }
        binding.btnFaseFimCarregamento.setOnClickListener {
            registarFase(FaseOperacional.FIM_CARREGAMENTO)
        }
        binding.btnFaseInicioTransporte.setOnClickListener {
            registarFase(FaseOperacional.INICIO_TRANSPORTE)
        }

        Animacoes.surgir(binding.root.getChildAt(0), atraso = 40L, deslocamentoY = 14f)
        Animacoes.cascata(binding.detailContent, atraso = 150L, intervalo = 60L)
        Animacoes.pressionar(
            binding.btnVoltar,
            binding.btnAtualizar,
            binding.btnTentar,
            binding.btnFaseInicioCarregamento,
            binding.btnFaseFimCarregamento,
            binding.btnFaseInicioTransporte
        )

        carregar(forcar = false)
    }

    /* ------------------------- Carregamento ------------------------- */

    private fun carregar(forcar: Boolean) {
        if (carregando) return
        carregando = true
        guiaJob?.cancel()

        val cache = lerCache(id)
        if (cache != null) {
            detalheAtual = cache
            render(cache)
        } else if (detalheAtual == null) {
            mostrarCarregando()
        }

        if (!NetworkUtils.estaLigado(this)) {
            carregando = false
            if (cache == null) {
                mostrarErro(
                    getString(R.string.detail_offline),
                    getString(R.string.detail_offline_text)
                )
            } else {
                mostrarGuiaOffline()
            }
            return
        }

        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.getApi(this@DetailActivity).carregamento(id)
                val corpo = resp.body()
                when {
                    resp.isSuccessful && corpo != null -> {
                        detalheAtual = corpo
                        prefs.guardarDetalhe(id, gson.toJson(corpo))
                        render(corpo)
                    }
                    resp.code() == 401 -> {
                        Sessao.sinalizarExpiracao()
                        if (cache == null) mostrarErro(
                            getString(R.string.session_expired_title),
                            getString(R.string.session_expired_message)
                        )
                    }
                    cache == null -> mostrarErro(
                        getString(R.string.detail_error),
                        getString(R.string.detail_error_text)
                    )
                }
            } catch (e: Exception) {
                if (cache == null) {
                    mostrarErro(
                        getString(R.string.detail_error),
                        e.message?.takeIf { it.isNotBlank() }
                            ?: getString(R.string.detail_error_text)
                    )
                } else {
                    mostrarGuiaOffline()
                }
            } finally {
                carregando = false
            }
        }
    }

    private fun lerCache(id: Long): CarregamentoDto? {
        val texto = prefs.lerDetalhe(id) ?: return null
        return try {
            gson.fromJson(texto, CarregamentoDto::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /* ------------------------- Render ------------------------- */

    private fun render(dados: CarregamentoDto) {
        val b = binding
        b.progressoDetalhe.visibility = View.GONE
        b.wrapErro.visibility = View.GONE
        b.conteudoDetalhe.visibility = View.VISIBLE
        if (!animouConteudo) {
            animouConteudo = true
            Animacoes.cascata(b.conteudoDetalhe, atraso = 80L, intervalo = 55L)
        }

        b.tvGuia.text = dados.guiaNumero
        b.tvFabrica.text = dados.fabrica?.nome?.takeIf { it.isNotBlank() }
            ?: getString(R.string.detail_no_factory)

        val chip = EstadoUi.chipEscuro(dados.estado)
        b.tvEstado.setBackgroundResource(chip.fundo)
        b.tvEstado.setTextColor(chip.corTexto)
        b.tvEstado.text = EstadoUi.etiqueta(this, dados.estado, dados.estadoEtiqueta).uppercase()

        b.tvTipo.text = dados.tipoCarga
        b.tvQtd.text = getString(R.string.quantity_value, Formato.numero(dados.quantidadeToneladas, 1))
        b.tvInicio.text = Formato.dataHora(dados.dataHoraInicio)
        val concluido = !dados.dataHoraFim.isNullOrBlank()
        b.tvFimLabel.setText(if (concluido) R.string.detail_finish else R.string.detail_limit)
        b.tvFim.text = Formato.dataHora(if (concluido) dados.dataHoraFim else dados.horaLimite)
        b.tvVeiculo.text = dados.veiculo?.matricula?.takeIf { it.isNotBlank() }
            ?: getString(R.string.not_available)
        b.tvMotorista.text = dados.motorista?.nome?.takeIf { it.isNotBlank() }
            ?: getString(R.string.not_available)

        val atraso = dados.atrasoMinutos.coerceAtLeast(0L)
        if (atraso > 0) {
            b.cardAtraso.visibility = View.VISIBLE
            b.tvAtraso.text = getString(R.string.detail_late, atraso / 60, atraso)
        } else {
            b.cardAtraso.visibility = View.GONE
        }
        if (dados.alertasAbertos > 0 && atraso == 0L) {
            b.cardAlertas.visibility = View.VISIBLE
            b.tvAlertas.text = getString(R.string.detail_alerts, dados.alertasAbertos)
        } else {
            b.cardAlertas.visibility = View.GONE
        }

        renderTimeline(dados)
        renderFases(dados)

        val posicao = dados.ultimaPosicao ?: dados.posicoes?.lastOrNull()
        if (posicao != null) {
            b.cardPosicao.visibility = View.VISIBLE
            b.tvLat.text = Formato.numero(posicao.latitude, 6)
            b.tvLng.text = Formato.numero(posicao.longitude, 6)
            b.tvPrecisao.text = posicao.precisaoMetros?.let { Formato.metros(it) }
                ?: getString(R.string.not_available)
            b.tvRegistada.text = Formato.dataHora(posicao.timestamp)
        } else {
            b.cardPosicao.visibility = View.GONE
        }

        b.tvId.text = dados.id.toString()
        b.tvUuid.text = dados.uuidLocal?.takeIf { it.isNotBlank() }
            ?: getString(R.string.not_available)

        val documento = dados.documento
        if (documento != null && documento.id > 0L) {
            carregarGuia(documento.id)
        } else {
            b.imgGuia.visibility = View.GONE
            b.progressoGuia.visibility = View.GONE
            b.tvGuiaPlaceholder.visibility = View.VISIBLE
            b.tvGuiaPlaceholder.setText(R.string.detail_photo_none)
        }
    }

    private fun renderTimeline(dados: CarregamentoDto) {
        val b = binding
        b.timelineContainer.removeAllViews()
        val etapas = dados.timeline.orEmpty()
        b.tvSemEtapas.visibility = if (etapas.isEmpty()) View.VISIBLE else View.GONE
        etapas.forEachIndexed { indice, etapa ->
            val item = ItemTimelineBinding.inflate(
                LayoutInflater.from(this), b.timelineContainer, false
            )
            val concluida = etapa.concluida
            item.wrapPonto.setBackgroundResource(
                if (concluida) R.drawable.bg_step_done else R.drawable.bg_step_pending
            )
            item.imgCheck.visibility = if (concluida) View.VISIBLE else View.GONE
            item.tvTitulo.text = etapa.titulo
            item.tvData.text = if (etapa.dataHora.isNullOrBlank()) {
                getString(R.string.detail_pending_step)
            } else {
                Formato.dataHora(etapa.dataHora)
            }
            item.linha.visibility =
                if (indice == etapas.lastIndex) View.INVISIBLE else View.VISIBLE
            b.timelineContainer.addView(item.root)
        }
        if (!animouTimeline && etapas.isNotEmpty()) {
            animouTimeline = true
            Animacoes.cascata(b.timelineContainer, atraso = 260L, intervalo = 45L, deslocamentoY = 10f)
        }
    }

    /* ------------------------- Fases operacionais ------------------------- */

    private fun renderFases(dados: CarregamentoDto) {
        val b = binding
        val fases = dados.fases
        b.tvFaseEspera.text = duracao(fases?.esperaMinutos)
        b.tvFaseCarregamento.text = duracao(fases?.carregamentoMinutos)
        b.tvFaseTransporte.text = duracao(fases?.transporteMinutos)

        lifecycleScope.launch {
            val local = FasesManager.local(this@DetailActivity, dados.id)
            withContext(Dispatchers.Main) { aplicarEstadoLocal(dados, local) }
        }
    }

    private fun duracao(minutos: Long?): String =
        if (minutos == null) getString(R.string.phase_no_data)
        else getString(R.string.phase_minutes, minutos)

    private fun aplicarEstadoLocal(dados: CarregamentoDto, local: FilaEntity?) {
        val b = binding
        val fases = dados.fases
        val gravacao = local?.tempoGravacaoLocalMs ?: 0L
        b.tvGravacaoLocal.visibility = if (gravacao > 0L) View.VISIBLE else View.GONE
        if (gravacao > 0L) {
            b.tvGravacaoLocal.text = getString(R.string.phase_recorded_local)
        }
        b.btnFaseInicioCarregamento.isEnabled =
            fases?.inicioCarregamento.isNullOrBlank() && local?.dataHoraInicioCarregamento == null
        b.btnFaseFimCarregamento.isEnabled =
            fases?.fimCarregamento.isNullOrBlank() && local?.dataHoraFimCarregamento == null
        b.btnFaseInicioTransporte.isEnabled =
            fases?.inicioTransporte.isNullOrBlank() && local?.dataHoraInicioTransporte == null
    }

    private fun registarFase(fase: FaseOperacional) {
        lifecycleScope.launch {
            val local = FasesManager.local(this@DetailActivity, id)
            if (local == null) {
                avisar(R.string.phase_not_found)
                return@launch
            }
            val gravado = FasesManager.registar(
                this@DetailActivity,
                carregamentoId = id,
                uuidLocal = local.uuidLocal,
                fase = fase
            )
            if (!gravado) {
                avisar(R.string.phase_already_recorded)
                return@launch
            }
            avisar(R.string.phase_saved_local)
            detalheAtual?.let { renderFases(it) }
            FasesManager.sincronizar(this@DetailActivity)
            carregar(forcar = true)
        }
    }

    /* ------------------------- Guia digitalizada ------------------------- */

    private fun carregarGuia(documentoId: Long) {
        val b = binding
        b.imgGuia.visibility = View.GONE
        b.tvGuiaPlaceholder.visibility = View.VISIBLE
        b.tvGuiaPlaceholder.setText(R.string.detail_photo_loading)

        if (!NetworkUtils.estaLigado(this)) {
            b.progressoGuia.visibility = View.GONE
            b.tvGuiaPlaceholder.setText(R.string.detail_photo_offline)
            return
        }

        b.progressoGuia.visibility = View.VISIBLE
        guiaJob = lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    RetrofitClient.getApi(this@DetailActivity).documento(documentoId)
                }
                val bytes = withContext(Dispatchers.IO) { resp.body()?.bytes() }
                if (!resp.isSuccessful || bytes == null) throw IllegalStateException("sem ficheiro")
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                if (bitmap == null) throw IllegalStateException("imagem inválida")
                b.imgGuia.setImageBitmap(bitmap)
                b.imgGuia.visibility = View.VISIBLE
                b.tvGuiaPlaceholder.visibility = View.GONE
                Animacoes.pop(b.imgGuia, 0.96f)
            } catch (e: Exception) {
                b.imgGuia.visibility = View.GONE
                b.tvGuiaPlaceholder.visibility = View.VISIBLE
                b.tvGuiaPlaceholder.setText(
                    if (NetworkUtils.estaLigado(this@DetailActivity)) {
                        R.string.detail_photo_error
                    } else {
                        R.string.detail_photo_offline
                    }
                )
            } finally {
                b.progressoGuia.visibility = View.GONE
            }
        }
    }

    private fun mostrarGuiaOffline() {
        val b = binding
        if (detalheAtual?.documento == null) {
            b.tvGuiaPlaceholder.setText(R.string.detail_photo_none)
        } else {
            b.tvGuiaPlaceholder.setText(R.string.detail_photo_offline)
        }
        b.imgGuia.visibility = View.GONE
        b.tvGuiaPlaceholder.visibility = View.VISIBLE
        b.progressoGuia.visibility = View.GONE
    }

    /* ------------------------- Estados ------------------------- */

    private fun mostrarCarregando() {
        val b = binding
        b.progressoDetalhe.visibility = View.VISIBLE
        b.wrapErro.visibility = View.GONE
        b.conteudoDetalhe.visibility = View.GONE
    }

    private fun mostrarErro(titulo: String, texto: String) {
        val b = binding
        b.progressoDetalhe.visibility = View.GONE
        b.conteudoDetalhe.visibility = View.GONE
        b.wrapErro.visibility = View.VISIBLE
        b.tvErroTitulo.text = titulo
        b.tvErroTexto.text = texto
    }

    private fun avisar(texto: Int) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
    }

    private fun cor(recurso: Int): Int = ContextCompat.getColor(this, recurso)

    companion object {
        const val EXTRA_ID = "carregamento_id"
    }
}
