package mz.co.logitrack.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.ParametroGlobal;
import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import mz.co.logitrack.dto.carregamento.FasesCarregamento;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.repository.AlertaAtrasoRepository;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.FabricaRepository;
import mz.co.logitrack.repository.LocalizacaoGPSRepository;
import mz.co.logitrack.repository.ParametroGlobalRepository;
import mz.co.logitrack.repository.UtilizadorRepository;
import mz.co.logitrack.repository.VeiculoRepository;
import mz.co.logitrack.service.AlertaService;
import mz.co.logitrack.service.ParametroService;
import mz.co.logitrack.service.ValidacaoGps;
import mz.co.logitrack.service.VehicleOperationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testes das regras críticas corrigidas: GPS, capacidade, veículo, alertas e RBAC. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegrasCriticasTest {

    private static final String SENHA = "test-demo-password-only-for-tests";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ParametroService parametroService;
    @Autowired private ParametroGlobalRepository parametroRepository;
    @Autowired private VehicleOperationService vehicleOperations;
    @Autowired private VeiculoRepository veiculoRepository;
    @Autowired private CarregamentoRepository carregamentoRepository;
    @Autowired private FabricaRepository fabricaRepository;
    @Autowired private LocalizacaoGPSRepository gpsRepository;
    @Autowired private UtilizadorRepository utilizadorRepository;
    @Autowired private AlertaService alertaService;
    @Autowired private AlertaAtrasoRepository alertaRepository;

    @Test
    @DisplayName("O parâmetro GPS tem teto fixo de 15 m e configurações antigas são normalizadas")
    void tetoGpsFixedo() {
        String chave = ParametroService.PRECISAO_MAXIMA_GPS_METROS;
        assertThat(parametroService.obterDouble(chave)).isEqualTo(50.0);

        assertThatThrownBy(() -> parametroService.atualizar(chave, "60"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("A precisão indicada não é válida.");

        ParametroGlobal parametro = parametroRepository.findByChaveIgnoreCase(chave).orElseThrow();
        String valorOriginal = parametro.getValor();
        try {
            parametro.setValor("60");
            parametroRepository.saveAndFlush(parametro);
            assertThat(parametroService.obterDouble(chave)).isEqualTo(50.0);
        } finally {
            parametro.setValor(valorOriginal);
            parametroRepository.saveAndFlush(parametro);
        }
    }

    @Test
    @DisplayName("Valores limite e inválidos de precisão GPS")
    void validacaoValoresLimiteGps() {
        assertThatCode(() -> ValidacaoGps.validar(0.0, 50.0)).doesNotThrowAnyException();
        assertThatCode(() -> ValidacaoGps.validar(50.0, 50.0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ValidacaoGps.validar(50.01, 50.0))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("A localização ainda não é precisa")
                .hasMessageContaining("máximo 15m");
        assertThatThrownBy(() -> ValidacaoGps.validar(Double.NaN, 50.0))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ValidacaoGps.validar(Double.POSITIVE_INFINITY, 50.0))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ValidacaoGps.validar(-1.0, 50.0))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Capacidade: negativo, zero, inferior, igual, superior e números inválidos")
    void validacaoCapacidade() {
        Veiculo veiculo = criarVeiculo(10.0);

        assertThatThrownBy(() -> vehicleOperations.validarNovaOperacao(veiculo, -1.0))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> vehicleOperations.validarNovaOperacao(veiculo, 0.0))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> vehicleOperations.validarNovaOperacao(veiculo, 9.0))
                .doesNotThrowAnyException();
        assertThatCode(() -> vehicleOperations.validarNovaOperacao(veiculo, 10.0))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> vehicleOperations.validarNovaOperacao(veiculo, 10.01))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("excede a capacidade");
        assertThatThrownBy(() -> vehicleOperations.validarNovaOperacao(veiculo, Double.NaN))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> vehicleOperations.validarNovaOperacao(
                veiculo, Double.POSITIVE_INFINITY))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Um veículo não aceita duas operações activas em simultâneo")
    void exclusividadeVeiculo() {
        Veiculo veiculo = criarVeiculo(100.0);
        criarCarregamentoAtivo(veiculo, 30.0);

        assertThatThrownBy(() -> vehicleOperations.validarNovaOperacao(veiculo, 10.0))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("já tem uma operação activa");
    }

    @Test
    @DisplayName("A soma de toneladas activas é calculada para o veículo")
    void somaToneladasAtivas() {
        Veiculo veiculo = criarVeiculo(100.0);
        criarCarregamentoAtivo(veiculo, 30.0);
        assertThat(carregamentoRepository.somarToneladasAtivas(
                veiculo.getId(), VehicleOperationService.ESTADOS_ATIVOS, null)).isEqualTo(30.0);
    }

    @Test
    @DisplayName("Libertar veículo preserva manutenção e outras operações activas")
    void libertacaoCorreta() {
        Veiculo ocupado = veiculoRepository.findById(1L).orElseThrow();
        assertThat(vehicleOperations.operacoesAtivas(ocupado.getId(), null)).hasSizeGreaterThan(1);
        vehicleOperations.libertarVeiculo(ocupado, null);
        assertThat(veiculoRepository.findById(1L).orElseThrow().getEstado())
                .isEqualTo(EstadoVeiculo.EM_TRANSITO);

        Veiculo livre = criarVeiculo(50.0);
        Carregamento unico = criarCarregamentoAtivo(livre, 20.0);
        livre.setEstado(EstadoVeiculo.EM_TRANSITO);
        veiculoRepository.saveAndFlush(livre);
        vehicleOperations.libertarVeiculo(livre, unico.getId());
        assertThat(veiculoRepository.findById(livre.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVeiculo.DISPONIVEL);

        livre.setEstado(EstadoVeiculo.MANUTENCAO);
        veiculoRepository.saveAndFlush(livre);
        vehicleOperations.libertarVeiculo(livre, null);
        assertThat(veiculoRepository.findById(livre.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVeiculo.MANUTENCAO);
    }

    @Test
    @DisplayName("Verificações repetidas não criam alertas duplicados, mesmo após leitura")
    void alertasSemDuplicacao() {
        Carregamento emAtraso = carregamentoRepository.findByUuidLocal(UUID.fromString(
                "77777777-7777-7777-7777-777777777777")).orElseThrow();
        var existentes = alertaRepository.findByCarregamentoIdOrderByDataHoraEmissaoDesc(
                emAtraso.getId());
        assertThat(existentes).hasSize(1);

        assertThat(alertaService.criarAlerta(emAtraso, 240)).isNull();

        Long alertaId = existentes.get(0).getId();
        alertaService.marcarLido(alertaId, true);
        assertThat(alertaService.criarAlerta(emAtraso, 240)).isNull();
        assertThat(alertaRepository.findByCarregamentoIdOrderByDataHoraEmissaoDesc(
                emAtraso.getId())).hasSize(1);
    }

    @Test
    @DisplayName("As três fases operacionais são registadas e calculadas separadamente")
    void fasesOperacionaisSeparadas() throws Exception {
        Carregamento c = carregamentoRepository.findByUuidLocal(UUID.fromString(
                "33333333-3333-3333-3333-333333333333")).orElseThrow();
        LocalDateTime inicio = c.getDataHoraInicio();
        LocalDateTime fim = c.getDataHoraFim();

        String corpo = objectMapper.writeValueAsString(Map.of(
                "dataHoraInicioCarregamento", inicio.plusMinutes(60),
                "dataHoraFimCarregamento", inicio.plusMinutes(100),
                "dataHoraInicioTransporte", inicio.plusMinutes(110)));

        mockMvc.perform(patch("/api/v1/carregamentos/" + c.getId() + "/fases")
                        .header("Authorization", "Bearer " + obterToken("admin@logitrack.co.mz"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fases.esperaMinutos").value(60))
                .andExpect(jsonPath("$.fases.carregamentoMinutos").value(40))
                .andExpect(jsonPath("$.fases.transporteMinutos").value(
                        java.time.Duration.between(inicio.plusMinutes(110), fim).toMinutes()));

        mockMvc.perform(get("/api/v1/kpis/resumo")
                        .header("Authorization", "Bearer " + obterToken("admin@logitrack.co.mz")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registosComFases").value(greaterThan(0)))
                .andExpect(jsonPath("$.tempoMedioCarregamentoHoras").exists())
                .andExpect(jsonPath("$.tempoMedioTransporteHoras").exists());

        mockMvc.perform(get("/api/v1/relatorios")
                        .header("Authorization", "Bearer " + obterToken("admin@logitrack.co.mz")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo.registosComFases").value(greaterThan(0)))
                .andExpect(jsonPath("$.linhas[?(@.guiaNumero=='GT-2026-0003')]"
                        + ".fases.esperaMinutos").value(hasItem(60)));

        mockMvc.perform(patch("/api/v1/carregamentos/" + c.getId() + "/fases")
                        .header("Authorization", "Bearer " + obterToken("admin@logitrack.co.mz"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataHoraInicioCarregamento", inicio.minusMinutes(5)))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("O motorista regista fases apenas nos seus carregamentos pela API móvel")
    void motoristaRegistaFasesPelaApiMobile() throws Exception {
        Veiculo veiculo = veiculoRepository.findAll().get(0);
        Carregamento proprio = criarCarregamentoAtivo(veiculo, 1.0);
        LocalDateTime inicio = proprio.getDataHoraInicio().withNano(0);
        proprio.setDataHoraInicio(inicio);
        proprio.setDataHoraFim(inicio.plusHours(3));
        carregamentoRepository.saveAndFlush(proprio);

        String tokenMotorista = obterToken("motorista@logitrack.co.mz");
        String corpo = objectMapper.writeValueAsString(Map.of(
                "dataHoraInicioCarregamento", inicio.plusMinutes(10),
                "dataHoraFimCarregamento", inicio.plusMinutes(30),
                "dataHoraInicioTransporte", inicio.plusMinutes(40)));

        mockMvc.perform(patch("/api/v1/mobile/carregamentos/" + proprio.getId() + "/fases")
                        .header("Authorization", "Bearer " + tokenMotorista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fases.esperaMinutos").value(10))
                .andExpect(jsonPath("$.fases.carregamentoMinutos").value(20))
                .andExpect(jsonPath("$.fases.transporteMinutos").value(140));

        Carregamento alheio = criarCarregamentoAtivo(veiculo, 1.0);
        alheio.setMotorista(utilizadorRepository.findByEmailIgnoreCase("admin@logitrack.co.mz")
                .orElseThrow());
        alheio.setDataHoraFim(alheio.getDataHoraInicio().plusHours(3));
        carregamentoRepository.saveAndFlush(alheio);

        mockMvc.perform(patch("/api/v1/mobile/carregamentos/" + alheio.getId() + "/fases")
                        .header("Authorization", "Bearer " + tokenMotorista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "dataHoraInicioCarregamento",
                                alheio.getDataHoraInicio().plusMinutes(5)))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Registos antigos não inventam durações para as fases")
    void fasesAntigasSemDados() {
        Carregamento antigo = carregamentoRepository.findByUuidLocal(UUID.fromString(
                "77777777-7777-7777-7777-777777777777")).orElseThrow();
        FasesCarregamento fases = FasesCarregamento.de(antigo);
        assertThat(fases.esperaMinutos()).isNull();
        assertThat(fases.carregamentoMinutos()).isNull();
        assertThat(fases.transporteMinutos()).isNull();
    }

    @Test
    @DisplayName("Operador lê referências para filtros, mas não pode criar fábricas")
    void operadorLeReferenciasEscreveNao() throws Exception {
        String token = obterToken("operador@logitrack.co.mz");

        mockMvc.perform(get("/api/v1/fabricas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/veiculos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/utilizadores/motoristas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // DELETE não tem corpo validado: falha @PreAuthorize antes da regra de negócio.
        mockMvc.perform(delete("/api/v1/fabricas/999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A sequência das fases é validada e o estado segue o início real do transporte")
    void sequenciaFasesMoveEstado() throws Exception {
        Veiculo veiculo = criarVeiculo(100.0);
        Carregamento c = criarCarregamentoAtivo(veiculo, 10.0);
        LocalDateTime inicio = LocalDateTime.now().minusHours(4).withNano(0);
        c.setEstado(EstadoCarregamento.REGISTADO);
        c.setDataHoraInicio(inicio);
        c.setDataHoraFim(null);
        carregamentoRepository.saveAndFlush(c);
        String token = obterToken("admin@logitrack.co.mz");
        String url = "/api/v1/carregamentos/" + c.getId();

        mockMvc.perform(patch(url + "/estado")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EM_TRANSITO\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(carregamentoRepository.findById(c.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoCarregamento.REGISTADO);

        patchFases(c.getId(), token, Map.of(
                        "dataHoraFimCarregamento", inicio.plusHours(2)))
                .andExpect(status().isUnprocessableEntity());
        patchFases(c.getId(), token, Map.of(
                        "dataHoraInicioCarregamento", inicio.minusMinutes(1)))
                .andExpect(status().isUnprocessableEntity());

        patchFases(c.getId(), token, Map.of(
                        "dataHoraInicioCarregamento", inicio.plusHours(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REGISTADO"));
        patchFases(c.getId(), token, Map.of(
                        "dataHoraFimCarregamento", inicio.plusHours(2)))
                .andExpect(status().isOk());
        patchFases(c.getId(), token, Map.of(
                        "dataHoraInicioTransporte", inicio.plusHours(3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EM_TRANSITO"));
        assertThat(veiculoRepository.findById(veiculo.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVeiculo.EM_TRANSITO);

        patchFases(c.getId(), token, Map.of(
                        "dataHoraFimTransporte", inicio.plusHours(3).plusMinutes(30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONCLUIDO"))
                .andExpect(jsonPath("$.fases.transporteMinutos").value(30));
        assertThat(veiculoRepository.findById(veiculo.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoVeiculo.DISPONIVEL);
    }

    @Test
    @DisplayName("GPS conserva a captura, rejeita futuro, deduplica e não promove ponto antigo no mapa")
    void gpsCapturaDuplicadoEOrdem() throws Exception {
        Veiculo veiculo = criarVeiculo(100.0);
        Carregamento c = criarCarregamentoAtivo(veiculo, 1.0);
        String tokenMotorista = obterToken("motorista@logitrack.co.mz");
        String url = "/api/v1/carregamentos/" + c.getId() + "/posicoes";
        LocalDateTime capturaAntiga = LocalDateTime.now().minusHours(2).withNano(0);
        String uuidAntigo = UUID.randomUUID().toString();
        String corpoAntigo = objectMapper.writeValueAsString(Map.of(
                "latitude", -25.9, "longitude", 32.5, "precisaoMetros", 8.0,
                "dataHoraCaptura", capturaAntiga, "uuidPonto", uuidAntigo));

        MvcResult primeira = mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + tokenMotorista)
                        .contentType(MediaType.APPLICATION_JSON).content(corpoAntigo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuidPonto").value(uuidAntigo))
                .andReturn();
        JsonNode json = objectMapper.readTree(primeira.getResponse().getContentAsString());
        assertThat(LocalDateTime.parse(json.get("timestamp").asText())).isEqualTo(capturaAntiga);
        assertThat(LocalDateTime.parse(json.get("recebidoEm").asText())).isAfter(capturaAntiga);

        mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + tokenMotorista)
                        .contentType(MediaType.APPLICATION_JSON).content(corpoAntigo))
                .andExpect(status().isOk());
        assertThat(gpsRepository.findByCarregamentoIdOrderByTimestampAsc(c.getId())).hasSize(1);

        mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + tokenMotorista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "latitude", -25.9, "longitude", 32.5, "precisaoMetros", 8.0,
                                "dataHoraCaptura", LocalDateTime.now().plusHours(2),
                                "uuidPonto", UUID.randomUUID().toString()))))
                .andExpect(status().isUnprocessableEntity());

        LocalDateTime capturaRecente = LocalDateTime.now().minusMinutes(30).withNano(0);
        mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + tokenMotorista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "latitude", -25.91, "longitude", 32.51, "precisaoMetros", 7.0,
                                "dataHoraCaptura", capturaRecente,
                                "uuidPonto", UUID.randomUUID().toString()))))
                .andExpect(status().isCreated());
        mockMvc.perform(post(url)
                        .header("Authorization", "Bearer " + tokenMotorista)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "latitude", -25.92, "longitude", 32.52, "precisaoMetros", 9.0,
                                "dataHoraCaptura", LocalDateTime.now().minusMinutes(90),
                                "uuidPonto", UUID.randomUUID().toString()))))
                .andExpect(status().isCreated());

        MvcResult mapa = mockMvc.perform(get("/api/v1/mapa")
                        .header("Authorization", "Bearer " + obterToken("admin@logitrack.co.mz")))
                .andExpect(status().isOk()).andReturn();
        JsonNode pontoAtual = null;
        for (JsonNode ponto : objectMapper.readTree(mapa.getResponse().getContentAsString())) {
            if (ponto.get("carregamentoId").asLong() == c.getId()) {
                pontoAtual = ponto;
                break;
            }
        }
        assertThat(pontoAtual).isNotNull();
        assertThat(LocalDateTime.parse(pontoAtual.get("timestamp").asText()))
                .isEqualTo(capturaRecente);
    }

    @Test
    @DisplayName("Relatório sem amostras devolve indicadores sem tempo, nunca zero")
    void relatorioSemAmostrasSemZeros() throws Exception {
        String token = obterToken("admin@logitrack.co.mz");
        mockMvc.perform(get("/api/v1/relatorios")
                        .param("fabricaId", "999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linhas").isEmpty())
                .andExpect(jsonPath("$.resumo.tempoMedioHoras")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.resumo.esperaMediaHoras")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.resumo.carregamentoMedioHoras")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.resumo.transporteMedioHoras")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    private ResultActions patchFases(Long id, String token, Map<String, Object> corpo)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/carregamentos/" + id + "/fases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(corpo)));
    }

    private Veiculo criarVeiculo(double capacidade) {
        String matricula = "TST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return veiculoRepository.saveAndFlush(new Veiculo(matricula, "Veículo de teste",
                capacidade, EstadoVeiculo.DISPONIVEL));
    }

    private Carregamento criarCarregamentoAtivo(Veiculo veiculo, double quantidade) {
        Carregamento c = new Carregamento();
        c.setUuidLocal(UUID.randomUUID());
        c.setGuiaNumero("GT-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        c.setTipoCarga("Cimento");
        c.setQuantidadeToneladas(quantidade);
        c.setEstado(EstadoCarregamento.EM_TRANSITO);
        c.setDataHoraInicio(LocalDateTime.now().minusHours(1));
        c.setFabrica(fabricaRepository.findAll().get(0));
        c.setMotorista(utilizadorRepository.findByEmailIgnoreCase("motorista@logitrack.co.mz")
                .orElseThrow());
        c.setVeiculo(veiculo);
        return carregamentoRepository.saveAndFlush(c);
    }

    private String obterToken(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "senha", SENHA))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("token").asText();
    }
}
