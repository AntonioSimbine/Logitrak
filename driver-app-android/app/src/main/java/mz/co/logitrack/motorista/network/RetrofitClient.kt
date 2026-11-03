package mz.co.logitrack.motorista.network

import android.content.Context
import mz.co.logitrack.motorista.util.Prefs
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Cliente Retrofit reconstruído quando o endereço do servidor ou o token
 * mudam. Assim a configuração do servidor passa a ter efeito imediato.
 */
object RetrofitClient {
    private var retrofit: Retrofit? = null
    private var api: Api? = null
    private var baseAtual: String? = null
    private var tokenAtual: String? = null

    @Synchronized
    fun getApi(context: Context): Api {
        val prefs = Prefs(context)
        val base = prefs.apiBase
        val token = prefs.token
        val existente = api
        if (existente != null && base == baseAtual && token == tokenAtual) {
            return existente
        }
        tokenAtual = token
        baseAtual = base

        val authInterceptor = Interceptor { chain ->
            val req = chain.request()
            val builder = req.newBuilder().addHeader("Accept", "application/json")
            val atual = Prefs(context).token
            if (!atual.isNullOrBlank()) {
                builder.addHeader("Authorization", "Bearer $atual")
            }
            chain.proceed(builder.build())
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val novo = Retrofit.Builder()
            .baseUrl(prefs.apiRoot)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit = novo
        return novo.create(Api::class.java).also { api = it }
    }

    @Synchronized
    fun invalidar() {
        api = null
        retrofit = null
        baseAtual = null
        tokenAtual = null
    }

    fun urlAbsoluta(context: Context, caminho: String): String {
        val base = Prefs(context).apiBase.trimEnd('/')
        return if (caminho.startsWith("http")) caminho else "$base${if (caminho.startsWith("/")) caminho else "/$caminho"}"
    }
}
