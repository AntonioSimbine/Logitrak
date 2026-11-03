package mz.co.logitrack.dto.dashboard;

/** Fábrica desenhada no mapa com o respetivo raio de geofence. */
public record FabricaMapa(
        Long id,
        String nome,
        String endereco,
        double latitude,
        double longitude,
        int raioGeofenceMetros,
        boolean ativa
) {
}
