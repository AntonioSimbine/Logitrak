package mz.co.logitrack.config;

import mz.co.logitrack.domain.Carregamento;
import mz.co.logitrack.domain.Fabrica;
import mz.co.logitrack.domain.LocalizacaoGPS;
import mz.co.logitrack.domain.Utilizador;
import mz.co.logitrack.domain.Veiculo;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import mz.co.logitrack.domain.enums.EstadoVeiculo;
import mz.co.logitrack.domain.enums.Role;
import mz.co.logitrack.repository.CarregamentoRepository;
import mz.co.logitrack.repository.FabricaRepository;
import mz.co.logitrack.repository.LocalizacaoGPSRepository;
import mz.co.logitrack.repository.UtilizadorRepository;
import mz.co.logitrack.repository.VeiculoRepository;
import mz.co.logitrack.service.AlertaService;
import mz.co.logitrack.service.DocumentoService;
import mz.co.logitrack.service.ParametroService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Povoa a base com um cenário operacional realista (utilizadores dos três
 * perfis, fábricas, frota, parâmetros, carregamentos, guias e GPS). A execução
 * é idempotente e pode ser desativada com LOGITRACK_DEMO_SEED=false.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final UtilizadorRepository utilizadorRepository;
    private final FabricaRepository fabricaRepository;
    private final VeiculoRepository veiculoRepository;
    private final CarregamentoRepository carregamentoRepository;
    private final LocalizacaoGPSRepository gpsRepository;
    private final ParametroService parametroService;
    private final DocumentoService documentoService;
    private final AlertaService alertaService;
    private final PasswordEncoder passwordEncoder;

    @Value("${logitrack.demo-seed:true}")
    private boolean ativo;

    @Value("${logitrack.demo.password:}")
    private String senhaDemo;

    public DataSeeder(UtilizadorRepository utilizadorRepository,
                      FabricaRepository fabricaRepository,
                      VeiculoRepository veiculoRepository,
                      CarregamentoRepository carregamentoRepository,
                      LocalizacaoGPSRepository gpsRepository,
                      ParametroService parametroService,
                      DocumentoService documentoService,
                      AlertaService alertaService,
                      PasswordEncoder passwordEncoder) {
        this.utilizadorRepository = utilizadorRepository;
        this.fabricaRepository = fabricaRepository;
        this.veiculoRepository = veiculoRepository;
        this.carregamentoRepository = carregamentoRepository;
        this.gpsRepository = gpsRepository;
        this.parametroService = parametroService;
        this.documentoService = documentoService;
        this.alertaService = alertaService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!ativo) {
            log.info("Seed de demonstração desativado (LOGITRACK_DEMO_SEED=false)");
            return;
        }
        if (senhaDemo == null || senhaDemo.isBlank()) {
            throw new IllegalStateException(
                    "Defina LOGITRACK_DEMO_PASSWORD ou desative o seed com LOGITRACK_DEMO_SEED=false.");
        }
        parametroService.garantirParametros();

        Utilizador admin = utilizador("Amélia Nhantumbo", "admin@logitrack.co.mz",
                Role.ADMINISTRADOR);
        Utilizador operador = utilizador("Carlos Mabote", "operador@logitrack.co.mz",
                Role.OPERADOR_CENTRAL);
        Utilizador motorista = utilizador("José Macuácua", "motorista@logitrack.co.mz",
                Role.MOTORISTA);
        Utilizador motorista2 = utilizador("Esperança Cumbe", "motorista2@logitrack.co.mz",
                Role.MOTORISTA);

        // Unidades da demonstração. Criadas antes da verificação de carregamentos
        // para que também sejam adicionadas a bases já existentes, sem apagar dados.
        Fabrica matola = fabrica("Fábrica da Matola", "Av. da Indústria, Matola, Maputo",
                -25.9622, 32.4589, 600, 24, "06:00 - 22:00");
        Fabrica dondo = fabrica("Fábrica de Dondo", "EN6, Dondo, Sofala",
                -19.6094, 34.7431, 500, 36, "05:00 - 23:00");
        Fabrica nacala = fabrica("Fábrica de Nacala", "Zona Industrial, Nacala, Nampula",
                -14.5428, 40.6728, 450, 48, "06:00 - 18:00");

        // Unidades operacionais do percurso Marracuene–Zimpeto.
        // As coordenadas de Guava, Mateque e CMC devem ser confirmadas no local.
        fabricaRota("Fábrica de Guava", "Guava, Marracuene",
                "Guava, Marracuene, Província de Maputo", -25.815556, 32.625000, 1200);
        fabricaRota("Fábrica de Mateque", "Mateque, Marracuene",
                "Mateque, Marracuene, Província de Maputo", -25.790000, 32.650000, 1500);
        fabricaRota("Centro Logístico CMC - Zimpeto", "CMC, Zimpeto",
                "CMC, Zimpeto, Maputo", -25.835000, 32.574000, 1200);
        fabricaRota("Unidade de Carga UJC", "Universidade Joaquim Chissano (UJC)",
                "Campus do Zimpeto, Rua do Grande Maputo, Q.88, Maputo",
                -25.820660, 32.579230, 100);

        if (carregamentoRepository.count() > 0) {
            log.info("Base já contém carregamentos — cenário preservado; unidades do percurso verificadas");
            return;
        }

        Veiculo v1 = veiculo("ADM-01-MC", "Volvo FH16", 34.0, EstadoVeiculo.DISPONIVEL);
        Veiculo v2 = veiculo("ADM-02-MC", "Scania R500", 30.0, EstadoVeiculo.DISPONIVEL);
        Veiculo v3 = veiculo("ACD-03-MC", "MAN TGX 33.500", 28.0, EstadoVeiculo.DISPONIVEL);
        veiculo("ADM-04-MC", "Mercedes-Benz Actros", 32.0, EstadoVeiculo.MANUTENCAO);

        LocalDateTime agora = LocalDateTime.now();

        // 1) Em trânsito e já fora do SLA — o agendador gera o alerta ao arrancar.
        Carregamento atrasado = carregamento(UUID.fromString(
                        "11111111-1111-1111-1111-111111111111"), "GT-2026-0001",
                "Cimento", 28.5, EstadoCarregamento.EM_TRANSITO,
                agora.minusHours(26), null, null, matola, v1, motorista);
        gps(atrasado, v1, -25.9622, 32.4589, 6.4, agora.minusHours(26));
        gps(atrasado, v1, -25.7410, 32.6812, 8.1, agora.minusHours(18));
        gps(atrasado, v1, -25.2010, 33.0120, 9.7, agora.minusHours(8));
        documento(atrasado);

        // 2) Em trânsito dentro do prazo.
        Carregamento transito = carregamento(UUID.fromString(
                        "22222222-2222-2222-2222-222222222222"), "GT-2026-0002",
                "Clínquer", 30.0, EstadoCarregamento.EM_TRANSITO,
                agora.minusHours(5), null, null, dondo, v2, motorista2);
        gps(transito, v2, -19.6094, 34.7431, 5.8, agora.minusHours(5));
        gps(transito, v2, -19.3810, 34.9020, 12.4, agora.minusHours(2));
        documento(transito);

        // 3) Concluído hoje.
        Carregamento hoje = carregamento(UUID.fromString(
                        "33333333-3333-3333-3333-333333333333"), "GT-2026-0003",
                "Gesso", 24.0, EstadoCarregamento.CONCLUIDO,
                agora.minusHours(8), agora.minusHours(2), null, matola, v3, motorista);
        gps(hoje, v3, -25.9622, 32.4589, 7.2, agora.minusHours(8));
        documento(hoje);

        // 4) Concluído no dia anterior.
        Carregamento ontem = carregamento(UUID.fromString(
                        "44444444-4444-4444-4444-444444444444"), "GT-2026-0004",
                "Carvão", 26.5, EstadoCarregamento.CONCLUIDO,
                agora.minusHours(31), agora.minusHours(19), null, dondo, v1, motorista2);
        gps(ontem, v1, -19.6094, 34.7431, 6.1, agora.minusHours(31));
        documento(ontem);

        // 5) Registado à espera de início.
        Carregamento registado = carregamento(UUID.fromString(
                        "55555555-5555-5555-5555-555555555555"), "GT-2026-0005",
                "Areia", 22.0, EstadoCarregamento.REGISTADO,
                agora.minusHours(1), null, null, nacala, v2, motorista);
        gps(registado, v2, -14.5428, 40.6728, 10.3, agora.minusHours(1));
        documento(registado);

        // 6) Rejeitado por guia ilegível.
        carregamento(UUID.fromString(
                        "66666666-6666-6666-6666-666666666666"), "GT-2026-0006",
                "Brita", 27.0, EstadoCarregamento.REJEITADO,
                agora.minusHours(20), agora.minusHours(19),
                "Guia ilegível — fotografia sem resolução mínima", matola, v3, motorista2);

        // 7) Atrasado com alerta aberto.
        Carregamento emAtraso = carregamento(UUID.fromString(
                        "77777777-7777-7777-7777-777777777777"), "GT-2026-0007",
                "Cimento", 31.0, EstadoCarregamento.ATRASADO,
                agora.minusHours(52), null, null, nacala, v1, motorista2);
        gps(emAtraso, v1, -14.5428, 40.6728, 11.2, agora.minusHours(52));
        documento(emAtraso);
        alertaService.criarAlerta(emAtraso, 240);

        // 8) Concluído há três dias (série temporal dos KPIs).
        Carregamento antigo = carregamento(UUID.fromString(
                        "88888888-8888-8888-8888-888888888888"), "GT-2026-0008",
                "Cal", 18.0, EstadoCarregamento.CONCLUIDO,
                agora.minusDays(3).minusHours(9), agora.minusDays(3).minusHours(2),
                null, matola, v2, motorista);
        gps(antigo, v2, -25.9622, 32.4589, 5.4, agora.minusDays(3).minusHours(9));
        documento(antigo);

        v1.setEstado(EstadoVeiculo.EM_TRANSITO);
        veiculoRepository.save(v1);
        v2.setEstado(EstadoVeiculo.EM_TRANSITO);
        veiculoRepository.save(v2);

        log.info("""
                ╔══════════════════════════════════════════════════════════════╗
                ║  Logitrack — dados de demonstração criados                   ║
                ║  Os utilizadores demo usam a palavra-passe definida em       ║
                ║  LOGITRACK_DEMO_PASSWORD.                                     ║
                ╚══════════════════════════════════════════════════════════════╝
                """);
        log.info("Fábricas: {} | Veículos: {} | Carregamentos: {} | Utilizadores: {}",
                fabricaRepository.count(), veiculoRepository.count(),
                carregamentoRepository.count(), utilizadorRepository.count());
    }

    private Utilizador utilizador(String nome, String email, Role perfil) {
        return utilizadorRepository.findByEmailIgnoreCase(email).map(existente -> {
            // Em modo demo a palavra-passe é sempre a definida no ambiente.
            existente.setSenhaHash(passwordEncoder.encode(senhaDemo));
            return utilizadorRepository.save(existente);
        }).orElseGet(() -> {
            Utilizador utilizador = new Utilizador(nome, email,
                    passwordEncoder.encode(senhaDemo), perfil);
            return utilizadorRepository.save(utilizador);
        });
    }


    private Fabrica fabricaRota(String nome, String nomeAntigo, String endereco,
                                double latitude, double longitude, int raio) {
        Fabrica existente = fabricaRepository.findAllByOrderByNomeAsc().stream()
                .filter(f -> f.getNome().equalsIgnoreCase(nome)
                        || f.getNome().equalsIgnoreCase(nomeAntigo)
                        || (nome.contains("Guava") && f.getNome().toLowerCase().contains("guava"))
                        || (nome.contains("Mateque") && f.getNome().toLowerCase().contains("mateque"))
                        || (nome.contains("CMC") && f.getNome().toLowerCase().contains("cmc"))
                        || (nome.contains("UJC") && (f.getNome().toLowerCase().contains("universidade joaquim")
                            || f.getNome().toLowerCase().contains("ujc"))))
                .findFirst().orElse(null);
        if (existente == null) {
            return fabricaRepository.save(new Fabrica(
                    nome, endereco, latitude, longitude, raio, 24, "06:00 - 22:00"));
        }
        existente.setNome(nome);
        existente.setEndereco(endereco);
        existente.setLatitude(latitude);
        existente.setLongitude(longitude);
        existente.setRaioGeofenceMetros(raio);
        return fabricaRepository.save(existente);
    }

    private Fabrica fabrica(String nome, String endereco, double latitude, double longitude,
                            int raio, int sla, String horario) {
        return fabricaRepository.findAllByOrderByNomeAsc().stream()
                .filter(f -> f.getNome().equalsIgnoreCase(nome))
                .findFirst()
                .orElseGet(() -> fabricaRepository.save(new Fabrica(
                        nome, endereco, latitude, longitude, raio, sla, horario)));
    }

    private Veiculo veiculo(String matricula, String modelo, double capacidade,
                            EstadoVeiculo estado) {
        return veiculoRepository.findAllByOrderByMatriculaAsc().stream()
                .filter(v -> v.getMatricula().equalsIgnoreCase(matricula))
                .findFirst()
                .orElseGet(() -> veiculoRepository.save(new Veiculo(
                        matricula, modelo, capacidade, estado)));
    }

    private Carregamento carregamento(UUID uuid, String guia, String tipo, double toneladas,
                                      EstadoCarregamento estado, LocalDateTime inicio,
                                      LocalDateTime fim, String motivo, Fabrica fabrica,
                                      Veiculo veiculo, Utilizador motorista) {
        Carregamento c = new Carregamento();
        c.setUuidLocal(uuid);
        c.setGuiaNumero(guia);
        c.setTipoCarga(tipo);
        c.setQuantidadeToneladas(toneladas);
        c.setEstado(estado);
        c.setDataHoraInicio(inicio);
        c.setDataHoraFim(fim);
        c.setMotivoRejeicao(motivo);
        c.setFabrica(fabrica);
        c.setVeiculo(veiculo);
        c.setMotorista(motorista);
        return carregamentoRepository.save(c);
    }

    private void gps(Carregamento carregamento, Veiculo veiculo, double latitude,
                     double longitude, double precisao, LocalDateTime timestamp) {
        gpsRepository.save(new LocalizacaoGPS(latitude, longitude, precisao,
                timestamp, carregamento, veiculo));
    }

    private void documento(Carregamento carregamento) {
        documentoService.salvarSvgSeed(carregamento,
                svgGuia(carregamento), carregamento.getGuiaNumero().toLowerCase());
    }

    private String svgGuia(Carregamento c) {
        String nomeFabrica = c.getFabrica() != null ? c.getFabrica().getNome() : "—";
        String nomeMotorista = c.getMotorista() != null ? c.getMotorista().getNome() : "—";
        String matricula = c.getVeiculo() != null ? c.getVeiculo().getMatricula() : "—";
        String inicio = c.getDataHoraInicio() != null ? c.getDataHoraInicio().format(DATA) : "—";
        List<String> linhas = new ArrayList<>();
        linhas.add("Guia: " + c.getGuiaNumero());
        linhas.add("Fábrica: " + nomeFabrica);
        linhas.add("Veículo: " + matricula);
        linhas.add("Motorista: " + nomeMotorista);
        linhas.add(String.format("Carga: %s — %.1f t", c.getTipoCarga(),
                c.getQuantidadeToneladas()));
        linhas.add("Partida: " + inicio);

        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < linhas.size(); i++) {
            texto.append("<text x=\"92\" y=\"").append(300 + i * 42)
                    .append("\" font-family=\"Courier New, monospace\" font-size=\"22\" fill=\"#1e293b\">")
                    .append(escapar(linhas.get(i))).append("</text>");
        }

        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="860" height="1120" viewBox="0 0 860 1120">
                  <defs>
                    <linearGradient id="papel" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%" stop-color="#fffdf7"/>
                      <stop offset="100%" stop-color="#f2ecdc"/>
                    </linearGradient>
                  </defs>
                  <rect width="860" height="1120" fill="#e2e8f0"/>
                  <rect x="40" y="40" width="780" height="1040" rx="20" fill="url(#papel)" stroke="#cbd5e1" stroke-width="3"/>
                  <rect x="40" y="40" width="780" height="120" rx="20" fill="#0b1f3a"/>
                  <rect x="40" y="120" width="780" height="40" fill="#0b1f3a"/>
                  <text x="92" y="108" font-family="Arial, sans-serif" font-size="34" font-weight="bold" fill="#ffffff">LOGITRACK</text>
                  <text x="92" y="140" font-family="Arial, sans-serif" font-size="16" fill="#93c5fd">GUIA DE TRANSPORTE · DOCUMENTO DIGITALIZADO</text>
                  <circle cx="740" cy="100" r="34" fill="#f26b2e"/>
                  <text x="740" y="110" text-anchor="middle" font-family="Arial, sans-serif" font-size="28" font-weight="bold" fill="#ffffff">LT</text>
                  <rect x="72" y="196" width="716" height="4" fill="#f26b2e"/>
                  __TEXTO__
                  <rect x="92" y="580" width="676" height="120" rx="12" fill="#f8fafc" stroke="#e2e8f0"/>
                  <text x="112" y="620" font-family="Arial, sans-serif" font-size="16" font-weight="bold" fill="#64748b">OBSERVAÇÕES</text>
                  <text x="112" y="654" font-family="Arial, sans-serif" font-size="16" fill="#475569">Documento de demonstração gerado automaticamente pelo Logitrack.</text>
                  <text x="112" y="682" font-family="Arial, sans-serif" font-size="16" fill="#475569">A autenticidade pode ser verificada pelo hash SHA-256 registado na central.</text>
                  <rect x="92" y="780" width="320" height="170" fill="none" stroke="#cbd5e1" stroke-dasharray="8 6"/>
                  <text x="112" y="820" font-family="Arial, sans-serif" font-size="15" fill="#94a3b8">Assinatura do motorista</text>
                  <rect x="452" y="780" width="316" height="170" fill="none" stroke="#cbd5e1" stroke-dasharray="8 6"/>
                  <text x="472" y="820" font-family="Arial, sans-serif" font-size="15" fill="#94a3b8">Carimbo da fábrica</text>
                  <text x="92" y="1020" font-family="Arial, sans-serif" font-size="14" fill="#94a3b8">Logitrack · Sistema de Gestão e Rastreabilidade de Carregamentos · Moçambique</text>
                </svg>
                """.replace("__TEXTO__", texto.toString());
    }

    private String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
