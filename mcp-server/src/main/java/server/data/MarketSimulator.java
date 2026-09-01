package server.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 行情模拟器：对股票池做真实感的随机游走行情
 *
 * - 每只股票维护当前价（基于基础价随机游走，±0.8%/步）
 * - getQuote（流式）逐秒取 tick；getStock（同步快照）取当前快照 —— 同源数据
 * - 涨跌幅相对昨收（basePrice * 0.99），成交量随机
 *
 * author Hao
 * date 2026/8/19
 */
public final class MarketSimulator {

    /** code -> 当前价 */
    private static final Map<String, AtomicReference<Double>> CURRENT_PRICE = new ConcurrentHashMap<>();

    /** 取当前行情快照（同步 getStock） */
    public static Map<String, Object> snapshot(String code) {
        Map<String, Object> stock = MockDataStore.STOCKS.get(code);
        if (stock == null) {
            throw new IllegalArgumentException("unknown stock: " + code);
        }
        double base = (double) stock.get("basePrice");
        double prevClose = base * 0.99;
        double price = currentPrice(code, base);

        return Map.of(
                "code", code,
                "name", stock.get("name"),
                "industry", stock.get("industry"),
                "price", round(price),
                "prevClose", round(prevClose),
                "changePercent", round((price - prevClose) / prevClose * 100),
                "high", round(Math.max(prevClose, price) * 1.01),
                "low", round(Math.min(prevClose, price) * 0.99),
                "volume", ThreadLocalRandom.current().nextInt(50_000, 5_000_000)
        );
    }

    /** 取下一个行情 tick（流式 getQuote，随机游走一步） */
    public static Map<String, Object> nextTick(String code) {
        Map<String, Object> stock = MockDataStore.STOCKS.get(code);
        if (stock == null) {
            throw new IllegalArgumentException("unknown stock: " + code);
        }
        double base = (double) stock.get("basePrice");
        double price = currentPrice(code, base);
        double prevClose = base * 0.99;

        return Map.of(
                "code", code,
                "name", stock.get("name"),
                "price", round(price),
                "changePercent", round((price - prevClose) / prevClose * 100),
                "volume", ThreadLocalRandom.current().nextInt(1_000, 500_000)
        );
    }

    /** 随机游走当前价（并发安全） */
    private static double currentPrice(String code, double base) {
        return CURRENT_PRICE.computeIfAbsent(code, k -> new AtomicReference<>(base))
                .updateAndGet(prev -> prev * (1 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.016));
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private MarketSimulator() {
    }
}
