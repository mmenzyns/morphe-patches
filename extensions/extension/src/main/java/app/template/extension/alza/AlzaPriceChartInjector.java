package app.template.extension.alza;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AlzaPriceChartInjector {
    private static final String TAG = "MorpheAlza";
    public static final String CHART_VIEW_TAG = "MORPHE_HLIDAC_CHART";

    public static final String PREFS_NAME = "morphe_alza_prefs";
    public static final String PREF_COMPACT_MODE = "compact_mode";

    // Application context reference for SharedPreferences
    private static volatile Context sAppContext = null;
    private static volatile Boolean sCompactModeCache = null;
    private static volatile WeakReference<Activity> sCurrentActivity = null;
    private static volatile boolean sLifecycleRegistered = false;

    // Market / Country detection
    private static volatile String sExplicitCurrency = null;
    private static volatile String sDetectedDomain = null;
    private static volatile String sDetectedCurrency = null;
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
        if (sAppContext != null) return sAppContext;
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method m = atClass.getMethod("currentApplication");
            Context app = (Context) m.invoke(null);
            if (app != null) {
                setApplicationContext(app);
                return sAppContext;
            }
        } catch (Throwable ignored) {}
        return sAppContext;
    }

    public static void setApplicationContext(Context context) {
        if (context != null) {
            if (sAppContext == null) {
                sAppContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            }
            if (sAppContext instanceof Application && !sLifecycleRegistered) {
                sLifecycleRegistered = true;
                ((Application) sAppContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityResumed(Activity activity) { sCurrentActivity = new WeakReference<>(activity); }
                    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { sCurrentActivity = new WeakReference<>(activity); }
                    @Override public void onActivityStarted(Activity activity) { sCurrentActivity = new WeakReference<>(activity); }
                    @Override public void onActivityPaused(Activity activity) {}
                    @Override public void onActivityStopped(Activity activity) {}
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                    @Override public void onActivityDestroyed(Activity activity) {
                        if (sCurrentActivity != null && sCurrentActivity.get() == activity) {
                            sCurrentActivity = null;
                        }
                    }
                });
            }
            if (isSlovakLocale(context)) {
                if (sExplicitCurrency == null) {
                    sDetectedDomain = "alza.sk";
                    sDetectedCurrency = "€";
                    sIsCountrySupported = true;
                }
            }
        }
    }

    public static Activity getCurrentActivity() {
        if (sCurrentActivity != null) {
            Activity act = sCurrentActivity.get();
            if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                return act;
            }
        }
        return findTopActivity();
    }

    private static Activity findTopActivity() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Method currentAtMethod = atClass.getMethod("currentActivityThread");
            Object at = currentAtMethod.invoke(null);
            Field mActivitiesField = atClass.getDeclaredField("mActivities");
            mActivitiesField.setAccessible(true);
            Map<?, ?> mActivities = (Map<?, ?>) mActivitiesField.get(at);
            if (mActivities != null) {
                for (Object activityRecord : mActivities.values()) {
                    Field pausedField = activityRecord.getClass().getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (!pausedField.getBoolean(activityRecord)) {
                        Field activityField = activityRecord.getClass().getDeclaredField("activity");
                        activityField.setAccessible(true);
                        Activity top = (Activity) activityField.get(activityRecord);
                        if (top != null) {
                            sCurrentActivity = new WeakReference<>(top);
                            return top;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isSlovak() {
        return isSlovak(sAppContext);
    }

    public static boolean isSlovak(Context context) {
        if ("€".equals(sExplicitCurrency)) {
            return true;
        }
        if ("Kč".equals(sExplicitCurrency) || "Ft".equals(sExplicitCurrency)) {
            return false;
        }
        return isSlovakLocale(context != null ? context : sAppContext);
    }

    public static boolean isSlovakLocale(Context context) {
        if (context != null) {
            try {
                Resources res = context.getResources();
                if (res != null) {
                    Configuration conf = res.getConfiguration();
                    if (conf != null) {
                        Locale loc = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && conf.getLocales() != null && !conf.getLocales().isEmpty()) {
                            loc = conf.getLocales().get(0);
                        } else {
                            loc = conf.locale;
                        }
                        if (loc != null) {
                            String lang = loc.getLanguage();
                            String country = loc.getCountry();
                            if ("sk".equalsIgnoreCase(lang) || "SK".equalsIgnoreCase(country)) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        try {
            Locale def = Locale.getDefault();
            if (def != null) {
                if ("sk".equalsIgnoreCase(def.getLanguage()) || "SK".equalsIgnoreCase(def.getCountry())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Checks if Compact Mode is enabled.
     * In Compact Mode: HomeCredit/alternative price is removed, and chart is smaller (~135dp).
     */
    public static boolean isCompactMode() {
        if (sCompactModeCache != null) {
            return sCompactModeCache.booleanValue();
        }
        Context ctx = getApplicationContext();
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
        Context ctx = getApplicationContext();
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
        if ("alza.sk".equalsIgnoreCase(sDetectedDomain) || "€".equals(sExplicitCurrency)) {
            return "alza.sk";
        }
        if ("alza.hu".equalsIgnoreCase(sDetectedDomain) || "Ft".equals(sExplicitCurrency)) {
            return "alza.hu";
        }
        if (isSlovak()) {
            return "alza.sk";
        }
        return sDetectedDomain != null ? sDetectedDomain : "alza.cz";
    }

    public static String getDetectedCurrency() {
        if ("€".equals(sExplicitCurrency) || "alza.sk".equalsIgnoreCase(sDetectedDomain)) {
            return "€";
        }
        if ("Ft".equals(sExplicitCurrency) || "alza.hu".equalsIgnoreCase(sDetectedDomain)) {
            return "Ft";
        }
        if (isSlovak()) {
            return "€";
        }
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
            Log.i(TAG, "filterAlternativePrice: Compact mode active -> eliminating HomeCredit (returning null)");
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
        if (productPrices == null) return;
        try {
            String str = productPrices.toString();
            if (str.contains("€")) {
                sExplicitCurrency = "€";
                sDetectedCurrency = "€";
                sDetectedDomain = "alza.sk";
                sIsCountrySupported = true;
                return;
            } else if (str.contains("Kč") || str.contains("Kc")) {
                sExplicitCurrency = "Kč";
                sDetectedCurrency = "Kč";
                sDetectedDomain = "alza.cz";
                sIsCountrySupported = true;
                return;
            } else if (str.contains("Ft") || str.contains("HUF")) {
                sExplicitCurrency = "Ft";
                sDetectedCurrency = "Ft";
                sDetectedDomain = "alza.hu";
                sIsCountrySupported = false;
                return;
            }

            Method mGetPrice = productPrices.getClass().getMethod("getPrice");
            Object priceObj = mGetPrice.invoke(productPrices);
            if (priceObj != null) {
                String pStr = priceObj.toString();
                if (pStr.contains("€")) {
                    sExplicitCurrency = "€";
                    sDetectedCurrency = "€";
                    sDetectedDomain = "alza.sk";
                    sIsCountrySupported = true;
                    return;
                } else if (pStr.contains("Kč") || pStr.contains("Kc")) {
                    sExplicitCurrency = "Kč";
                    sDetectedCurrency = "Kč";
                    sDetectedDomain = "alza.cz";
                    sIsCountrySupported = true;
                    return;
                } else if (pStr.contains("Ft") || pStr.contains("HUF")) {
                    sExplicitCurrency = "Ft";
                    sDetectedCurrency = "Ft";
                    sDetectedDomain = "alza.hu";
                    sIsCountrySupported = false;
                    return;
                }

                Method mPrimary = priceObj.getClass().getMethod("getPrimaryPrice");
                Object primary = mPrimary.invoke(priceObj);
                String primaryStr = primary != null ? primary.toString() : "";
                if (primaryStr.contains("€")) {
                    sExplicitCurrency = "€";
                    sDetectedCurrency = "€";
                    sDetectedDomain = "alza.sk";
                    sIsCountrySupported = true;
                } else if (primaryStr.contains("Kč") || primaryStr.contains("Kc")) {
                    sExplicitCurrency = "Kč";
                    sDetectedCurrency = "Kč";
                    sDetectedDomain = "alza.cz";
                    sIsCountrySupported = true;
                } else if (primaryStr.contains("Ft") || primaryStr.contains("HUF")) {
                    sExplicitCurrency = "Ft";
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
                String newId = String.valueOf(productId);
                if (!newId.equals(activeCommodityId)) {
                    sExplicitCurrency = null;
                }
                activeCommodityId = newId;
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
                            String newId = matcher.group(1);
                            if (!newId.equals(activeCommodityId)) {
                                sExplicitCurrency = null;
                            }
                            activeCommodityId = newId;
                        }
                        if (hrefStr.contains(".sk") || hrefStr.contains("/sk/")) {
                            sExplicitCurrency = "€";
                            sDetectedDomain = "alza.sk";
                            sDetectedCurrency = "€";
                            sIsCountrySupported = true;
                        } else if (hrefStr.contains(".cz") || hrefStr.contains("/cz/")) {
                            sExplicitCurrency = "Kč";
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
                            sExplicitCurrency = "Ft";
                            sDetectedDomain = "alza.hu";
                            sDetectedCurrency = "Ft";
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
        String domain = getDetectedDomain();
        Log.i(TAG, "fetchData: requesting data for commodityId=" + commodityId + " (domain=" + domain + ", currency=" + getDetectedCurrency() + ")");
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

    /**
     * Injects "Morphe – Hlídač shopů" menu item into Alza's Account Menu list (k2f) under Nastavení.
     */
    public static Object injectMorpheMenuItem(Object k2fObj) {
        if (k2fObj == null) return null;
        try {
            Field bField = k2fObj.getClass().getField("b");
            bField.setAccessible(true);
            List<?> items = (List<?>) bField.get(k2fObj);
            if (items == null || items.isEmpty()) return k2fObj;

            // Check if already injected
            for (Object item : items) {
                if (item != null && item.toString().contains("MenuAccountMorphe")) {
                    return k2fObj;
                }
            }

            int targetIdx = -1;
            Object templateI2f = null;
            for (int i = 0; i < items.size(); i++) {
                Object i2fObj = items.get(i);
                if (i2fObj != null) {
                    String str = i2fObj.toString();
                    if (str.contains("MenuAccountDarkTheme") || str.contains("MenuAccountAppLanguage") || str.contains("ThemeSettings")) {
                        targetIdx = i;
                        templateI2f = i2fObj;
                        break;
                    }
                }
            }

            if (templateI2f == null) {
                for (int i = items.size() - 1; i >= 0; i--) {
                    Object i2fObj = items.get(i);
                    if (i2fObj != null) {
                        try {
                            Field itemField = i2fObj.getClass().getField("b");
                            itemField.setAccessible(true);
                            Object menuItem = itemField.get(i2fObj);
                            if (menuItem != null && menuItem.getClass().getName().contains("Button")) {
                                targetIdx = i;
                                templateI2f = i2fObj;
                                break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }

            if (templateI2f != null) {
                Field i2fBField = templateI2f.getClass().getField("b");
                i2fBField.setAccessible(true);
                Object templateButton = i2fBField.get(templateI2f);

                Object morpheButton = createMorpheMenuButton(templateButton);
                if (morpheButton != null) {
                    Constructor<?> i2fCtor = templateI2f.getClass().getConstructor(String.class, Object.class);
                    Object morpheI2f = i2fCtor.newInstance("MenuAccountMorphe", morpheButton);

                    List<Object> newItems = new ArrayList<>(items);
                    newItems.add(targetIdx + 1, morpheI2f);

                    Constructor<?> k2fCtor = k2fObj.getClass().getConstructor(List.class);
                    Log.i(TAG, "Successfully injected MenuAccountMorphe at position " + (targetIdx + 1));
                    return k2fCtor.newInstance(newItems);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error injecting Morphe menu item", t);
        }
        return k2fObj;
    }

    private static Object createMorpheMenuButton(Object templateButton) {
        if (templateButton == null) return null;
        try {
            Class<?> btnClass = templateButton.getClass();
            Field titleField = btnClass.getDeclaredField("title");
            titleField.setAccessible(true);
            Field descField = btnClass.getDeclaredField("description");
            descField.setAccessible(true);
            Field iconField = btnClass.getDeclaredField("iconId");
            iconField.setAccessible(true);
            Field colorTintField = btnClass.getDeclaredField("colorTint");
            colorTintField.setAccessible(true);
            Field onClickedField = btnClass.getDeclaredField("onClicked");
            onClickedField.setAccessible(true);
            Field isIconGoneField = btnClass.getDeclaredField("isIconGoneForNull");
            isIconGoneField.setAccessible(true);
            Field tagField = btnClass.getDeclaredField("tag");
            tagField.setAccessible(true);
            Field enabledField = btnClass.getDeclaredField("enabled");
            enabledField.setAccessible(true);
            Field isNewField = btnClass.getDeclaredField("isNew");
            isNewField.setAccessible(true);
            Field isTestField = btnClass.getDeclaredField("isTest");
            isTestField.setAccessible(true);
            Field isBlockedField = btnClass.getDeclaredField("isBlockedInOfflineMode");
            isBlockedField.setAccessible(true);

            ClassLoader cl = templateButton.getClass().getClassLoader();
            Class<?> ftrClass = cl.loadClass("cz.alza.base.utils.text.format.model.data.FormattedTextResource");
            Field compField = ftrClass.getField("Companion");
            Object comp = compField.get(null);
            Class<?> textFormatClass = cl.loadClass("cz.alza.base.utils.text.format.model.data.TextFormat");
            Object emptyFormatArray = Array.newInstance(textFormatClass, 0);
            Method fromTextMethod = comp.getClass().getMethod("fromText", String.class, emptyFormatArray.getClass());

            boolean sk = isSlovak(getApplicationContext());
            String titleStr = sk ? "Morphe – Hlídač shopov" : "Morphe – Hlídač shopů";
            Object titleObj = fromTextMethod.invoke(comp, titleStr, emptyFormatArray);

            for (Constructor<?> ctor : btnClass.getConstructors()) {
                Class<?>[] pTypes = ctor.getParameterTypes();
                if (pTypes.length == 12 && pTypes[0] == ftrClass && pTypes[11] == String.class) {
                    return ctor.newInstance(
                            titleObj,
                            null,
                            iconField.get(templateButton),
                            colorTintField.get(templateButton),
                            onClickedField.get(templateButton),
                            isIconGoneField.getBoolean(templateButton),
                            "MenuAccountMorphe",
                            true,
                            false,
                            false,
                            false,
                            "MenuAccountMorphe"
                    );
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error creating Morphe menu button", t);
        }
        return null;
    }

    /**
     * Intercepts clicks on menu buttons and opens Morphe settings dialog when Morphe item is clicked.
     */
    public static Object wrapMenuButtonAction(final Object originalAction) {
        if (originalAction == null) return null;
        try {
            Class<?>[] interfaces = originalAction.getClass().getInterfaces();
            if (interfaces == null || interfaces.length == 0) {
                Class<?> fn1Class = originalAction.getClass().getClassLoader().loadClass("kotlin.jvm.functions.Function1");
                interfaces = new Class<?>[]{fn1Class};
            }
            return Proxy.newProxyInstance(
                    originalAction.getClass().getClassLoader(),
                    interfaces,
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("invoke".equals(method.getName()) && args != null && args.length == 1) {
                                Object button = args[0];
                                if (button != null) {
                                    try {
                                        Method getIdMethod = button.getClass().getMethod("getId");
                                        Object id = getIdMethod.invoke(button);
                                        if ("MenuAccountMorphe".equals(id)) {
                                            Log.i(TAG, "Morphe menu item clicked! Opening settings dialog");
                                            openMorpheSettings();
                                            return null;
                                        }
                                    } catch (Throwable t) {
                                        Log.w(TAG, "Error checking button id", t);
                                    }
                                }
                            }
                            return method.invoke(originalAction, args);
                        }
                    }
            );
        } catch (Throwable t) {
            Log.e(TAG, "Error wrapping menu button action", t);
            return originalAction;
        }
    }

    public static void openMorpheSettings() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Activity act = getCurrentActivity();
                if (act != null) {
                    MorpheSettingsDialog.show(act, null);
                } else {
                    Log.w(TAG, "No foreground activity to show Morphe settings dialog");
                }
            }
        });
    }
}
