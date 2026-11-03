package mz.co.logitrack.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Objects;

/** Alerta emitido pelo motor de SLA quando um carregamento excede o limite. */
@Entity
@Table(name = "alertas_atraso",
        indexes = {
                @Index(name = "idx_alertas_carregamento", columnList = "carregamento_id"),
                @Index(name = "idx_alertas_lido", columnList = "lido"),
                @Index(name = "idx_alertas_emissao", columnList = "data_hora_emissao")
        })
public class AlertaAtraso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "data_hora_emissao", nullable = false)
    private LocalDateTime dataHoraEmissao;

    @NotNull
    @Column(name = "minutos_excedidos", nullable = false)
    private Integer minutosExcedidos;

    @NotBlank
    @Size(max = 500)
    @Column(name = "mensagem", nullable = false, length = 500)
    private String mensagem;

    @NotNull
    @Column(name = "lido", nullable = false)
    private Boolean lido = Boolean.FALSE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carregamento_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_alertas_carregamento"))
    private Carregamento carregamento;

    public AlertaAtraso() {
    }

    public AlertaAtraso(LocalDateTime dataHoraEmissao, Integer minutosExcedidos,
                        String mensagem, Carregamento carregamento) {
        this.dataHoraEmissao = dataHoraEmissao;
        this.minutosExcedidos = minutosExcedidos;
        this.mensagem = mensagem;
        this.carregamento = carregamento;
        this.lido = Boolean.FALSE;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getDataHoraEmissao() {
        return dataHoraEmissao;
    }

    public void setDataHoraEmissao(LocalDateTime dataHoraEmissao) {
        this.dataHoraEmissao = dataHoraEmissao;
    }

    public Integer getMinutosExcedidos() {
        return minutosExcedidos;
    }

    public void setMinutosExcedidos(Integer minutosExcedidos) {
        this.minutosExcedidos = minutosExcedidos;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public Boolean getLido() {
        return lido;
    }

    public void setLido(Boolean lido) {
        this.lido = lido;
    }

    public Carregamento getCarregamento() {
        return carregamento;
    }

    public void setCarregamento(Carregamento carregamento) {
        this.carregamento = carregamento;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AlertaAtraso that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
