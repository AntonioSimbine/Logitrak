package mz.co.logitrack.service;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.dto.carregamento.FasesCarregamento;
import mz.co.logitrack.dto.relatorio.RelatorioFiltro;
import mz.co.logitrack.dto.relatorio.RelatorioLinha;
import mz.co.logitrack.dto.relatorio.RelatorioResponse;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.mapper.CarregamentoMapper;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.service.export.ExportadorRelatorio;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Relatórios operacionais com filtros, pré-visualização e exportação (W5). */
@Service
public class RelatorioService {

    private final CarregamentoRepository carregamentoRepository;
    private final CarregamentoMapper mapper;
    private final Map<String, ExportadorRelatorio> exportadores = new LinkedHashMap<>();

    public RelatorioService(CarregamentoRepository carregamentoRepository,
                            CarregamentoMapper mapper,
                            List<ExportadorRelatorio> exportadores) {
        this.carregamentoRepository = carregamentoRepository;
        this.mapper = mapper;
        for (ExportadorRelatorio exportador : exportadores) {
            this.exportadores.put(exportador.formato().toLowerCase(Locale.ROOT), exportador);
        }
    }

    @Transactional(readOnly = true)
    public RelatorioResponse gerar(RelatorioFiltro filtro) {
        LocalDateTime inicio = filtro.de() != null ? filtro.de().atStartOfDay() : null;
        LocalDateTime fim = filtro.ate() != null
                ? filtro.ate().plusDays(1).atStartOfDay().minusNanos(1) : null;

        List<Carregamento> registos = carregamentoRepository
                .findByFiltros(filtro.estado(), filtro.fabricaId(), filtro.veiculoId(),
                        filtro.motoristaId(), inicio, fim, null, Pageable.unpaged())
                .getContent().stream()
                .sorted(Comparator.comparing(Carregamento::getDataHoraInicio,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<RelatorioLinha> linhas = registos.stream()
                .map(c -> new RelatorioLinha(
                        c.getId(),
                        c.getGuiaNumero(),
                        c.getFabrica() != null ? c.getFabrica().getNome() : null,
                        c.getVeiculo() != null ? c.getVeiculo().getMatricula() : null,
                        c.getMotorista() != null ? c.getMotorista().getNome() : null,
                        c.getTipoCarga(),
                        c.getQuantidadeToneladas() != null ? c.getQuantidadeToneladas() : 0,
                        c.getEstado() != null ? c.getEstado().getEtiqueta() : null,
                        c.getDataHoraInicio(),
                        c.getDataHoraFim(),
                        mapper.atrasoMinutos(c),
                        c.getMotivoRejeicao(),
                        FasesCarregamento.de(c)))
                .toList();

        double toneladas = linhas.stream().mapToDouble(RelatorioLinha::toneladas).sum();
        long concluidos = registos.stream()
                .filter(c -> c.getEstado() == EstadoCarregamento.CONCLUIDO).count();
        long atrasados = linhas.stream().filter(l -> l.atrasoMinutos() > 0).count();
        double tempoMedio = registos.stream()
                .filter(c -> c.getEstado() == EstadoCarregamento.CONCLUIDO
                        && c.getDataHoraInicio() != null && c.getDataHoraFim() != null)
                .mapToLong(c -> Duration.between(c.getDataHoraInicio(), c.getDataHoraFim())
                        .toMinutes())
                .average().orElse(0) / 60.0;

        RelatorioResponse.ResumoRelatorio resumo =
                new RelatorioResponse.ResumoRelatorio(linhas.size(),
                        Math.round(toneladas * 10.0) / 10.0,
                        concluidos, atrasados,
                        Math.round(tempoMedio * 10.0) / 10.0,
                        mediaFaseHoras(linhas, 0),
                        mediaFaseHoras(linhas, 1),
                        mediaFaseHoras(linhas, 2),
                        linhas.stream().filter(l -> l.fases() != null
                                && (l.fases().inicioCarregamento() != null
                                || l.fases().fimCarregamento() != null
                                || l.fases().inicioTransporte() != null)).count());
        return new RelatorioResponse(linhas, resumo, filtro);
    }

    @Transactional(readOnly = true)
    public Exportacao exportar(RelatorioFiltro filtro, String formato) {
        String chave = formato == null ? "pdf" : formato.trim().toLowerCase(Locale.ROOT);
        ExportadorRelatorio exportador = exportadores.get(chave);
        if (exportador == null) {
            throw ApiException.badRequest(
                    "Não foi possível gerar o relatório nesse formato. Escolha PDF ou Excel.");
        }
        RelatorioResponse dados = gerar(filtro);
        return new Exportacao(exportador.gerar(dados),
                exportador.nomeFicheiro(filtro), exportador.contentType());
    }

    /** 0 = espera, 1 = carregamento, 2 = transporte. */
    private double mediaFaseHoras(List<RelatorioLinha> linhas, int fase) {
        return Math.round(linhas.stream()
                .map(RelatorioLinha::fases)
                .filter(Objects::nonNull)
                .map(f -> switch (fase) {
                    case 0 -> f.esperaMinutos();
                    case 1 -> f.carregamentoMinutos();
                    default -> f.transporteMinutos();
                })
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average().orElse(0) / 60.0 * 10.0) / 10.0;
    }

    public record Exportacao(byte[] conteudo, String nomeFicheiro, String contentType) {
    }
}
