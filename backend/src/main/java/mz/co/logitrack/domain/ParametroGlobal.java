package mz.co.logitrack.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Objects;

/** Parâmetro global configurável em tempo de execução pelo Administrador. */
@Entity
@Table(name = "parametros_globais",
        uniqueConstraints = @UniqueConstraint(name = "uk_parametros_chave", columnNames = "chave"))
public class ParametroGlobal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 80)
    @Column(name = "chave", nullable = false, unique = true, length = 80)
    private String chave;

    @NotBlank
    @Size(max = 255)
    @Column(name = "valor", nullable = false, length = 255)
    private String valor;

    @Size(max = 500)
    @Column(name = "descricao", length = 500)
    private String descricao;

    public ParametroGlobal() {
    }

    public ParametroGlobal(String chave, String valor, String descricao) {
        this.chave = chave;
        this.valor = valor;
        this.descricao = descricao;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ParametroGlobal that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
