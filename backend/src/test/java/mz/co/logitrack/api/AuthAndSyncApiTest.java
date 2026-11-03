package mz.co.logitrack.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.DocumentoGuiaRepository;
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
import java.util.Map;
import java.util.UUID;

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
                    .andExpect(jsonPath("$.error").value("Credenciais inválidas"));
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
    @DisplayName("GPS com precisão superior a 15 m é rejeitado com a mensagem exata")
    void precisaoGpsInsuficiente() throws Exception {
        String payload = payload(UUID.randomUUID(), 1L, LAT_MATOLA, LON_MATOLA, 18.5);

        mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + obterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Precisão de GPS insuficiente para validação (Máx: 15m)"));
    }

    @Test
    @DisplayName("Registo fora do geofence exige confirmação explícita")
    void geofenceSemConfirmacaoFalha() throws Exception {
        String payload = payload(UUID.randomUUID(), 1L, -20.5000, 33.5000, 6.0);

        mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + obterToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("Fora do raio de geofence")));
    }

    @Test
    @DisplayName("Sincronização válida cria EM_TRANSITO e duplicado é idempotente (200)")
    void sincronizacaoValidaEIdempotente() throws Exception {
        UUID uuidLocal = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String payload = payload(uuidLocal, 1L, LAT_MATOLA, LON_MATOLA, 6.0);
        String token = obterToken();

        MvcResult primeira = mockMvc.perform(post("/api/v1/carregamentos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotente", "false"))
                .andExpect(jsonPath("$.uuidLocal").value(uuidLocal.toString()))
                .andExpect(jsonPath("$.estado").value("EM_TRANSITO"))
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
        assertThat(guardado.getEstado()).isEqualTo(EstadoCarregamento.EM_TRANSITO);
        assertThat(documentoGuiaRepository.existsByCarregamentoId(guardado.getId())).isTrue();
    }

    @Test
    @DisplayName("Endpoints móveis exigem autenticação JWT")
    void dashboardMovelSemTokenDevolve401() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    private String obterToken() throws Exception {
        String corpo = objectMapper.writeValueAsString(Map.of(
                "email", EMAIL_MOTORISTA, "senha", SENHA));

        MvcResult resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(resultado.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String payload(UUID uuid, long fabricaId, double latitude,
                           double longitude, double precisao) throws Exception {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("uuidLocal", uuid.toString());
        dados.put("guiaNumero", "GT-TESTE-" + uuid.toString().substring(0, 8));
        dados.put("tipoCarga", "Cimento");
        dados.put("quantidadeToneladas", 30.0);
        dados.put("fabricaId", fabricaId);
        dados.put("veiculoId", 3L);
        dados.put("latitude", latitude);
        dados.put("longitude", longitude);
        dados.put("precisaoMetros", precisao);
        dados.put("fotoBase64", fotoBase64);
        dados.put("confirmarForaGeofence", false);
        return objectMapper.writeValueAsString(dados);
    }
}
