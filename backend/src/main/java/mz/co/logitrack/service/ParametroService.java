package mz.co.logitrack.service;

import mz.co.logitrack.domain.ParametroGlobal;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.repository.ParametroGlobalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acesso aos parâmetros globais (W10). Os parâmetros são lidos em tempo de
 * execução, sem reiniciar a aplicação, e possuem valores por omissão seguros
 * caso a tabela ainda não esteja povoada.
 */
@Service
public class ParametroService {

    public static final String TOLERANCIA_ATRASO_MINUTOS = "TOLERANCIA_ATRASO_MINUTOS";
    public static final String FREQUENCIA_RECOLHA_GPS_SEGUNDOS = "FREQUENCIA_RECOLHA_GPS_SEGUNDOS";
    public static final String INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS =
            "INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS";
    public static final String PRECISAO_MAXIMA_GPS_METROS = "PRECISAO_MAXIMA_GPS_METROS";
    public static final String INTERVALO_VERIFICACAO_SLA_MINUTOS = "INTERVALO_VERIFICACAO_SLA_MINUTOS";

    /** Teto fixo do RNF005: nenhuma configuração pode aceitar precisão superior. */
    public static final double LIMITE_GPS_METROS = 50.0;
    public static final String ORIENTACAO_GPS =
            "Vá para um local aberto e tente novamente.";

    private static final Logger log = LoggerFactory.getLogger(ParametroService.class);

    private static final Map<String, String> PADROES = new LinkedHashMap<>();
    private static final Map<String, String> DESCRICOES = new LinkedHashMap<>();

    static {
        PADROES.put(TOLERANCIA_ATRASO_MINUTOS, "30");
        DESCRICOES.put(TOLERANCIA_ATRASO_MINUTOS,
                "Margem de espera, em minutos, antes de avisar que a viagem está atrasada.");
        PADROES.put(FREQUENCIA_RECOLHA_GPS_SEGUNDOS, "60");
        DESCRICOES.put(FREQUENCIA_RECOLHA_GPS_SEGUNDOS,
                "De quantos em quantos segundos o telemóvel envia a localização à central.");
        PADROES.put(INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS, "5");
        DESCRICOES.put(INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS,
                "Periodicidade com que a app envia à central os registos guardados no telemóvel "
                        + "(intervalo de envio automático).");
        PADROES.put(PRECISAO_MAXIMA_GPS_METROS, "50.0");
        DESCRICOES.put(PRECISAO_MAXIMA_GPS_METROS,
                "Define quão exata deve ser a localização, em metros, para o registo ser aceite. "
                        + "Quanto menor o valor, mais rigorosa a verificação.");
        PADROES.put(INTERVALO_VERIFICACAO_SLA_MINUTOS, "1");
        DESCRICOES.put(INTERVALO_VERIFICACAO_SLA_MINUTOS,
                "De quantos em quantos minutos a central verifica se alguma viagem ultrapassou o prazo.");
    }

    private final ParametroGlobalRepository parametroRepository;

    public ParametroService(ParametroGlobalRepository parametroRepository) {
        this.parametroRepository = parametroRepository;
    }

    @Transactional(readOnly = true)
    public String obter(String chave) {
        return parametroRepository.findByChaveIgnoreCase(chave)
                .map(ParametroGlobal::getValor)
                .orElseGet(() -> PADROES.getOrDefault(chave, ""));
    }

    @Transactional(readOnly = true)
    public int obterInt(String chave) {
        String valor = obter(chave);
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException ex) {
            log.warn("Parâmetro {} inválido ({}). A usar valor por omissão.", chave, valor);
            return Integer.parseInt(PADROES.getOrDefault(chave, "0"));
        }
    }

    @Transactional(readOnly = true)
    public long obterLong(String chave) {
        String valor = obter(chave);
        try {
            return Long.parseLong(valor.trim());
        } catch (NumberFormatException ex) {
            return Long.parseLong(PADROES.getOrDefault(chave, "0"));
        }
    }

    @Transactional(readOnly = true)
    public double obterDouble(String chave) {
        String valor = obter(chave);
        double resultado;
        try {
            resultado = Double.parseDouble(valor.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            log.warn("Parâmetro {} inválido ({}). A usar valor por omissão.", chave, valor);
            resultado = Double.parseDouble(PADROES.getOrDefault(chave, "0"));
        }
        // Configurações antigas ou inválidas não podem alargar o limite do RNF005.
        if (PRECISAO_MAXIMA_GPS_METROS.equals(chave)
                && (Double.isNaN(resultado) || resultado <= 0 || resultado > LIMITE_GPS_METROS)) {
            log.warn("Parâmetro {} com valor incompatível ({}). A aplicar o teto de {} m.",
                    chave, valor, LIMITE_GPS_METROS);
            return LIMITE_GPS_METROS;
        }
        return resultado;
    }

    @Transactional(readOnly = true)
    public List<ParametroGlobal> listar() {
        return parametroRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public Map<String, String> mapaParametros() {
        Map<String, String> mapa = new LinkedHashMap<>(PADROES);
        for (ParametroGlobal p : listar()) {
            mapa.put(p.getChave(), p.getValor());
        }
        return mapa;
    }

    @Transactional
    public ParametroGlobal atualizar(String chave, String valor) {
        ParametroGlobal parametro = parametroRepository.findByChaveIgnoreCase(chave)
                .orElseThrow(() -> {
                    log.warn("Parâmetro não encontrado: {}", chave);
                    return ApiException.notFound(
                            "Não foi possível encontrar o parâmetro. Confirme a chave e tente novamente.");
                });
        validarValor(chave, valor);
        parametro.setValor(valor.trim());
        return parametroRepository.save(parametro);
    }

    private void validarValor(String chave, String valor) {
        if (valor == null || valor.isBlank()) {
            throw ApiException.unprocessable(
                    "Faltam dados obrigatórios. Preencha os campos assinalados.");
        }
        String limpo = valor.trim().replace(',', '.');
        switch (chave) {
            case TOLERANCIA_ATRASO_MINUTOS -> {
                int v = parseInteiro(chave, limpo);
                if (v < 0 || v > 10080) {
                    log.warn("Tolerância {} fora do intervalo permitido (0 a 10080 minutos)", v);
                    throw ApiException.unprocessable(
                            "O valor da margem de espera não é válido. Corrija o valor e tente novamente.");
                }
            }
            case FREQUENCIA_RECOLHA_GPS_SEGUNDOS -> {
                int v = parseInteiro(chave, limpo);
                if (v < 10 || v > 3600) {
                    log.warn("Frequência de GPS {} fora do intervalo permitido (10 a 3600 segundos)", v);
                    throw ApiException.unprocessable(
                            "O valor da periodicidade da localização não é válido. "
                                    + "Corrija o valor e tente novamente.");
                }
            }
            case INTERVALO_SINCRONIZACAO_AUTOMATICA_MINUTOS, INTERVALO_VERIFICACAO_SLA_MINUTOS -> {
                int v = parseInteiro(chave, limpo);
                if (v < 1 || v > 1440) {
                    log.warn("Intervalo {} fora do intervalo permitido (1 a 1440 minutos)", v);
                    throw ApiException.unprocessable(
                            "O valor da periodicidade não é válido. Corrija o valor e tente novamente.");
                }
            }
            case PRECISAO_MAXIMA_GPS_METROS -> {
                double v = parseDecimal(chave, limpo);
                if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0 || v > LIMITE_GPS_METROS) {
                    log.warn("Precisão {} fora do intervalo permitido (0 a {} m)",
                            v, LIMITE_GPS_METROS);
                    throw ApiException.unprocessable(
                            "A precisão indicada não é válida. " + ORIENTACAO_GPS);
                }
            }
            default -> {
                // Parâmetros livres são aceites tal como enviados.
            }
        }
    }

    private int parseInteiro(String chave, String valor) {
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException ex) {
            log.warn("Parâmetro {} com valor não inteiro: {}", chave, valor);
            throw ApiException.unprocessable(
                    "O valor deve ser um número inteiro. Corrija o valor e tente novamente.");
        }
    }

    private double parseDecimal(String chave, String valor) {
        try {
            return Double.parseDouble(valor);
        } catch (NumberFormatException ex) {
            log.warn("Parâmetro {} com valor numérico inválido: {}", chave, valor);
            throw ApiException.unprocessable(
                    "O valor deve ser um número. Corrija o valor e tente novamente.");
        }
    }

    /** Chamado pelo seeder para garantir os parâmetros de fábrica. */
    @Transactional
    public void garantirParametros() {
        if (parametroRepository.count() > 0) {
            return;
        }
        PADROES.forEach((chave, valor) ->
                parametroRepository.save(new ParametroGlobal(chave, valor, DESCRICOES.get(chave))));
    }
}
