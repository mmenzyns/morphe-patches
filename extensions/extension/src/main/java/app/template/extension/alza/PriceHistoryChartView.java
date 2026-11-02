package app.template.extension.alza;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PriceHistoryChartView extends View {
    public enum State {
        LOADING,
        LOADED,
        EMPTY,
        ERROR,
        UNSUPPORTED_COUNTRY
    }

    private State state = State.LOADING;
    private PriceHistoryData data;
    private String commodityId;
    private boolean isCollapsed = false;

    // Paints
    private Paint cardBgPaint;
    private Paint cardBorderPaint;
    private Paint gridLinePaint;
    private Paint titlePaint;
    private Paint subtitlePaint;
    private Paint labelPaint;
    private Paint discountLabelPaint;
    private Paint discountValuePaint;
    private Paint currentCurvePaint;
    private Paint currentAreaPaint;
    private Paint originalCurvePaint;
    private Paint toggleTextPaint;
    private Paint gearTextPaint;
    private Paint badgePaint;
    private Paint badgeBgPaint;

    private Path currentCurvePath = new Path();
    private Path currentAreaPath = new Path();
    private Path originalCurvePath = new Path();
    private RectF cardRect = new RectF();
    private RectF badgeRect = new RectF();

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/yy", Locale.getDefault());
    private final DecimalFormat priceFormat;
    private final DecimalFormat euroFormat;
    private final DecimalFormat euroFormatRound;

    private float paddingPx;
    private float cornerRadiusPx;

    public PriceHistoryChartView(Context context) {
        super(context);
        priceFormat = initPriceFormat(0);
        euroFormat = initPriceFormat(1);
        euroFormatRound = initPriceFormat(2);
        init();
    }

    public PriceHistoryChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        priceFormat = initPriceFormat(0);
        euroFormat = initPriceFormat(1);
        euroFormatRound = initPriceFormat(2);
        init();
    }

    private DecimalFormat initPriceFormat(int type) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        if (type == 1) {
            return new DecimalFormat("#,##0.00", symbols);
        } else if (type == 2) {
            return new DecimalFormat("#,##0", symbols);
        }
        return new DecimalFormat("#,###", symbols);
    }

    private void init() {
        paddingPx = dpToPx(12f);
        cornerRadiusPx = dpToPx(12f);

        cardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBorderPaint.setStyle(Paint.Style.STROKE);
        cardBorderPaint.setStrokeWidth(dpToPx(1f));

        gridLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridLinePaint.setStyle(Paint.Style.STROKE);
        gridLinePaint.setStrokeWidth(dpToPx(1f));

        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(dpToPx(12.5f));
        titlePaint.setFakeBoldText(true);

        subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subtitlePaint.setTextSize(dpToPx(10f));

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setTextSize(dpToPx(9.5f));

        discountLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        discountLabelPaint.setTextSize(dpToPx(12f));

        discountValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        discountValuePaint.setTextSize(dpToPx(13.5f));
        discountValuePaint.setFakeBoldText(true);

        toggleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        toggleTextPaint.setTextSize(dpToPx(11f));
        toggleTextPaint.setFakeBoldText(true);

        gearTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gearTextPaint.setTextSize(dpToPx(13f));

        badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgePaint.setTextSize(dpToPx(8.5f));
        badgePaint.setFakeBoldText(true);

        badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeBgPaint.setStyle(Paint.Style.FILL);

        currentCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        currentCurvePaint.setStyle(Paint.Style.STROKE);
        currentCurvePaint.setStrokeWidth(dpToPx(2.5f));
        currentCurvePaint.setColor(Color.parseColor("#00A651")); // Hlídač shopů green

        currentAreaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        currentAreaPaint.setStyle(Paint.Style.FILL);

        originalCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        originalCurvePaint.setStyle(Paint.Style.STROKE);
        originalCurvePaint.setStrokeWidth(dpToPx(1.5f));
        originalCurvePaint.setColor(Color.parseColor("#EF4444")); // Red strikethrough price
        originalCurvePaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(4f), dpToPx(4f)}, 0));
    }

    public void setCommodityId(String commodityId) {
        this.commodityId = commodityId;
    }

    public String getCommodityId() {
        return commodityId;
    }

    public void setData(PriceHistoryData data) {
        this.data = data;
        if (data != null && data.hasData()) {
            this.state = State.LOADED;
            if (data.getCommodityId() != null) {
                this.commodityId = data.getCommodityId();
            }
        } else {
            this.state = State.EMPTY;
        }
        invalidate();
        postInvalidate();
    }

    public PriceHistoryData getData() {
        return data;
    }

    public void setState(State state) {
        this.state = state;
        invalidate();
        postInvalidate();
    }

    public State getState() {
        return state;
    }

    public void setCollapsed(boolean collapsed) {
        if (this.isCollapsed != collapsed) {
            this.isCollapsed = collapsed;
            requestLayout();
            invalidate();
        }
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            int width = getWidth();

            // Rightmost header buttons:
            // [width - 45dp .. width]: Toggle ("▼ Sbalit" / "▲ Graf")
            // [width - 80dp .. width - 45dp]: Settings Gear ("⚙")
            float toggleLeft = width - paddingPx - dpToPx(45f);
            float gearLeft = width - paddingPx - dpToPx(80f);

            if (isCollapsed) {
                // When collapsed, check if gear clicked
                if (x >= gearLeft && x < toggleLeft) {
                    openSettingsDialog();
                    return true;
                }
                // Otherwise tapping collapsed bar expands it
                isCollapsed = false;
                requestLayout();
                invalidate();
                return true;
            } else {
                // When expanded:
                if (y <= dpToPx(42f)) {
                    if (x >= toggleLeft) {
                        isCollapsed = true;
                        requestLayout();
                        invalidate();
                        return true;
                    }
                    if (x >= gearLeft && x < toggleLeft) {
                        openSettingsDialog();
                        return true;
                    }
                    openHlidacWeb();
                    return true;
                }
                // Tapping chart plot area opens web
                openHlidacWeb();
                return true;
            }
        }
        return true;
    }

    private void openSettingsDialog() {
        MorpheSettingsDialog.show(getContext(), new Runnable() {
            @Override
            public void run() {
                requestLayout();
                invalidate();
            }
        });
    }

    private void openHlidacWeb() {
        if (commodityId == null || commodityId.isEmpty()) return;
        try {
            String domain = AlzaPriceChartInjector.getDetectedDomain();
            if (domain == null || domain.isEmpty()) domain = "alza.cz";
            String url = "https://www.hlidacshopu.cz/app/?url=https://www." + domain + "/a-d" + commodityId + ".htm";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width <= 0) {
            try {
                width = getResources().getDisplayMetrics().widthPixels;
            } catch (Throwable ignored) {
                width = 1080;
            }
        }
        boolean compact = AlzaPriceChartInjector.isCompactMode();
        int expandedHeightDp = compact ? 135 : 210;
        int targetHeight = isCollapsed ? (int) dpToPx(38f) : (int) dpToPx(expandedHeightDp);
        setMeasuredDimension(width, targetHeight);
    }

    private boolean isDarkMode() {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private String formatPrice(double price) {
        String currency = AlzaPriceChartInjector.getDetectedCurrency();
        if ("€".equals(currency)) {
            if (price == Math.floor(price)) {
                return euroFormatRound.format(price) + " €";
            }
            return euroFormat.format(price) + " €";
        }
        return priceFormat.format(price) + " " + currency;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean dark = isDarkMode();
        int width = getWidth();
        int height = getHeight();

        // Theme colors
        int bgColor = dark ? Color.parseColor("#181824") : Color.parseColor("#FFFFFF");
        int borderColor = dark ? Color.parseColor("#2E2E3E") : Color.parseColor("#E5E7EB");
        int textNormalColor = dark ? Color.parseColor("#E5E7EB") : Color.parseColor("#1F2937");
        int subtitleColor = dark ? Color.parseColor("#9CA3AF") : Color.parseColor("#6B7280");
        int gridColor = dark ? Color.parseColor("#252538") : Color.parseColor("#F3F4F6");
        int labelColor = dark ? Color.parseColor("#9CA3AF") : Color.parseColor("#6B7280");
        int toggleColor = dark ? Color.parseColor("#00A651") : Color.parseColor("#008A43");

        cardBgPaint.setColor(bgColor);
        cardBorderPaint.setColor(borderColor);
        gridLinePaint.setColor(gridColor);
        titlePaint.setColor(textNormalColor);
        subtitlePaint.setColor(subtitleColor);
        labelPaint.setColor(labelColor);
        discountLabelPaint.setColor(textNormalColor);
        toggleTextPaint.setColor(toggleColor);
        gearTextPaint.setColor(subtitleColor);

        // Draw card background & border
        cardRect.set(paddingPx / 2, paddingPx / 2, width - (paddingPx / 2), height - (paddingPx / 2));
        canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, cardBgPaint);
        canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, cardBorderPaint);

        if (isCollapsed) {
            drawCollapsedState(canvas, width, height, dark);
        } else {
            drawExpandedState(canvas, width, height, dark);
        }
    }

    private void drawCollapsedState(Canvas canvas, int width, int height, boolean dark) {
        float midY = height / 2f;
        boolean sk = AlzaPriceChartInjector.isSlovak(getContext());

        // 1. Hlídač shopů Logo
        float logoHeight = dpToPx(13.5f);
        float logoLeft = paddingPx + dpToPx(4f);
        float logoTop = midY - (logoHeight / 2f);
        HlidacLogo.draw(canvas, logoLeft, logoTop, logoHeight, dark);

        float curX = logoLeft + HlidacLogo.getScaledWidth(logoHeight) + dpToPx(8f);

        // CZ / SK subtle pill badge
        drawMarketBadge(canvas, curX, midY - dpToPx(7f), dark);
        curX += dpToPx(38f);

        // 2. Skutečná sleva or country notice
        if (state == State.UNSUPPORTED_COUNTRY) {
            discountValuePaint.setColor(Color.parseColor("#9CA3AF"));
            canvas.drawText(sk ? "Iba CZ a SK" : "Pouze CZ a SK", curX, midY + dpToPx(4.5f), discountValuePaint);
        } else if (data != null) {
            PriceHistoryData.DiscountCalculation calc = data.calculateHlidacShopuDiscount();
            String discountPrefix = sk ? "Skutočná zľava: " : "Skutečná sleva: ";
            String discountText;
            if (calc != null && calc.realDiscountPercent > 0) {
                discountValuePaint.setColor(Color.parseColor("#00A651")); // Green
                discountText = discountPrefix + "-" + Math.round(calc.realDiscountPercent) + " %";
            } else {
                discountValuePaint.setColor(Color.parseColor("#9CA3AF")); // Neutral
                discountText = discountPrefix + "0 %";
            }
            canvas.drawText(discountText, curX, midY + dpToPx(4.5f), discountValuePaint);
        }

        // 3. Settings Gear "⚙"
        float gearX = width - paddingPx - dpToPx(65f);
        canvas.drawText("⚙", gearX, midY + dpToPx(4.5f), gearTextPaint);

        // 4. "▲ Graf" toggle
        float expandX = width - paddingPx - dpToPx(40f);
        canvas.drawText("▲ Graf", expandX, midY + dpToPx(4f), toggleTextPaint);
    }

    private void drawExpandedState(Canvas canvas, int width, int height, boolean dark) {
        boolean compact = AlzaPriceChartInjector.isCompactMode();
        boolean sk = AlzaPriceChartInjector.isSlovak(getContext());

        float headerLeft = paddingPx + dpToPx(4f);
        float headerTop = paddingPx + dpToPx(compact ? 11f : 14f);

        // 1. Hlídač shopů Vector SVG Logo
        float logoHeight = dpToPx(compact ? 13.5f : 16f);
        HlidacLogo.draw(canvas, headerLeft, headerTop - dpToPx(compact ? 10f : 12f), logoHeight, dark);

        // 2. CZ / SK market badge right after logo
        float badgeLeft = headerLeft + HlidacLogo.getScaledWidth(logoHeight) + dpToPx(8f);
        drawMarketBadge(canvas, badgeLeft, headerTop - dpToPx(compact ? 10f : 11f), dark);

        // 3. Controls on the right: "⚙" and "▼ Sbalit" / "▼ Zbaliť"
        float gearX = width - paddingPx - dpToPx(68f);
        canvas.drawText("⚙", gearX, headerTop - dpToPx(0.5f), gearTextPaint);

        float toggleX = width - paddingPx - dpToPx(45f);
        canvas.drawText(sk ? "▼ Zbaliť" : "▼ Sbalit", toggleX, headerTop - dpToPx(0.5f), toggleTextPaint);

        // 4. SKUTEČNÁ / SKUTOČNÁ ZĽAVA
        float discountRowY = headerTop + dpToPx(compact ? 13f : 16f);
        if (state == State.UNSUPPORTED_COUNTRY) {
            discountValuePaint.setColor(Color.parseColor("#9CA3AF"));
            canvas.drawText(sk ? "Hlídač shopov sleduje iba české a slovenské obchody" : "Hlídač shopů sleduje pouze české a slovenské obchody", headerLeft, discountRowY, subtitlePaint);
        } else if (state == State.LOADED && data != null) {
            PriceHistoryData.DiscountCalculation calc = data.calculateHlidacShopuDiscount();

            // "Skutečná sleva: " / "Skutočná zľava: "
            String discountPrefix = sk ? "Skutočná zľava: " : "Skutečná sleva: ";
            discountLabelPaint.setTextSize(dpToPx(compact ? 11f : 12f));
            canvas.drawText(discountPrefix, headerLeft, discountRowY, discountLabelPaint);
            float labelWidth = discountLabelPaint.measureText(discountPrefix);

            // Value: "-XX %" or "0 %"
            String valStr;
            if (calc != null && calc.realDiscountPercent > 0) {
                discountValuePaint.setColor(Color.parseColor("#00A651")); // Green
                valStr = "-" + Math.round(calc.realDiscountPercent) + " %";
            } else {
                boolean fakeDiscount = data.getClaimedDiscount() != null && data.getClaimedDiscount() > 0;
                discountValuePaint.setColor(fakeDiscount ? Color.parseColor("#F59E0B") : Color.parseColor("#9CA3AF"));
                valStr = "0 %";
            }
            discountValuePaint.setTextSize(dpToPx(compact ? 12f : 13.5f));
            canvas.drawText(valStr, headerLeft + labelWidth, discountRowY, discountValuePaint);
        }

        // 5. Plot Area
        float plotLeft = headerLeft;
        float plotTop = headerTop + dpToPx(compact ? 24f : 28f);
        float plotRight = width - paddingPx - dpToPx(4f);
        float plotBottom = height - paddingPx - dpToPx(compact ? 8f : 14f);

        switch (state) {
            case LOADING:
                drawCenteredText(canvas, sk ? "Načítavam históriu cien z Hlídača shopov..." : "Načítám historii cen z Hlídače shopů...", plotLeft, plotTop, plotRight, plotBottom, subtitlePaint);
                break;
            case EMPTY:
                drawCenteredText(canvas, sk ? "História cien pre tento produkt nie je k dispozícii" : "Historie cen pro tento produkt není k dispozici", plotLeft, plotTop, plotRight, plotBottom, subtitlePaint);
                break;
            case ERROR:
                drawCenteredText(canvas, sk ? "Údaje o histórii cien sa nepodarilo načítať" : "Data o historii cen se nepodařilo načíst", plotLeft, plotTop, plotRight, plotBottom, subtitlePaint);
                break;
            case UNSUPPORTED_COUNTRY:
                drawCenteredText(canvas, sk ? "Hlídač shopov podporuje výhradne CZ a SK trh" : "Hlídač shopů podporuje výhradně CZ a SK trh", plotLeft, plotTop, plotRight, plotBottom, subtitlePaint);
                break;
            case LOADED:
                drawChart(canvas, plotLeft, plotTop, plotRight, plotBottom, compact);
                break;
        }
    }

    private void drawMarketBadge(Canvas canvas, float left, float top, boolean dark) {
        float bWidth = dpToPx(34f);
        float bHeight = dpToPx(13f);
        badgeRect.set(left, top, left + bWidth, top + bHeight);

        badgeBgPaint.setColor(dark ? Color.parseColor("#1B3320") : Color.parseColor("#E8F5E9"));
        canvas.drawRoundRect(badgeRect, dpToPx(4f), dpToPx(4f), badgeBgPaint);

        badgePaint.setColor(Color.parseColor("#00A651"));
        float tx = left + (bWidth / 2f) - (badgePaint.measureText("CZ/SK") / 2f);
        float ty = top + (bHeight / 2f) - ((badgePaint.descent() + badgePaint.ascent()) / 2f);
        canvas.drawText("CZ/SK", tx, ty, badgePaint);
    }

    private void drawCenteredText(Canvas canvas, String text, float left, float top, float right, float bottom, Paint paint) {
        float x = (left + right) / 2f;
        float y = (top + bottom) / 2f - ((paint.descent() + paint.ascent()) / 2f);
        Paint.Align prevAlign = paint.getTextAlign();
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, x, y, paint);
        paint.setTextAlign(prevAlign);
    }

    private void drawChart(Canvas canvas, float left, float top, float right, float bottom, boolean compact) {
        if (data == null || !data.hasData()) return;

        List<PriceHistoryData.PricePoint> points = data.getCurrentPricePoints();
        int count = points.size();
        if (count < 2) return;

        double minPrice = data.getMinPrice() != null ? data.getMinPrice() : points.get(0).price;
        double maxPrice = data.getMaxPrice() != null ? data.getMaxPrice() : points.get(0).price;

        if (maxPrice <= minPrice) {
            maxPrice = minPrice + 1.0;
        }

        // Measure Y-axis label gutter
        String topLabel = formatPrice(maxPrice);
        String btmLabel = formatPrice(minPrice);
        labelPaint.setTextSize(dpToPx(compact ? 8.5f : 9.5f));
        float maxLabelWidth = Math.max(labelPaint.measureText(topLabel), labelPaint.measureText(btmLabel));
        float yGutter = maxLabelWidth + dpToPx(compact ? 6f : 8f);

        float chartRight = right - yGutter;
        if (chartRight <= left + dpToPx(50f)) {
            chartRight = right;
        }

        // Horizontal grid lines and labels
        int gridLines = compact ? 2 : 3;
        for (int i = 0; i < gridLines; i++) {
            float y = top + (bottom - top) * i / (gridLines - 1f);
            canvas.drawLine(left, y, chartRight, y, gridLinePaint);

            double priceVal = maxPrice - (maxPrice - minPrice) * i / (gridLines - 1f);
            String label = formatPrice(priceVal);
            canvas.drawText(label, right - labelPaint.measureText(label), y + dpToPx(3f), labelPaint);
        }

        // Dates on X-axis
        long minTime = points.get(0).timestamp;
        long maxTime = points.get(count - 1).timestamp;
        if (maxTime <= minTime) maxTime = minTime + 1;

        int numDates = compact ? 3 : ((chartRight - left) > dpToPx(240f) ? 4 : 3);
        float dateY = bottom + dpToPx(compact ? 9.5f : 13f);
        for (int i = 0; i < numDates; i++) {
            float frac = (float) i / (float) (numDates - 1);
            long time = minTime + (long) ((maxTime - minTime) * frac);
            String dateText = dateFormat.format(new Date(time));
            float dWidth = labelPaint.measureText(dateText);

            float dx;
            if (i == 0) {
                dx = left;
            } else if (i == numDates - 1) {
                dx = chartRight - dWidth;
            } else {
                dx = left + (chartRight - left) * frac - (dWidth / 2f);
            }
            canvas.drawText(dateText, dx, dateY, labelPaint);
        }

        // Build curve & shaded area
        currentCurvePath.reset();
        currentAreaPath.reset();

        float startX = left;
        float startY = bottom - (float) ((points.get(0).price - minPrice) / (maxPrice - minPrice) * (bottom - top));

        currentCurvePath.moveTo(startX, startY);
        currentAreaPath.moveTo(startX, bottom);
        currentAreaPath.lineTo(startX, startY);

        for (int i = 1; i < count; i++) {
            PriceHistoryData.PricePoint p = points.get(i);
            float x = left + (float) ((p.timestamp - minTime) / (double) (maxTime - minTime) * (chartRight - left));
            float y = bottom - (float) ((p.price - minPrice) / (maxPrice - minPrice) * (bottom - top));

            currentCurvePath.lineTo(x, y);
            currentAreaPath.lineTo(x, y);
        }

        currentAreaPath.lineTo(chartRight, bottom);
        currentAreaPath.close();

        // Shaded area
        int curveColor = Color.parseColor("#00A651");
        int startGradColor = Color.argb(compact ? 60 : 75, Color.red(curveColor), Color.green(curveColor), Color.blue(curveColor));
        int endGradColor = Color.argb(0, Color.red(curveColor), Color.green(curveColor), Color.blue(curveColor));

        currentAreaPaint.setShader(new LinearGradient(
                0, top, 0, bottom,
                startGradColor, endGradColor,
                Shader.TileMode.CLAMP
        ));

        canvas.drawPath(currentAreaPath, currentAreaPaint);
        currentCurvePaint.setStrokeWidth(dpToPx(compact ? 2f : 2.5f));
        canvas.drawPath(currentCurvePath, currentCurvePaint);

        // Optional original price line (strikethrough)
        List<PriceHistoryData.PricePoint> origPoints = data.getOriginalPricePoints();
        if (origPoints != null && origPoints.size() >= 2) {
            originalCurvePath.reset();
            boolean first = true;
            for (PriceHistoryData.PricePoint op : origPoints) {
                float ox = left + (float) ((op.timestamp - minTime) / (double) (maxTime - minTime) * (chartRight - left));
                float oy = bottom - (float) ((op.price - minPrice) / (maxPrice - minPrice) * (bottom - top));
                if (first) {
                    originalCurvePath.moveTo(ox, oy);
                    first = false;
                } else {
                    originalCurvePath.lineTo(ox, oy);
                }
            }
            canvas.drawPath(originalCurvePath, originalCurvePaint);
        }
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}
