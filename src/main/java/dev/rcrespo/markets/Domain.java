package dev.rcrespo.markets;

import java.math.BigDecimal;

public final class Domain {
    private Domain() {}
    public enum AssetKind { CRYPTO, FIAT }
    public record Asset(String symbol, String name, AssetKind kind, BigDecimal usdReference, int version) {}
    public record TradingPair(String id, String baseSymbol, String quoteSymbol) {}
    public record MarketIndex(String id, String name, BigDecimal divisor, BigDecimal valueUsd) {}
    public record Constituent(String symbol, BigDecimal quantity) {}
    public record Conversion(String from, String to, String amount, String rate, String result) {}
    public static final class BusinessException extends RuntimeException {
        private final String code;
        public BusinessException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
}
