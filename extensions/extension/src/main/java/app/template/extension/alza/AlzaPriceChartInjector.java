package app.template.extension.alza;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class AlzaPriceChartInjector {
    private static final String TAG = "MorpheAlza";
    public static final String CHART_VIEW_TAG = "MORPHE_HLIDAC_CHART";

    // Captured synchronously at GodFragment.onCreateView entry (index 0)
    private static final ThreadLocal<Object> currentFragment = new ThreadLocal<Object>();

    // Extracted directly from Compose ProductPrices model
    private static volatile String activePrimaryPrice = null;
    private static volatile String activeSecondaryPrice = null;
    private static volatile Object activePriceModel = null;

    public static void onFragmentCreateViewStart(Object fragment) {
        try {
            if (fragment != null) {
                Log.i(TAG, "onFragmentCreateViewStart: captured " + fragment.getClass().getName());
                currentFragment.set(fragment);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in onFragmentCreateViewStart", t);
        }
    }

    /**
     * Captured directly at w4m.i entry to immediately obtain the active price strings
     * before layout occurs.
     */
    public static void onPriceModelCaptured(Object productPrices) {
        try {
            if (productPrices != null) {
                activePriceModel = productPrices;
                extractPriceStrings(productPrices);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in onPriceModelCaptured", t);
        }
    }

    /**
     * Hooked directly inside the price Column in w4m.i right below the price tags.
     * Reserves vertical space inside Compose layout so all subsequent elements
     * (Buy button, parameters, etc.) are pushed down natively.
     */
    public static void onPriceSectionCompose(Object composer) {
        try {
            if (composer != null) {
                reserveComposeSpace(composer, 226.0f);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in onPriceSectionCompose", t);
        }
    }

    /**
     * Combined entry point for compatibility.
     */
    public static void onPriceSectionRender(Object composer, Object productPrices) {
        onPriceModelCaptured(productPrices);
        onPriceSectionCompose(composer);
    }

    public static View wrapFragmentView(View originalView, Object maybeFragment) {
        if (originalView == null) return null;

        try {
            Object fragment = currentFragment.get();
            currentFragment.remove();

            if (fragment == null) {
                fragment = maybeFragment;
            }

            Log.i(TAG, "wrapFragmentView: fragment=" + (fragment != null ? fragment.getClass().getName() : "null"));

            // Resolve GodFragment even if wrapped in ComposableLambda / oed
            Object godFragment = findGodFragment(fragment);
            if (godFragment == null && maybeFragment != null) {
                godFragment = findGodFragment(maybeFragment);
            }

            if (godFragment == null) {
                Log.w(TAG, "Could not find GodFragment instance");
                return originalView;
            }

            Log.i(TAG, "Found GodFragment: " + godFragment.getClass().getName());

            // Extract destination from GodFragment via U() method reflection
            Method methodU = godFragment.getClass().getMethod("U");
            Object destination = methodU.invoke(godFragment);
            if (destination == null) {
                Log.d(TAG, "GodFragment U() returned null (not a destination fragment yet)");
                return originalView;
            }

            String destClass = destination.getClass().getName();
            String destStr = destination.toString();
            Log.i(TAG, "Destination: " + destClass + ", toString: " + destStr);

            boolean isProductDetail = destClass.contains("i3m")
                    || destClass.toLowerCase().contains("productdetail")
                    || destStr.contains("ProductDetail")
                    || destStr.contains("cz.alza.base.lib.product.detail");

            if (!isProductDetail) {
                // Also check if any field holds ProductDetailParams
                Object testParams = extractProductParams(destination);
                if (testParams != null) {
                    isProductDetail = true;
                }
            }

            if (!isProductDetail) {
                Log.d(TAG, "Not a product detail screen, skipping injection");
                return originalView;
            }

            Object params = extractProductParams(destination);
            String commodityId = extractCommodityId(params, destination);
            String productName = extractProductName(params);

            Log.i(TAG, "Extracted product: id=" + commodityId + ", name=" + productName);

            if (commodityId == null || commodityId.isEmpty() || commodityId.equals("0")) {
                Log.w(TAG, "Could not extract valid commodityId from destination params");
                return originalView;
            }

            final Context context = originalView.getContext();

            final FrameLayout rootLayout = new FrameLayout(context);
            rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            rootLayout.addView(originalView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));

            final PriceHistoryChartView chartView = new PriceHistoryChartView(context);
            chartView.setTag(CHART_VIEW_TAG);
            chartView.setCommodityId(commodityId);

            FrameLayout.LayoutParams chartLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            chartLp.gravity = Gravity.TOP;

            int sideMargin = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8,
                    context.getResources().getDisplayMetrics()
            );
            chartLp.setMargins(sideMargin, 0, sideMargin, 0);

            // Add chart view to root layout
            rootLayout.addView(chartView, chartLp);

            setupInlineScrollSync(rootLayout, originalView, chartView);
            Log.i(TAG, "Injected inline PriceHistoryChartView for product " + commodityId);

            fetchData(chartView, commodityId, null, productName);
            return rootLayout;

        } catch (Throwable t) {
            Log.e(TAG, "Error in wrapFragmentView", t);
            return originalView;
        }
    }

    private static void setupInlineScrollSync(
            final FrameLayout rootLayout,
            final View originalView,
            final PriceHistoryChartView chartView
    ) {
        // Start INVISIBLE so it never flashes at the top of the screen over the product image
        chartView.setVisibility(View.INVISIBLE);

        final ViewTreeObserver vto = rootLayout.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            private int lastY = Integer.MIN_VALUE;

            @Override
            public boolean onPreDraw() {
                try {
                    if (chartView.isDismissed()) {
                        if (chartView.getVisibility() != View.GONE) {
                            chartView.setVisibility(View.GONE);
                        }
                        return true;
                    }

                    int priceBottom = findPriceBottom(originalView, rootLayout);
                    if (priceBottom != Integer.MIN_VALUE) {
                        if (priceBottom != lastY) {
                            lastY = priceBottom;
                            chartView.setTranslationY(priceBottom);
                        }

                        int chartH = chartView.getHeight();
                        int rootH = rootLayout.getHeight();
                        if (chartH > 0 && rootH > 0) {
                            if (priceBottom + chartH < 0 || priceBottom > rootH) {
                                if (chartView.getVisibility() != View.INVISIBLE) {
                                    chartView.setVisibility(View.INVISIBLE);
                                }
                            } else {
                                if (chartView.getVisibility() != View.VISIBLE) {
                                    chartView.setVisibility(View.VISIBLE);
                                }
                            }
                        } else {
                            if (chartView.getVisibility() != View.VISIBLE) {
                                chartView.setVisibility(View.VISIBLE);
                            }
                        }
                    } else {
                        // Price node not located yet or scrolled off: hide cleanly
                        if (chartView.getVisibility() != View.INVISIBLE) {
                            chartView.setVisibility(View.INVISIBLE);
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "onPreDraw error", t);
                }
                return true;
            }
        });
    }

    private static int findPriceBottom(View originalView, View rootLayout) {
        View composeRoot = findAndroidComposeView(originalView);
        if (composeRoot == null) composeRoot = originalView;

        AccessibilityNodeInfo rootInfo = composeRoot.createAccessibilityNodeInfo();
        if (rootInfo == null && composeRoot != originalView) {
            rootInfo = originalView.createAccessibilityNodeInfo();
        }

        if (rootInfo != null) {
            try {
                Rect foundBounds = new Rect();
                boolean found = searchPriceNode(rootInfo, foundBounds);
                if (found && foundBounds.bottom > 0) {
                    int[] rootLoc = new int[2];
                    rootLayout.getLocationOnScreen(rootLoc);

                    int spacing = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            8,
                            originalView.getResources().getDisplayMetrics()
                    );

                    int y = (foundBounds.bottom - rootLoc[1]) + spacing;
                    return y;
                }
            } catch (Throwable t) {
                Log.e(TAG, "findPriceBottom error", t);
            } finally {
                try {
                    rootInfo.recycle();
                } catch (Throwable ignored) {}
            }
        }

        return Integer.MIN_VALUE;
    }

    private static boolean searchPriceNode(AccessibilityNodeInfo node, Rect outBounds) {
        if (node == null) return false;

        // 1. Check children first for deepest/tightest leaf match
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    if (searchPriceNode(child, outBounds)) {
                        return true;
                    }
                } finally {
                    try {
                        child.recycle();
                    } catch (Throwable ignored) {}
                }
            }
        }

        // 2. Check if this node itself contains price info
        CharSequence desc = node.getContentDescription();
        CharSequence text = node.getText();
        String descStr = desc != null ? desc.toString() : "";
        String textStr = text != null ? text.toString() : "";

        boolean matches = false;
        if (activeSecondaryPrice != null && !activeSecondaryPrice.isEmpty()) {
            if (descStr.contains(activeSecondaryPrice) || textStr.contains(activeSecondaryPrice)) {
                matches = true;
            }
        }
        if (!matches && (descStr.contains("bez DPH") || textStr.contains("bez DPH"))) {
            matches = true;
        }
        if (!matches && activePrimaryPrice != null && !activePrimaryPrice.isEmpty()) {
            if (descStr.contains(activePrimaryPrice) || textStr.contains(activePrimaryPrice)) {
                matches = true;
            }
        }

        if (matches) {
            node.getBoundsInScreen(outBounds);
            if (outBounds.bottom > 0) {
                return true;
            }
        }

        return false;
    }

    private static View findAndroidComposeView(View view) {
        if (view == null) return null;
        String name = view.getClass().getName();
        if (name.contains("AndroidComposeView") || (name.startsWith("androidx.compose.ui.platform.") && !name.endsWith("ComposeView"))) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View found = findAndroidComposeView(vg.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void extractPriceStrings(Object productPrices) {
        try {
            if (productPrices == null) return;
            activePriceModel = productPrices;

            Method getPriceMethod = productPrices.getClass().getMethod("getPrice");
            Object priceObj = getPriceMethod.invoke(productPrices);
            if (priceObj == null) return;

            String[] primaryMethodNames = new String[]{"getPrimaryPrice", "getPrice", "getPriceWithVat", "getFormattedPrice", "getText"};
            for (String name : primaryMethodNames) {
                try {
                    Method m = priceObj.getClass().getMethod(name);
                    Object res = m.invoke(priceObj);
                    if (res != null && !res.toString().trim().isEmpty()) {
                        activePrimaryPrice = res.toString().trim();
                        Log.i(TAG, "extractPriceStrings: activePrimaryPrice=" + activePrimaryPrice + " (via " + name + ")");
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            String[] secondaryMethodNames = new String[]{"getSecondaryPrice", "getPriceWithoutVat"};
            for (String name : secondaryMethodNames) {
                try {
                    Method m = priceObj.getClass().getMethod(name);
                    Object res = m.invoke(priceObj);
                    if (res != null && !res.toString().trim().isEmpty()) {
                        activeSecondaryPrice = res.toString().trim();
                        Log.i(TAG, "extractPriceStrings: activeSecondaryPrice=" + activeSecondaryPrice + " (via " + name + ")");
                        break;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in extractPriceStrings", t);
        }
    }

    private static void reserveComposeSpace(Object composer, float heightDp) {
        try {
            ClassLoader cl = composer.getClass().getClassLoader();
            if (cl == null) return;

            Class<?> bihClass = cl.loadClass("bih");
            Field bField = bihClass.getField("b");
            Object modifierInstance = bField.get(null);

            Class<?> jppClass = cl.loadClass("jpp");
            Method fMethod = null;
            for (Method m : jppClass.getMethods()) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 2 && (pts[1] == float.class || pts[1] == Float.class)) {
                    fMethod = m;
                    break;
                }
            }
            if (fMethod == null) {
                Log.w(TAG, "reserveComposeSpace: could not find jpp height method");
                return;
            }
            Object sizedModifier = fMethod.invoke(null, modifierInstance, heightDp);

            Class<?> sf3Class = cl.loadClass("sf3");
            Method spacerMethod = null;
            for (Method m : sf3Class.getMethods()) {
                Class<?>[] pTypes = m.getParameterTypes();
                if (pTypes.length == 3 && pTypes[0].isInstance(sizedModifier) && pTypes[2] == int.class) {
                    spacerMethod = m;
                    break;
                }
            }
            if (spacerMethod != null) {
                spacerMethod.invoke(null, sizedModifier, composer, 6);
                Log.i(TAG, "reserveComposeSpace: successfully emitted " + heightDp + "dp spacer via sf3." + spacerMethod.getName());
            } else {
                Log.w(TAG, "reserveComposeSpace: could not find sf3 spacer method");
            }
        } catch (Throwable t) {
            Log.e(TAG, "reserveComposeSpace error: " + t.getMessage(), t);
        }
    }

    private static Object findGodFragment(Object obj) {
        if (obj == null) return null;
        if (hasMethod(obj.getClass(), "U")) {
            return obj;
        }

        // Check 1 level of fields
        try {
            for (Field f : obj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(obj);
                if (val != null) {
                    if (hasMethod(val.getClass(), "U")) {
                        return val;
                    }
                    // Check 2nd level
                    try {
                        for (Field f2 : val.getClass().getDeclaredFields()) {
                            f2.setAccessible(true);
                            Object val2 = f2.get(val);
                            if (val2 != null && hasMethod(val2.getClass(), "U")) {
                                return val2;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean hasMethod(Class<?> clazz, String name) {
        try {
            clazz.getMethod(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object extractProductParams(Object destination) {
        if (destination == null) return null;
        try {
            Field fa = destination.getClass().getField("a");
            Object val = fa.get(destination);
            if (val != null) return val;
        } catch (Throwable ignored) {}

        for (Field f : destination.getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object val = f.get(destination);
                if (val != null && val.getClass().getName().contains("ProductDetailParams")) {
                    return val;
                }
            } catch (Throwable ignored) {}
        }

        for (Method m : destination.getClass().getDeclaredMethods()) {
            try {
                if (m.getParameterTypes().length == 0 && m.getReturnType().getName().contains("ProductDetailParams")) {
                    m.setAccessible(true);
                    Object val = m.invoke(destination);
                    if (val != null) return val;
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static String extractCommodityId(Object params, Object destination) {
        if (params != null) {
            // 1. Try getProductId() on params
            try {
                Method m = params.getClass().getMethod("getProductId");
                Object res = m.invoke(params);
                if (res instanceof Number && ((Number) res).intValue() > 0) {
                    return String.valueOf(((Number) res).intValue());
                }
            } catch (Throwable ignored) {}

            // 2. Try declared fields for productId on params
            for (Field f : params.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    if (f.getName().equalsIgnoreCase("productId") || f.getName().equalsIgnoreCase("id")) {
                        Object val = f.get(params);
                        if (val instanceof Number && ((Number) val).intValue() > 0) {
                            return String.valueOf(((Number) val).intValue());
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 3. Try getMeta() -> getHref() on params (for WithMeta)
            try {
                Method m = params.getClass().getMethod("getMeta");
                Object meta = m.invoke(params);
                if (meta != null) {
                    Method mh = meta.getClass().getMethod("getHref");
                    Object href = mh.invoke(meta);
                    if (href != null) {
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("/product/([0-9]+)").matcher(href.toString());
                        if (matcher.find()) {
                            return matcher.group(1);
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 4. Try regex on params.toString()
            try {
                String str = params.toString();
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:/product/|productId=)([0-9]+)").matcher(str);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Throwable ignored) {}
        }

        // 5. Fallback: regex on destination.toString()
        if (destination != null) {
            try {
                String destStr = destination.toString();
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:/product/|productId=)([0-9]+)").matcher(destStr);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    private static String extractProductName(Object params) {
        if (params == null) return null;
        try {
            Method m = params.getClass().getMethod("getProductInfo");
            Object info = m.invoke(params);
            if (info != null) {
                Method mn = info.getClass().getMethod("getName");
                Object nameObj = mn.invoke(info);
                if (nameObj != null) return nameObj.toString();
            }
        } catch (Throwable ignored) {}
        return null;
    }

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
                flp.gravity = Gravity.BOTTOM;
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
        Log.i(TAG, "fetchData: requesting data for commodityId=" + commodityId);
        HlidacShopuClient.getInstance().fetchPriceHistory(
                commodityId,
                null,
                identifier,
                productName,
                new HlidacShopuClient.Callback() {
                    @Override
                    public void onSuccess(PriceHistoryData data) {
                        Log.i(TAG, "fetchData onSuccess: hasData=" + (data != null && data.hasData()));
                        chartView.setData(data);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e(TAG, "fetchData onError: " + throwable.getMessage(), throwable);
                        chartView.setState(PriceHistoryChartView.State.ERROR);
                    }
                }
        );
    }
}
