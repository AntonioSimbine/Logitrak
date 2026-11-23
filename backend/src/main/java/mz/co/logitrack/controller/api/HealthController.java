package mz.co.logitrack.controller.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ponto de entrada informativo da API. O frontend é servido separadamente
 * (por exemplo em http://localhost:3000).
 */
@RestController
public class HealthController {

    @GetMapping({"/", "/api/v1/health"})
    public Map<String, Object> estado() {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("aplicacao", "logitrack-api");
        dados.put("estado", "UP");
        dados.put("versao", "1.0.0");
        dados.put("api", "/api/v1");
        dados.put("dataHora", OffsetDateTime.now().toString());
        return dados;
    }
}
