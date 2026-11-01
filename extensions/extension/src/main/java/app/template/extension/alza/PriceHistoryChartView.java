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

    // Paints
    private Paint cardBgPaint;
    private Paint cardBorderPaint;
    private Paint gridLinePaint;
    private Paint titlePaint;
    private Paint subtitlePaint;
    private Paint labelPaint;
    private Paint currentCurvePaint;
    private Paint currentAreaPaint;
    private Paint originalCurvePaint;
    private Paint badgeBgPaint;
    private Paint badgeTextPaint;

    private Path currentCurvePath = new Path();
    private Path currentAreaPath = new Path();
    private Path originalCurvePath = new Path();
    private RectF cardRect = new RectF();

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/yy", Locale.getDefault());
    private final DecimalFormat priceFormat;

    private int heightPx;
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
        DecimalFormat df = new DecimalFormat("#,###", symbols);
        return df;
    }

    private void init() {
        paddingPx = dpToPx(12f);
        cornerRadiusPx = dpToPx(12f);
        heightPx = (int) dpToPx(180f);

        cardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBorderPaint.setStyle(Paint.Style.STROKE);
        cardBorderPaint.setStrokeWidth(dpToPx(1f));

        gridLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridLinePaint.setStyle(Paint.Style.STROKE);
        gridLinePaint.setStrokeWidth(dpToPx(1f));

        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTextSize(dpToPx(13f));
        titlePaint.setFakeBoldText(true);

        subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subtitlePaint.setTextSize(dpToPx(11f));

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setTextSize(dpToPx(9f));

        badgeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeTextPaint.setTextSize(dpToPx(9.5f));
        badgeTextPaint.setFakeBoldText(true);
        badgeTextPaint.setColor(Color.WHITE);

        currentCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        currentCurvePaint.setStyle(Paint.Style.STROKE);
        currentCurvePaint.setStrokeWidth(dpToPx(2.5f));
        currentCurvePaint.setColor(Color.parseColor("#00A651")); // Alza green

        currentAreaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        currentAreaPaint.setStyle(Paint.Style.FILL);

        originalCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        originalCurvePaint.setStyle(Paint.Style.STROKE);
        originalCurvePaint.setStrokeWidth(dpToPx(1.5f));
        originalCurvePaint.setColor(Color.parseColor("#EF4444")); // Red/original price
        originalCurvePaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(4f), dpToPx(4f)}, 0));

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openHlidacWeb();
            }
        });
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
        setMeasuredDimension(width, heightPx);
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

        // Colors based on theme
        int bgColor = dark ? Color.parseColor("#181824") : Color.parseColor("#F9FAFB");
        int borderColor = dark ? Color.parseColor("#2E2E3E") : Color.parseColor("#E5E7EB");
        int titleColor = dark ? Color.parseColor("#F3F4F6") : Color.parseColor("#111827");
        int subtitleColor = dark ? Color.parseColor("#9CA3AF") : Color.parseColor("#6B7280");
        int gridColor = dark ? Color.parseColor("#252538") : Color.parseColor("#F3F4F6");
        int labelColor = dark ? Color.parseColor("#6B7280") : Color.parseColor("#9CA3AF");

        cardBgPaint.setColor(bgColor);
        cardBorderPaint.setColor(borderColor);
        gridLinePaint.setColor(gridColor);
        titlePaint.setColor(titleColor);
        subtitlePaint.setColor(subtitleColor);
        labelPaint.setColor(labelColor);

        // Draw card background
        cardRect.set(paddingPx / 2, paddingPx / 2, width - (paddingPx / 2), height - (paddingPx / 2));
        canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, cardBgPaint);
        canvas.drawRoundRect(cardRect, cornerRadiusPx, cornerRadiusPx, cardBorderPaint);

        // Title and badge
        float headerLeft = paddingPx + dpToPx(4f);
        float headerTop = paddingPx + dpToPx(14f);

        canvas.drawText("Hlídač shopů", headerLeft, headerTop, titlePaint);
        float titleWidth = titlePaint.measureText("Hlídač shopů");

        // Discount or status badge
        if (data != null && data.getRealDiscount() != null && data.getRealDiscount() > 0) {
            String badgeText = "-" + Math.round(data.getRealDiscount()) + " %";
            float badgeTextWidth = badgeTextPaint.measureText(badgeText);
            float badgeLeft = headerLeft + titleWidth + dpToPx(8f);
            float badgeTop = headerTop - dpToPx(11f);
            float badgeRight = badgeLeft + badgeTextWidth + dpToPx(10f);
            float badgeBottom = headerTop + dpToPx(3f);

            badgeBgPaint.setColor(Color.parseColor("#00A651"));
            RectF badgeRect = new RectF(badgeLeft, badgeTop, badgeRight, badgeBottom);
            canvas.drawRoundRect(badgeRect, dpToPx(4f), dpToPx(4f), badgeBgPaint);
            canvas.drawText(badgeText, badgeLeft + dpToPx(5f), headerTop - dpToPx(1f), badgeTextPaint);
        }

        // Subtitle: Min / Max / Current info
        if (state == State.LOADED && data != null) {
            StringBuilder sb = new StringBuilder();
            if (data.getMinPrice() != null) {
                sb.append("Min: ").append(priceFormat.format(data.getMinPrice())).append(" Kč");
            }
            if (data.getMaxPrice() != null) {
                if (sb.length() > 0) sb.append("  |  ");
                sb.append("Max: ").append(priceFormat.format(data.getMaxPrice())).append(" Kč");
            }
            if (data.getIdentifier() != null && !data.getIdentifier().isEmpty()) {
                if (sb.length() > 0) sb.append("  |  ");
                sb.append(data.getIdentifier());
            }

            canvas.drawText(sb.toString(), headerLeft, headerTop + dpToPx(16f), subtitlePaint);
        }

        // Draw State or Chart
        float plotLeft = headerLeft;
        float plotTop = headerTop + dpToPx(26f);
        float plotRight = width - paddingPx - dpToPx(4f);
        float plotBottom = height - paddingPx - dpToPx(16f);

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

        // Grid lines (3 horizontal lines)
        int gridLines = 3;
        for (int i = 0; i < gridLines; i++) {
            float y = top + (bottom - top) * i / (gridLines - 1f);
            canvas.drawLine(left, y, right, y, gridLinePaint);

            double priceVal = maxPrice - (maxPrice - minPrice) * i / (gridLines - 1f);
            String label = priceFormat.format(priceVal) + " Kč";
            canvas.drawText(label, right - labelPaint.measureText(label), y - dpToPx(3f), labelPaint);
        }

        // Date markers at start and end
        long minTime = points.get(0).timestamp;
        long maxTime = points.get(count - 1).timestamp;
        if (maxTime <= minTime) maxTime = minTime + 1;

        String startDate = dateFormat.format(new Date(minTime));
        String endDate = dateFormat.format(new Date(maxTime));
        canvas.drawText(startDate, left, bottom + dpToPx(12f), labelPaint);
        canvas.drawText(endDate, right - labelPaint.measureText(endDate), bottom + dpToPx(12f), labelPaint);

        // Build Paths
        currentCurvePath.reset();
        currentAreaPath.reset();

        float startX = left;
        float startY = bottom - (float) ((points.get(0).price - minPrice) / (maxPrice - minPrice) * (bottom - top));

        currentCurvePath.moveTo(startX, startY);
        currentAreaPath.moveTo(startX, bottom);
        currentAreaPath.lineTo(startX, startY);

        for (int i = 1; i < count; i++) {
            PriceHistoryData.PricePoint p = points.get(i);
            float x = left + (float) ((p.timestamp - minTime) / (double) (maxTime - minTime) * (right - left));
            float y = bottom - (float) ((p.price - minPrice) / (maxPrice - minPrice) * (bottom - top));

            currentCurvePath.lineTo(x, y);
            currentAreaPath.lineTo(x, y);
        }

        currentAreaPath.lineTo(right, bottom);
        currentAreaPath.close();

        // Shaded area gradient under curve
        int curveColor = Color.parseColor("#00A651");
        int startGradColor = Color.argb(80, Color.red(curveColor), Color.green(curveColor), Color.blue(curveColor));
        int endGradColor = Color.argb(0, Color.red(curveColor), Color.green(curveColor), Color.blue(curveColor));

        currentAreaPaint.setShader(new LinearGradient(
                0, top, 0, bottom,
                startGradColor, endGradColor,
                Shader.TileMode.CLAMP
        ));

        canvas.drawPath(currentAreaPath, currentAreaPaint);
        canvas.drawPath(currentCurvePath, currentCurvePaint);

        // Optional original price line (strikethrough price)
        List<PriceHistoryData.PricePoint> origPoints = data.getOriginalPricePoints();
        if (origPoints != null && origPoints.size() >= 2) {
            originalCurvePath.reset();
            boolean first = true;
            for (PriceHistoryData.PricePoint op : origPoints) {
                float ox = left + (float) ((op.timestamp - minTime) / (double) (maxTime - minTime) * (right - left));
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

