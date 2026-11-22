package mz.co.logitrack.service;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.VeiculoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Regras de ocupação da frota.
 *
 * <p>Decisão de negócio: não existe regra explícita nos documentos do projecto
 * sobre a associação veículo/carregamento. Adopta-se, por coerência com o
 * controlo de capacidade, que um veículo não pode ter duas operações activas
 * em simultâneo. Registos antigos com sobreposição são preservados e
 * assinalados nos logs.</p>
 */
@Service
public class VehicleOperationService {

    public static final List<EstadoCarregamento> ESTADOS_ATIVOS = List.of(
            EstadoCarregamento.REGISTADO,
            EstadoCarregamento.EM_TRANSITO,
            EstadoCarregamento.ATRASADO);

    private static final Logger log = LoggerFactory.getLogger(VehicleOperationService.class);

    private final CarregamentoRepository carregamentoRepository;
    private final VeiculoRepository veiculoRepository;

    public VehicleOperationService(CarregamentoRepository carregamentoRepository,
                                   VeiculoRepository veiculoRepository) {
        this.carregamentoRepository = carregamentoRepository;
        this.veiculoRepository = veiculoRepository;
    }

    /** Valida quantidade, capacidade acumulada e exclusividade da operação. */
    @Transactional(readOnly = true)
    public void validarNovaOperacao(Veiculo veiculo, double quantidadeToneladas) {
        validarNovaOperacao(veiculo, quantidadeToneladas, null);
    }

    @Transactional(readOnly = true)
    public void validarNovaOperacao(Veiculo veiculo, double quantidadeToneladas,
                                    Long carregamentoAtual) {
        validarQuantidade(quantidadeToneladas);

        Double capacidade = veiculo.getCapacidadeCargaToneladas();
        if (capacidade == null || Double.isNaN(capacidade) || Double.isInfinite(capacidade)
                || capacidade <= 0) {
            log.warn("Veículo {} com capacidade inválida: {}", veiculo.getMatricula(), capacidade);
            throw ApiException.unprocessable(
                    "O veículo selecionado não tem capacidade configurada. "
                            + "Peça à central para corrigir os dados do veículo.");
        }
        if (quantidadeToneladas > capacidade) {
            log.warn("Quantidade {} t excede a capacidade {} t do veículo {}",
                    quantidadeToneladas, capacidade, veiculo.getMatricula());
            throw ApiException.unprocessable(String.format(
                    "A quantidade indicada (%.2f t) excede a capacidade do veículo %s (%.2f t). "
                            + "Reduza a quantidade ou escolha outro veículo.",
                    quantidadeToneladas, veiculo.getMatricula(), capacidade));
        }

        List<Carregamento> ativos = operacoesAtivas(veiculo.getId(), carregamentoAtual);
        if (!ativos.isEmpty()) {
            String guia = ativos.get(0).getGuiaNumero();
            throw ApiException.unprocessable(
                    "O veículo " + veiculo.getMatricula()
                            + " já tem uma operação activa (guia " + guia
                            + "). Conclua-a antes de iniciar outra.");
        }
        double acumulado = carregamentoRepository.somarToneladasAtivas(
                veiculo.getId(), ESTADOS_ATIVOS, carregamentoAtual);
        if (acumulado + quantidadeToneladas > capacidade + 1e-9) {
            log.warn("Cargas activas {} t excedem a capacidade {} t do veículo {}",
                    acumulado + quantidadeToneladas, capacidade, veiculo.getMatricula());
            throw ApiException.unprocessable(String.format(
                    "A soma das cargas em curso (%.2f t) excede a capacidade do veículo %s (%.2f t). "
                            + "Reduza a quantidade ou escolha outro veículo.",
                    acumulado + quantidadeToneladas, veiculo.getMatricula(), capacidade));
        }
    }

    /** Reavalia a disponibilidade após concluir/rejeitar uma operação. */
    @Transactional
    public void libertarVeiculo(Veiculo veiculo, Long carregamentoAtual) {
        if (veiculo == null) {
            return;
        }
        if (veiculo.getEstado() == EstadoVeiculo.MANUTENCAO) {
            log.info("Veículo {} mantido em manutenção após terminar carregamento {}",
                    veiculo.getMatricula(), carregamentoAtual);
            return;
        }
        List<Carregamento> restantes = operacoesAtivas(veiculo.getId(), carregamentoAtual);
        EstadoVeiculo destino = restantes.isEmpty()
                ? EstadoVeiculo.DISPONIVEL : EstadoVeiculo.EM_TRANSITO;
        if (veiculo.getEstado() != destino) {
            veiculo.setEstado(destino);
            veiculoRepository.save(veiculo);
        }
        if (!restantes.isEmpty()) {
            log.warn("Veículo {} continua ocupado por {} operação(ões) activa(s) "
                            + "após terminar carregamento {}",
                    veiculo.getMatricula(), restantes.size(), carregamentoAtual);
        }
    }

    @Transactional(readOnly = true)
    public List<Carregamento> operacoesAtivas(Long veiculoId, Long excluirId) {
        List<Carregamento> ativos = carregamentoRepository
                .findByVeiculoIdAndEstadoIn(veiculoId, ESTADOS_ATIVOS);
        if (excluirId == null) {
            return ativos;
        }
        return ativos.stream().filter(c -> !excluirId.equals(c.getId())).toList();
    }

    private void validarQuantidade(double quantidade) {
        if (Double.isNaN(quantidade) || Double.isInfinite(quantidade) || quantidade <= 0) {
            throw ApiException.unprocessable(
                    "A quantidade deve ser superior a zero. Corrija o valor e tente novamente.");
        }
    }

    /** Assinala sobreposições herdadas sem alterar os registos antigos. */
    @EventListener(ApplicationReadyEvent.class)
    public void sinalizarInconsistenciasHistoricas() {
        for (Veiculo veiculo : veiculoRepository.findAll()) {
            List<Carregamento> ativos = operacoesAtivas(veiculo.getId(), null);
            if (ativos.size() > 1) {
                log.warn("INCONSISTÊNCIA HISTÓRICA: veículo {} (id={}) tem {} operações activas: {}",
                        veiculo.getMatricula(), veiculo.getId(), ativos.size(),
                        ativos.stream().map(Carregamento::getGuiaNumero).toList());
            }
        }
    }
}
