package app.template.extension.alza;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PriceHistoryData implements Serializable {
    public static class PricePoint implements Serializable {
        public final long timestamp;
        public final double price;

        public PricePoint(long timestamp, double price) {
            this.timestamp = timestamp;
            this.price = price;
        }
    }

    private final String commodityId;
    private final String productName;
    private final String identifier; // EAN or Part Number
    private final Double currentPrice;
    private final Double commonPrice; // Omnibus 30-day reference price before discount
    private final Double minPrice;
    private final Double maxPrice;
    private final Double realDiscount;
    private final Double claimedDiscount;
    private final List<PricePoint> currentPricePoints;
    private final List<PricePoint> originalPricePoints;

    public PriceHistoryData(
            String commodityId,
            String productName,
            String identifier,
            Double currentPrice,
            Double commonPrice,
            Double minPrice,
            Double maxPrice,
            Double realDiscount,
            Double claimedDiscount,
            List<PricePoint> currentPricePoints,
            List<PricePoint> originalPricePoints
    ) {
        this.commodityId = commodityId;
        this.productName = productName;
        this.identifier = identifier;
        this.currentPrice = currentPrice;
        this.commonPrice = commonPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.realDiscount = realDiscount;
        this.claimedDiscount = claimedDiscount;
        this.currentPricePoints = currentPricePoints != null ? currentPricePoints : new ArrayList<PricePoint>();
        this.originalPricePoints = originalPricePoints != null ? originalPricePoints : new ArrayList<PricePoint>();
    }

    public String getCommodityId() {
        return commodityId;
    }

    public String getProductName() {
        return productName;
    }

    public String getIdentifier() {
        return identifier;
    }

    public Double getCurrentPrice() {
        return currentPrice;
    }

    public Double getCommonPrice() {
        return commonPrice;
    }

    public Double getMinPrice() {
        return minPrice;
    }

    public Double getMaxPrice() {
        return maxPrice;
    }

    public Double getRealDiscount() {
        return realDiscount;
    }

    public Double getClaimedDiscount() {
        return claimedDiscount;
    }

    public List<PricePoint> getCurrentPricePoints() {
        return Collections.unmodifiableList(currentPricePoints);
    }

    public List<PricePoint> getOriginalPricePoints() {
        return Collections.unmodifiableList(originalPricePoints);
    }

    public boolean hasData() {
        return !currentPricePoints.isEmpty();
    }

    /**
     * Calculates the real 30-day reference price before the current price level started
     * (in accordance with the EU Omnibus directive / Hlídač shopů methodology).
     */
    public Double getEffectiveReferencePrice() {
        if (commonPrice != null && commonPrice > 0) {
            return commonPrice;
        }
        return calculate30DayReferencePrice(currentPricePoints, currentPrice);
    }

    /**
     * Returns the real discount percentage calculated against the 30-day lowest price
     * before the current discount period.
     */
    public Double getEffectiveRealDiscount() {
        if (realDiscount != null && realDiscount > 0) {
            return realDiscount;
        }
        Double ref = getEffectiveReferencePrice();
        if (ref != null && currentPrice != null && ref > currentPrice) {
            return Math.round(((ref - currentPrice) / ref) * 1000.0) / 10.0;
        }
        return null;
    }

    private static Double calculate30DayReferencePrice(List<PricePoint> points, Double curPrice) {
        if (points == null || points.size() < 2) return null;
        if (curPrice == null) {
            curPrice = points.get(points.size() - 1).price;
        }

        // Find when the current price drop / level started
        int n = points.size();
        int idx = n - 1;
        while (idx >= 0 && Math.abs(points.get(idx).price - curPrice) < 0.01) {
            idx--;
        }

        if (idx < 0) {
            // Price has always been constant
            return null;
        }

        // Progressive discounting: if the discount was introduced in multiple steps,
        // walk back through consecutive decreases to find when the discounting campaign began
        while (idx > 0 && points.get(idx).price < points.get(idx - 1).price) {
            idx--;
        }

        // Timestamp of when price first dropped below standard level
        long changeTimestamp = points.get(idx + 1).timestamp;
        long thirtyDaysBefore = changeTimestamp - (30L * 24 * 60 * 60 * 1000L);

        double minBefore = Double.MAX_VALUE;
        boolean found = false;

        for (int i = idx; i >= 0; i--) {
            PricePoint pt = points.get(i);
            if (pt.price < minBefore) {
                minBefore = pt.price;
            }
            found = true;
            if (pt.timestamp < thirtyDaysBefore) {
                // Included the price that was active when entering the 30-day window
                break;
            }
        }

        return (found && minBefore != Double.MAX_VALUE) ? minBefore : null;
    }
}
