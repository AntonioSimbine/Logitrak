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

    @Streaming
    @GET("documentos/{id}/ficheiro")
    suspend fun documento(@Path("id") id: Long): Response<ResponseBody>
}
