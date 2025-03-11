package me.pacphi.ai.resos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.data.jdbc.AutoConfigureDataJdbc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles(profiles = { "dev" })
@AutoConfigureDataJdbc
public class SpringAiResOsBackendApplicationTests {

    @Test
    void contextLoads() {}
}
