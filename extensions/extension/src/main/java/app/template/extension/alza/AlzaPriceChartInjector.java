package app.template.extension.alza;

import android.content.Context;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Method;

public class AlzaPriceChartInjector {
    private static final String TAG = "MorpheAlza";
    public static final String CHART_VIEW_TAG = "MORPHE_HLIDAC_CHART";

    public static void injectChart(
            final ViewGroup targetContainer,
            final String commodityId,
            final String ean,
            final String partNumber,
            final String productName
    ) {
        if (targetContainer == null) {
            Log.w(TAG, "injectChart called with null targetContainer");
            return;
        }

        try {
            // Find existing view by tag in container or parent
            View existing = targetContainer.findViewWithTag(CHART_VIEW_TAG);
            if (existing == null && targetContainer.getParent() instanceof ViewGroup) {
                existing = ((ViewGroup) targetContainer.getParent()).findViewWithTag(CHART_VIEW_TAG);
            }

            final String cascadeIdentifier = buildIdentifier(ean, partNumber);

            if (existing instanceof PriceHistoryChartView) {
                PriceHistoryChartView chartView = (PriceHistoryChartView) existing;
                chartView.setCommodityId(commodityId);
                chartView.setState(PriceHistoryChartView.State.LOADING);
                fetchData(chartView, commodityId, cascadeIdentifier, productName);
                return;
            }

            // If targetContainer is a ComposeView, it may disallow addView directly.
            // Check if it's attached to parent ViewGroup or attach listener
            String className = targetContainer.getClass().getName();
            if (className.contains("ComposeView")) {
                ViewParent parent = targetContainer.getParent();
                if (parent instanceof ViewGroup) {
                    injectChart((ViewGroup) parent, commodityId, ean, partNumber, productName);
                    return;
                } else {
                    targetContainer.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            ViewParent p = v.getParent();
                            if (p instanceof ViewGroup) {
                                injectChart((ViewGroup) p, commodityId, ean, partNumber, productName);
                            }
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {}
                    });
                    return;
                }
            }

            // Create new chart view
            Context context = targetContainer.getContext();
            final PriceHistoryChartView chartView = new PriceHistoryChartView(context);
            chartView.setTag(CHART_VIEW_TAG);
            chartView.setCommodityId(commodityId);

            ViewGroup.LayoutParams lp;
            if (targetContainer instanceof LinearLayout) {
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                llp.topMargin = 16;
                llp.bottomMargin = 16;
                lp = llp;
            } else if (targetContainer instanceof FrameLayout) {
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                flp.gravity = android.view.Gravity.BOTTOM;
                lp = flp;
            } else {
                lp = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }

            targetContainer.addView(chartView, lp);
            fetchData(chartView, commodityId, cascadeIdentifier, productName);

        } catch (Throwable t) {
            Log.e(TAG, "Error in injectChart", t);
        }
    }

    public static void injectChart(
            ViewGroup targetContainer,
            int commodityId,
            String ean,
            String partNumber,
            String productName
    ) {
        injectChart(targetContainer, String.valueOf(commodityId), ean, partNumber, productName);
    }

    public static View wrapFragmentView(View originalView, Object fragment) {
        if (originalView == null || fragment == null) return originalView;
        try {
            Method methodU = fragment.getClass().getMethod("U");
            Object destination = methodU.invoke(fragment);
            if (destination == null) return originalView;

            String destClass = destination.getClass().getName();
            if (!destClass.contains("i3m") && !destClass.contains("ProductDetailDestination")) {
                return originalView;
            }

            String commodityId = null;
            String productName = null;
            try {
                java.lang.reflect.Field fieldParams = destination.getClass().getField("a");
                Object params = fieldParams.get(destination);
                if (params != null) {
                    try {
                        Method getProductId = params.getClass().getMethod("getProductId");
                        Object idObj = getProductId.invoke(params);
                        if (idObj instanceof Number) {
                            commodityId = String.valueOf(((Number) idObj).intValue());
                        }
                    } catch (Throwable ignored) {}

                    try {
                        Method getProductInfo = params.getClass().getMethod("getProductInfo");
                        Object info = getProductInfo.invoke(params);
                        if (info != null) {
                            Method getName = info.getClass().getMethod("getName");
                            Object nameObj = getName.invoke(info);
                            if (nameObj != null) {
                                productName = nameObj.toString();
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error extracting product info in wrapFragmentView", t);
            }

            if (commodityId == null || commodityId.isEmpty() || commodityId.equals("0")) {
                return originalView;
            }

            Context context = originalView.getContext();
            FrameLayout rootLayout = new FrameLayout(context);
            rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            rootLayout.addView(originalView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            PriceHistoryChartView chartView = new PriceHistoryChartView(context);
            chartView.setTag(CHART_VIEW_TAG);
            chartView.setCommodityId(commodityId);

            FrameLayout.LayoutParams chartLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            chartLp.gravity = android.view.Gravity.BOTTOM;
            int margin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8,
                    context.getResources().getDisplayMetrics()
            );
            chartLp.setMargins(margin, margin, margin, margin);
            rootLayout.addView(chartView, chartLp);

            fetchData(chartView, commodityId, null, productName);
            return rootLayout;
        } catch (Throwable t) {
            Log.e(TAG, "Error in wrapFragmentView", t);
            return originalView;
        }
    }

    public static void injectFromFragment(final ViewGroup targetContainer, final Object fragment) {
        if (targetContainer == null || fragment == null) return;
        try {
            // Extract destination from GodFragment via U() method reflection
            Method methodU = fragment.getClass().getMethod("U");
            Object destination = methodU.invoke(fragment);
            if (destination == null) return;

            String destClass = destination.getClass().getName();
            // Check if destination is product detail
            if (destClass.contains("i3m") || destClass.contains("ProductDetailDestination")) {
                // destination.a is ProductDetailParams
                java.lang.reflect.Field fieldParams = destination.getClass().getField("a");
                Object params = fieldParams.get(destination);

                String commodityId = null;
                String productName = null;

                if (params != null) {
                    try {
                        Method getProductId = params.getClass().getMethod("getProductId");
                        Object idObj = getProductId.invoke(params);
                        if (idObj instanceof Number) {
                            commodityId = String.valueOf(((Number) idObj).intValue());
                        }
                    } catch (Throwable ignored) {}

                    try {
                        Method getProductInfo = params.getClass().getMethod("getProductInfo");
                        Object info = getProductInfo.invoke(params);
                        if (info != null) {
                            Method getName = info.getClass().getMethod("getName");
                            Object nameObj = getName.invoke(info);
                            if (nameObj != null) {
                                productName = nameObj.toString();
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                if (commodityId != null && !commodityId.isEmpty() && !commodityId.equals("0")) {
                    injectChart(targetContainer, commodityId, null, null, productName);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in injectFromFragment", t);
        }
    }

    private static String buildIdentifier(String ean, String partNumber) {
        if (ean != null && !ean.trim().isEmpty()) {
            return "EAN: " + ean.trim();
        }
        if (partNumber != null && !partNumber.trim().isEmpty()) {
            return "Kód: " + partNumber.trim();
        }
        return null;
    }

    private static void fetchData(
            final PriceHistoryChartView chartView,
            final String commodityId,
            final String identifier,
            final String productName
    ) {
        HlidacShopuClient.getInstance().fetchPriceHistory(
                commodityId,
                null,
                identifier,
                productName,
                new HlidacShopuClient.Callback() {
                    @Override
                    public void onSuccess(PriceHistoryData data) {
                        chartView.setData(data);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        chartView.setState(PriceHistoryChartView.State.ERROR);
                    }
                }
        );
    }
}
