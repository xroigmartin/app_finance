package com.xroig.finance.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI financeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Mis Finanzas API")
                .description("""
                        API REST de la aplicación de finanzas personales: cuentas, categorías, movimientos \
                        (transacciones y transferencias), presupuestos, reglas de categorización, \
                        importación de extractos bancarios (CSV/XLS/XLSX), inversiones (carteras, \
                        valores, operaciones e importación de informes Flex de Interactive Brokers) y \
                        el dashboard de reporting. Los errores de dominio se devuelven como \
                        `application/problem+json` (404 no encontrado, 409 conflicto, 400 validación).""")
                .version("v1"));
    }
}
