package mz.co.logitrack.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Objects;

/** Ponto de GPS recolhido durante o carregamento / transporte. */
@Entity
@Table(name = "localizacoes_gps",
        indexes = {
                @Index(name = "idx_gps_carregamento_ts", columnList = "carregamento_id,timestamp"),
                @Index(name = "idx_gps_veiculo_ts", columnList = "veiculo_id,timestamp")
        })
public class LocalizacaoGPS {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @NotNull
    @DecimalMin("0.0")
    @Column(name = "precisao_metros", nullable = false)
    private Double precisaoMetros;

    @NotNull
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carregamento_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_gps_carregamento"))
    private Carregamento carregamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_gps_veiculo"))
    private Veiculo veiculo;

    public LocalizacaoGPS() {
    }

    public LocalizacaoGPS(Double latitude, Double longitude, Double precisaoMetros,
                          LocalDateTime timestamp, Carregamento carregamento, Veiculo veiculo) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.precisaoMetros = precisaoMetros;
        this.timestamp = timestamp;
        this.carregamento = carregamento;
        this.veiculo = veiculo;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getPrecisaoMetros() {
        return precisaoMetros;
    }

    public void setPrecisaoMetros(Double precisaoMetros) {
        this.precisaoMetros = precisaoMetros;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Carregamento getCarregamento() {
        return carregamento;
    }

    public void setCarregamento(Carregamento carregamento) {
        this.carregamento = carregamento;
    }

    public Veiculo getVeiculo() {
        return veiculo;
    }

    public void setVeiculo(Veiculo veiculo) {
        this.veiculo = veiculo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LocalizacaoGPS that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
