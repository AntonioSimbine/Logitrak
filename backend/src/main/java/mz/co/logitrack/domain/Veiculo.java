package mz.co.logitrack.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import mz.co.logitrack.domain.enums.EstadoVeiculo;

import java.util.Objects;

/** Veículo da frota de transporte. */
@Entity
@Table(name = "veiculos",
        uniqueConstraints = @UniqueConstraint(name = "uk_veiculos_matricula", columnNames = "matricula"),
        indexes = @Index(name = "idx_veiculos_estado", columnList = "estado"))
public class Veiculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 20)
    @Column(name = "matricula", nullable = false, unique = true, length = 20)
    private String matricula;

    @NotBlank
    @Size(max = 120)
    @Column(name = "modelo", nullable = false, length = 120)
    private String modelo;

    @NotNull
    @DecimalMin("0.1")
    @Column(name = "capacidade_carga_toneladas", nullable = false)
    private Double capacidadeCargaToneladas;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoVeiculo estado = EstadoVeiculo.DISPONIVEL;

    public Veiculo() {
    }

    public Veiculo(String matricula, String modelo, Double capacidadeCargaToneladas, EstadoVeiculo estado) {
        this.matricula = matricula;
        this.modelo = modelo;
        this.capacidadeCargaToneladas = capacidadeCargaToneladas;
        this.estado = estado;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public String getModelo() {
        return modelo;
    }

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public Double getCapacidadeCargaToneladas() {
        return capacidadeCargaToneladas;
    }

    public void setCapacidadeCargaToneladas(Double capacidadeCargaToneladas) {
        this.capacidadeCargaToneladas = capacidadeCargaToneladas;
    }

    public EstadoVeiculo getEstado() {
        return estado;
    }

    public void setEstado(EstadoVeiculo estado) {
        this.estado = estado;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Veiculo veiculo)) {
            return false;
        }
        return id != null && Objects.equals(id, veiculo.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
