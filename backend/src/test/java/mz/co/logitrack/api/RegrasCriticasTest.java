package mz.co.logitrack.api;

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
    @Autowired private UtilizadorRepository utilizadorRepository;
    @Autowired private AlertaService alertaService;
    @Autowired private AlertaAtrasoRepository alertaRepository;

    @Test
    @DisplayName("O parâmetro GPS tem teto fixo de 15 m e configurações antigas são normalizadas")
    void tetoGpsFixedo() {
        String chave = ParametroService.PRECISAO_MAXIMA_GPS_METROS;
        assertThat(parametroService.obterDouble(chave)).isEqualTo(15.0);

        assertThatThrownBy(() -> parametroService.atualizar(chave, "50"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("A precisão indicada não é válida.");

        ParametroGlobal parametro = parametroRepository.findByChaveIgnoreCase(chave).orElseThrow();
        String valorOriginal = parametro.getValor();
        try {
            parametro.setValor("50");
            parametroRepository.saveAndFlush(parametro);
            assertThat(parametroService.obterDouble(chave)).isEqualTo(15.0);
        } finally {
            parametro.setValor(valorOriginal);
            parametroRepository.saveAndFlush(parametro);
        }
    }

    @Test
    @DisplayName("Valores limite e inválidos de precisão GPS")
    void validacaoValoresLimiteGps() {
        assertThatCode(() -> ValidacaoGps.validar(0.0, 15.0)).doesNotThrowAnyException();
        assertThatCode(() -> ValidacaoGps.validar(15.0, 15.0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ValidacaoGps.validar(15.01, 15.0))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("A localização ainda não é precisa.");
        assertThatThrownBy(() -> ValidacaoGps.validar(Double.NaN, 15.0))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ValidacaoGps.validar(Double.POSITIVE_INFINITY, 15.0))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ValidacaoGps.validar(-1.0, 15.0))
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
