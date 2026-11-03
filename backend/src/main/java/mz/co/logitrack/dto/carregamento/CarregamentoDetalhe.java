package mz.co.logitrack.dto.carregamento;

import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.domain.enums.EstadoVeiculo;

import java.time.LocalDateTime;
import java.util.List;

/** Detalhe completo de um carregamento (M6, W3 e W4). */
public record CarregamentoDetalhe(
        Long id,
        String uuidLocal,
        String guiaNumero,
        String tipoCarga,
        double quantidadeToneladas,
        EstadoCarregamento estado,
        String estadoEtiqueta,
        LocalDateTime dataHoraInicio,
        LocalDateTime dataHoraFim,
        String motivoRejeicao,
        long atrasoMinutos,
        LocalDateTime horaLimite,
        FabricaResumo fabrica,
        VeiculoResumo veiculo,
        MotoristaResumo motorista,
        DocumentoResumo documento,
        List<PosicaoResumo> posicoes,
        PosicaoResumo ultimaPosicao,
        List<EtapaTimeline> timeline,
        int alertasAbertos
) {
    public record FabricaResumo(Long id, String nome, String endereco, Double latitude,
                                Double longitude, Integer raioGeofenceMetros,
                                Integer slaLimiteHoras, String horarioAcostagem, Boolean ativa) {
    }

    public record VeiculoResumo(Long id, String matricula, String modelo,
                                Double capacidadeCargaToneladas, EstadoVeiculo estado,
                                String estadoEtiqueta) {
    }

    public record MotoristaResumo(Long id, String nome, String email) {
    }

    public record DocumentoResumo(Long id, String caminhoFotografia, LocalDateTime dataHoraCaptura,
                                  Long tamanhoBytes, String mimeType, String hashSha256) {
    }

    public record PosicaoResumo(Double latitude, Double longitude, Double precisaoMetros,
                                LocalDateTime timestamp) {
    }

    public record EtapaTimeline(String codigo, String titulo, boolean concluida,
                                LocalDateTime dataHora, String icone) {
    }
}
