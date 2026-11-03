package mz.co.logitrack.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes da fórmula de Haversine usada no geofence (RN03). */
class GeoUtilsTest {

    @Test
    @DisplayName("Distância entre a Fábrica da Matola e a Fábrica de Dondo")
    void distanciaEntreFabricas() {
        // Matola (-25.9622, 32.4589) → Dondo (-19.6094, 34.7431)
        double metros = GeoUtils.distanciaMetros(-25.9622, 32.4589, -19.6094, 34.7431);

        // ~744 km; tolerância para acomodar o raio médio da Terra.
        assertThat(metros).isBetween(738_000.0, 750_000.0);
    }

    @Test
    @DisplayName("Pontos iguais devolvem distância zero")
    void pontosIguais() {
        assertThat(GeoUtils.distanciaMetros(-25.9622, 32.4589, -25.9622, 32.4589))
                .isZero();
    }

    @Test
    @DisplayName("Deslocamento curto é calculado com precisão métrica")
    void deslocamentoCurto() {
        // 0.001° de latitude ≈ 111.2 m
        double metros = GeoUtils.distanciaMetros(-25.9622, 32.4589, -25.9612, 32.4589);

        assertThat(metros).isBetween(110.0, 112.5);
    }

    @Test
    @DisplayName("Formatação legível de distâncias")
    void formatacao() {
        assertThat(GeoUtils.formatarDistancia(240)).isEqualTo("240 m");
        assertThat(GeoUtils.formatarDistancia(1_240)).isEqualTo("1.2 km");
    }
}
