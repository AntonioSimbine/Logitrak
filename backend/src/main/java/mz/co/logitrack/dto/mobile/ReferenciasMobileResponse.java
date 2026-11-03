package mz.co.logitrack.dto.mobile;

import java.util.List;
import java.util.Map;

/** Dados de referência e parâmetros descarregados para cache offline. */
public record ReferenciasMobileResponse(
        List<FabricaMobile> fabricas,
        List<VeiculoMobile> veiculos,
        Map<String, String> parametros,
        List<String> tiposCarga
) {
    public record FabricaMobile(Long id, String nome, String endereco, Double latitude,
                                Double longitude, Integer raioGeofenceMetros,
                                Integer slaLimiteHoras, String horarioAcostagem) {
    }

    public record VeiculoMobile(Long id, String matricula, String modelo,
                                Double capacidadeCargaToneladas, String estado) {
    }
}
