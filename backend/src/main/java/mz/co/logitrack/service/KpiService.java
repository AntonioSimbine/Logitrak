package mz.co.logitrack.service;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.dto.dashboard.KpiResponse;
import mz.co.logitrack.dto.dashboard.KpiResumo;
import mz.co.logitrack.mapper.CarregamentoMapper;
import mz.co.logitrack.repository.CarregamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Cálculo dos KPIs gerenciais (W2 e W6). */
@Service
public class KpiService {

    private static final Locale PT = Locale.forLanguageTag("pt-PT");

    private final CarregamentoRepository carregamentoRepository;
    private final CarregamentoMapper mapper;
    private final ParametroService parametroService;

    public KpiService(CarregamentoRepository carregamentoRepository,
                      CarregamentoMapper mapper,
                      ParametroService parametroService) {
        this.carregamentoRepository = carregamentoRepository;
        this.mapper = mapper;
        this.parametroService = parametroService;
    }

    @Transactional(readOnly = true)
    public KpiResumo resumo() {
        LocalDateTime inicioHoje = LocalDate.now().atStartOfDay();
        LocalDateTime fimHoje = inicioHoje.plusDays(1).minusNanos(1);
        List<Carregamento> concluidos = concluidos();

        return new KpiResumo(
                carregamentoRepository.countByEstado(EstadoCarregamento.EM_TRANSITO),
                carregamentoRepository.countByEstado(EstadoCarregamento.ATRASADO),
                carregamentoRepository.countByEstadoAndDataHoraFimBetween(
                        EstadoCarregamento.CONCLUIDO, inicioHoje, fimHoje),
                tempoMedioHoras(concluidos)
        );
    }

    @Transactional(readOnly = true)
    public KpiResponse kpis() {
        KpiResumo resumo = resumo();
        List<Carregamento> concluidos = concluidos();
        int tolerancia = parametroService.obterInt(ParametroService.TOLERANCIA_ATRASO_MINUTOS);

        List<KpiResponse.VolumeFabrica> volumes = carregamentoRepository.volumePorFabrica()
                .stream()
                .map(linha -> new KpiResponse.VolumeFabrica(
                        (String) linha[0],
                        ((Number) linha[1]).doubleValue()))
                .toList();

        double totalToneladas = volumes.stream()
                .mapToDouble(KpiResponse.VolumeFabrica::toneladas).sum();

        Map<DayOfWeek, List<Double>> porDia = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dia : DayOfWeek.values()) {
            porDia.put(dia, new ArrayList<>());
        }
        for (Carregamento c : concluidos) {
            if (c.getDataHoraFim() == null) {
                continue;
            }
            double horas = Duration.between(c.getDataHoraInicio(), c.getDataHoraFim())
                    .toMinutes() / 60.0;
            porDia.get(c.getDataHoraFim().getDayOfWeek()).add(horas);
        }
        List<KpiResponse.SerieDia> serie = new ArrayList<>();
        for (DayOfWeek dia : DayOfWeek.values()) {
            List<Double> valores = porDia.get(dia);
            double media = valores.isEmpty() ? 0
                    : valores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            serie.add(new KpiResponse.SerieDia(
                    capitalizar(dia.getDisplayName(TextStyle.SHORT, PT)), arredondar(media)));
        }

        long noPrazo = 0;
        long atrasados = 0;
        for (Carregamento c : concluidos) {
            LocalDateTime limite = limiteSla(c, tolerancia);
            if (limite == null || c.getDataHoraFim() == null
                    || !c.getDataHoraFim().isAfter(limite)) {
                noPrazo++;
            } else {
                atrasados++;
            }
        }
        long total = noPrazo + atrasados;
        double percentagem = total == 0 ? 100.0
                : arredondar(noPrazo * 100.0 / total);

        return new KpiResponse(
                resumo,
                volumes,
                serie,
                new KpiResponse.CumprimentoPrazos(noPrazo, atrasados, percentagem),
                carregamentoRepository.count(),
                arredondar(totalToneladas),
                tempoMedioHoras(concluidos)
        );
    }

    private List<Carregamento> concluidos() {
        return carregamentoRepository
                .findByEstadoAndDataHoraFimIsNotNullOrderByDataHoraFimDesc(
                        EstadoCarregamento.CONCLUIDO);
    }

    private double tempoMedioHoras(List<Carregamento> concluidos) {
        return arredondar(concluidos.stream()
                .filter(c -> c.getDataHoraInicio() != null && c.getDataHoraFim() != null)
                .mapToLong(c -> Duration.between(
                        c.getDataHoraInicio(), c.getDataHoraFim()).toMinutes())
                .average().orElse(0) / 60.0);
    }

    private LocalDateTime limiteSla(Carregamento c, int tolerancia) {
        if (c.getDataHoraInicio() == null || c.getFabrica() == null
                || c.getFabrica().getSlaLimiteHoras() == null) {
            return null;
        }
        return c.getDataHoraInicio()
                .plusHours(c.getFabrica().getSlaLimiteHoras())
                .plusMinutes(tolerancia);
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        String limpo = texto.replace(".", "");
        return limpo.substring(0, 1).toUpperCase(PT) + limpo.substring(1);
    }

    private double arredondar(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
