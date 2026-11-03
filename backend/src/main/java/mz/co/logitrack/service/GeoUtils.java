package mz.co.logitrack.service;

import java.util.Locale;

/** Cálculos geográficos usados na validação de geofence. */
public final class GeoUtils {

    /** Raio médio da Terra em metros. */
    public static final double RAIO_TERRA_METROS = 6_371_000.0;

    private GeoUtils() {
    }

    /**
     * Distância em metros entre dois pontos usando a fórmula de Haversine.
     * Implementação numericamente estável para distâncias curtas.
     */
    public static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RAIO_TERRA_METROS * c;
    }

    public static String formatarDistancia(double metros) {
        if (metros < 1000) {
            return String.format(Locale.ROOT, "%.0f m", metros);
        }
        return String.format(Locale.ROOT, "%.1f km", metros / 1000.0);
    }
}
