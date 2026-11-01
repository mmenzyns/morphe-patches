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
        ERROR
    }

    private State state = State.LOADING;
    private PriceHistoryData data;
    private String commodityId;
    private boolean isCollapsed = false;
    private boolean isDismissed = false;

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

    private Path currentCurvePath = new Path();
    private Path currentAreaPath = new Path();
    private Path originalCurvePath = new Path();
    private RectF cardRect = new RectF();

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/yy", Locale.getDefault());
    private final DecimalFormat priceFormat;

    private float paddingPx;
    private float cornerRadiusPx;

    public PriceHistoryChartView(Context context) {
        super(context);
        priceFormat = initPriceFormat();
        init();
    }

    public PriceHistoryChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        priceFormat = initPriceFormat();
        init();
    }

    private DecimalFormat initPriceFormat() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.getDefault());
        symbols.setGroupingSeparator(' ');
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
    }

    public void setState(State state) {
        this.state = state;
        invalidate();
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

    public boolean isDismissed() {
        return isDismissed;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            int width = getWidth();

            // 1. Close button "✕" in top-right area (last 44dp)
            if (x >= width - dpToPx(44f) && y <= dpToPx(40f)) {
                isDismissed = true;
                setVisibility(View.GONE);
                return true;
            }

            // 2. Toggle button area or header when collapsed
            if (isCollapsed) {
                // Tapping collapsed bar expands it
                isCollapsed = false;
                requestLayout();
                invalidate();
                return true;
            } else {
                // When expanded:
                // Check if touch is on top header
                if (y <= dpToPx(40f)) {
                    // Check toggle button area [width - 90dp .. width - 44dp]
                    if (x >= width - dpToPx(90f)) {
                        isCollapsed = true;
                        requestLayout();
                        invalidate();
                        return true;
                    }
                    openHlidacWeb();
                    return true;
                }
                // Tapping chart area opens web
                openHlidacWeb();
                return true;
            }
        }
        return true;
    }

    private void openHlidacWeb() {
        if (commodityId == null || commodityId.isEmpty()) return;
        try {
            String url = "https://www.hlidacshopu.cz/app/?url=https://www.alza.cz/a-d" + commodityId + ".htm";
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int targetHeight = isCollapsed ? (int) dpToPx(38f) : (int) dpToPx(210f);
        setMeasuredDimension(width, targetHeight);
    }

    private boolean isDarkMode() {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
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

        // 1. Hlídač shopů Logo
        float logoHeight = dpToPx(14f);
        float logoLeft = paddingPx + dpToPx(4f);
        float logoTop = midY - (logoHeight / 2f);
        HlidacLogo.draw(canvas, logoLeft, logoTop, logoHeight, dark);

        float curX = logoLeft + HlidacLogo.getScaledWidth(logoHeight) + dpToPx(10f);

        // 2. Skutečná sleva
        if (data != null) {
            PriceHistoryData.DiscountCalculation calc = data.calculateHlidacShopuDiscount();
            String discountText;
            if (calc != null && calc.realDiscountPercent > 0) {
                discountValuePaint.setColor(Color.parseColor("#00A651")); // Green
                discountText = "Skutečná sleva: -" + Math.round(calc.realDiscountPercent) + " %";
            } else {
                discountValuePaint.setColor(Color.parseColor("#9CA3AF")); // Neutral
                discountText = "Skutečná sleva: 0 %";
            }
            canvas.drawText(discountText, curX, midY + dpToPx(4.5f), discountValuePaint);
        }

        // 3. "▲ Graf" toggle
        float expandX = width - paddingPx - dpToPx(50f);
        canvas.drawText("▲ Graf", expandX, midY + dpToPx(4f), toggleTextPaint);

        // 4. "✕" close button
        float closeX = width - paddingPx - dpToPx(8f);
        canvas.drawText("✕", closeX, midY + dpToPx(4f), subtitlePaint);
    }

    private void drawExpandedState(Canvas canvas, int width, int height, boolean dark) {
        float headerLeft = paddingPx + dpToPx(4f);
        float headerTop = paddingPx + dpToPx(14f);

        // 1. Hlídač shopů Vector SVG Logo
        float logoHeight = dpToPx(16f);
        HlidacLogo.draw(canvas, headerLeft, headerTop - dpToPx(12f), logoHeight, dark);

        // 2. Controls on the right: "▼ Sbalit" and "✕"
        float toggleX = width - paddingPx - dpToPx(60f);
        canvas.drawText("▼ Sbalit", toggleX, headerTop - dpToPx(0.5f), toggleTextPaint);

        float closeX = width - paddingPx - dpToPx(8f);
        canvas.drawText("✕", closeX, headerTop - dpToPx(0.5f), subtitlePaint);

        // 3. SKUTEČNÁ SLEVA (Čistě naformátovaná, bez zbytečných čísel pod logem)
        if (state == State.LOADED && data != null) {
            PriceHistoryData.DiscountCalculation calc = data.calculateHlidacShopuDiscount();

            float discountRowY = headerTop + dpToPx(16f);

            // "Skutečná sleva: "
            canvas.drawText("Skutečná sleva: ", headerLeft, discountRowY, discountLabelPaint);
            float labelWidth = discountLabelPaint.measureText("Skutečná sleva: ");

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
            canvas.drawText(valStr, headerLeft + labelWidth, discountRowY, discountValuePaint);
            float valWidth = discountValuePaint.measureText(valStr);

            // Subtitle note / context (e.g. "Alza uvádí -35 % • podle nejnižší ceny za 30 dní před slevou")
            StringBuilder noteSb = new StringBuilder();
            if (data.getClaimedDiscount() != null && data.getClaimedDiscount() > 0) {
                noteSb.append("Alza uvádí -").append(Math.round(data.getClaimedDiscount())).append(" %");
                if (calc != null && calc.realDiscountPercent <= 0) {
                    noteSb.append(" (klamavá)");
                }
            }

            if (calc != null && calc.methodologyNote != null && !calc.methodologyNote.isEmpty()) {
                if (noteSb.length() > 0) {
                    noteSb.append("  •  ");
                }
                noteSb.append(calc.methodologyNote);
            }

            if (noteSb.length() > 0) {
                float noteX = headerLeft + labelWidth + valWidth + dpToPx(10f);
                if (noteX + subtitlePaint.measureText(noteSb.toString()) < (width - paddingPx - dpToPx(8f))) {
                    canvas.drawText(noteSb.toString(), noteX, discountRowY, subtitlePaint);
                } else {
                    canvas.drawText(noteSb.toString(), headerLeft, discountRowY + dpToPx(14f), subtitlePaint);
                }
            }
        }

        // 4. Draw Chart or Loading/Empty/Error states
        float plotLeft = headerLeft;
        float plotTop = headerTop + dpToPx(36f);
        float plotRight = width - paddingPx - dpToPx(4f);
        float plotBottom = height - paddingPx - dpToPx(14f);

        switch (state) {
            case LOADING:
                drawCenteredText(canvas, "Načítám historii cen z Hlídače shopů...", plotLeft, plotTop, plotRight, plotBottom, subtitlePaint);
                break;
            case EMPTY:
                drawCenteredText(canvas, "Historie cen pro tento produkt není k dispozici", plotLeft, plotTop, plotRight, plotBottom, subtitlePaint);
                break;
            case ERROR:
                drawCenteredText(canvas, "Data o historii cen se nepodařilo načíst", plotLeft, plotTop, plotRight, plotBottom, subtitlePaint);
                break;
            case LOADED:
                drawChart(canvas, plotLeft, plotTop, plotRight, plotBottom);
                break;
        }
    }

    private void drawCenteredText(Canvas canvas, String text, float left, float top, float right, float bottom, Paint paint) {
        float x = (left + right) / 2f;
        float y = (top + bottom) / 2f - ((paint.descent() + paint.ascent()) / 2f);
        Paint.Align prevAlign = paint.getTextAlign();
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, x, y, paint);
        paint.setTextAlign(prevAlign);
    }

    private void drawChart(Canvas canvas, float left, float top, float right, float bottom) {
        if (data == null || !data.hasData()) return;

        List<PriceHistoryData.PricePoint> points = data.getCurrentPricePoints();
        int count = points.size();
        if (count < 2) return;

        double minPrice = data.getMinPrice() != null ? data.getMinPrice() : points.get(0).price;
        double maxPrice = data.getMaxPrice() != null ? data.getMaxPrice() : points.get(0).price;

        if (maxPrice <= minPrice) {
            maxPrice = minPrice + 1.0;
        }

        // --- Y-AXIS PADDING FIX ---
        // Measure maximum Y-axis label width so chart curve stops BEFORE the Y-axis labels!
        String topLabel = priceFormat.format(maxPrice) + " Kč";
        String btmLabel = priceFormat.format(minPrice) + " Kč";
        float maxLabelWidth = Math.max(labelPaint.measureText(topLabel), labelPaint.measureText(btmLabel));
        float yGutter = maxLabelWidth + dpToPx(8f);

        float chartRight = right - yGutter;
        if (chartRight <= left + dpToPx(60f)) {
            chartRight = right; // Fallback for very small screens
        }

        // Draw 3 horizontal grid lines and Y-axis labels in the right gutter
        int gridLines = 3;
        for (int i = 0; i < gridLines; i++) {
            float y = top + (bottom - top) * i / (gridLines - 1f);
            canvas.drawLine(left, y, chartRight, y, gridLinePaint);

            double priceVal = maxPrice - (maxPrice - minPrice) * i / (gridLines - 1f);
            String label = priceFormat.format(priceVal) + " Kč";
            canvas.drawText(label, right - labelPaint.measureText(label), y + dpToPx(3.5f), labelPaint);
        }

        // --- X-AXIS DATES (3 or 4 evenly spaced dates) ---
        long minTime = points.get(0).timestamp;
        long maxTime = points.get(count - 1).timestamp;
        if (maxTime <= minTime) maxTime = minTime + 1;

        int numDates = (chartRight - left) > dpToPx(240f) ? 4 : 3;
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
            canvas.drawText(dateText, dx, bottom + dpToPx(13f), labelPaint);
        }

        // --- BUILD & DRAW CURVE & AREA ---
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

        // Shaded area gradient under curve
        int curveColor = Color.parseColor("#00A651");
        int startGradColor = Color.argb(75, Color.red(curveColor), Color.green(curveColor), Color.blue(curveColor));
        int endGradColor = Color.argb(0, Color.red(curveColor), Color.green(curveColor), Color.blue(curveColor));

        currentAreaPaint.setShader(new LinearGradient(
                0, top, 0, bottom,
                startGradColor, endGradColor,
                Shader.TileMode.CLAMP
        ));

        canvas.drawPath(currentAreaPath, currentAreaPaint);
        canvas.drawPath(currentCurvePath, currentCurvePaint);

        // Optional original price line (strikethrough / crossed price)
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
