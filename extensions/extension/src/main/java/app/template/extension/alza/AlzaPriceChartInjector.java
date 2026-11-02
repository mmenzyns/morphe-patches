package app.template.extension.alza;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AlzaPriceChartInjector {
    private static final String TAG = "MorpheAlza";
    public static final String CHART_VIEW_TAG = "MORPHE_HLIDAC_CHART";

    public static final String PREFS_NAME = "morphe_alza_prefs";
    public static final String PREF_COMPACT_MODE = "compact_mode";

    // Application context reference for SharedPreferences
    private static volatile Context sAppContext = null;
    private static volatile Boolean sCompactModeCache = null;

    // Market / Country detection (defaults to CZ)
    private static volatile String sDetectedDomain = "alza.cz";
    private static volatile String sDetectedCurrency = "Kč";
    private static volatile boolean sIsCountrySupported = true;
    private static volatile Field sAlternativePriceField = null;

    // Dynamic bindings populated by patch fingerprints (no hardcoded obfuscated names)
    private static volatile String sAndroidViewClass = null;
    private static volatile String sAndroidViewMethod = null;
    private static volatile String sFunction1Class = null;
    private static volatile Method sAndroidViewResolvedMethod = null;
    private static volatile Object sFactoryProxy = null;
    private static volatile Object sUpdateProxy = null;
    private static volatile Object sSettingsFactoryProxy = null;
    private static volatile ClassLoader sLastClassLoader = null;

    // Captured product info
    private static volatile String activeCommodityId = null;
    private static volatile String activeProductName = null;
    private static volatile WeakReference<Object> lastGodFragment = new WeakReference<Object>(null);

    /**
     * Initialized by bytecode hook with names discovered by Morphe fingerprints.
     */
    public static void initComposeBindings(String androidViewClass, String androidViewMethod, String function1Class) {
        sAndroidViewClass = androidViewClass;
        sAndroidViewMethod = androidViewMethod;
        sFunction1Class = function1Class;
        Log.i(TAG, "initComposeBindings: androidView=" + sAndroidViewClass + "." + sAndroidViewMethod + ", function1=" + sFunction1Class);
    }

    public static Context getApplicationContext() {
        return sAppContext;
    }

    public static void setApplicationContext(Context context) {
        if (context != null && sAppContext == null) {
            sAppContext = context.getApplicationContext();
        }
    }

    /**
     * Checks if Compact Mode is enabled.
     * In Compact Mode: HomeCredit/alternative price is removed, and chart is smaller (~135dp).
     */
    public static boolean isCompactMode() {
        if (sCompactModeCache != null) {
            return sCompactModeCache.booleanValue();
        }
        Context ctx = sAppContext;
        if (ctx != null) {
            try {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                boolean val = prefs.getBoolean(PREF_COMPACT_MODE, false);
                sCompactModeCache = Boolean.valueOf(val);
                return val;
            } catch (Throwable t) {
                Log.w(TAG, "isCompactMode error reading prefs", t);
            }
        }
        return false;
    }

    public static void setCompactMode(boolean compact) {
        sCompactModeCache = Boolean.valueOf(compact);
        Context ctx = sAppContext;
        if (ctx != null) {
            try {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putBoolean(PREF_COMPACT_MODE, compact).apply();
            } catch (Throwable t) {
                Log.w(TAG, "setCompactMode error saving prefs", t);
            }
        }
    }

    public static String getDetectedDomain() {
        return sDetectedDomain != null ? sDetectedDomain : "alza.cz";
    }

    public static String getDetectedCurrency() {
        return sDetectedCurrency != null ? sDetectedCurrency : "Kč";
    }

    public static boolean isCountrySupported() {
        return sIsCountrySupported;
    }

    /**
     * Hooked at index 0 of ProductPrices.getAlternativePrice().
     * In Compact Mode, returns null to eliminate the entire HomeCredit / installments block.
     * In normal mode, returns the original AlternativePrice.
     * Also detects market and currency from ProductPrices.
     */
    public static Object filterAlternativePrice(Object productPrices) {
        if (productPrices != null) {
            detectMarketFromPrices(productPrices);
        }
        if (isCompactMode()) {
            return null;
        }
        return getDirectAlternativePrice(productPrices);
    }

    private static Object getDirectAlternativePrice(Object productPrices) {
        if (productPrices == null) return null;
        try {
            if (sAlternativePriceField == null) {
                Field f = productPrices.getClass().getDeclaredField("alternativePrice");
                f.setAccessible(true);
                sAlternativePriceField = f;
            }
            return sAlternativePriceField.get(productPrices);
        } catch (Throwable t) {
            Log.e(TAG, "Error accessing alternativePrice field", t);
            return null;
        }
    }

    public static void detectMarketFromPrices(Object productPrices) {
        try {
            Method mGetPrice = productPrices.getClass().getMethod("getPrice");
            Object priceObj = mGetPrice.invoke(productPrices);
            if (priceObj != null) {
                Method mPrimary = priceObj.getClass().getMethod("getPrimaryPrice");
                Object primary = mPrimary.invoke(priceObj);
                String primaryStr = primary != null ? primary.toString() : "";

                if (primaryStr.contains("€")) {
                    sDetectedCurrency = "€";
                    sDetectedDomain = "alza.sk";
                    sIsCountrySupported = true;
                } else if (primaryStr.contains("Kč") || primaryStr.contains("Kc")) {
                    sDetectedCurrency = "Kč";
                    sDetectedDomain = "alza.cz";
                    sIsCountrySupported = true;
                } else if (primaryStr.contains("Ft") || primaryStr.contains("HUF")) {
                    sDetectedCurrency = "Ft";
                    sDetectedDomain = "alza.hu";
                    sIsCountrySupported = false;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Hooked on ProductDetailParams$WithId constructor to capture product ID cleanly.
     */
    public static void onProductDetailParams(int productId, Object productInfo) {
        try {
            if (productId > 0) {
                activeCommodityId = String.valueOf(productId);
                if (productInfo != null) {
                    try {
                        Method m = productInfo.getClass().getMethod("getName");
                        Object name = m.invoke(productInfo);
                        if (name != null) {
                            activeProductName = name.toString();
                        }
                    } catch (Throwable ignored) {}
                }
                Log.i(TAG, "onProductDetailParams: Captured product id=" + activeCommodityId + ", name=" + activeProductName);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in onProductDetailParams", t);
        }
    }

    /**
     * Hooked on ProductDetailParams$WithMeta constructor to capture product ID and market from Meta.
     */
    public static void onProductDetailParamsWithMeta(Object meta, Object productInfo) {
        try {
            if (meta != null) {
                try {
                    Method mh = meta.getClass().getMethod("getHref");
                    Object href = mh.invoke(meta);
                    if (href != null) {
                        String hrefStr = href.toString();
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:/product/|productId=|-d)([0-9]+)").matcher(hrefStr);
                        if (matcher.find()) {
                            activeCommodityId = matcher.group(1);
                        }
                        if (hrefStr.contains(".sk") || hrefStr.contains("/sk/")) {
                            sDetectedDomain = "alza.sk";
                            sDetectedCurrency = "€";
                            sIsCountrySupported = true;
                        } else if (hrefStr.contains(".cz") || hrefStr.contains("/cz/")) {
                            sDetectedDomain = "alza.cz";
                            sDetectedCurrency = "Kč";
                            sIsCountrySupported = true;
                        } else if (hrefStr.contains(".de") || hrefStr.contains("/de/")) {
                            sDetectedDomain = "alza.de";
                            sIsCountrySupported = false;
                        } else if (hrefStr.contains(".at") || hrefStr.contains("/at/")) {
                            sDetectedDomain = "alza.at";
                            sIsCountrySupported = false;
                        } else if (hrefStr.contains(".hu") || hrefStr.contains("/hu/")) {
                            sDetectedDomain = "alza.hu";
                            sIsCountrySupported = false;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (productInfo != null) {
                try {
                    Method m = productInfo.getClass().getMethod("getName");
                    Object name = m.invoke(productInfo);
                    if (name != null) {
                        activeProductName = name.toString();
                    }
                } catch (Throwable ignored) {}
            }
            Log.i(TAG, "onProductDetailParamsWithMeta: Captured product id=" + activeCommodityId + ", name=" + activeProductName);
        } catch (Throwable t) {
            Log.e(TAG, "Error in onProductDetailParamsWithMeta", t);
        }
    }

    /**
     * Lightweight hook on GodFragment.onCreateView entry to keep a weak reference for fallback resolution.
     */
    public static void onFragmentCreateViewStart(Object fragment) {
        try {
            if (fragment != null) {
                Object god = findGodFragment(fragment);
                if (god != null) {
                    lastGodFragment = new WeakReference<Object>(god);
                    resolveProductFromGodFragment(god);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in onFragmentCreateViewStart", t);
        }
    }

    private static Method resolveAndroidViewMethod(ClassLoader cl) {
        if (sAndroidViewResolvedMethod != null && sLastClassLoader == cl) {
            return sAndroidViewResolvedMethod;
        }
        Class<?> androidViewOwnerClass = null;
        if (sAndroidViewClass != null && !sAndroidViewClass.isEmpty()) {
            try {
                androidViewOwnerClass = cl.loadClass(sAndroidViewClass);
            } catch (Throwable ignored) {}
        }
        if (androidViewOwnerClass != null) {
            String methodName = sAndroidViewMethod != null ? sAndroidViewMethod : "a";
            for (Method m : androidViewOwnerClass.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterTypes().length == 6) {
                    sAndroidViewResolvedMethod = m;
                    break;
                }
            }
        }
        return sAndroidViewResolvedMethod;
    }

    /**
     * Injects MorpheSettingsCardView into Alza's Compose settings screens.
     */
    public static void onSettingsCompose(Object composer) {
        try {
            if (composer == null) return;
            ClassLoader cl = composer.getClass().getClassLoader();
            if (cl == null) return;

            Method androidViewMethod = resolveAndroidViewMethod(cl);
            if (androidViewMethod == null) return;

            Class<?> vwcClass = androidViewMethod.getParameterTypes()[0];
            if (sSettingsFactoryProxy == null || sLastClassLoader != cl) {
                sLastClassLoader = cl;
                sSettingsFactoryProxy = Proxy.newProxyInstance(
                        cl,
                        new Class<?>[]{vwcClass},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                String name = method.getName();
                                if ("invoke".equals(name) && args != null && args.length == 1 && args[0] instanceof Context) {
                                    Context context = (Context) args[0];
                                    setApplicationContext(context);
                                    return new MorpheSettingsCardView(context);
                                }
                                if ("toString".equals(name)) return "MorpheSettingsFactoryProxy";
                                if ("hashCode".equals(name)) return Integer.valueOf(System.identityHashCode(proxy));
                                if ("equals".equals(name)) {
                                    return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
                                }
                                return null;
                            }
                        }
                );
            }

            // AndroidView(factory=sSettingsFactoryProxy, modifier=null, update=null, composer, changed=6, default=6)
            androidViewMethod.invoke(null, sSettingsFactoryProxy, null, null, composer, 6, 6);
            Log.i(TAG, "onSettingsCompose: emitted MorpheSettingsCardView");
        } catch (Throwable t) {
            Log.e(TAG, "Error in onSettingsCompose: " + t.getMessage(), t);
        }
    }

    /**
     * Natively emits Compose AndroidView (androidx.compose.ui.viewinterop.AndroidView)
     * at the end of the price section.
     */
    public static void onPriceSectionCompose(Object composer) {
        try {
            if (composer == null) return;

            if (activeCommodityId == null || activeCommodityId.isEmpty()) {
                resolveProductFromGodFragment(lastGodFragment.get());
            }

            ClassLoader cl = composer.getClass().getClassLoader();
            if (cl == null) return;

            Method androidViewMethod = resolveAndroidViewMethod(cl);
            if (androidViewMethod == null) {
                Log.e(TAG, "onPriceSectionCompose: AndroidView method not resolved");
                return;
            }

            Class<?> vwcClass = androidViewMethod.getParameterTypes()[0];

            if (sFactoryProxy == null || sUpdateProxy == null || sLastClassLoader != cl) {
                sLastClassLoader = cl;
                sFactoryProxy = Proxy.newProxyInstance(
                        cl,
                        new Class<?>[]{vwcClass},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                String name = method.getName();
                                if (name.equals("invoke") && args != null && args.length == 1 && args[0] instanceof Context) {
                                    Context context = (Context) args[0];
                                    setApplicationContext(context);

                                    PriceHistoryChartView chartView = new PriceHistoryChartView(context);
                                    chartView.setTag(CHART_VIEW_TAG);

                                    if (activeCommodityId == null || activeCommodityId.isEmpty()) {
                                        resolveProductFromGodFragment(lastGodFragment.get());
                                    }

                                    String commodityId = activeCommodityId;
                                    String productName = activeProductName;
                                    if (commodityId != null && !commodityId.isEmpty() && !commodityId.equals("0")) {
                                        chartView.setCommodityId(commodityId);
                                        fetchData(chartView, commodityId, null, productName);
                                    }
                                    Log.i(TAG, "Compose AndroidView factory: created native inline PriceHistoryChartView for " + commodityId);
                                    return chartView;
                                }
                                if (name.equals("toString")) return "PriceHistoryChartFactoryProxy";
                                if (name.equals("hashCode")) return Integer.valueOf(System.identityHashCode(proxy));
                                if (name.equals("equals")) {
                                    return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
                                }
                                if (method.getReturnType().equals(boolean.class)) return Boolean.FALSE;
                                if (method.getReturnType().equals(int.class)) return Integer.valueOf(0);
                                return null;
                            }
                        }
                );

                sUpdateProxy = Proxy.newProxyInstance(
                        cl,
                        new Class<?>[]{vwcClass},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                String name = method.getName();
                                if (name.equals("invoke") && args != null && args.length == 1) {
                                    if (args[0] instanceof PriceHistoryChartView) {
                                        PriceHistoryChartView chartView = (PriceHistoryChartView) args[0];
                                        setApplicationContext(chartView.getContext());

                                        if (activeCommodityId == null || activeCommodityId.isEmpty()) {
                                            resolveProductFromGodFragment(lastGodFragment.get());
                                        }
                                        String commodityId = activeCommodityId;
                                        if (commodityId != null && !commodityId.isEmpty() && !commodityId.equals("0")) {
                                            if (!commodityId.equals(chartView.getCommodityId())) {
                                                chartView.setCommodityId(commodityId);
                                                fetchData(chartView, commodityId, null, activeProductName);
                                            } else if (chartView.getData() == null && chartView.getState() != PriceHistoryChartView.State.LOADING) {
                                                fetchData(chartView, commodityId, null, activeProductName);
                                            }
                                        }
                                        Log.i(TAG, "Compose AndroidView update: chartView for " + chartView.getCommodityId() + " (state=" + chartView.getState() + ")");
                                    }
                                    return null;
                                }
                                if (name.equals("toString")) return "PriceHistoryChartUpdateProxy";
                                if (name.equals("hashCode")) return Integer.valueOf(System.identityHashCode(proxy));
                                if (name.equals("equals")) {
                                    return Boolean.valueOf(args != null && args.length > 0 && proxy == args[0]);
                                }
                                if (method.getReturnType().equals(boolean.class)) return Boolean.FALSE;
                                if (method.getReturnType().equals(int.class)) return Integer.valueOf(0);
                                return null;
                            }
                        }
                );
            }

            // Call AndroidView(factory, modifier=null, update=updateProxy, composer, changed=6, default=2)
            androidViewMethod.invoke(null, sFactoryProxy, null, sUpdateProxy, composer, 6, 2);
            Log.i(TAG, "onPriceSectionCompose: successfully emitted native inline AndroidView");

        } catch (Throwable t) {
            Log.e(TAG, "Error in onPriceSectionCompose: " + t.getMessage(), t);
        }
    }

    private static void resolveProductFromGodFragment(Object godFragment) {
        if (godFragment == null) return;
        try {
            Method[] methods = godFragment.getClass().getMethods();
            Object destination = null;
            for (Method m : methods) {
                if (m.getParameterTypes().length == 0 && m.getName().length() <= 2) {
                    try {
                        Object res = m.invoke(godFragment);
                        if (res != null && res.getClass().getName().contains("Destination")) {
                            destination = res;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            if (destination != null) {
                Object params = extractProductParams(destination);
                String commodityId = extractCommodityId(params, destination);
                String productName = extractProductName(params);
                if (commodityId != null && !commodityId.isEmpty() && !commodityId.equals("0")) {
                    activeCommodityId = commodityId;
                    activeProductName = productName;
                    Log.i(TAG, "resolveProductFromGodFragment: id=" + commodityId + ", name=" + productName);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "resolveProductFromGodFragment error: " + t.getMessage());
        }
    }

    private static Object findGodFragment(Object obj) {
        if (obj == null) return null;
        if (obj.getClass().getName().contains("GodFragment")) {
            return obj;
        }

        try {
            for (Field f : obj.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(obj);
                if (val != null && val.getClass().getName().contains("GodFragment")) {
                    return val;
                }
            }
        } catch (Throwable ignored) {}
        return null;
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
            try {
                Method m = params.getClass().getMethod("getProductId");
                Object res = m.invoke(params);
                if (res instanceof Number && ((Number) res).intValue() > 0) {
                    return String.valueOf(((Number) res).intValue());
                }
            } catch (Throwable ignored) {}

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

            try {
                Method m = params.getClass().getMethod("getMeta");
                Object meta = m.invoke(params);
                if (meta != null) {
                    Method mh = meta.getClass().getMethod("getHref");
                    Object href = mh.invoke(meta);
                    if (href != null) {
                        String hrefStr = href.toString();
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:/product/|productId=|-d)([0-9]+)").matcher(hrefStr);
                        if (matcher.find()) {
                            return matcher.group(1);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (destination != null) {
            try {
                String destStr = destination.toString();
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:/product/|productId=|-d)([0-9]+)").matcher(destStr);
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

    private static void fetchData(
            final PriceHistoryChartView chartView,
            final String commodityId,
            final String identifier,
            final String productName
    ) {
        Log.i(TAG, "fetchData: requesting data for commodityId=" + commodityId + " (domain=" + sDetectedDomain + ")");
        if (!sIsCountrySupported) {
            chartView.setState(PriceHistoryChartView.State.UNSUPPORTED_COUNTRY);
            return;
        }

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
