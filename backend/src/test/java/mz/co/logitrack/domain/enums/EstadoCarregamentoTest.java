package mz.co.logitrack.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes das transições de estado do carregamento. */
class EstadoCarregamentoTest {

    @Test
    @DisplayName("REGISTADO só pode passar a EM_TRANSITO ou REJEITADO")
    void transicoesAPartirDeRegistado() {
        assertThat(EstadoCarregamento.REGISTADO.podeTransitarPara(EstadoCarregamento.EM_TRANSITO))
                .isTrue();
        assertThat(EstadoCarregamento.REGISTADO.podeTransitarPara(EstadoCarregamento.REJEITADO))
                .isTrue();
        assertThat(EstadoCarregamento.REGISTADO.podeTransitarPara(EstadoCarregamento.CONCLUIDO))
                .isFalse();
        assertThat(EstadoCarregamento.REGISTADO.podeTransitarPara(EstadoCarregamento.REGISTADO))
                .isFalse();
    }

    @Test
    @DisplayName("EM_TRANSITO pode atrasar ou concluir")
    void transicoesAPartirDeEmTransito() {
        assertThat(EstadoCarregamento.EM_TRANSITO.podeTransitarPara(EstadoCarregamento.ATRASADO))
                .isTrue();
        assertThat(EstadoCarregamento.EM_TRANSITO.podeTransitarPara(EstadoCarregamento.CONCLUIDO))
                .isTrue();
        assertThat(EstadoCarregamento.EM_TRANSITO.podeTransitarPara(EstadoCarregamento.REJEITADO))
                .isTrue();
    }

    @Test
    @DisplayName("ATRASADO recupera para CONCLUIDO ou REJEITADO")
    void transicoesAPartirDeAtrasado() {
        assertThat(EstadoCarregamento.ATRASADO.podeTransitarPara(EstadoCarregamento.CONCLUIDO))
                .isTrue();
        assertThat(EstadoCarregamento.ATRASADO.podeTransitarPara(EstadoCarregamento.EM_TRANSITO))
                .isFalse();
    }

    @Test
    @DisplayName("Estados finais não transitam")
    void estadosFinaisSaoImutaveis() {
        assertThat(EstadoCarregamento.CONCLUIDO.isFinal()).isTrue();
        assertThat(EstadoCarregamento.REJEITADO.isFinal()).isTrue();
        assertThat(EstadoCarregamento.CONCLUIDO.podeTransitarPara(EstadoCarregamento.EM_TRANSITO))
                .isFalse();
        assertThat(EstadoCarregamento.REJEITADO.podeTransitarPara(EstadoCarregamento.REGISTADO))
                .isFalse();
    }

    @Test
    @DisplayName("Estados exclusivos do dispositivo móvel existem")
    void estadosLocais() {
        assertThat(EstadoCarregamento.GUARDADO_LOCAL.isFinal()).isFalse();
        assertThat(EstadoCarregamento.PENDENTE_SINCRONIZACAO.isFinal()).isFalse();
        assertThat(EstadoCarregamento.GUARDADO_LOCAL.podeTransitarPara(
                EstadoCarregamento.REGISTADO)).isTrue();
    }
}
