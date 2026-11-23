package mz.co.logitrack.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import mz.co.logitrack.motorista.BuildConfig
import mz.co.logitrack.motorista.R
import mz.co.logitrack.motorista.databinding.FragmentPerfilBinding
import mz.co.logitrack.motorista.util.Animacoes
import mz.co.logitrack.motorista.util.Formato
import mz.co.logitrack.motorista.util.Prefs

class PerfilFragment : Fragment(R.layout.fragment_perfil) {
    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Prefs

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPerfilBinding.bind(view)
        prefs = Prefs(requireContext())

        val nome = prefs.userName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.driver_fallback)
        val email = prefs.userEmail?.takeIf { it.isNotBlank() }
            ?: prefs.emailRecordado?.takeIf { it.isNotBlank() }
            ?: getString(R.string.profile_no_email)

        binding.tvNomePerfil.text = nome
        binding.tvEmailPerfil.text = email
        binding.tvContaEmail.text = email
        binding.tvAvatarPerfil.text = Formato.iniciais(nome)
        binding.tvVersao.text = getString(R.string.profile_version, BuildConfig.VERSION_NAME)

        binding.cardSync.setOnClickListener {
            startActivity(Intent(requireContext(), SyncActivity::class.java))
        }
        binding.cardHistorico.setOnClickListener {
            findNavController().navigate(R.id.queueFragment)
        }
        binding.cardAtualizacao.setOnClickListener {
            startActivity(
                Intent(requireContext(), AtualizacaoActivity::class.java)
                    .putExtra(AtualizacaoActivity.EXTRA_MANUAL, true)
            )
        }
        binding.btnSair.setOnClickListener { confirmarSaida() }
        prepararAnimacoes()
    }

    private fun prepararAnimacoes() {
        val raiz = requireView() as ViewGroup
        (raiz.getChildAt(0) as? View)?.let {
            Animacoes.surgir(it, atraso = 40L, deslocamentoY = 14f)
        }
        Animacoes.cascata(binding.perfilContent, atraso = 170L, intervalo = 55L)
        Animacoes.pressionar(
            binding.cardSync,
            binding.cardHistorico,
            binding.cardAtualizacao,
            binding.btnSair
        )
    }

    private fun confirmarSaida() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.logout) { _, _ -> terminarSessao() }
            .show()
    }

    private fun terminarSessao() {
        prefs.limparSessao()
        Toast.makeText(requireContext(), R.string.logout_done, Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(requireContext(), LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
