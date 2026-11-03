package mz.co.logitrack.service.admin;

import mz.co.logitrack.domain.Utilizador;
import mz.co.logitrack.domain.enums.Role;
import mz.co.logitrack.dto.admin.ResetPasswordRequest;
import mz.co.logitrack.dto.admin.UtilizadorRequest;
import mz.co.logitrack.dto.auth.UtilizadorResponse;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.UtilizadorRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** CRUD de utilizadores e perfis de acesso (W8). */
@Service
public class UtilizadorService {

    private final UtilizadorRepository utilizadorRepository;
    private final CarregamentoRepository carregamentoRepository;
    private final PasswordEncoder passwordEncoder;

    public UtilizadorService(UtilizadorRepository utilizadorRepository,
                             CarregamentoRepository carregamentoRepository,
                             PasswordEncoder passwordEncoder) {
        this.utilizadorRepository = utilizadorRepository;
        this.carregamentoRepository = carregamentoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UtilizadorResponse> listar() {
        return utilizadorRepository.findAllByOrderByNomeAsc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<UtilizadorResponse> motoristas() {
        return utilizadorRepository.findByPerfilOrderByNomeAsc(Role.MOTORISTA).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public UtilizadorResponse criar(UtilizadorRequest pedido) {
        if (pedido.senha() == null || pedido.senha().length() < 6) {
            throw ApiException.unprocessable(
                    "A palavra-passe é obrigatória e deve ter pelo menos 6 caracteres");
        }
        String email = pedido.email().trim().toLowerCase();
        if (utilizadorRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.unprocessable("Já existe um utilizador com este e-mail");
        }
        Utilizador utilizador = new Utilizador();
        utilizador.setNome(pedido.nome().trim());
        utilizador.setEmail(email);
        utilizador.setSenhaHash(passwordEncoder.encode(pedido.senha()));
        utilizador.setPerfil(pedido.perfil());
        utilizador.setAtivo(pedido.ativo() == null ? Boolean.TRUE : pedido.ativo());
        utilizador.setDataCriacao(LocalDateTime.now());
        return toResponse(utilizadorRepository.save(utilizador));
    }

    @Transactional
    public UtilizadorResponse atualizar(Long id, UtilizadorRequest pedido) {
        Utilizador utilizador = entidade(id);
        String email = pedido.email().trim().toLowerCase();
        if (utilizadorRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw ApiException.unprocessable("Já existe um utilizador com este e-mail");
        }
        utilizador.setNome(pedido.nome().trim());
        utilizador.setEmail(email);
        utilizador.setPerfil(pedido.perfil());
        if (pedido.ativo() != null) {
            utilizador.setAtivo(pedido.ativo());
        }
        if (pedido.senha() != null && !pedido.senha().isBlank()) {
            if (pedido.senha().length() < 6) {
                throw ApiException.unprocessable(
                        "A palavra-passe deve ter pelo menos 6 caracteres");
            }
            utilizador.setSenhaHash(passwordEncoder.encode(pedido.senha()));
        }
        return toResponse(utilizadorRepository.save(utilizador));
    }

    @Transactional
    public UtilizadorResponse definirAtivo(Long id, boolean ativo, Long administradorId) {
        Utilizador utilizador = entidade(id);
        if (id.equals(administradorId) && !ativo) {
            throw ApiException.unprocessable("Não pode desativar a sua própria conta");
        }
        if (!ativo && utilizador.getPerfil() == Role.ADMINISTRADOR
                && utilizadorRepository.countByPerfilAndAtivoTrue(Role.ADMINISTRADOR) <= 1) {
            throw ApiException.unprocessable(
                    "Deve existir pelo menos um administrador ativo");
        }
        utilizador.setAtivo(ativo);
        return toResponse(utilizadorRepository.save(utilizador));
    }

    @Transactional
    public void redefinirSenha(Long id, ResetPasswordRequest pedido) {
        Utilizador utilizador = entidade(id);
        utilizador.setSenhaHash(passwordEncoder.encode(pedido.novaSenha()));
        utilizadorRepository.save(utilizador);
    }

    @Transactional
    public void eliminar(Long id, Long administradorId) {
        Utilizador utilizador = entidade(id);
        if (id.equals(administradorId)) {
            throw ApiException.unprocessable("Não pode eliminar a sua própria conta");
        }
        if (carregamentoRepository.countByMotoristaId(id) > 0) {
            throw ApiException.unprocessable(
                    "O utilizador tem carregamentos associados. Desative a conta.");
        }
        if (utilizador.getPerfil() == Role.ADMINISTRADOR
                && utilizadorRepository.countByPerfil(Role.ADMINISTRADOR) <= 1) {
            throw ApiException.unprocessable("Deve existir pelo menos um administrador");
        }
        utilizadorRepository.delete(utilizador);
    }

    public Utilizador entidade(Long id) {
        return utilizadorRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Utilizador não encontrado"));
    }

    private UtilizadorResponse toResponse(Utilizador u) {
        return new UtilizadorResponse(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
                u.getPerfil().getEtiqueta(), u.getAtivo(), u.getDataCriacao());
    }
}
