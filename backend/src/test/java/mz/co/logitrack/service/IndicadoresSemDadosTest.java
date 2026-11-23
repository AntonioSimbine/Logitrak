package mz.co.logitrack.service;

import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.mapper.CarregamentoMapper;
import mz.co.logitrack.repository.CarregamentoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Em central sem amostras, nenhuma média é inventada como zero. */
@ExtendWith(MockitoExtension.class)
class IndicadoresSemDadosTest {

    @Mock
    private CarregamentoRepository carregamentoRepository;

    @Mock
    private CarregamentoMapper mapper;

    @Mock
    private ParametroService parametroService;

    @InjectMocks
    private KpiService kpiService;

    @Test
    void mediasSemAmostrasSaoNulas() {
        when(carregamentoRepository.findByEstadoAndDataHoraFimIsNotNullOrderByDataHoraFimDesc(
                any(EstadoCarregamento.class))).thenReturn(List.of());

        var resumo = kpiService.resumo();
        assertThat(resumo.tempoMedioEsperaHoras()).isNull();
        assertThat(resumo.tempoMedioCarregamentoHoras()).isNull();
        assertThat(resumo.tempoMedioTransporteHoras()).isNull();
        assertThat(resumo.registosComFases()).isZero();

        var kpis = kpiService.kpis();
        assertThat(kpis.tempoMedioHoras()).isNull();
        assertThat(kpis.cumprimentoPrazos().percentagem()).isNull();
        assertThat(kpis.tempoMedioPorDia()).hasSize(7)
                .allSatisfy(dia -> assertThat(dia.horas()).isNull());
    }
}
