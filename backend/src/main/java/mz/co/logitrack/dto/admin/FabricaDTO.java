package mz.co.logitrack.dto.admin;

/** Fábrica completa devolvida ao módulo de administração. */
public record FabricaDTO(Long id, String nome, String endereco, Double latitude,
                         Double longitude, Integer raioGeofenceMetros,
                         Integer slaLimiteHoras, String horarioAcostagem, Boolean ativa) {
}
