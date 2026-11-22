package mz.co.logitrack.motorista.util

import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Degradação segura do armazenamento da sessão: se o Keystore não estiver
 * disponível, os dados ficam apenas na memória do processo e nunca são
 * gravados em texto claro no dispositivo. Ao reiniciar, o motorista volta a
 * autenticar-se.
 */
class MemoriaSharedPreferences : SharedPreferences {
    private val dados = ConcurrentHashMap<String, Any>()

    override fun getAll(): MutableMap<String, *> = HashMap(dados)

    override fun getString(key: String?, defValue: String?): String? =
        (dados[key] as? String) ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        val valor = dados[key] as? Set<String> ?: return defValues
        return valor.toMutableSet()
    }

    override fun getInt(key: String?, defValue: Int): Int = (dados[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = (dados[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = (dados[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (dados[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = dados.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val alteracoes = LinkedHashMap<String, Any?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = aplicar(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor =
            aplicar(key, values?.toSet())

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = aplicar(key, value)
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = aplicar(key, value)
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = aplicar(key, value)
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = aplicar(key, value)

        override fun remove(key: String?): SharedPreferences.Editor = aplicar(key, REMOVIDO)

        override fun clear(): SharedPreferences.Editor {
            alteracoes.clear()
            dados.clear()
            return this
        }

        override fun commit(): Boolean {
            aplicarAlteracoes()
            return true
        }

        override fun apply() = aplicarAlteracoes()

        private fun aplicar(key: String?, value: Any?): SharedPreferences.Editor {
            if (key != null) alteracoes[key] = value
            return this
        }

        private fun aplicarAlteracoes() {
            alteracoes.forEach { (chave, valor) ->
                if (valor === REMOVIDO) {
                    dados.remove(chave)
                } else if (valor != null) {
                    dados[chave] = valor
                } else {
                    dados.remove(chave)
                }
            }
            alteracoes.clear()
        }
    }

    companion object {
        private val REMOVIDO = Any()

        /** Instância partilhada pelo processo quando o Keystore falha. */
        val INSTANCIA: MemoriaSharedPreferences by lazy { MemoriaSharedPreferences() }
    }
}
