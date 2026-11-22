package mz.co.logitrack.motorista.network

import mz.co.logitrack.motorista.data.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface Api {
    @POST("auth/login")
    suspend fun login(@Body req: LoginRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun me(): Response<UtilizadorDto>

    @GET("auth/me")
    suspend fun meComToken(@Header("Authorization") autorizacao: String): Response<UtilizadorDto>

    @GET("mobile/dashboard")
    suspend fun dashboard(): Response<DashboardDto>

    @GET("mobile/referencias")
    suspend fun referencias(): Response<ReferenciasDto>

    @GET("mobile/carregamentos")
    suspend fun carregamentos(): Response<List<RecenteDto>>

    @GET("mobile/carregamentos/{id}")
    suspend fun carregamento(@Path("id") id: Long): Response<CarregamentoDto>

    @POST("carregamentos")
    suspend fun syncCarregamento(@Body req: SyncRequest): Response<RecenteDto>

    @POST("carregamentos/{id}/posicoes")
    suspend fun registarPosicao(
        @Path("id") id: Long,
        @Body req: PosicaoRequest
    ): Response<PosicaoDto>

    @PATCH("mobile/carregamentos/{id}/fases")
    suspend fun registarFases(
        @Path("id") id: Long,
        @Body req: RegistarFasesRequest
    ): Response<RecenteDto>

    @GET("mobile/notificacoes")
    suspend fun notificacoes(): Response<List<NotificacaoDto>>

    @GET("mobile/notificacoes/nao-lidas")
    suspend fun notificacoesNaoLidas(): Response<Map<String, Long>>

    @PATCH("mobile/notificacoes/{id}/lida")
    suspend fun marcarNotificacaoLida(
        @Path("id") id: Long,
        @Body req: MarcarLidoRequest
    ): Response<NotificacaoDto>

    @PATCH("mobile/notificacoes/lidas")
    suspend fun marcarNotificacoesLidas(): Response<Map<String, Int>>

    @Streaming
    @GET("documentos/{id}/ficheiro")
    suspend fun documento(@Path("id") id: Long): Response<ResponseBody>
}
