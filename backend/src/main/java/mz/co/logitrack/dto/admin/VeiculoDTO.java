package mz.co.logitrack.dto.admin;

import mz.co.logitrack.domain.enums.EstadoVeiculo;

/** Veículo completo devolvido ao módulo de administração. */
public record VeiculoDTO(Long id, String matricula, String modelo,
                         Double capacidadeCargaToneladas, EstadoVeiculo estado,
                         String estadoEtiqueta) {
}
