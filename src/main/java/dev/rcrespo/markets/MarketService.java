package dev.rcrespo.markets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.rcrespo.markets.Domain.*;

@Service
public class MarketService {
    private final MarketStore store;
    public MarketService(MarketStore store) { this.store = store; }
    public static void page(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0) throw new BusinessException("BAD_INPUT", "limit must be 1..100 and offset must be non-negative");
    }
    public static BigDecimal decimal(String value, boolean positive) {
        if (value == null || !value.matches("[0-9]{1,15}(\\.[0-9]{1,12})?")) throw new BusinessException("BAD_INPUT", "Use a decimal string with at most 15 integer and 12 fractional digits");
        BigDecimal number = new BigDecimal(value);
        if (positive && number.signum() == 0) throw new BusinessException("BAD_INPUT", "Reference price must be positive");
        return number;
    }
    public static String format(BigDecimal value, int scale) { return value.setScale(scale, RoundingMode.HALF_EVEN).toPlainString(); }
    public static String rate(Asset base, Asset quote) { return format(base.usdReference().divide(quote.usdReference(), 12, RoundingMode.HALF_EVEN), 12); }

    public Conversion convert(String from, String to, String amount) {
        BigDecimal value = decimal(amount, false);
        var assets = store.assetsBySymbols(new HashSet<>(List.of(from, to)));
        if (!assets.containsKey(from) || !assets.containsKey(to)) throw new BusinessException("NOT_FOUND", "Unknown currency symbol");
        BigDecimal base = assets.get(from).usdReference();
        BigDecimal quote = assets.get(to).usdReference();
        // Round the final conversion once, rather than multiplying by a rounded display rate.
        return new Conversion(from, to, format(value, 12), rate(assets.get(from), assets.get(to)),
                value.multiply(base).divide(quote, 12, RoundingMode.HALF_EVEN).toPlainString());
    }

    @Transactional
    public TradingPair addPair(String base, String quote) {
        if (base.equals(quote)) throw new BusinessException("BAD_INPUT", "A trading pair needs two different assets");
        if (store.assetsBySymbols(Set.of(base, quote)).size() != 2) throw new BusinessException("NOT_FOUND", "Unknown currency symbol");
        var pair = new TradingPair(base + "-" + quote, base, quote);
        try { store.insertPair(pair); }
        catch (DuplicateKeyException exception) { throw new BusinessException("PAIR_EXISTS", "Trading pair already exists"); }
        return pair;
    }

    @Transactional
    public Asset updateReference(String symbol, String value, int expectedVersion) {
        BigDecimal price = decimal(value, true);
        if (expectedVersion < 0) throw new BusinessException("BAD_INPUT", "Version must be non-negative");
        if (!store.assetsBySymbols(Set.of(symbol)).containsKey(symbol)) throw new BusinessException("NOT_FOUND", "Unknown currency symbol");
        if (symbol.equals("USD")) throw new BusinessException("BAD_INPUT", "USD is the fixed reference unit");
        if (!store.updateReference(symbol, price, expectedVersion)) throw new BusinessException("CONFLICT", "Reference changed; reload the version and retry");
        return store.assetsBySymbols(Set.of(symbol)).get(symbol);
    }
}
