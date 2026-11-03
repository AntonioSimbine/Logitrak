package mz.co.logitrack.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Fotografia digitalizada da Guia de Transporte. O ficheiro fica fora da base
 * de dados; aqui guarda-se o caminho, o hash SHA-256 (integridade) e os
 * metadados de captura.
 */
@Entity
@Table(name = "documentos_guia",
        indexes = {
                @Index(name = "idx_documentos_carregamento", columnList = "carregamento_id"),
                @Index(name = "idx_documentos_captura", columnList = "data_hora_captura")
        })
public class DocumentoGuia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 500)
    @Column(name = "caminho_fotografia", nullable = false, length = 500)
    private String caminhoFotografia;

    @NotNull
    @Column(name = "data_hora_captura", nullable = false)
    private LocalDateTime dataHoraCaptura;

    @NotNull
    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    @Size(max = 64)
    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;

    @Size(max = 80)
    @Column(name = "mime_type", length = 80)
    private String mimeType;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carregamento_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_documentos_carregamento"))
    private Carregamento carregamento;

    public DocumentoGuia() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCaminhoFotografia() {
        return caminhoFotografia;
    }

    public void setCaminhoFotografia(String caminhoFotografia) {
        this.caminhoFotografia = caminhoFotografia;
    }

    public LocalDateTime getDataHoraCaptura() {
        return dataHoraCaptura;
    }

    public void setDataHoraCaptura(LocalDateTime dataHoraCaptura) {
        this.dataHoraCaptura = dataHoraCaptura;
    }

    public Long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(Long tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
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
        if (!(o instanceof DocumentoGuia that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
