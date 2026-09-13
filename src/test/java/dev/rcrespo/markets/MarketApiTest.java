package dev.rcrespo.markets;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Timeout(20)
class MarketApiTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean MarketStore store;
    @BeforeEach void reset() {
        jdbc.update("delete from trading_pairs where id not in ('BTC-USD','ETH-USD','ETH-BTC','EUR-USD','USD-JPY')");
        Map.of("BTC", "60000", "ETH", "3000", "SOL", "150", "USD", "1", "EUR", "1.1", "JPY", "0.007").forEach((symbol, price) ->
                jdbc.update("update market_assets set usd_reference = ?, version = 0 where symbol = ?", new BigDecimal(price), symbol));
        clearInvocations(store);
    }
    private JsonNode execute(String query) {
        var response = http.postForEntity("/graphql", Map.of("query", query), JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }
    private void error(JsonNode response, String code) {
        assertThat(response.at("/errors/0/extensions/code").asText()).isEqualTo(code);
    }
    @Test void batchesBothSidesOfTradingPairs() {
        var result = execute("{ pairs { id base { symbol } quote { symbol } referenceRate } }");
        assertThat(result.has("errors")).isFalse();
        assertThat(result.at("/data/pairs").size()).isEqualTo(5);
        assertThat(result.at("/data/pairs/0/referenceRate").asText()).isEqualTo("60000.000000000000");
        verify(store, times(1)).assetsBySymbols(Set.of("BTC", "ETH", "EUR", "USD", "JPY"));
    }
    @Test void selectingPairIdsDoesNotLoadAssets() {
        execute("{ pairs { id } }");
        verify(store, never()).assetsBySymbols(anySet());
    }
    @Test void convertsCrossAndIdentityRates() {
        var result = execute("{ cross: convert(from: \"ETH\", to: \"BTC\", amount: \"2\") { rate result } same: convert(from: \"EUR\", to: \"EUR\", amount: \"7.25\") { result } }");
        assertThat(result.at("/data/cross/rate").asText()).isEqualTo("0.050000000000");
        assertThat(result.at("/data/cross/result").asText()).isEqualTo("0.100000000000");
        assertThat(result.at("/data/same/result").asText()).isEqualTo("7.250000000000");
    }
    @Test void roundsFinalConversionWithoutDoubleRounding() {
        var result = execute("{ convert(from: \"USD\", to: \"JPY\", amount: \"700\") { result } }");
        assertThat(result.at("/data/convert/result").asText()).isEqualTo("100000.000000000000");
    }
    @Test void validatesDecimalStringsAndSymbols() {
        for (String amount : List.of("-1", "NaN", "1e4", "0.0000000000001", "1000000000000000"))
            error(execute("{ convert(from: \"BTC\", to: \"USD\", amount: \"" + amount + "\") { result } }"), "BAD_INPUT");
        error(execute("{ convert(from: \"UNKNOWN\", to: \"USD\", amount: \"1\") { result } }"), "NOT_FOUND");
    }
    @Test void indicesHaveNormalizedBaseLevelsAndNestedAssets() {
        var result = execute("{ indices { id level valueUsd constituents { asset { symbol } quantity } } }");
        assertThat(result.has("errors")).isFalse();
        assertThat(result.at("/data/indices/0/level").asText()).isEqualTo("100.00000000");
        assertThat(result.at("/data/indices/0/valueUsd").asText()).isEqualTo("1200.000000000000");
        assertThat(result.at("/data/indices/1/level").asText()).isEqualTo("100.00000000");
        verify(store, times(1)).constituentsByIndices(Set.of("CRYPTO-2", "FX-2"));
    }
    @Test void priceChangeRevaluesPairsAndIndices() {
        var mutation = execute("mutation { setReferencePrice(symbol: \"BTC\", usdReference: \"66000\", expectedVersion: 0) { version usdReference } }");
        assertThat(mutation.at("/data/setReferencePrice/version").asInt()).isEqualTo(1);
        var result = execute("{ indices { id level } convert(from: \"BTC\", to: \"USD\", amount: \"1\") { result } }");
        assertThat(result.at("/data/indices/0/level").asText()).isEqualTo("105.00000000");
        assertThat(result.at("/data/convert/result").asText()).isEqualTo("66000.000000000000");
    }
    @Test void protectsAnchorAndConflictingWrites() {
        error(execute("mutation { setReferencePrice(symbol: \"USD\", usdReference: \"2\", expectedVersion: 0) { symbol } }"), "BAD_INPUT");
        error(execute("mutation { setReferencePrice(symbol: \"BTC\", usdReference: \"0\", expectedVersion: 0) { symbol } }"), "BAD_INPUT");
        error(execute("mutation { setReferencePrice(symbol: \"BTC\", usdReference: \"60001\", expectedVersion: 5) { symbol } }"), "CONFLICT");
    }
    @Test void concurrentPriceUpdatesDoNotLoseChanges() {
        String query = "mutation { setReferencePrice(symbol: \"ETH\", usdReference: \"3300\", expectedVersion: 0) { version } }";
        var first = CompletableFuture.supplyAsync(() -> execute(query));
        var second = CompletableFuture.supplyAsync(() -> execute(query));
        var results = List.of(first.join(), second.join());
        assertThat(results.stream().filter(r -> !r.has("errors")).count()).isEqualTo(1);
        error(results.stream().filter(r -> r.has("errors")).findFirst().orElseThrow(), "CONFLICT");
    }
    @Test void createsPairsAndRejectsDuplicatesAndSelfPairs() {
        var result = execute("mutation { addPair(base: \"SOL\", quote: \"EUR\") { id referenceRate } }");
        assertThat(result.at("/data/addPair/id").asText()).isEqualTo("SOL-EUR");
        assertThat(jdbc.queryForObject("select count(*) from trading_pairs where id = 'SOL-EUR'", Integer.class)).isEqualTo(1);
        error(execute("mutation { addPair(base: \"SOL\", quote: \"EUR\") { id } }"), "PAIR_EXISTS");
        error(execute("mutation { addPair(base: \"USD\", quote: \"USD\") { id } }"), "BAD_INPUT");
    }
    @Test void mutationInvalidatesRequestLoaderCache() {
        var result = execute("mutation { before: addPair(base: \"SOL\", quote: \"EUR\") { base { usdReference } } update: setReferencePrice(symbol: \"SOL\", usdReference: \"165\", expectedVersion: 0) { version } after: addPair(base: \"SOL\", quote: \"JPY\") { base { usdReference } } }");
        assertThat(result.has("errors")).isFalse();
        assertThat(result.at("/data/before/base/usdReference").asText()).isEqualTo("150.000000000000");
        assertThat(result.at("/data/after/base/usdReference").asText()).isEqualTo("165.000000000000");
    }
    @Test void filtersAndBoundsPages() {
        assertThat(execute("{ assets(kind: CRYPTO, limit: 2) { symbol } }").at("/data/assets").size()).isEqualTo(2);
        error(execute("{ pairs(limit: 101) { id } }"), "BAD_INPUT");
        error(execute("{ assets(offset: -1) { symbol } }"), "BAD_INPUT");
    }
}
