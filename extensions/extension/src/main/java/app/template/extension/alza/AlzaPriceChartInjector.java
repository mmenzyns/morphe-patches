package app.template.extension.alza;

import android.content.Context;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AlzaPriceChartInjector {
    private static final String TAG = "MorpheAlza";
    public static final String CHART_VIEW_TAG = "MORPHE_HLIDAC_CHART";

    // Dynamic bindings populated by patch fingerprints (no hardcoded obfuscated names)
    private static volatile String sAndroidViewClass = null;
    private static volatile String sAndroidViewMethod = null;
    private static volatile String sFunction1Class = null;
    private static volatile Method sAndroidViewResolvedMethod = null;
    private static volatile Object sFactoryProxy = null;
    private static volatile Object sUpdateProxy = null;
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
     * Hooked on ProductDetailParams$WithMeta constructor to capture product ID from Meta.
     */
    public static void onProductDetailParamsWithMeta(Object meta, Object productInfo) {
        try {
            if (meta != null) {
                try {
                    Method mh = meta.getClass().getMethod("getHref");
                    Object href = mh.invoke(meta);
                    if (href != null) {
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:/product/|productId=|-d)([0-9]+)").matcher(href.toString());
                        if (matcher.find()) {
                            activeCommodityId = matcher.group(1);
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

    /**
     * Natively emits Compose AndroidView (androidx.compose.ui.viewinterop.AndroidView)
     * at the end of the price Column in w4m.i.
     *
     * PriceHistoryChartView is added directly to AndroidViewsHandler in AndroidComposeView,
     * scrolls 100% fluidly with the screen, and pushes down subsequent content natively.
     */
    public static void onPriceSectionCompose(Object composer) {
        try {
            if (composer == null) return;

            if (activeCommodityId == null || activeCommodityId.isEmpty()) {
                resolveProductFromGodFragment(lastGodFragment.get());
            }

            ClassLoader cl = composer.getClass().getClassLoader();
            if (cl == null) return;

            Class<?> vwcClass = null;
            if (sFunction1Class != null && !sFunction1Class.isEmpty()) {
                try {
                    vwcClass = cl.loadClass(sFunction1Class);
                } catch (Throwable ignored) {}
            }

            if (sAndroidViewResolvedMethod == null || sLastClassLoader != cl) {
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
                            if (vwcClass == null) {
                                vwcClass = m.getParameterTypes()[0];
                            }
                            break;
                        }
                    }
                }
            }

            Method androidViewMethod = sAndroidViewResolvedMethod;
            if (androidViewMethod == null) {
                Log.e(TAG, "onPriceSectionCompose: AndroidView method not resolved");
                return;
            }
            if (vwcClass == null) {
                vwcClass = androidViewMethod.getParameterTypes()[0];
            }

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
            // default = 2 means bit 1 (2) is set -> use default Modifier; bit 2 (4) is 0 -> use updateProxy
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
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:/product/|productId=|-d)([0-9]+)").matcher(href.toString());
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
