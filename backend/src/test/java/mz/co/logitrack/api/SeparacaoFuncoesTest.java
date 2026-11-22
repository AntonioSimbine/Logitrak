package mz.co.logitrack.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import mz.co.logitrack.domain.AlertaAtraso;
import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.Notificacao;
import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import mz.co.logitrack.domain.enums.OrigemNotificacao;
import mz.co.logitrack.domain.enums.TipoNotificacao;
import mz.co.logitrack.repository.AlertaAtrasoRepository;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.FabricaRepository;
import mz.co.logitrack.repository.NotificacaoRepository;
import mz.co.logitrack.repository.UtilizadorRepository;
import mz.co.logitrack.repository.VeiculoRepository;
import mz.co.logitrack.service.AlertaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Responsabilidades por perfil, autoria real e origem das notificações. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeparacaoFuncoesTest {

    private static final String SENHA = "test-demo-password-only-for-tests";
    private static final String ADMIN = "admin@logitrack.co.mz";
    private static final String OPERADOR = "operador@logitrack.co.mz";
    private static final String MOTORISTA = "motorista@logitrack.co.mz";
    private static final String MOTORISTA_2 = "motorista2@logitrack.co.mz";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CarregamentoRepository carregamentoRepository;
    @Autowired private VeiculoRepository veiculoRepository;
    @Autowired private FabricaRepository fabricaRepository;
    @Autowired private UtilizadorRepository utilizadorRepository;
    @Autowired private NotificacaoRepository notificacaoRepository;
    @Autowired private AlertaAtrasoRepository alertaRepository;
    @Autowired private AlertaService alertaService;

    @Test
    @DisplayName("API móvel é exclusiva do motorista; sync mantém a excepção de ADMIN")
    void rotasMoveisPorPerfil() throws Exception {
        String operador = obterToken(OPERADOR);
        String admin = obterToken(ADMIN);
        String motorista = obterToken(MOTORISTA);

        mockMvc.perform(get("/api/v1/mobile/dashboard")
                        .header("Authorization", "Bearer " + operador))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/mobile/notificacoes")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/mobile/carregamentos/1/fases")
                        .header("Authorization", "Bearer " + operador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/mobile/dashboard")
                        .header("Authorization", "Bearer " + motorista))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motoristaEmail").doesNotExist());
        mockMvc.perform(get("/api/v1/utilizadores")
                        .header("Authorization", "Bearer " + motorista))
                .andExpect(status().isForbidden());

        // Corpo inválido chega à validação (422): a autorização de sync passou.
        mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
        // Com corpo válido a validação passa e a autorização recusa o operador.
        mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + operador)
                        .contentType(MediaType.APPLICATION_JSON).content(syncValido()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GPS e fotografia da guia são executados apenas pelo motorista")
    void execucaoOperacionalReservadaAoMotorista() throws Exception {
        Carregamento c = criarCarregamentoAtivo();
        String posicao = objectMapper.writeValueAsString(Map.of(
                "latitude", -25.9, "longitude", 32.5, "precisaoMetros", 8.0));

        for (String email : new String[]{ADMIN, OPERADOR}) {
            String token = obterToken(email);
            mockMvc.perform(post("/api/v1/carregamentos/" + c.getId() + "/posicoes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(posicao))
                    .andExpect(status().isForbidden());
            mockMvc.perform(multipart("/api/v1/documentos/carregamento/" + c.getId())
                            .file(new MockMultipartFile("ficheiro", "guia.jpg",
                                    "image/jpeg", new byte[]{1, 2, 3}))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(post("/api/v1/carregamentos/" + c.getId() + "/posicoes")
                        .header("Authorization", "Bearer " + obterToken(MOTORISTA))
                        .contentType(MediaType.APPLICATION_JSON).content(posicao))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Operador lê referências dos filtros e não altera dados administrativos")
    void operadorSoLeReferencias() throws Exception {
        String token = obterToken(OPERADOR);

        mockMvc.perform(get("/api/v1/fabricas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/veiculos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/utilizadores/motoristas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/parametros").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/fabricas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Fabrica Teste\",\"endereco\":\"Maputo\","
                                + "\"latitude\":-25.9,\"longitude\":32.5,"
                                + "\"raioGeofenceMetros\":100,\"slaLimiteHoras\":4,"
                                + "\"horarioAcostagem\":\"08:00-18:00\",\"ativa\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/veiculos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"TST-01\",\"modelo\":\"Teste\","
                                + "\"capacidadeCargaToneladas\":30.0,\"estado\":\"DISPONIVEL\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/utilizadores")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Teste\",\"email\":\"novo-teste@logitrack.co.mz\","
                                + "\"perfil\":\"MOTORISTA\",\"ativo\":true,\"senha\":\"senha123\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/sla/verificar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/sla/verificar")
                        .header("Authorization", "Bearer " + obterToken(ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RejeiçãoManual guarda autor real e origem de Operador e Administrador")
    void autoriaDaRejeicaoManual() throws Exception {
        Long motoristaId = utilizadorRepository.findByEmailIgnoreCase(MOTORISTA).orElseThrow().getId();

        Carregamento rejeitadoPorOperador = criarCarregamentoAtivo();
        rejeitar(rejeitadoPorOperador.getId(), OPERADOR, "Guia ilegível");

        Carregamento gravado = carregamentoRepository.findById(rejeitadoPorOperador.getId())
                .orElseThrow();
        Long operadorId = utilizadorRepository.findByEmailIgnoreCase(OPERADOR).orElseThrow().getId();
        assertThat(gravado.getAutorId()).isEqualTo(operadorId);
        assertThat(gravado.getAutorPerfil()).isEqualTo("OPERADOR_CENTRAL");
        assertThat(gravado.getTipoAccao()).isEqualTo("REJEICAO");
        assertThat(gravado.getDataHoraAccao()).isNotNull();

        Notificacao notificacao = notificacaoRepository.findAll().stream()
                .filter(n -> n.getCarregamentoId() != null
                        && n.getCarregamentoId().equals(gravado.getId())
                        && n.getTipo() == TipoNotificacao.REJEICAO)
                .findFirst().orElseThrow();
        assertThat(notificacao.getDestinatarioId()).isEqualTo(motoristaId);
        assertThat(notificacao.getOrigem()).isEqualTo(OrigemNotificacao.OPERADOR);
        assertThat(notificacao.getAutorId()).isEqualTo(operadorId);
        assertThat(notificacao.getAutorNome())
                .isEqualTo(utilizadorRepository.findByEmailIgnoreCase(OPERADOR)
                        .orElseThrow().getNome());
        assertThat(notificacao.getAutorPerfil()).isEqualTo("OPERADOR_CENTRAL");
        assertThat(notificacao.getMensagem()).contains("Guia ilegível");

        Carregamento rejeitadoPorAdmin = criarCarregamentoAtivo();
        rejeitar(rejeitadoPorAdmin.getId(), ADMIN, "Cancelamento administrativo");
        Notificacao doAdmin = notificacaoRepository.findAll().stream()
                .filter(n -> n.getCarregamentoId() != null
                        && n.getCarregamentoId().equals(rejeitadoPorAdmin.getId())
                        && n.getTipo() == TipoNotificacao.REJEICAO)
                .findFirst().orElseThrow();
        assertThat(doAdmin.getOrigem()).isEqualTo(OrigemNotificacao.ADMINISTRADOR);
        assertThat(doAdmin.getAutorPerfil()).isEqualTo("ADMINISTRADOR");
    }

    @Test
    @DisplayName("Atraso automático é SISTEMA e não tem autor humano")
    void atrasoAutomaticoEhSistema() {
        Carregamento c = criarCarregamentoAtivo();
        alertaService.criarAlerta(c, 42);

        Notificacao notificacao = notificacaoRepository.findAll().stream()
                .filter(n -> n.getCarregamentoId() != null
                        && n.getCarregamentoId().equals(c.getId())
                        && n.getTipo() == TipoNotificacao.ATRASO)
                .findFirst().orElseThrow();
        assertThat(notificacao.getOrigem()).isEqualTo(OrigemNotificacao.SISTEMA);
        assertThat(notificacao.getAutorId()).isNull();
        assertThat(notificacao.getAutorNome()).isNull();
        assertThat(notificacao.getAcaoTipo()).isEqualTo("DETECAO_AUTOMATICA_SLA");

        AlertaAtraso alerta = alertaRepository
                .findByCarregamentoIdOrderByDataHoraEmissaoDesc(c.getId()).get(0);
        alertaService.marcarLido(alerta.getId(), true,
                new mz.co.logitrack.security.AuthenticatedUser(
                        utilizadorRepository.findByEmailIgnoreCase(OPERADOR).orElseThrow()));
        assertThat(alertaRepository.findById(alerta.getId()).orElseThrow()
                .getTratadoPorPerfil()).isEqualTo("OPERADOR_CENTRAL");
    }

    @Test
    @DisplayName("Motorista recebe só as suas notificações; leitura e contadores no servidor")
    void destinatariosLeituraEContadores() throws Exception {
        Long motoristaId = utilizadorRepository.findByEmailIgnoreCase(MOTORISTA).orElseThrow().getId();
        Long outroId = utilizadorRepository.findByEmailIgnoreCase(MOTORISTA_2).orElseThrow().getId();

        Carregamento c = criarCarregamentoAtivo();
        rejeitar(c.getId(), OPERADOR, "Motivo de teste");

        String token = obterToken(MOTORISTA);
        MvcResult lista = mockMvc.perform(get("/api/v1/mobile/notificacoes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        var json = objectMapper.readTree(lista.getResponse().getContentAsString());
        assertThat(json.isArray()).isTrue();
        for (var n : json) {
            // O contrato não expõe destinatário, mas nenhum aviso alheio pode aparecer.
            assertThat(n.has("origem")).isTrue();
        }
        long antes = notificacaoRepository.countByDestinatarioIdAndLidoFalse(motoristaId);
        assertThat(antes).isGreaterThan(0);
        assertThat(notificacaoRepository
                .findTop100ByDestinatarioIdOrderByDataHoraCriacaoDesc(outroId))
                .noneMatch(n -> n.getCarregamentoId() != null
                        && n.getCarregamentoId().equals(c.getId()));

        Notificacao avaliada = notificacaoRepository.findAll().stream()
                .filter(n -> n.getCarregamentoId() != null && n.getCarregamentoId().equals(c.getId())
                        && n.getTipo() == TipoNotificacao.REJEICAO)
                .findFirst().orElseThrow();

        mockMvc.perform(patch("/api/v1/mobile/notificacoes/" + avaliada.getId() + "/lida")
                        .header("Authorization", "Bearer " + obterToken(MOTORISTA_2))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lido\":true}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/mobile/notificacoes/" + avaliada.getId() + "/lida")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lido\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lido").value(true))
                .andExpect(jsonPath("$.dataHoraLeitura").exists());

        assertThat(notificacaoRepository.findById(avaliada.getId()).orElseThrow().getLido()).isTrue();
        assertThat(notificacaoRepository.countByDestinatarioIdAndLidoFalse(motoristaId))
                .isEqualTo(antes - 1);

        mockMvc.perform(get("/api/v1/mobile/notificacoes/nao-lidas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.naoLidas").value(antes - 1));
    }

    private void rejeitar(Long id, String email, String motivo) throws Exception {
        mockMvc.perform(patch("/api/v1/carregamentos/" + id + "/estado")
                        .header("Authorization", "Bearer " + obterToken(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "estado", "REJEITADO", "motivoRejeicao", motivo))))
                .andExpect(status().isOk());
    }

    private Carregamento criarCarregamentoAtivo() {
        Veiculo veiculo = veiculoRepository.saveAndFlush(new Veiculo(
                "TST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                "Veículo de teste", 100.0, EstadoVeiculo.DISPONIVEL));
        Carregamento c = new Carregamento();
        c.setUuidLocal(UUID.randomUUID());
        c.setGuiaNumero("GT-SEP-" + UUID.randomUUID().toString().substring(0, 8));
        c.setTipoCarga("Cimento");
        c.setQuantidadeToneladas(1.0);
        c.setEstado(EstadoCarregamento.EM_TRANSITO);
        c.setDataHoraInicio(LocalDateTime.now().minusHours(1));
        c.setFabrica(fabricaRepository.findAll().get(0));
        c.setMotorista(utilizadorRepository.findByEmailIgnoreCase(MOTORISTA).orElseThrow());
        c.setVeiculo(veiculo);
        return carregamentoRepository.saveAndFlush(c);
    }

    private String syncValido() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "uuidLocal", UUID.randomUUID().toString(),
                "guiaNumero", "GT-SEP-" + UUID.randomUUID().toString().substring(0, 8),
                "tipoCarga", "Cimento",
                "quantidadeToneladas", 1.0,
                "veiculoId", 1L,
                "latitude", -25.9,
                "longitude", 32.5,
                "precisaoMetros", 8.0,
                "fotoBase64", "aGVsbG8=",
                "confirmarForaGeofence", true));
    }

    private String obterToken(String email) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "senha", SENHA))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("token").asText();
    }
}
