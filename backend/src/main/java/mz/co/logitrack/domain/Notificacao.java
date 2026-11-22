package mz.co.logitrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import mz.co.logitrack.domain.enums.OrigemNotificacao;
import mz.co.logitrack.domain.enums.TipoNotificacao;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Notificação destinada a um utilizador específico. A autoria é sempre
 * preenchida pelo servidor a partir da autenticação; o cliente não envia
 * remetente.
 */
@Entity
@Table(name = "notificacoes", indexes = {
        @Index(name = "idx_notificacoes_destinatario_lido",
                columnList = "destinatario_id,lido"),
        @Index(name = "idx_notificacoes_criacao", columnList = "data_hora_criacao"),
        @Index(name = "idx_notificacoes_carregamento", columnList = "carregamento_id")
})
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "destinatario_id", nullable = false)
    private Long destinatarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private TipoNotificacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemNotificacao origem = OrigemNotificacao.DESCONHECIDO;

    @Column(name = "autor_id")
    private Long autorId;

    @Column(name = "autor_nome", length = 120)
    private String autorNome;

    @Column(name = "autor_perfil", length = 30)
    private String autorPerfil;

    @Column(name = "acao_tipo", length = 60)
    private String acaoTipo;

    @Column(name = "data_hora_criacao", nullable = false)
    private LocalDateTime dataHoraCriacao = LocalDateTime.now();

    @Column(name = "carregamento_id")
    private Long carregamentoId;

    @Column(name = "guia_numero", length = 60)
    private String guiaNumero;

    @Column(name = "titulo", length = 120)
    private String titulo;

    @Column(name = "mensagem", length = 500)
    private String mensagem;

    @Column(name = "lido", nullable = false)
    private Boolean lido = Boolean.FALSE;

    @Column(name = "data_hora_leitura")
    private LocalDateTime dataHoraLeitura;

    public Notificacao() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDestinatarioId() {
        return destinatarioId;
    }

    public void setDestinatarioId(Long destinatarioId) {
        this.destinatarioId = destinatarioId;
    }

    public TipoNotificacao getTipo() {
        return tipo;
    }

    public void setTipo(TipoNotificacao tipo) {
        this.tipo = tipo;
    }

    public OrigemNotificacao getOrigem() {
        return origem;
    }

    public void setOrigem(OrigemNotificacao origem) {
        this.origem = origem;
    }

    public Long getAutorId() {
        return autorId;
    }

    public void setAutorId(Long autorId) {
        this.autorId = autorId;
    }

    public String getAutorNome() {
        return autorNome;
    }

    public void setAutorNome(String autorNome) {
        this.autorNome = autorNome;
    }

    public String getAutorPerfil() {
        return autorPerfil;
    }

    public void setAutorPerfil(String autorPerfil) {
        this.autorPerfil = autorPerfil;
    }

    public String getAcaoTipo() {
        return acaoTipo;
    }

    public void setAcaoTipo(String acaoTipo) {
        this.acaoTipo = acaoTipo;
    }

    public LocalDateTime getDataHoraCriacao() {
        return dataHoraCriacao;
    }

    public void setDataHoraCriacao(LocalDateTime dataHoraCriacao) {
        this.dataHoraCriacao = dataHoraCriacao;
    }

    public Long getCarregamentoId() {
        return carregamentoId;
    }

    public void setCarregamentoId(Long carregamentoId) {
        this.carregamentoId = carregamentoId;
    }

    public String getGuiaNumero() {
        return guiaNumero;
    }

    public void setGuiaNumero(String guiaNumero) {
        this.guiaNumero = guiaNumero;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
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

    public LocalDateTime getDataHoraLeitura() {
        return dataHoraLeitura;
    }

    public void setDataHoraLeitura(LocalDateTime dataHoraLeitura) {
        this.dataHoraLeitura = dataHoraLeitura;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notificacao that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
