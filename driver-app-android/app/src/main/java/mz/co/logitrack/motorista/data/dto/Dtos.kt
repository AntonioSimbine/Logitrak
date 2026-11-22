package mz.co.logitrack.motorista.data.dto

/* Modelos alinhados com os DTOs reais do backend Logitrack. */

data class LoginRequest(val email: String, val senha: String)

data class AuthResponse(
    val token: String,
    val tipo: String?,
    val expiresInSegundos: Long,
    val utilizador: UtilizadorDto
)

data class UtilizadorDto(
    val id: Long,
    val nome: String,
    val email: String,
    val perfil: String?,
    val perfilEtiqueta: String?,
    val ativo: Boolean?,
    val dataCriacao: String?
)

data class DashboardDto(
    val motoristaId: Long?,
    val motoristaNome: String?,
    val recentes: List<RecenteDto>?,
    val total: Long,
    val emTransito: Long,
    val atrasados: Long,
    val concluidos: Long
)

data class RecenteDto(
    val id: Long,
    val uuidLocal: String?,
    val guiaNumero: String,
    val tipoCarga: String,
    val quantidadeToneladas: Double,
    val estado: String,
    val estadoEtiqueta: String?,
    val dataHoraInicio: String?,
    val dataHoraFim: String?,
    val motivoRejeicao: String?,
    val fabricaId: Long?,
    val fabricaNome: String?,
    val veiculoId: Long?,
    val veiculoMatricula: String?,
    val motoristaId: Long?,
    val motoristaNome: String?,
    val atrasoMinutos: Long,
    val atrasado: Boolean,
    val temDocumento: Boolean
)

data class ReferenciasDto(
    val fabricas: List<FabricaDto>?,
    val veiculos: List<VeiculoDto>?,
    val parametros: Map<String, String>?,
    val tiposCarga: List<String>?
)

data class FabricaDto(
    val id: Long,
    val nome: String,
    val endereco: String?,
    val latitude: Double?,
    val longitude: Double?,
    val raioGeofenceMetros: Int?,
    val slaLimiteHoras: Int?,
    val horarioAcostagem: String?
)

data class VeiculoDto(
    val id: Long,
    val matricula: String,
    val modelo: String?,
    val capacidadeCargaToneladas: Double?,
    val estado: String?
)

data class CarregamentoDto(
    val id: Long,
    val uuidLocal: String?,
    val guiaNumero: String,
    val tipoCarga: String,
    val quantidadeToneladas: Double,
    val estado: String,
    val estadoEtiqueta: String?,
    val dataHoraInicio: String?,
    val dataHoraFim: String?,
    val motivoRejeicao: String?,
    val atrasoMinutos: Long,
    val horaLimite: String?,
    val fabrica: FabricaDetalheDto?,
    val veiculo: VeiculoDetalheDto?,
    val motorista: MotoristaDto?,
    val documento: DocumentoDto?,
    val posicoes: List<PosicaoDto>?,
    val ultimaPosicao: PosicaoDto?,
    val timeline: List<TimelineDto>?,
    val alertasAbertos: Int,
    val fases: FasesDto?
)

data class FasesDto(
    val inicioEspera: String?,
    val inicioCarregamento: String?,
    val fimCarregamento: String?,
    val inicioTransporte: String?,
    val fimTransporte: String?,
    val esperaMinutos: Long?,
    val carregamentoMinutos: Long?,
    val transporteMinutos: Long?
)

data class FabricaDetalheDto(
    val id: Long,
    val nome: String,
    val endereco: String?,
    val latitude: Double?,
    val longitude: Double?,
    val raioGeofenceMetros: Int?,
    val slaLimiteHoras: Int?,
    val horarioAcostagem: String?,
    val ativa: Boolean?
)

data class VeiculoDetalheDto(
    val id: Long,
    val matricula: String,
    val modelo: String?,
    val capacidadeCargaToneladas: Double?,
    val estado: String?,
    val estadoEtiqueta: String?
)

data class MotoristaDto(val id: Long, val nome: String, val email: String)

data class DocumentoDto(
    val id: Long,
    val caminhoFotografia: String?,
    val dataHoraCaptura: String?,
    val tamanhoBytes: Long?,
    val mimeType: String?,
    val hashSha256: String?
)

data class PosicaoDto(
    val latitude: Double,
    val longitude: Double,
    val precisaoMetros: Double?,
    val timestamp: String?
)

data class TimelineDto(
    val codigo: String?,
    val titulo: String,
    val concluida: Boolean,
    val dataHora: String?,
    val icone: String?
)

data class SyncRequest(
    val uuidLocal: String,
    val guiaNumero: String,
    val tipoCarga: String,
    val quantidadeToneladas: Double,
    val fabricaId: Long?,
    val veiculoId: Long,
    val latitude: Double,
    val longitude: Double,
    val precisaoMetros: Double,
    val dataHoraInicio: String?,
    val dataHoraInicioCarregamento: String? = null,
    val dataHoraFimCarregamento: String? = null,
    val dataHoraInicioTransporte: String? = null,
    val fotoBase64: String,
    val confirmarForaGeofence: Boolean
)

data class PosicaoRequest(
    val latitude: Double,
    val longitude: Double,
    val precisaoMetros: Double,
    val timestamp: String? = null
)

/** Contrato do PATCH /mobile/carregamentos/{id}/fases. */
data class RegistarFasesRequest(
    val dataHoraInicioCarregamento: String? = null,
    val dataHoraFimCarregamento: String? = null,
    val dataHoraInicioTransporte: String? = null
)

data class ApiErrorDto(val error: String?, val detalhes: Map<String, String>?)
