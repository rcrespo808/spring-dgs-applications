package dev.rcrespo.markets;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BinanceClientTest {
    @Test void cachesFreshQuotesAndRejectsExpiredDataOnFailure() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-13T00:00:00Z"));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("[{\"symbol\":\"BTCUSDT\",\"lastPrice\":\"60000\",\"priceChangePercent\":\"1\"},{\"symbol\":\"ETHUSDT\",\"lastPrice\":\"3000\",\"priceChangePercent\":\"-2\"},{\"symbol\":\"SOLUSDT\",\"lastPrice\":\"150\",\"priceChangePercent\":\"0\"}]");
        var client = new BinanceClient(new ObjectMapper(), http, clock);
        assertThat(client.prices().getFirst().quoteAsset()).isEqualTo("USDT");
        assertThat(client.prices()).hasSize(3);
        verify(http, times(1)).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        when(clock.instant()).thenReturn(Instant.parse("2026-09-13T00:00:06Z"));
        when(response.statusCode()).thenReturn(429);
        assertThatThrownBy(client::prices).isInstanceOfSatisfying(Domain.BusinessException.class, e -> assertThat(e.code()).isEqualTo("FEED_UNAVAILABLE"));
    }
    @Test void rejectsIncompletePayloads() throws Exception {
        HttpClient http = mock(HttpClient.class);
        @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("[]");
        var client = new BinanceClient(new ObjectMapper(), http, Clock.systemUTC());
        assertThatThrownBy(client::prices).isInstanceOf(Domain.BusinessException.class);
    }
}
