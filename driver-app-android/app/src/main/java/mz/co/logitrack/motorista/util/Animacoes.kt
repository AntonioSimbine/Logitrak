package mz.co.logitrack.motorista.util

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.doOnLayout
import mz.co.logitrack.motorista.R

/**
 * Camada única de animações da aplicação.
 *
 * Todas as animações são feitas com ViewPropertyAnimator/ObjectAnimator, que
 * correm na thread de composição da GPU. Não são criados bitmaps nem desenhos
 * personalizados. Quando o utilizador desativa as animações no sistema
 * (Accessibility > Remove animations), os métodos devolvem imediatamente o
 * estado final sem animar.
 */
object Animacoes {

    private const val CURTA = 170L
    private const val NORMAL = 300L

    /* ----------------------------- Preferências ----------------------------- */

    fun ativas(contexto: Context): Boolean {
        val escala = Settings.Global.getFloat(
            contexto.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return escala > 0f
    }

    /* ------------------------------- Entradas ------------------------------- */

    /** Faz uma vista surgir suavemente de baixo para cima. */
    fun surgir(
        vista: View,
        atraso: Long = 0L,
        deslocamentoY: Float = 16f,
        escalaInicial: Float = 1f,
        duracao: Long = NORMAL
    ) {
        if (!ativas(vista.context)) return
        vista.animate().cancel()
        vista.alpha = 0f
        vista.translationY = dp(vista, deslocamentoY)
        if (escalaInicial != 1f) {
            vista.scaleX = escalaInicial
            vista.scaleY = escalaInicial
        }
        vista.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(atraso)
            .setDuration(duracao)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()
    }

    /** Entrada escalonada de várias vistas. */
    fun entrada(
        vararg vistas: View,
        deslocamentoY: Float = 16f,
        atraso: Long = 40L,
        intervalo: Long = 55L,
        duracao: Long = NORMAL
    ) {
        vistas.forEachIndexed { indice, vista ->
            if (vista.visibility != View.VISIBLE) return@forEachIndexed
            surgir(vista, atraso + indice * intervalo, deslocamentoY, duracao = duracao)
        }
    }

    /**
     * Entrada em cascata dos filhos visíveis de um contentor.
     * Ideal para ecrãs e cartões com vários blocos.
     */
    fun cascata(
        contentor: ViewGroup,
        atraso: Long = 60L,
        intervalo: Long = 55L,
        deslocamentoY: Float = 18f,
        duracao: Long = NORMAL
    ) {
        if (!ativas(contentor.context)) return
        contentor.post {
            if (!contentor.isAttachedToWindow) return@post
            var indice = 0
            for (i in 0 until contentor.childCount) {
                val filho = contentor.getChildAt(i)
                if (filho.visibility != View.VISIBLE || filho.height <= 0) continue
                surgir(
                    filho,
                    atraso + indice * intervalo,
                    deslocamentoY,
                    duracao = duracao
                )
                indice++
            }
        }
    }

    /** Entrada lateral usada na troca de passos do assistente. */
    fun deslizarPasso(vista: View, paraDireita: Boolean) {
        if (!ativas(vista.context)) return
        vista.animate().cancel()
        vista.alpha = 0f
        vista.translationX = dp(vista, if (paraDireita) 26f else -26f)
        vista.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(230L)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }

    /** Realça um elemento que acabou de aparecer ou mudou de valor. */
    fun pop(vista: View, escalaInicial: Float = 0.72f, atraso: Long = 0L) {
        if (!ativas(vista.context)) return
        vista.animate().cancel()
        vista.alpha = 0f
        vista.scaleX = escalaInicial
        vista.scaleY = escalaInicial
        vista.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(atraso)
            .setDuration(360L)
            .setInterpolator(OvershootInterpolator(2.1f))
            .start()
    }

    /* --------------------------- Micro-interações --------------------------- */

    /** Reduz ligeiramente o cartão/botão enquanto o dedo o pressiona. */
    fun pressionar(vararg vistas: View) {
        vistas.forEach { vista ->
            if (vista.getTag(R.id.tag_pressao) == true) return@forEach
            vista.setTag(R.id.tag_pressao, true)
            vista.setOnTouchListener { v, evento ->
                if (!ativas(v.context)) return@setOnTouchListener false
                when (evento.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate()
                        .scaleX(0.965f)
                        .scaleY(0.965f)
                        .setDuration(110L)
                        .setInterpolator(DecelerateInterpolator())
                        .start()

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(CURTA)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .start()
                }
                false
            }
        }
    }

    /** Abana um bloco para chamar a atenção para um erro. */
    fun tremer(vista: View) {
        if (!ativas(vista.context)) return
        vista.animate().cancel()
        val passo = dp(vista, 7f)
        val animador = ObjectAnimator.ofFloat(
            vista,
            View.TRANSLATION_X,
            0f, -passo, passo, -passo * 0.65f, passo * 0.65f, -passo * 0.3f, 0f
        )
        animador.duration = 330L
        animador.interpolator = DecelerateInterpolator()
        animador.start()
    }

    /* --------------------------- Animações contínuas --------------------------- */

    /** Respiração suave (ponto de ligação offline). */
    fun respirar(vista: View) {
        if (!ativas(vista.context)) return
        parar(vista)
        val animador = ObjectAnimator.ofFloat(vista, View.ALPHA, 1f, 0.4f)
        animador.duration = 1100L
        animador.repeatMode = ValueAnimator.REVERSE
        animador.repeatCount = ValueAnimator.INFINITE
        animador.interpolator = AccelerateDecelerateInterpolator()
        vista.setTag(R.id.tag_animacao, animador)
        animador.start()
    }

    /** Movimento lento de câmara (Ken Burns) sobre a fotografia do camião. */
    fun zoomLento(vista: View) {
        if (!ativas(vista.context)) return
        parar(vista)
        vista.doOnLayout {
            if (!it.isAttachedToWindow) return@doOnLayout
            it.pivotX = it.width / 2f
            it.pivotY = it.height / 2f
            val animador = ObjectAnimator.ofPropertyValuesHolder(
                it,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.09f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.09f)
            )
            animador.duration = 9000L
            animador.repeatMode = ValueAnimator.REVERSE
            animador.repeatCount = ValueAnimator.INFINITE
            animador.interpolator = LinearInterpolator()
            it.setTag(R.id.tag_animacao, animador)
            animador.start()
        }
    }

    /** Cancela animações contínuas associadas à vista. */
    fun parar(vista: View) {
        (vista.getTag(R.id.tag_animacao) as? Animator)?.cancel()
        vista.setTag(R.id.tag_animacao, null)
    }

    /* ------------------------------- Listas ------------------------------- */

    /**
     * Revela um item de lista apenas quando é mostrado pela primeira vez.
     * A chave evita repetir a animação em cada rebind do RecyclerView.
     */
    fun revelarItem(vista: View, chave: Any?, atraso: Long = 0L) {
        if (vista.getTag(R.id.tag_animado) == chave) return
        vista.setTag(R.id.tag_animado, chave)
        surgir(vista, atraso = atraso, deslocamentoY = 12f, duracao = 240L)
    }

    private fun dp(vista: View, valor: Float): Float =
        valor * vista.resources.displayMetrics.density
}
