package mz.co.logitrack.domain.enums;

/** Estado operacional do veículo da frota. */
public enum EstadoVeiculo {

    DISPONIVEL("Disponível"),
    EM_TRANSITO("Em trânsito"),
    MANUTENCAO("Manutenção");

    private final String etiqueta;

    EstadoVeiculo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
