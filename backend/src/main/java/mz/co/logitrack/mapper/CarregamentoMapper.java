package mz.co.logitrack.mapper;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.DocumentoGuia;
import mz.co.logitrack.domain.Fabrica;
import mz.co.logitrack.domain.LocalizacaoGPS;
import mz.co.logitrack.domain.Utilizador;
import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.dto.carregamento.CarregamentoDetalhe;
import mz.co.logitrack.dto.carregamento.CarregamentoResumo;
import mz.co.logitrack.dto.carregamento.FasesCarregamento;
import mz.co.logitrack.dto.dashboard.PontoMapa;
import mz.co.logitrack.service.ParametroService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Conversão das entidades JPA para os DTOs da API (sem expor lazy loading). */
@Component
public class CarregamentoMapper {

    private final ParametroService parametroService;

    public CarregamentoMapper(ParametroService parametroService) {
        this.parametroService = parametroService;
    }

    public CarregamentoResumo toResumo(Carregamento c) {
        if (c == null) {
            return null;
        }
        long atraso = atrasoMinutos(c);
        Fabrica f = c.getFabrica();
        Veiculo v = c.getVeiculo();
        Utilizador m = c.getMotorista();
        return new CarregamentoResumo(
                c.getId(),
                c.getUuidLocal() != null ? c.getUuidLocal().toString() : null,
                c.getGuiaNumero(),
                c.getTipoCarga(),
                c.getQuantidadeToneladas() != null ? c.getQuantidadeToneladas() : 0,
                c.getEstado(),
                c.getEstado() != null ? c.getEstado().getEtiqueta() : null,
                c.getDataHoraInicio(),
                c.getDataHoraFim(),
                c.getMotivoRejeicao(),
                f != null ? f.getId() : null,
                f != null ? f.getNome() : null,
                v != null ? v.getId() : null,
                v != null ? v.getMatricula() : null,
                m != null ? m.getId() : null,
                m != null ? m.getNome() : null,
                atraso,
                c.getEstado() == EstadoCarregamento.ATRASADO || atraso > 0,
                c.getDocumento() != null,
                FasesCarregamento.de(c)
        );
    }

    public CarregamentoDetalhe toDetalhe(Carregamento c, List<LocalizacaoGPS> posicoes,
                                         int alertasAbertos) {
        List<CarregamentoDetalhe.PosicaoResumo> pontos = posicoes == null ? List.of()
                : posicoes.stream().map(this::toPosicao).toList();
        CarregamentoDetalhe.PosicaoResumo ultima = pontos.isEmpty()
                ? null : pontos.get(pontos.size() - 1);

        return new CarregamentoDetalhe(
                c.getId(),
                c.getUuidLocal() != null ? c.getUuidLocal().toString() : null,
                c.getGuiaNumero(),
                c.getTipoCarga(),
                c.getQuantidadeToneladas() != null ? c.getQuantidadeToneladas() : 0,
                c.getEstado(),
                c.getEstado() != null ? c.getEstado().getEtiqueta() : null,
                c.getDataHoraInicio(),
                c.getDataHoraFim(),
                c.getMotivoRejeicao(),
                atrasoMinutos(c),
                horaLimite(c),
                toFabrica(c.getFabrica()),
                toVeiculo(c.getVeiculo()),
                toMotorista(c.getMotorista()),
                toDocumento(c.getDocumento()),
                pontos,
                ultima,
                timeline(c),
                alertasAbertos,
                FasesCarregamento.de(c)
        );
    }

    public CarregamentoDetalhe.FabricaResumo toFabrica(Fabrica f) {
        if (f == null) {
            return null;
        }
        return new CarregamentoDetalhe.FabricaResumo(
                f.getId(), f.getNome(), f.getEndereco(), f.getLatitude(), f.getLongitude(),
                f.getRaioGeofenceMetros(), f.getSlaLimiteHoras(), f.getHorarioAcostagem(),
                f.getAtiva());
    }

    public CarregamentoDetalhe.VeiculoResumo toVeiculo(Veiculo v) {
        if (v == null) {
            return null;
        }
        return new CarregamentoDetalhe.VeiculoResumo(
                v.getId(), v.getMatricula(), v.getModelo(), v.getCapacidadeCargaToneladas(),
                v.getEstado(), v.getEstado() != null ? v.getEstado().getEtiqueta() : null);
    }

    public CarregamentoDetalhe.MotoristaResumo toMotorista(Utilizador u) {
        if (u == null) {
            return null;
        }
        return new CarregamentoDetalhe.MotoristaResumo(u.getId(), u.getNome(), u.getEmail());
    }

    public CarregamentoDetalhe.DocumentoResumo toDocumento(DocumentoGuia d) {
        if (d == null) {
            return null;
        }
        return new CarregamentoDetalhe.DocumentoResumo(
                d.getId(), d.getCaminhoFotografia(), d.getDataHoraCaptura(),
                d.getTamanhoBytes(), d.getMimeType(), d.getHashSha256());
    }

    public CarregamentoDetalhe.PosicaoResumo toPosicao(LocalizacaoGPS g) {
        return new CarregamentoDetalhe.PosicaoResumo(
                g.getLatitude(), g.getLongitude(), g.getPrecisaoMetros(), g.getTimestamp(),
                g.getRecebidoEm(), g.getUuidPonto());
    }

    public PontoMapa toPontoMapa(LocalizacaoGPS g) {
        Carregamento c = g.getCarregamento();
        return new PontoMapa(
                c.getId(),
                c.getGuiaNumero(),
                g.getVeiculo().getId(),
                g.getVeiculo().getMatricula(),
                c.getMotorista() != null ? c.getMotorista().getNome() : null,
                c.getFabrica() != null ? c.getFabrica().getNome() : null,
                c.getEstado() != null ? c.getEstado().getEtiqueta() : null,
                g.getLatitude(),
                g.getLongitude(),
                g.getPrecisaoMetros(),
                g.getTimestamp(),
                g.getRecebidoEm()
        );
    }

    public LocalDateTime horaLimite(Carregamento c) {
        if (c == null || c.getDataHoraInicio() == null || c.getFabrica() == null
                || c.getFabrica().getSlaLimiteHoras() == null) {
            return null;
        }
        int tolerancia = parametroService.obterInt(ParametroService.TOLERANCIA_ATRASO_MINUTOS);
        return c.getDataHoraInicio()
                .plusHours(c.getFabrica().getSlaLimiteHoras())
                .plusMinutes(tolerancia);
    }

    public long atrasoMinutos(Carregamento c) {
        LocalDateTime limite = horaLimite(c);
        if (limite == null) {
            return 0;
        }
        LocalDateTime referencia = c.getDataHoraFim() != null
                ? c.getDataHoraFim() : LocalDateTime.now();
        if (!referencia.isAfter(limite)) {
            return 0;
        }
        return Math.max(0, Duration.between(limite, referencia).toMinutes());
    }

    private List<CarregamentoDetalhe.EtapaTimeline> timeline(Carregamento c) {
        List<CarregamentoDetalhe.EtapaTimeline> etapas = new ArrayList<>();
        EstadoCarregamento estado = c.getEstado();
        boolean rejeitado = estado == EstadoCarregamento.REJEITADO;
        boolean emTransito = estado == EstadoCarregamento.EM_TRANSITO
                || estado == EstadoCarregamento.ATRASADO
                || estado == EstadoCarregamento.CONCLUIDO;
        boolean concluido = estado == EstadoCarregamento.CONCLUIDO;

        etapas.add(new CarregamentoDetalhe.EtapaTimeline(
                "guardado", "Guardado no telemóvel. Aguarda envio.", true,
                c.getDataHoraInicio(), "save"));
        etapas.add(new CarregamentoDetalhe.EtapaTimeline(
                "sincronizado", "Enviado à central", true,
                c.getDataHoraInicio(), "cloud"));
        boolean carregamentoIniciado = c.getDataHoraInicioCarregamento() != null;
        etapas.add(new CarregamentoDetalhe.EtapaTimeline(
                "espera", "Espera na fábrica", carregamentoIniciado && !rejeitado,
                c.getDataHoraInicioCarregamento(), "clock"));
        etapas.add(new CarregamentoDetalhe.EtapaTimeline(
                "carregamento", "Carregamento", c.getDataHoraFimCarregamento() != null,
                c.getDataHoraFimCarregamento(), "package"));
        etapas.add(new CarregamentoDetalhe.EtapaTimeline(
                "transito", "Em trânsito",
                emTransito && c.getDataHoraInicioTransporte() != null && !rejeitado,
                c.getDataHoraInicioTransporte(), "truck"));
        etapas.add(new CarregamentoDetalhe.EtapaTimeline(
                "concluido", "Concluído", concluido,
                concluido ? c.getDataHoraFim() : null, "check"));
        if (rejeitado) {
            etapas.add(new CarregamentoDetalhe.EtapaTimeline(
                    "rejeitado", "Não aceite", true,
                    c.getDataHoraFim(), "x"));
        }
        return etapas;
    }
}
