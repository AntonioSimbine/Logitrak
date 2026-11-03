package mz.co.logitrack.motorista.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

data class FotoProcessada(
    val base64: String,
    val largura: Int,
    val altura: Int,
    val bytes: Int
)

/** Compressão de fotografias equivalente à implementação da PWA. */
object Imagem {
    const val MIN_LARGURA = 640
    const val MIN_ALTURA = 360
    const val MAX_BASE64 = 1_500_000
    private const val MAX_LADO_LEITURA = 2600

    fun carregar(context: Context, uri: Uri): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opcoes = BitmapFactory.Options().apply {
                inSampleSize = calcularAmostra(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opcoes)
            } ?: return null
            val rotacao = try {
                context.contentResolver.openInputStream(uri)?.use { entrada ->
                    when (ExifInterface(entrada).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (_: Exception) {
                0f
            }
            if (rotacao == 0f) bitmap else rotacionar(bitmap, rotacao)
        } catch (_: Exception) {
            null
        }
    }

    fun carregar(caminho: String): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(caminho, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opcoes = BitmapFactory.Options().apply {
                inSampleSize = calcularAmostra(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(caminho, opcoes) ?: return null
            val rotacao = try {
                when (ExifInterface(caminho).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (_: Exception) {
                0f
            }
            if (rotacao == 0f) bitmap else rotacionar(bitmap, rotacao)
        } catch (_: Exception) {
            null
        }
    }

    fun deBase64(valor: String?): Bitmap? {
        if (valor.isNullOrBlank()) return null
        return try {
            val limpo = valor.substringAfter(',', valor)
            val bytes = Base64.decode(limpo, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    /** Decodifica uma miniatura amostrada, poupando memória nas listas. */
    fun deBase64Miniatura(valor: String?, ladoMaximo: Int = 480): Bitmap? {
        if (valor.isNullOrBlank()) return null
        return try {
            val limpo = valor.substringAfter(',', valor)
            val bytes = Base64.decode(limpo, Base64.DEFAULT)
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
            if (limites.outWidth <= 0 || limites.outHeight <= 0) return null

            var amostra = 1
            val maior = maxOf(limites.outWidth, limites.outHeight)
            while (maior / (amostra * 2) >= ladoMaximo) {
                amostra *= 2
            }
            val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)
        } catch (_: Exception) {
            null
        }
    }

    fun processar(original: Bitmap): FotoProcessada? {
        return try {
            var bitmap = escalar(original, 1600)
            var qualidade = 0.82
            var base64 = comprimir(bitmap, qualidade)
            val qualidades = doubleArrayOf(0.72, 0.60, 0.48, 0.38, 0.30)
            var indice = 0
            while (base64.length > MAX_BASE64 && indice < qualidades.size) {
                qualidade = qualidades[indice++]
                base64 = comprimir(bitmap, qualidade)
            }
            if (base64.length > MAX_BASE64) {
                bitmap = escalar(original, 1200)
                qualidade = 0.62
                base64 = comprimir(bitmap, qualidade)
                indice = 0
                while (base64.length > MAX_BASE64 && indice < qualidades.size) {
                    qualidade = qualidades[indice++]
                    base64 = comprimir(bitmap, qualidade)
                }
            }
            if (base64.length > MAX_BASE64) return null
            FotoProcessada(
                base64 = base64,
                largura = bitmap.width,
                altura = bitmap.height,
                bytes = (base64.length * 0.75).toInt()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun comprimir(bitmap: Bitmap, qualidade: Double): String {
        val saida = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, (qualidade * 100).toInt(), saida)
        return Base64.encodeToString(saida.toByteArray(), Base64.NO_WRAP)
    }

    private fun escalar(original: Bitmap, limite: Int): Bitmap {
        val maior = maxOf(original.width, original.height)
        if (maior <= limite) return original
        val escala = limite.toFloat() / maior
        val largura = maxOf(1, (original.width * escala).toInt())
        val altura = maxOf(1, (original.height * escala).toInt())
        return Bitmap.createScaledBitmap(original, largura, altura, true)
    }

    private fun calcularAmostra(largura: Int, altura: Int): Int {
        var amostra = 1
        var maior = maxOf(largura, altura)
        while (maior / 2 >= MAX_LADO_LEITURA) {
            maior /= 2
            amostra *= 2
        }
        return amostra
    }

    private fun rotacionar(bitmap: Bitmap, graus: Float): Bitmap {
        val matriz = Matrix().apply { postRotate(graus) }
        val rodado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
        if (rodado != bitmap) bitmap.recycle()
        return rodado
    }
}
