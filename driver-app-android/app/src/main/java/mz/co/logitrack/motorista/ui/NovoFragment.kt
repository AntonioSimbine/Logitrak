package mz.co.logitrack.motorista.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.data.dto.FabricaDto
import mz.co.logitrack.motorista.data.dto.ReferenciasDto
import mz.co.logitrack.motorista.data.dto.VeiculoDto
import mz.co.logitrack.motorista.data.local.AppDb
import mz.co.logitrack.motorista.data.local.FilaEntity
import mz.co.logitrack.motorista.databinding.FragmentNovoBinding
import mz.co.logitrack.motorista.network.RetrofitClient
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Geo
import mz.co.logitrack.motorista.util.Imagem
import mz.co.logitrack.motorista.util.Ligacao
import mz.co.logitrack.motorista.util.Prefs
import mz.co.logitrack.motorista.worker.SyncWorker
import java.io.File
import java.util.Date
import java.util.UUID

class NovoFragment : Fragment(R.layout.fragment_novo) {
    private var _binding: FragmentNovoBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs
    private val gsonRascunho = com.google.gson.Gson()
    private var rascunhoRestaurado = false

    private data class RascunhoNovo(
        val guia: String = "",
        val quantidade: String = "",
        val tipo: String = "",
        val veiculoId: Long = 0L,
        val fabricaId: Long? = null,
        val passo: Int = 1
    )

    private var refs: ReferenciasDto? = null
    private var veiculos: List<VeiculoDto> = emptyList()
    private var fabricas: List<FabricaDto> = emptyList()
    private var tipos: List<String> = emptyList()

    private var veiculoId: Long = 0L
    private var tipoSelecionado: String = ""
    private var fabricaId: Long? = null
    private var fabricaNome: String = ""
    private var foraGeofence = false

    private var fotoBase64: String? = null
    private var fotoLargura = 0
    private var fotoAltura = 0

    private var lat: Double? = null
    private var lng: Double? = null
    private var precisao: Double = 0.0
    // Instante da leitura GPS, usado como hora real de início do carregamento.
    private var capturaEm: String? = null

    private var ligado = true
    private var aGuardar = false
    private var passo = 1
    private var gpsToken: CancellationTokenSource? = null
    private var gpsTimeout: Job? = null

    private val permissaoGps = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        val concedida = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            resultado[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (concedida) {
            obterGps()
        } else {
            mostrarGpsIndisponivel(getString(R.string.gps_permission_denied))
            avisar(R.string.gps_permission_denied)
        }
    }

    private val resultadoFoto = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        val dados = resultado.data ?: return@registerForActivityResult
        val caminho = dados.getStringExtra(CameraActivity.EXTRA_FICHEIRO) ?: return@registerForActivityResult
        val ficheiro = File(caminho)
        val base64 = try {
            ficheiro.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            avisar(R.string.photo_required)
            return@registerForActivityResult
        } finally {
            ficheiro.delete()
        }
        if (base64.isBlank()) {
            avisar(R.string.photo_required)
            return@registerForActivityResult
        }
        val largura = dados.getIntExtra(CameraActivity.EXTRA_LARGURA, 0)
        val altura = dados.getIntExtra(CameraActivity.EXTRA_ALTURA, 0)
        fotoBase64 = base64
        fotoLargura = largura
        fotoAltura = altura
        atualizarFoto()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNovoBinding.bind(view)
        prefs = Prefs(requireContext())
        ligado = Ligacao.estado.value

        binding.btnVoltar.setOnClickListener { voltar() }
        binding.btnAnterior.setOnClickListener { mostrarPasso(passo - 1) }
        binding.btnProximo.setOnClickListener {
            if (validarPasso()) mostrarPasso(passo + 1)
        }
        binding.btnFoto.setOnClickListener { abrirCamera() }
        binding.btnAtualizarGps.setOnClickListener { pedirGps() }
        binding.btnGuardar.setOnClickListener { guardar() }
        binding.cbConfirmar.setOnCheckedChangeListener { _, _ -> atualizarResumo() }

        binding.etGuia.doAfterTextChanged { atualizarResumo() }
        binding.etQtd.doAfterTextChanged { atualizarResumo() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (passo > 1) {
                mostrarPasso(passo - 1)
            } else {
                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        configurarVeiculos()
        configurarTipos()
        configurarFabricas()

        mostrarPasso(1, animar = false)
        observarLigacao()
        carregarReferencias()
        pedirGps()
        atualizarResumo()
        restaurarRascunho()
        prepararAnimacoes()
    }

    private fun prepararAnimacoes() {
        Animacoes.cascata(requireView() as ViewGroup, atraso = 40L, intervalo = 60L)
        Animacoes.cascata(binding.stepFabrica, atraso = 240L, intervalo = 45L)
        Animacoes.pressionar(
            binding.btnVoltar,
            binding.btnAnterior,
            binding.btnProximo,
            binding.btnGuardar,
            binding.btnFoto,
            binding.btnAtualizarGps
        )
    }

    /* ------------------------- Navegação dos passos ------------------------- */

    private fun voltar() {
        if (passo > 1) {
            mostrarPasso(passo - 1)
        } else {
            findNavController().popBackStack()
        }
    }

    private fun mostrarPasso(novo: Int, animar: Boolean = true) {
        val b = _binding ?: return
        val anterior = passo
        passo = novo.coerceIn(1, 4)

        val passos = listOf(b.stepFabrica, b.stepDocumentos, b.stepLocalizacao, b.stepConfirmar)
        passos.forEachIndexed { indice, vista ->
            vista.visibility = if (indice + 1 == passo) View.VISIBLE else View.GONE
        }

        b.tvStepIndicator.text = getString(R.string.wizard_step, passo)
        b.btnAnterior.visibility = if (passo > 1) View.VISIBLE else View.GONE
        b.btnProximo.visibility = if (passo < 4) View.VISIBLE else View.GONE
        b.btnGuardar.visibility = if (passo == 4) View.VISIBLE else View.GONE
        limparErroFormulario()
        atualizarStepper()

        if (animar) {
            val alvo = passos[passo - 1]
            Animacoes.deslizarPasso(alvo, paraDireita = passo >= anterior)
        }
        b.scrollNovo.smoothScrollTo(0, 0)
    }

    private fun atualizarStepper() {
        val b = _binding ?: return
        val pontos = listOf(b.stepDot1, b.stepDot2, b.stepDot3, b.stepDot4)
        val etiquetas = listOf(b.stepLabel1, b.stepLabel2, b.stepLabel3, b.stepLabel4)
        val linhas = listOf(b.stepLine1, b.stepLine2, b.stepLine3)

        pontos.forEachIndexed { indice, ponto ->
            val numero = indice + 1
            ponto.setBackgroundResource(
                when {
                    numero < passo -> R.drawable.bg_step_done
                    numero == passo -> R.drawable.bg_step_current
                    else -> R.drawable.bg_step_pending
                }
            )
            ponto.text = if (numero < passo) "✓" else numero.toString()
            ponto.setTextColor(cor(if (numero <= passo) R.color.white else R.color.muted))
            etiquetas[indice].setTextColor(
                cor(
                    when {
                        numero == passo -> R.color.blue
                        numero < passo -> R.color.navy
                        else -> R.color.muted
                    }
                )
            )
        }

        linhas.forEachIndexed { indice, linha ->
            linha.setBackgroundColor(cor(if (indice + 1 < passo) R.color.blue else R.color.line))
        }
        Animacoes.pop(pontos[passo - 1], 0.7f)
    }

    private fun validarPasso(): Boolean {
        val b = _binding ?: return false
        val guia = b.etGuia.text?.toString()?.trim().orEmpty()
        val quantidade = b.etQtd.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
            ?: 0.0
        val tipo = tipoSelecionado.ifBlank { b.actvTipo.text?.toString()?.trim().orEmpty() }
        val veiculo = veiculos.firstOrNull { it.id == veiculoId }
        val capacidade = veiculo?.capacidadeCargaToneladas
        limparErroFormulario()

        return when (passo) {
            1 -> when {
                guia.isBlank() -> {
                    erroFormulario(R.string.missing_guide)
                    b.etGuia.requestFocus()
                    false
                }
                veiculo == null -> {
                    erroFormulario(R.string.missing_vehicle)
                    false
                }
                tipo.isBlank() -> {
                    erroFormulario(R.string.missing_type)
                    false
                }
                quantidade <= 0.0 -> {
                    erroFormulario(R.string.invalid_quantity)
                    b.etQtd.requestFocus()
                    false
                }
                capacidade != null && quantidade > capacidade -> {
                    erroFormulario(
                        getString(R.string.capacity_exceeded, Formato.numero(capacidade, 1))
                    )
                    b.etQtd.requestFocus()
                    false
                }
                fabricaId == null -> {
                    erroFormulario(R.string.missing_factory)
                    false
                }
                else -> true
            }
            2 -> if (fotoBase64 == null) {
                erroFormulario(R.string.missing_photo)
                false
            } else {
                true
            }
            3 -> when {
                lat == null || lng == null -> {
                    erroFormulario(R.string.missing_gps)
                    false
                }
                precisao > Geo.precisaoMaxima(refs) -> {
                    erroFormulario(
                        getString(R.string.gps_insufficient, Geo.formatarMetros(Geo.precisaoMaxima(refs)))
                    )
                    false
                }
                else -> true
            }
            else -> true
        }
    }

    /* ------------------------- Referências ------------------------- */

    private fun carregarReferencias() {
        val cache = prefs.lerReferencias()
        if (cache != null) {
            refs = cache
            prepararListas()
            if (lat != null) avaliarGeofence()
        }

        if (!ligado) {
            if (cache == null) {
                avisar(R.string.refs_unavailable)
                findNavController().popBackStack()
            }
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = RetrofitClient.getApi(requireContext()).referencias()
                val corpo = resp.body()
                if (resp.isSuccessful && corpo != null) {
                    refs = corpo
                    prefs.guardarReferencias(corpo)
                    prepararListas()
                    if (lat != null) avaliarGeofence()
                } else if (cache == null) {
                    avisar(R.string.refs_error)
                }
            } catch (e: Exception) {
                if (cache == null) avisar(R.string.refs_error)
            }
        }
    }

    private fun prepararListas() {
        val dados = refs ?: return
        veiculos = dados.veiculos.orEmpty()
        fabricas = dados.fabricas.orEmpty()
        tipos = dados.tiposCarga.orEmpty()
        if (veiculos.isEmpty()) {
            avisar(R.string.no_vehicles)
        }
        configurarVeiculos()
        configurarTipos()
        configurarFabricas()
        if (fabricaId != null) {
            val atual = fabricas.firstOrNull { it.id == fabricaId }
            if (atual != null) _binding?.actvFabrica?.setText(atual.nome, false)
        }
        aplicarRascunhoAposRefs()
    }

    private fun configurarVeiculos() {
        val b = _binding ?: return
        val etiquetas = veiculos.map { v ->
            buildString {
                append(v.matricula)
                v.modelo?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                v.capacidadeCargaToneladas?.let { append(" (").append(Formato.numero(it, 1)).append(" t)") }
            }
        }
        b.actvVeiculo.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, etiquetas)
        )
        b.actvVeiculo.setOnItemClickListener { _, _, posicao, _ ->
            veiculoId = veiculos.getOrNull(posicao)?.id ?: 0L
            atualizarResumo()
        }
    }

    private fun configurarTipos() {
        val b = _binding ?: return
        b.actvTipo.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tipos)
        )
        b.actvTipo.setOnItemClickListener { _, _, posicao, _ ->
            tipoSelecionado = tipos.getOrNull(posicao).orEmpty()
            atualizarResumo()
        }
    }

    private fun configurarFabricas() {
        val b = _binding ?: return
        b.actvFabrica.setAdapter(
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                fabricas.map { it.nome }
            )
        )
        b.actvFabrica.setOnItemClickListener { _, _, posicao, _ ->
            fabricas.getOrNull(posicao)?.let { selecionarFabricaManual(it) }
        }
    }

    /* ------------------------- GPS e geofence ------------------------- */

    private fun pedirGps() {
        val fina = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val aproximada = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fina && !aproximada) {
            permissaoGps.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        obterGps()
    }

    @SuppressLint("MissingPermission")
    private fun obterGps() {
        val b = _binding ?: return
        val gestor = requireContext().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val gpsLigado = gestor?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val redeLigada = gestor?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        if (!gpsLigado && !redeLigada) {
            mostrarGpsIndisponivel(getString(R.string.gps_services_off))
            avisar(R.string.gps_services_off)
            return
        }

        b.tvGpsPrecisao.setText(R.string.gps_waiting)
        b.tvGpsPrecisao.setTextColor(cor(R.color.ink))
        gpsToken?.cancel()
        gpsTimeout?.cancel()
        val token = CancellationTokenSource()
        gpsToken = token
        val fused = LocationServices.getFusedLocationProviderClient(requireActivity())

        gpsTimeout = viewLifecycleOwner.lifecycleScope.launch {
            delay(20_000)
            if (isAdded) {
                mostrarGpsIndisponivel(getString(R.string.gps_timeout))
                avisar(R.string.gps_timeout)
            }
        }

        try {
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { localizacao ->
                    if (localizacao != null) aplicarLocalizacao(localizacao) else obterUltima(token)
                }
                .addOnFailureListener { obterUltima(token) }
        } catch (e: SecurityException) {
            mostrarGpsIndisponivel(getString(R.string.gps_permission_denied))
        } catch (e: Exception) {
            mostrarGpsIndisponivel(getString(R.string.gps_error))
        }
    }

    @SuppressLint("MissingPermission")
    private fun obterUltima(token: CancellationTokenSource) {
        try {
            LocationServices.getFusedLocationProviderClient(requireActivity())
                .lastLocation
                .addOnSuccessListener { localizacao ->
                    if (localizacao != null) {
                        aplicarLocalizacao(localizacao)
                    } else {
                        mostrarGpsIndisponivel(getString(R.string.gps_no_signal))
                    }
                }
                .addOnFailureListener {
                    mostrarGpsIndisponivel(getString(R.string.gps_error))
                }
        } catch (e: Exception) {
            mostrarGpsIndisponivel(getString(R.string.gps_error))
        }
    }

    private fun aplicarLocalizacao(localizacao: Location) {
        gpsTimeout?.cancel()
        val b = _binding ?: return
        val idadeMs = System.currentTimeMillis() - localizacao.time
        if (idadeMs > Geo.IDADE_MAXIMA_POSICAO_MS) {
            mostrarGpsIndisponivel(getString(R.string.gps_no_signal))
            return
        }
        lat = localizacao.latitude
        lng = localizacao.longitude
        precisao = localizacao.accuracy.toDouble()
        capturaEm = Formato.iso(Date(localizacao.time))

        val maximo = Geo.precisaoMaxima(refs)
        if (precisao > maximo) {
            b.tvGpsPrecisao.text = getString(R.string.gps_weak)
            b.tvGpsPrecisao.setTextColor(cor(R.color.amber))
        } else {
            b.tvGpsPrecisao.text = getString(R.string.gps_ok, Geo.formatarMetros(precisao))
            b.tvGpsPrecisao.setTextColor(cor(R.color.green))
        }
        avaliarGeofence()
    }

    private fun mostrarGpsIndisponivel(mensagem: String) {
        gpsTimeout?.cancel()
        val b = _binding ?: return
        b.tvGpsPrecisao.setText(R.string.gps_unavailable)
        b.tvGpsPrecisao.setTextColor(cor(R.color.amber))
        b.tvGpsPrecisao.contentDescription = mensagem
    }

    private fun avaliarGeofence() {
        val b = _binding ?: return
        val posicaoLat = lat
        val posicaoLng = lng
        val dados = refs
        if (posicaoLat == null || posicaoLng == null || dados == null) {
            atualizarGeo(
                getString(R.string.factory_detecting),
                getString(R.string.factory_detecting_info),
                getString(R.string.geofence_waiting),
                R.color.amber
            )
            return
        }
        if (fabricas.isEmpty()) {
            mostrarAvisoFabrica(getString(R.string.factory_none))
            atualizarGeo(
                getString(R.string.factory_detecting),
                getString(R.string.factory_none),
                getString(R.string.geofence_waiting),
                R.color.amber
            )
            return
        }
        val mais = Geo.fabricaMaisProxima(dados.fabricas, posicaoLat, posicaoLng)
        if (mais == null) {
            mostrarAvisoFabrica(getString(R.string.factory_cannot_compare))
            atualizarGeo(
                getString(R.string.factory_detecting),
                getString(R.string.factory_cannot_compare),
                getString(R.string.geofence_waiting),
                R.color.amber
            )
            return
        }

        if (mais.dentro) {
            fabricaId = mais.fabrica.id
            fabricaNome = mais.fabrica.nome
            foraGeofence = false
            b.actvFabrica.setText(mais.fabrica.nome, false)
            b.actvFabrica.isEnabled = false
            b.tvFabricaNome.text = mais.fabrica.nome
            b.tvFabricaInfo.text = getString(
                R.string.factory_gps_detect,
                Math.round(mais.distancia),
                mais.raio
            )
            atualizarGeo(
                mais.fabrica.nome,
                getString(R.string.factory_gps_detect, Math.round(mais.distancia), mais.raio),
                getString(R.string.geofence_inside),
                R.color.green
            )
            definirPillAutomatica(true)
            esconderAvisoFabrica()
            b.wrapFabricaManual.visibility = View.GONE
            b.wrapConfirm.visibility = View.GONE
            b.cbConfirmar.isChecked = false
            atualizarResumo()
            return
        }

        b.actvFabrica.isEnabled = true
        if (fabricaId == null) {
            selecionarFabricaManual(mais.fabrica)
        }
        b.wrapFabricaManual.visibility = View.VISIBLE
        definirPillAutomatica(false)
        mostrarAvisoFabrica(
            getString(
                R.string.factory_outside,
                mais.fabrica.nome,
                Math.round(mais.distancia),
                mais.raio
            )
        )
        atualizarGeo(
            mais.fabrica.nome,
            getString(R.string.factory_outside_short, Math.round(mais.distancia), mais.raio),
            getString(R.string.geofence_outside),
            R.color.amber
        )
        aplicarSelecaoManual()
    }

    private fun atualizarGeo(nome: String, info: String, estado: String, corEstado: Int) {
        val b = _binding ?: return
        b.tvGeoFabrica.text = nome
        b.tvGeoInfo.text = info
        b.tvGeoEstado.text = estado
        b.tvGeoEstado.setTextColor(cor(corEstado))
        b.tvGeoEstado.setBackgroundResource(
            when (corEstado) {
                R.color.green -> R.drawable.bg_chip_green
                R.color.red -> R.drawable.bg_chip_red
                else -> R.drawable.bg_chip_amber
            }
        )
    }

    private fun selecionarFabricaManual(fabrica: FabricaDto) {
        fabricaId = fabrica.id
        fabricaNome = fabrica.nome
        _binding?.actvFabrica?.setText(fabrica.nome, false)
        aplicarSelecaoManual()
        atualizarResumo()
    }

    private fun aplicarSelecaoManual() {
        val b = _binding ?: return
        val fabrica = fabricas.firstOrNull { it.id == fabricaId }
        if (fabrica == null) {
            fabricaId = null
            fabricaNome = ""
            foraGeofence = false
            b.tvFabricaNome.setText(R.string.factory_select)
            b.tvFabricaInfo.setText(R.string.factory_required)
            atualizarGeo(
                getString(R.string.factory_select),
                getString(R.string.factory_required),
                getString(R.string.geofence_waiting),
                R.color.amber
            )
            b.wrapConfirm.visibility = View.GONE
            b.cbConfirmar.isChecked = false
            atualizarResumo()
            return
        }

        b.tvFabricaNome.text = fabrica.nome
        val raio = (fabrica.raioGeofenceMetros ?: Geo.RAIO_PADRAO.toInt()).coerceAtLeast(1)
        val posicaoLat = lat
        val posicaoLng = lng
        val fLat = fabrica.latitude
        val fLng = fabrica.longitude
        if (posicaoLat == null || posicaoLng == null || fLat == null || fLng == null) {
            foraGeofence = false
            b.tvFabricaInfo.setText(R.string.factory_no_location)
            atualizarGeo(
                fabrica.nome,
                getString(R.string.factory_no_location),
                getString(R.string.geofence_waiting),
                R.color.amber
            )
            b.wrapConfirm.visibility = View.GONE
            atualizarResumo()
            return
        }

        val distancia = Geo.haversine(posicaoLat, posicaoLng, fLat, fLng)
        foraGeofence = distancia > raio
        b.tvFabricaInfo.text = if (foraGeofence) {
            getString(R.string.factory_outside_short, Math.round(distancia), raio)
        } else {
            getString(R.string.factory_inside, Math.round(distancia), raio)
        }
        atualizarGeo(
            fabrica.nome,
            b.tvFabricaInfo.text.toString(),
            getString(if (foraGeofence) R.string.geofence_outside else R.string.geofence_inside),
            if (foraGeofence) R.color.amber else R.color.green
        )
        b.wrapConfirm.visibility = if (foraGeofence) View.VISIBLE else View.GONE
        if (!foraGeofence) b.cbConfirmar.isChecked = false
        atualizarResumo()
    }

    private fun definirPillAutomatica(automatica: Boolean) {
        val b = _binding ?: return
        if (automatica) {
            b.pillFabrica.setBackgroundResource(R.drawable.bg_lock_pill)
            b.ivPillFabrica.setImageResource(R.drawable.ic_lock)
            b.ivPillFabrica.imageTintList = ColorStateList.valueOf(cor(R.color.green))
            b.tvPillFabrica.setText(R.string.factory_auto_pill)
            b.tvPillFabrica.setTextColor(cor(R.color.green))
        } else {
            b.pillFabrica.setBackgroundResource(R.drawable.bg_lock_pill_manual)
            b.ivPillFabrica.setImageResource(R.drawable.ic_pin)
            b.ivPillFabrica.imageTintList = ColorStateList.valueOf(cor(R.color.amber))
            b.tvPillFabrica.setText(R.string.factory_gps_pill)
            b.tvPillFabrica.setTextColor(cor(R.color.amber))
        }
    }

    private fun mostrarAvisoFabrica(texto: String) {
        val b = _binding ?: return
        b.tvAvisoFabrica.text = texto
        b.avisoFabrica.visibility = View.VISIBLE
        b.tvAvisoGeo.text = texto
        b.avisoGeo.visibility = View.VISIBLE
    }

    private fun esconderAvisoFabrica() {
        val b = _binding ?: return
        b.avisoFabrica.visibility = View.GONE
        b.avisoGeo.visibility = View.GONE
    }

    /* ------------------------- Fotografia ------------------------- */

    private fun abrirCamera() {
        val intent = Intent(requireContext(), CameraActivity::class.java)
        try {
            resultadoFoto.launch(intent)
        } catch (e: Exception) {
            avisar(R.string.camera_gallery_unavailable)
        }
    }

    private fun atualizarFoto() {
        val b = _binding ?: return
        val base64 = fotoBase64
        if (base64 == null) {
            b.imgFoto.visibility = View.GONE
            b.imgFoto.setImageDrawable(null)
            b.wrapFotoVazia.visibility = View.VISIBLE
            b.tvFotoStatus.setText(R.string.photo_required)
            b.tvFotoStatus.setTextColor(cor(R.color.muted))
            b.tvFotoBotao.setText(R.string.photo_button)
            atualizarResumo()
            return
        }
        Imagem.deBase64(base64)?.let {
            val eraVisivel = b.imgFoto.visibility == View.VISIBLE
            b.imgFoto.setImageBitmap(it)
            b.imgFoto.visibility = View.VISIBLE
            b.wrapFotoVazia.visibility = View.GONE
            if (!eraVisivel) Animacoes.pop(b.imgFoto, 0.94f)
        }
        b.tvFotoStatus.text = getString(R.string.photo_ready, fotoLargura, fotoAltura)
        b.tvFotoStatus.setTextColor(cor(R.color.green))
        b.tvFotoBotao.setText(R.string.photo_replace)
        atualizarResumo()
    }

    private fun atualizarResumo() {
        val b = _binding ?: return
        val guia = b.etGuia.text?.toString()?.trim().orEmpty()
        b.tvResumoGuia.text = guia.ifBlank { getString(R.string.not_available) }

        b.tvResumoFabrica.text = fabricaNome.takeIf { it.isNotBlank() }
            ?: getString(R.string.not_available)

        val veiculo = veiculos.firstOrNull { it.id == veiculoId }
        b.tvResumoVeiculo.text = veiculo?.matricula
            ?: b.actvVeiculo.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.not_available)

        val tipo = tipoSelecionado.ifBlank {
            b.actvTipo.text?.toString()?.trim().orEmpty()
        }
        b.tvResumoTipo.text = tipo.ifBlank { getString(R.string.not_available) }

        val quantidade = b.etQtd.text?.toString()?.trim()
            ?.replace(',', '.')
            ?.toDoubleOrNull() ?: 0.0
        b.tvResumoQtd.text = if (quantidade > 0.0) {
            getString(R.string.quantity_value, Formato.numero(quantidade, 1))
        } else {
            getString(R.string.not_available)
        }

        if (fotoBase64 != null) {
            b.tvResumoFoto.setText(R.string.summary_photo_attached)
            b.tvResumoFoto.setTextColor(cor(R.color.green))
        } else {
            b.tvResumoFoto.setText(R.string.summary_photo_missing)
            b.tvResumoFoto.setTextColor(cor(R.color.amber))
        }
    }

    /* ------------------------- Guardar ------------------------- */

    private fun guardar() {
        if (aGuardar) return
        val b = _binding ?: return
        val guia = b.etGuia.text?.toString()?.trim().orEmpty()
        val quantidade = b.etQtd.text?.toString()?.trim()?.replace(',', '.')?.toDoubleOrNull()
            ?: 0.0
        val tipo = tipoSelecionado.ifBlank { b.actvTipo.text?.toString()?.trim().orEmpty() }
        val veiculo = veiculos.firstOrNull { it.id == veiculoId }
        val posicaoLat = lat
        val posicaoLng = lng
        val maximo = Geo.precisaoMaxima(refs)

        limparErroFormulario()
        when {
            guia.isBlank() -> {
                erroFormulario(R.string.missing_guide)
                b.etGuia.requestFocus()
                return
            }
            veiculo == null -> {
                erroFormulario(R.string.missing_vehicle)
                return
            }
            tipo.isBlank() -> {
                erroFormulario(R.string.missing_type)
                return
            }
            quantidade <= 0.0 -> {
                erroFormulario(R.string.invalid_quantity)
                b.etQtd.requestFocus()
                return
            }
            veiculo.capacidadeCargaToneladas?.let { quantidade > it } == true -> {
                erroFormulario(
                    getString(
                        R.string.capacity_exceeded,
                        Formato.numero(veiculo.capacidadeCargaToneladas!!, 1)
                    )
                )
                b.etQtd.requestFocus()
                return
            }
            fotoBase64 == null -> {
                erroFormulario(R.string.missing_photo)
                return
            }
            posicaoLat == null || posicaoLng == null -> {
                erroFormulario(R.string.missing_gps)
                return
            }
            precisao > maximo -> {
                erroFormulario(getString(R.string.gps_insufficient, Geo.formatarMetros(maximo)))
                return
            }
            fabricaId == null -> {
                erroFormulario(R.string.missing_factory)
                return
            }
            foraGeofence && !b.cbConfirmar.isChecked -> {
                erroFormulario(R.string.confirm_required)
                return
            }
        }

        aGuardar = true
        b.btnGuardar.isEnabled = false
        b.progressoNovo.visibility = View.VISIBLE
        val idFabrica = fabricaId
        val confirmar = foraGeofence && b.cbConfirmar.isChecked
        val entidade = FilaEntity(
            uuidLocal = UUID.randomUUID().toString(),
            motoristaId = prefs.userId,
            guiaNumero = guia,
            tipoCarga = tipo,
            quantidadeToneladas = quantidade,
            fabricaId = idFabrica,
            fabricaNome = fabricaNome.takeIf { it.isNotBlank() },
            veiculoId = veiculo.id,
            veiculoMatricula = veiculo.matricula,
            latitude = posicaoLat,
            longitude = posicaoLng,
            precisaoMetros = precisao,
            fotoBase64 = fotoBase64!!,
            dataHoraInicio = capturaEm ?: Formato.agoraIso(),
            confirmarForaGeofence = confirmar
        )

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val dao = AppDb.get(requireContext()).filaDao()
                val inicioGravacao = System.nanoTime()
                dao.inserir(entidade)
                val milissegundos = (System.nanoTime() - inicioGravacao) / 1_000_000L
                dao.atualizar(entidade.copy(tempoGravacaoLocalMs = milissegundos))
                agendarSincronizacao()
                prefs.guardarRascunhoNovo(null)
                val activity = requireActivity()
                val intent = Intent(activity, SuccessActivity::class.java)
                    .putExtra(SuccessActivity.EXTRA_CODIGO, guia)
                    .putExtra(
                        SuccessActivity.EXTRA_DATA,
                        Formato.dataHora(entidade.dataHoraInicio)
                    )
                findNavController().popBackStack()
                activity.startActivity(intent)
            } catch (e: Exception) {
                avisar(R.string.save_failed)
            } finally {
                aGuardar = false
                _binding?.let {
                    it.btnGuardar.isEnabled = true
                    it.progressoNovo.visibility = View.GONE
                }
            }
        }
    }

    private fun agendarSincronizacao() {
        val contexto = requireContext().applicationContext
        val pedido = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(contexto).enqueue(pedido)
    }

    private fun guardarRascunho() {
        val b = _binding ?: return
        if (prefs.userId <= 0L) return
        val rascunho = RascunhoNovo(
            guia = b.etGuia.text?.toString()?.trim().orEmpty(),
            quantidade = b.etQtd.text?.toString()?.trim().orEmpty(),
            tipo = tipoSelecionado.ifBlank { b.actvTipo.text?.toString()?.trim().orEmpty() },
            veiculoId = veiculoId,
            fabricaId = fabricaId,
            passo = passo
        )
        prefs.guardarRascunhoNovo(gsonRascunho.toJson(rascunho))
    }

    private fun restaurarRascunho() {
        if (rascunhoRestaurado) return
        val texto = prefs.lerRascunhoNovo() ?: return
        val r = try { gsonRascunho.fromJson(texto, RascunhoNovo::class.java) } catch (_: Exception) { null } ?: return
        rascunhoRestaurado = true
        val b = _binding ?: return
        if (b.etGuia.text.isNullOrBlank() && r.guia.isNotBlank()) b.etGuia.setText(r.guia)
        if (b.etQtd.text.isNullOrBlank() && r.quantidade.isNotBlank()) b.etQtd.setText(r.quantidade)
        if (r.tipo.isNotBlank()) {
            tipoSelecionado = r.tipo
            b.actvTipo.setText(r.tipo, false)
        }
        if (r.veiculoId > 0L) veiculoId = r.veiculoId
        if (r.fabricaId != null) fabricaId = r.fabricaId
        if (r.passo in 1..4) mostrarPasso(r.passo, animar = false)
    }

    private fun aplicarRascunhoAposRefs() {
        val texto = prefs.lerRascunhoNovo() ?: return
        val r = try { gsonRascunho.fromJson(texto, RascunhoNovo::class.java) } catch (_: Exception) { null } ?: return
        val b = _binding ?: return
        if (r.veiculoId > 0L) {
            val v = veiculos.firstOrNull { it.id == r.veiculoId }
            if (v != null) {
                veiculoId = v.id
                val etiqueta = buildString {
                    append(v.matricula)
                    v.modelo?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                    v.capacidadeCargaToneladas?.let { append(" (").append(Formato.numero(it, 1)).append(" t)") }
                }
                b.actvVeiculo.setText(etiqueta, false)
            }
        }
        if (r.tipo.isNotBlank() && tipos.contains(r.tipo)) {
            tipoSelecionado = r.tipo
            b.actvTipo.setText(r.tipo, false)
        }
        if (r.fabricaId != null) {
            val f = fabricas.firstOrNull { it.id == r.fabricaId }
            if (f != null) {
                fabricaId = f.id
                fabricaNome = f.nome
                b.actvFabrica.setText(f.nome, false)
            }
        }
        atualizarResumo()
    }

    /* ------------------------- Ligações ------------------------- */

    private fun observarLigacao() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Ligacao.estado.collectLatest { ligado = it }
            }
        }
    }

    private fun avisar(texto: Int) = avisar(getString(texto))

    private fun avisar(texto: String) {
        Toast.makeText(requireContext(), texto, Toast.LENGTH_LONG).show()
    }

    private fun erroFormulario(texto: Int) = erroFormulario(getString(texto))

    private fun erroFormulario(texto: String) {
        val b = _binding ?: return
        b.tvErroFormulario.text = getString(R.string.form_error_title) + ": " + texto
        b.tvErroFormulario.visibility = View.VISIBLE
        Animacoes.tremer(b.tvErroFormulario)
    }

    private fun limparErroFormulario() {
        val b = _binding ?: return
        b.tvErroFormulario.visibility = View.GONE
        b.tvErroFormulario.text = null
    }

    private fun cor(recurso: Int): Int = ContextCompat.getColor(requireContext(), recurso)

    override fun onPause() {
        if (!aGuardar) guardarRascunho()
        super.onPause()
    }

    override fun onDestroyView() {
        gpsToken?.cancel()
        gpsToken = null
        gpsTimeout?.cancel()
        gpsTimeout = null
        super.onDestroyView()
        _binding = null
    }
}
