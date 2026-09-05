package com.tfg.finanzas.inversion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * PrecioMercadoProvider contra el endpoint no oficial de graficos de Yahoo
 * Finance (sin API key, pero tampoco soportado oficialmente: puede cambiar de
 * forma o bloquear peticiones sin aviso, de ahi que cualquier fallo se trague
 * como "precio no disponible" en vez de romper el calculo de la cartera).
 */
@Slf4j
@Component
public class YahooFinancePrecioMercadoProvider implements PrecioMercadoProvider {

    private static final String URL = "https://query1.finance.yahoo.com/v8/finance/chart/{ticker}?interval=1d&range=1d";

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public YahooFinancePrecioMercadoProvider(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; finanzas-tfg/1.0)")
                .build();
    }

    @Override
    public Optional<BigDecimal> obtenerPrecioActual(String ticker) {
        try {
            String cuerpo = restClient.get()
                    .uri(URL, ticker)
                    .retrieve()
                    .body(String.class);

            JsonNode resultados = objectMapper.readTree(cuerpo).path("chart").path("result");
            if (!resultados.isArray() || resultados.isEmpty()) {
                return Optional.empty();
            }
            JsonNode meta = resultados.get(0).path("meta");
            if (!meta.hasNonNull("regularMarketPrice")) {
                return Optional.empty();
            }
            return Optional.of(BigDecimal.valueOf(meta.get("regularMarketPrice").asDouble()));
        } catch (Exception e) {
            log.warn("No se ha podido obtener el precio de mercado de '{}': {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }
}
