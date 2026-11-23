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
                @Index(name = "idx_gps_veiculo_ts", columnList = "veiculo_id,timestamp"),
                @Index(name = "idx_gps_uuid_ponto", columnList = "uuid_ponto")
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

    /** Instante real da captura no dispositivo (pode ser antigo, após offline). */
    @NotNull
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    /** Instante em que a central recebeu a posição — nunca substitui a captura. */
    @Column(name = "recebido_em")
    private LocalDateTime recebidoEm;

    /** Identificador do ponto gerado no dispositivo, para reenvio idempotente. */
    @Column(name = "uuid_ponto", length = 64)
    private String uuidPonto;

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
        this(latitude, longitude, precisaoMetros, timestamp, carregamento, veiculo,
                null, LocalDateTime.now());
    }

    public LocalizacaoGPS(Double latitude, Double longitude, Double precisaoMetros,
                          LocalDateTime timestamp, Carregamento carregamento, Veiculo veiculo,
                          String uuidPonto, LocalDateTime recebidoEm) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.precisaoMetros = precisaoMetros;
        this.timestamp = timestamp;
        this.carregamento = carregamento;
        this.veiculo = veiculo;
        this.uuidPonto = uuidPonto;
        this.recebidoEm = recebidoEm != null ? recebidoEm : LocalDateTime.now();
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

    public LocalDateTime getRecebidoEm() {
        return recebidoEm;
    }

    public void setRecebidoEm(LocalDateTime recebidoEm) {
        this.recebidoEm = recebidoEm;
    }

    public String getUuidPonto() {
        return uuidPonto;
    }

    public void setUuidPonto(String uuidPonto) {
        this.uuidPonto = uuidPonto;
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
