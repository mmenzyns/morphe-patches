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
}

