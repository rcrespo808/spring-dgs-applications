package dev.rcrespo.markets;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import static dev.rcrespo.markets.Domain.BusinessException;

@Component
public class BinanceClient {
    public record ExchangePrice(String symbol, String price, String change24h, String quoteAsset, String observedAt, String source) {}
    private static final URI ENDPOINT = URI.create("https://data-api.binance.vision/api/v3/ticker/24hr?symbols=%5B%22BTCUSDT%22,%22ETHUSDT%22,%22SOLUSDT%22%5D");
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Clock clock;
    private List<ExchangePrice> cached = List.of();
    private Instant expires = Instant.MIN;
    @Autowired
    public BinanceClient(ObjectMapper mapper) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), Clock.systemUTC());
    }
    BinanceClient(ObjectMapper mapper, HttpClient http, Clock clock) { this.mapper = mapper; this.http = http; this.clock = clock; }

    public synchronized List<ExchangePrice> prices() {
        if (clock.instant().isBefore(expires)) return cached;
        try {
            var response = http.send(HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Binance HTTP " + response.statusCode());
            var records = mapper.readTree(response.body());
            List<ExchangePrice> prices = new ArrayList<>();
            if (!records.isArray()) throw new IllegalStateException("Invalid market response");
            for (var record : records) {
                String price = record.path("lastPrice").asText();
                String change = record.path("priceChangePercent").asText();
                if (new BigDecimal(price).signum() <= 0) throw new IllegalStateException("Invalid price");
                new BigDecimal(change);
                prices.add(new ExchangePrice(record.path("symbol").asText(), price, change, "USDT", clock.instant().toString(), "BINANCE_REST"));
            }
            if (prices.size() != 3 || !prices.stream().map(ExchangePrice::symbol).collect(java.util.stream.Collectors.toSet()).equals(Set.of("BTCUSDT", "ETHUSDT", "SOLUSDT"))) throw new IllegalStateException("Incomplete market response");
            cached = List.copyOf(prices);
            expires = clock.instant().plusSeconds(5);
            return cached;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("FEED_UNAVAILABLE", "Binance data unavailable; retry shortly");
        } catch (Exception exception) {
            throw new BusinessException("FEED_UNAVAILABLE", "Binance data unavailable; retry shortly");
        }
    }
}
