package mz.co.logitrack.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.util.Objects;

/**
 * Fábrica / unidade de carregamento. As coordenadas e o raio definem o
 * geofence usado para associar automaticamente um carregamento à origem.
 */
@Entity
@Table(name = "fabricas",
        indexes = {
                @Index(name = "idx_fabricas_ativa", columnList = "ativa"),
                @Index(name = "idx_fabricas_nome", columnList = "nome")
        })
public class Fabrica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 160)
    @Column(name = "nome", nullable = false, length = 160)
    private String nome;

    @NotBlank
    @Size(max = 255)
    @Column(name = "endereco", nullable = false, length = 255)
    private String endereco;

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
    @Min(10)
    @Max(50000)
    @Column(name = "raio_geofence_metros", nullable = false)
    private Integer raioGeofenceMetros = 300;

    @NotNull
    @Min(1)
    @Max(720)
    @Column(name = "sla_limite_horas", nullable = false)
    private Integer slaLimiteHoras = 24;

    @NotBlank
    @Size(max = 50)
    @Column(name = "horario_acostagem", nullable = false, length = 50)
    private String horarioAcostagem = "06:00 - 22:00";

    @NotNull
    @Column(name = "ativa", nullable = false)
    private Boolean ativa = Boolean.TRUE;

    public Fabrica() {
    }

    public Fabrica(String nome, String endereco, Double latitude, Double longitude,
                   Integer raioGeofenceMetros, Integer slaLimiteHoras, String horarioAcostagem) {
        this.nome = nome;
        this.endereco = endereco;
        this.latitude = latitude;
        this.longitude = longitude;
        this.raioGeofenceMetros = raioGeofenceMetros;
        this.slaLimiteHoras = slaLimiteHoras;
        this.horarioAcostagem = horarioAcostagem;
        this.ativa = Boolean.TRUE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
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

    public Integer getRaioGeofenceMetros() {
        return raioGeofenceMetros;
    }

    public void setRaioGeofenceMetros(Integer raioGeofenceMetros) {
        this.raioGeofenceMetros = raioGeofenceMetros;
    }

    public Integer getSlaLimiteHoras() {
        return slaLimiteHoras;
    }

    public void setSlaLimiteHoras(Integer slaLimiteHoras) {
        this.slaLimiteHoras = slaLimiteHoras;
    }

    public String getHorarioAcostagem() {
        return horarioAcostagem;
    }

    public void setHorarioAcostagem(String horarioAcostagem) {
        this.horarioAcostagem = horarioAcostagem;
    }

    public Boolean getAtiva() {
        return ativa;
    }

    public void setAtiva(Boolean ativa) {
        this.ativa = ativa;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Fabrica that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
