package mz.co.logitrack.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.DocumentoGuiaRepository;
import mz.co.logitrack.repository.UtilizadorRepository;
import mz.co.logitrack.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração dos requisitos críticos da Fase I/II:
 * login genérico, precisão GPS, geofence, idempotência e transição de estado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndSyncApiTest {

    private static final String SENHA = "test-demo-password-only-for-tests";
    private static final String EMAIL_MOTORISTA = "motorista@logitrack.co.mz";
    private static final String EMAIL_MOTORISTA_2 = "motorista2@logitrack.co.mz";
    private static final double LAT_MATOLA = -25.9622;
    private static final double LON_MATOLA = 32.4589;

    private static String fotoBase64;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CarregamentoRepository carregamentoRepository;

    @Autowired
    private DocumentoGuiaRepository documentoGuiaRepository;

    @Autowired
    private UtilizadorRepository utilizadorRepository;

    @Autowired
    private VeiculoRepository veiculoRepository;

    @BeforeAll
    static void gerarFotografia() throws Exception {
        BufferedImage imagem = new BufferedImage(800, 500, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 800, 500);
        g.setColor(new Color(11, 31, 58));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 38));
        g.drawString("LOGITRACK", 48, 90);
        g.setColor(new Color(71, 85, 105));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
        g.drawString("GUIA DE TRANSPORTE", 48, 160);
        g.drawString("GT-TESTE-0001", 48, 220);
        g.drawString("Carga: Cimento — 30 t", 48, 280);
        g.dispose();

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpg", saida);
        fotoBase64 = Base64.getEncoder().encodeToString(saida.toByteArray());
    }

    @Test
    @DisplayName("Login inválido devolve 401 com mensagem genérica (não revela o e-mail)")
    void loginInvalidoDevolveErroGenerico() throws Exception {
        String corpoEmailExistente = objectMapper.writeValueAsString(Map.of(
                "email", EMAIL_MOTORISTA, "senha", "palavra-passe-errada"));
        String corpoEmailInexistente = objectMapper.writeValueAsString(Map.of(
                "email", "naoexiste@logitrack.co.mz", "senha", SENHA));

        for (String corpo : new String[]{corpoEmailExistente, corpoEmailInexistente}) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(corpo))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value(
                            "Não foi possível entrar. Verifique o e-mail e a palavra-passe."));
        }
    }

    @Test
    @DisplayName("Login válido devolve JWT e perfil do motorista")
    void loginValidoDevolveToken() throws Exception {
        String token = obterToken();

        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL_MOTORISTA))
                .andExpect(jsonPath("$.perfil").value("MOTORISTA"));
    }

    @Test
    @DisplayName("GPS com precisão superior a 15 m é rejeitado com orientação clara")
    void precisaoGpsInsuficiente() throws Exception {
        String payload = payload(UUID.randomUUID(), 1L, LAT_MATOLA, LON_MATOLA, 18.5,
                criarVeiculoTeste(100.0), 30.0);

        mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + obterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(
                        "Faltam dados obrigatórios. Preencha os campos assinalados."))
                .andExpect(jsonPath("$.detalhes.precisaoMetros").value(
                        org.hamcrest.Matchers.containsString("A localização ainda não é precisa")))
                .andExpect(jsonPath("$.detalhes.precisaoMetros").value(
                        org.hamcrest.Matchers.containsString("Vá para um local aberto")));
    }

    @Test
    @DisplayName("Registo fora do geofence exige confirmação explícita")
    void geofenceSemConfirmacaoFalha() throws Exception {
        String payload = payload(UUID.randomUUID(), 1L, -20.5000, 33.5000, 6.0,
                criarVeiculoTeste(100.0), 30.0);

        mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + obterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("Está fora da área da fábrica.")))
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("distância máxima")));
    }

    @Test
    @DisplayName("Sincronização válida cria REGISTADO e duplicado é idempotente (200)")
    void sincronizacaoValidaEIdempotente() throws Exception {
        UUID uuidLocal = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        long veiculoId = criarVeiculoTeste(100.0);
        String payload = payload(uuidLocal, 1L, LAT_MATOLA, LON_MATOLA, 6.0, veiculoId, 30.0);
        String token = obterToken();

        MvcResult primeira = mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotente", "false"))
                .andExpect(jsonPath("$.uuidLocal").value(uuidLocal.toString()))
                .andExpect(jsonPath("$.estado").value("REGISTADO"))
                .andExpect(jsonPath("$.temDocumento").value(true))
                .andReturn();

        assertThat(primeira.getResponse().getContentAsString()).isNotBlank();

        MvcResult segunda = mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotente", "true"))
                .andExpect(jsonPath("$.uuidLocal").value(uuidLocal.toString()))
                .andReturn();

        JsonNode corpo = objectMapper.readTree(segunda.getResponse().getContentAsString());
        long idPrimeiro = objectMapper.readTree(
                primeira.getResponse().getContentAsString()).get("id").asLong();
        assertThat(corpo.get("id").asLong()).isEqualTo(idPrimeiro);

        var guardado = carregamentoRepository.findByUuidLocal(uuidLocal).orElseThrow();
        assertThat(guardado.getEstado()).isEqualTo(EstadoCarregamento.REGISTADO);
        assertThat(veiculoRepository.findById(veiculoId).orElseThrow().getEstado())
                .isNotEqualTo(EstadoVeiculo.EM_TRANSITO);
        assertThat(documentoGuiaRepository.existsByCarregamentoId(guardado.getId())).isTrue();
    }

    @Test
    @DisplayName("Outro motorista não pode usar a idempotência para ler registo alheio")
    void sincronizacaoComDoisMotoristasDiferentes() throws Exception {
        UUID uuidLocal = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
        long veiculoMotorista1 = criarVeiculoTeste(100.0);
        String payload = payload(uuidLocal, 1L, LAT_MATOLA, LON_MATOLA, 6.0,
                veiculoMotorista1, 30.0);
        String tokenMotorista1 = obterToken(EMAIL_MOTORISTA);
        String tokenMotorista2 = obterToken(EMAIL_MOTORISTA_2);

        MvcResult primeira = mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + tokenMotorista1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotente", "false"))
                .andExpect(jsonPath("$.uuidLocal").value(uuidLocal.toString()))
                .andReturn();
        long idRegisto = objectMapper.readTree(
                primeira.getResponse().getContentAsString()).get("id").asLong();

        // O mesmo uuidLocal na mão de outro motorista não devolve o registo.
        mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + tokenMotorista2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(
                        "Não tem permissão para esta operação. Fale com a central."));

        // O proprietário continua a reenviar sem duplicar.
        mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + tokenMotorista1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotente", "true"))
                .andExpect(jsonPath("$.id").value(idRegisto))
                .andExpect(jsonPath("$.uuidLocal").value(uuidLocal.toString()));

        var guardado = carregamentoRepository.findByUuidLocal(uuidLocal).orElseThrow();
        Long idMotorista1 = utilizadorRepository.findByEmailIgnoreCase(EMAIL_MOTORISTA)
                .orElseThrow().getId();
        assertThat(guardado.getMotorista().getId()).isEqualTo(idMotorista1);
        assertThat(documentoGuiaRepository.existsByCarregamentoId(idRegisto)).isTrue();

        // O segundo motorista continua a poder criar os próprios registos.
        UUID uuidSegundoMotorista = UUID.fromString("cccccccc-dddd-eeee-ffff-000000000001");
        mockMvc.perform(post("/api/v1/sync/carregamentos")
                        .header("Authorization", "Bearer " + tokenMotorista2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(uuidSegundoMotorista, 1L,
                                LAT_MATOLA, LON_MATOLA, 6.0,
                                criarVeiculoTeste(100.0), 30.0)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotente", "false"))
                .andExpect(jsonPath("$.uuidLocal").value(uuidSegundoMotorista.toString()));
    }

    @Test
    @DisplayName("Endpoints móveis exigem autenticação JWT")
    void dashboardMovelSemTokenDevolve401() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Dois envios concorrentes do mesmo uuidLocal devolvem 201 e 200")
    void enviosConcorrentesMesmoUuidLocal() throws Exception {
        UUID uuidLocal = UUID.randomUUID();
        long veiculoId = criarVeiculoTeste(100.0);
        String payload = payload(uuidLocal, 1L, LAT_MATOLA, LON_MATOLA, 6.0, veiculoId, 30.0);
        String token = obterToken();

        List<MvcResult> resultados = executarConcorrentes(token, payload, payload);

        assertThat(statuses(resultados)).containsExactlyInAnyOrder(201, 200);
        long idCriado = id(resultados, 201);
        long idRepetido = id(resultados, 200);
        assertThat(idRepetido).isEqualTo(idCriado);
        assertThat(carregamentoRepository.countByUuidLocal(uuidLocal)).isEqualTo(1);
        assertThat(documentoGuiaRepository.countByCarregamentoId(idCriado)).isEqualTo(1);
    }

    @Test
    @DisplayName("Pedidos concorrentes distintos para o mesmo veículo preservam a validação 422")
    void pedidosConcorrentesDistintosMesmoVeiculo() throws Exception {
        UUID uuidA = UUID.randomUUID();
        UUID uuidB = UUID.randomUUID();
        long veiculoId = criarVeiculoTeste(10.0);
        String token = obterToken();
        String payloadA = payload(uuidA, 1L, LAT_MATOLA, LON_MATOLA, 6.0, veiculoId, 8.0);
        String payloadB = payload(uuidB, 1L, LAT_MATOLA, LON_MATOLA, 6.0, veiculoId, 8.0);

        List<MvcResult> resultados = executarConcorrentes(token, payloadA, payloadB);

        assertThat(statuses(resultados)).containsExactlyInAnyOrder(201, 422);
        boolean criouA = carregamentoRepository.findByUuidLocal(uuidA).isPresent();
        boolean criouB = carregamentoRepository.findByUuidLocal(uuidB).isPresent();
        assertThat(criouA ^ criouB).isTrue();
    }

    private List<MvcResult> executarConcorrentes(String token, String payloadA,
                                                  String payloadB) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        Callable<MvcResult> tarefaA = () -> {
            inicio.await();
            return mockMvc.perform(post("/api/v1/carregamentos")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadA))
                    .andReturn();
        };
        Callable<MvcResult> tarefaB = () -> {
            inicio.await();
            return mockMvc.perform(post("/api/v1/carregamentos")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadB))
                    .andReturn();
        };
        try {
            Future<MvcResult> futuroA = pool.submit(tarefaA);
            Future<MvcResult> futuroB = pool.submit(tarefaB);
            inicio.countDown();
            return List.of(futuroA.get(60, TimeUnit.SECONDS), futuroB.get(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<Integer> statuses(List<MvcResult> resultados) {
        return resultados.stream()
                .map(r -> r.getResponse().getStatus())
                .toList();
    }

    private long id(List<MvcResult> resultados, int status) throws Exception {
        return resultados.stream()
                .filter(r -> r.getResponse().getStatus() == status)
                .findFirst()
                .map(r -> {
                    try {
                        return objectMapper.readTree(r.getResponse().getContentAsString())
                                .get("id").asLong();
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .orElseThrow();
    }

    private String obterToken() throws Exception {
        return obterToken(EMAIL_MOTORISTA);
    }

    private String obterToken(String email) throws Exception {
        String corpo = objectMapper.writeValueAsString(Map.of(
                "email", email, "senha", SENHA));

        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("token").asText();
    }

    private long criarVeiculoTeste(double capacidade) {
        String matricula = "TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return veiculoRepository.save(new Veiculo(matricula, "Veículo de teste",
                capacidade, EstadoVeiculo.DISPONIVEL)).getId();
    }

    private String payload(UUID uuid, long fabricaId, double latitude,
                           double longitude, double precisao) throws Exception {
        return payload(uuid, fabricaId, latitude, longitude, precisao, 3L, 30.0);
    }

    private String payload(UUID uuid, long fabricaId, double latitude,
                           double longitude, double precisao, long veiculoId,
                           double quantidade) throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("uuidLocal", uuid.toString());
        dados.put("guiaNumero", "GT-TESTE-" + uuid.toString().substring(0, 8));
        dados.put("tipoCarga", "Cimento");
        dados.put("quantidadeToneladas", quantidade);
        dados.put("fabricaId", fabricaId);
        dados.put("veiculoId", veiculoId);
        dados.put("latitude", latitude);
        dados.put("longitude", longitude);
        dados.put("precisaoMetros", precisao);
        dados.put("fotoBase64", fotoBase64);
        dados.put("confirmarForaGeofence", false);
        return objectMapper.writeValueAsString(dados);
    }
}
