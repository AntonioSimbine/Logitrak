package mz.co.logitrack.domain.enums;

/**
 * Ciclo de vida do carregamento no servidor central.
 *
 * <p>Os estados GUARDADO_LOCAL e PENDENTE_SINCRONIZACAO existem apenas na app
 * móvel (fonte de verdade local). Quando o registo é aceite pelo servidor,
 * passa por REGISTADO e entra imediatamente em EM_TRANSITO, conforme a regra
 * "Envio ao Servidor com Sucesso: REGISTADO -> EM_TRANSITO".</p>
 */
public enum EstadoCarregamento {

    GUARDADO_LOCAL("Guardado no telemóvel. Aguarda envio."),
    PENDENTE_SINCRONIZACAO("Guardado no telemóvel. Aguarda envio."),
    REGISTADO("Registado"),
    EM_TRANSITO("Em trânsito"),
    ATRASADO("Atrasado"),
    CONCLUIDO("Concluído"),
    REJEITADO("Não aceite");

    private final String etiqueta;

    EstadoCarregamento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public boolean isFinal() {
        return this == CONCLUIDO || this == REJEITADO;
    }

    /**
     * Transições permitidas no servidor. GUARDADO_LOCAL e
     * PENDENTE_SINCRONIZACAO são estados exclusivamente locais e não são
     * aceites como origem de transição na central.
     */
    public boolean podeTransitarPara(EstadoCarregamento destino) {
        if (destino == null || destino == this) {
            return false;
        }
        return switch (this) {
            case REGISTADO -> destino == EM_TRANSITO || destino == REJEITADO;
            case EM_TRANSITO -> destino == ATRASADO || destino == CONCLUIDO || destino == REJEITADO;
            case ATRASADO -> destino == CONCLUIDO || destino == REJEITADO;
            case GUARDADO_LOCAL -> destino == PENDENTE_SINCRONIZACAO || destino == REGISTADO
                    || destino == REJEITADO;
            case PENDENTE_SINCRONIZACAO -> destino == REGISTADO || destino == REJEITADO;
            case CONCLUIDO, REJEITADO -> false;
        };
    }
}
