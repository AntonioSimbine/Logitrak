package mz.co.logitrack.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import mz.co.logitrack.domain.enums.EstadoCarregamento;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Carregamento registado na central. O campo {@code uuidLocal} é a chave de
 * idempotência enviada pela app móvel; duas sincronizações do mesmo UUID
 * produzem um único registo central.
 */
@Entity
@Table(name = "carregamentos",
        uniqueConstraints = @UniqueConstraint(name = "uk_carregamentos_uuid_local", columnNames = "uuid_local"),
        indexes = {
                @Index(name = "idx_carregamentos_estado", columnList = "estado"),
                @Index(name = "idx_carregamentos_data_inicio", columnList = "data_hora_inicio"),
                @Index(name = "idx_carregamentos_fabrica", columnList = "fabrica_id"),
                @Index(name = "idx_carregamentos_veiculo", columnList = "veiculo_id"),
                @Index(name = "idx_carregamentos_motorista", columnList = "motorista_id"),
                @Index(name = "idx_carregamentos_guia", columnList = "guia_numero"),
                @Index(name = "idx_carregamentos_estado_inicio", columnList = "estado,data_hora_inicio")
        })
public class Carregamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "uuid_local", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private UUID uuidLocal;

    @NotBlank
    @Size(max = 60)
    @Column(name = "guia_numero", nullable = false, length = 60)
    private String guiaNumero;

    @NotBlank
    @Size(max = 80)
    @Column(name = "tipo_carga", nullable = false, length = 80)
    private String tipoCarga;

    @NotNull
    @DecimalMin("0.01")
    @Column(name = "quantidade_toneladas", nullable = false)
    private Double quantidadeToneladas;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoCarregamento estado = EstadoCarregamento.REGISTADO;

    @Column(name = "data_hora_inicio")
    private LocalDateTime dataHoraInicio;

    @Column(name = "data_hora_fim")
    private LocalDateTime dataHoraFim;

    @Size(max = 500)
    @Column(name = "motivo_rejeicao", length = 500)
    private String motivoRejeicao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fabrica_id", foreignKey = @ForeignKey(name = "fk_carregamentos_fabrica"))
    private Fabrica fabrica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "veiculo_id", foreignKey = @ForeignKey(name = "fk_carregamentos_veiculo"))
    private Veiculo veiculo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "motorista_id", nullable = false, foreignKey = @ForeignKey(name = "fk_carregamentos_motorista"))
    private Utilizador motorista;

    @OneToOne(mappedBy = "carregamento", fetch = FetchType.LAZY)
    private DocumentoGuia documento;

    public Carregamento() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getUuidLocal() {
        return uuidLocal;
    }

    public void setUuidLocal(UUID uuidLocal) {
        this.uuidLocal = uuidLocal;
    }

    public String getGuiaNumero() {
        return guiaNumero;
    }

    public void setGuiaNumero(String guiaNumero) {
        this.guiaNumero = guiaNumero;
    }

    public String getTipoCarga() {
        return tipoCarga;
    }

    public void setTipoCarga(String tipoCarga) {
        this.tipoCarga = tipoCarga;
    }

    public Double getQuantidadeToneladas() {
        return quantidadeToneladas;
    }

    public void setQuantidadeToneladas(Double quantidadeToneladas) {
        this.quantidadeToneladas = quantidadeToneladas;
    }

    public EstadoCarregamento getEstado() {
        return estado;
    }

    public void setEstado(EstadoCarregamento estado) {
        this.estado = estado;
    }

    public LocalDateTime getDataHoraInicio() {
        return dataHoraInicio;
    }

    public void setDataHoraInicio(LocalDateTime dataHoraInicio) {
        this.dataHoraInicio = dataHoraInicio;
    }

    public LocalDateTime getDataHoraFim() {
        return dataHoraFim;
    }

    public void setDataHoraFim(LocalDateTime dataHoraFim) {
        this.dataHoraFim = dataHoraFim;
    }

    public String getMotivoRejeicao() {
        return motivoRejeicao;
    }

    public void setMotivoRejeicao(String motivoRejeicao) {
        this.motivoRejeicao = motivoRejeicao;
    }

    public Fabrica getFabrica() {
        return fabrica;
    }

    public void setFabrica(Fabrica fabrica) {
        this.fabrica = fabrica;
    }

    public Veiculo getVeiculo() {
        return veiculo;
    }

    public void setVeiculo(Veiculo veiculo) {
        this.veiculo = veiculo;
    }

    public Utilizador getMotorista() {
        return motorista;
    }

    public void setMotorista(Utilizador motorista) {
        this.motorista = motorista;
    }

    public DocumentoGuia getDocumento() {
        return documento;
    }

    public void setDocumento(DocumentoGuia documento) {
        this.documento = documento;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Carregamento that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
