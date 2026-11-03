package mz.co.logitrack;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LogitrackApplicationTests {

    @Test
    void contextLoads() {
        // Garante que todos os beans, entidades, repositórios e rotas arrancam
        // corretamente com a configuração de teste (H2 em modo MySQL).
    }
}
