package mz.co.logitrack.service;

import mz.co.logitrack.domain.Fabrica;
import mz.co.logitrack.exception.ApiException;
import mz.co.logitrack.repository.FabricaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deteção automática da fábrica de origem por geofence (Haversine) e validação
 * de registos fora do raio (RN03).
 */
@Service
public class GeofenceService {

    private final FabricaRepository fabricaRepository;

    public GeofenceService(FabricaRepository fabricaRepository) {
        this.fabricaRepository = fabricaRepository;
    }

    public record Deteccao(Fabrica fabrica, double distanciaMetros, boolean dentro) {
    }

    /** Deteta a fábrica ativa mais próxima dentro do respetivo raio. */
    @Transactional(readOnly = true)
    public Optional<Deteccao> detetar(double latitude, double longitude) {
        List<Fabrica> ativas = fabricaRepository.findByAtivaTrueOrderByNomeAsc();
        return ativas.stream()
                .map(f -> new Deteccao(f, GeoUtils.distanciaMetros(
                        latitude, longitude, f.getLatitude(), f.getLongitude()),
                        GeoUtils.distanciaMetros(latitude, longitude,
                                f.getLatitude(), f.getLongitude()) <= f.getRaioGeofenceMetros()))
                .filter(Deteccao::dentro)
                .min(Comparator.comparingDouble(Deteccao::distanciaMetros));
    }

    /** Fábrica ativa mais próxima, mesmo que fora do raio. */
    @Transactional(readOnly = true)
    public Optional<Deteccao> maisProxima(double latitude, double longitude) {
        return fabricaRepository.findByAtivaTrueOrderByNomeAsc().stream()
                .map(f -> {
                    double d = GeoUtils.distanciaMetros(
                            latitude, longitude, f.getLatitude(), f.getLongitude());
                    return new Deteccao(f, d, d <= f.getRaioGeofenceMetros());
                })
                .min(Comparator.comparingDouble(Deteccao::distanciaMetros));
    }

    /**
     * Resolve a fábrica de um registo:
     * <ul>
     *   <li>sem fabricaId: deteta automaticamente pelo geofence;</li>
     *   <li>com fabricaId: valida a distância; se estiver fora, exige
     *       confirmação explícita do motorista (RN03).</li>
     * </ul>
     *
     * @throws ApiException 422 quando não é possível resolver/validar a origem
     */
    @Transactional(readOnly = true)
    public Fabrica resolver(Long fabricaId, double latitude, double longitude,
                            boolean confirmarForaGeofence) {

        if (fabricaId != null) {
            Fabrica fabrica = fabricaRepository.findById(fabricaId)
                    .orElseThrow(() -> ApiException.unprocessable("Fábrica não encontrada"));
            if (!Boolean.TRUE.equals(fabrica.getAtiva())) {
                throw ApiException.unprocessable("A fábrica selecionada está inativa");
            }
            double distancia = GeoUtils.distanciaMetros(
                    latitude, longitude, fabrica.getLatitude(), fabrica.getLongitude());
            if (distancia > fabrica.getRaioGeofenceMetros() && !confirmarForaGeofence) {
                throw ApiException.unprocessable(String.format(
                        "Fora do raio de geofence de %s (distância %s; raio %d m). "
                                + "Confirme o registo para continuar.",
                        fabrica.getNome(),
                        GeoUtils.formatarDistancia(distancia),
                        fabrica.getRaioGeofenceMetros()));
            }
            return fabrica;
        }

        Optional<Deteccao> deteccao = detetar(latitude, longitude);
        if (deteccao.isPresent()) {
            return deteccao.get().fabrica();
        }

        if (confirmarForaGeofence) {
            return maisProxima(latitude, longitude)
                    .map(Deteccao::fabrica)
                    .orElseThrow(() -> ApiException.unprocessable(
                            "Não existem fábricas ativas configuradas"));
        }

        String detalhe = maisProxima(latitude, longitude)
                .map(d -> String.format(" A mais próxima é %s, a %s (raio %d m).",
                        d.fabrica().getNome(),
                        GeoUtils.formatarDistancia(d.distanciaMetros()),
                        d.fabrica().getRaioGeofenceMetros()))
                .orElse("");
        throw ApiException.unprocessable(
                "Nenhuma fábrica dentro do raio de geofence." + detalhe
                        + " Aproxime-se da fábrica ou confirme o registo manualmente.");
    }
}
