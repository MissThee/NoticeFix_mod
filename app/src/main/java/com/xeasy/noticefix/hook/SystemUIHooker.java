package com.xeasy.noticefix.hook;

import static com.xeasy.noticefix.hook.HookConstant.gson;

import android.annotation.SuppressLint;
import android.app.AndroidAppHelper;
import android.app.KeyguardManager;
import android.app.Notification;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.RemoteViews;

import com.google.gson.reflect.TypeToken;
import com.xeasy.noticefix.bean.CustomIconBean;
import com.xeasy.noticefix.bean.IconFunc;
import com.xeasy.noticefix.bean.IconLibBean;
import com.xeasy.noticefix.dao.GlobalConfigDao;
import com.xeasy.noticefix.dao.IconFuncDao;
import com.xeasy.noticefix.utils.ImageTools;
import com.xeasy.noticefix.utils.ImageUtils;
import com.xeasy.noticefix.utils.ReflexUtil;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SystemUIHooker implements IXposedHookLoadPackage {

    private static final String LOG_PREV = "NoticeFix---";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {


        // 激活状态 com.xeasy.noticefix.activity.MainActivity.activeXposed
        if (loadPackageParam.packageName.equals("com.xeasy.noticefix")) {
            Class<?> aClass = XposedHelpers.findClass("com.xeasy.noticefix.activity.MainActivity", loadPackageParam.classLoader);
            XposedHelpers.findAndHookMethod(aClass, "activeXposed"
                    , boolean.class
                    , new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            System.out.println("beforeHookedMethod ====" + param);
                            param.args[0] = true;
                        }
                    });
        }


        if (loadPackageParam.packageName.equals("com.android.systemui")) {
            // hook sustemui的通知监听器
            try {
                inflateViews(loadPackageParam.classLoader);
            } catch (Exception e) {
                XposedBridge.log(LOG_PREV + "hook -- inflateViews 错误");
                XposedBridge.log(e);
            }
            try {
                setIcon(loadPackageParam.classLoader);
            } catch (Exception e) {
                XposedBridge.log(LOG_PREV + "hook -- setIcon 错误");
                XposedBridge.log(e);
            }
            try {
                setSystemExpanded(loadPackageParam.classLoader);
            } catch (Exception e) {
                XposedBridge.log(LOG_PREV + "hook -- setSystemExpanded 错误");
                XposedBridge.log(e);
            }
            try {
                fixIconColor(loadPackageParam.classLoader);
            } catch (Exception e) {
                XposedBridge.log(LOG_PREV + "hook -- fixIconColor 错误");
                XposedBridge.log(e);
            }

        }
    }


    // android.app.Notification.Builder#bindSmallIcon
    private void fixIconColor(ClassLoader classLoader) {
        final Class<?> clazz = XposedHelpers.findClass(
                "android.app.Notification.Builder", classLoader);
        final Class<?> args0 = XposedHelpers.findClass(
                "android.widget.RemoteViews", classLoader);
        final Class<?> args1 = XposedHelpers.findClass(
                "android.app.Notification.StandardTemplateParams", classLoader);
        //Hook有参构造函数，修改参数
        XposedHelpers.findAndHookMethod(clazz, "bindSmallIcon",
                args0, args1
                , new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
//                        RemoteViews contentView = (RemoteViews) param.args[0];
//                        contentView.
                    }
                });
    }


    // com.android.systemui.statusbar.policy.HeadsUpManager#onAlertEntryAdded
    // todo 测试展开通知
    private void setSystemExpanded(ClassLoader classLoader) {
        //Hook
        XposedHelpers.findAndHookMethod(
                XposedHelpers.findClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", classLoader)
                , "setSystemExpanded"
                , boolean.class
                , new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if ( HookConstant.globalConfigDao.read ) {
                            // 获取包名
                            Object mEntry = ReflexUtil.getField4Obj(param.thisObject, "mEntry");
                            Object mSbn = ReflexUtil.getField4Obj(mEntry, "mSbn");
                            String pkg = (String) ReflexUtil.getField4Obj(mSbn, "pkg");
                            CustomIconBean customIconBean = HookConstant.customIconBeanMap.get(pkg);

                            if ( HookConstant.globalConfigDao.expandAllNotice ||
                                    ( customIconBean != null && customIconBean.expandStatusBar ) ) {
                                param.args[0] = true;
                            }
                        }
                    }
                });
        // com.android.systemui.statusbar.policy.HeadsUpManager#onAlertEntryAdded
        XposedHelpers.findAndHookMethod(
                XposedHelpers.findClass("com.android.systemui.statusbar.policy.HeadsUpManager", classLoader)
                , "onAlertEntryAdded"
                , XposedHelpers.findClass("com.android.systemui.statusbar.AlertingNotificationManager.AlertEntry", classLoader)
                , new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        KeyguardManager keyguardManager = (KeyguardManager) AndroidAppHelper.currentApplication().getSystemService(Context.KEYGUARD_SERVICE);
                        boolean keyguardLocked = keyguardManager.isKeyguardLocked();
                        if (! keyguardLocked ) {
                            if ( param.args[0].getClass().getSimpleName().contains("HeadsUpEntryPhone")) {
                                // 反射获取参数0的 mEntry 属性
                                Object mEntry = ReflexUtil.getField4Obj(
                                        XposedHelpers.findClass("com.android.systemui.statusbar.AlertingNotificationManager.AlertEntry", classLoader)
                                        , param.args[0], "mEntry");
                                if ( HookConstant.globalConfigDao.read ) {
                                    // 获取包名
                                    Object mSbn = ReflexUtil.getField4Obj(mEntry, "mSbn");
                                    String pkg = (String) ReflexUtil.getField4Obj(mSbn, "pkg");
                                    CustomIconBean customIconBean = HookConstant.customIconBeanMap.get(pkg);

                                    if ( HookConstant.globalConfigDao.expandAllNotice ||
                                            ( customIconBean != null && customIconBean.expandHeadsUp ) ) {
                                        // 反射获取 mEntry 的 row 属性 拿到 该条通知的 ExpandableNotificationRow 对象
                                        Object row = ReflexUtil.getField4Obj(mEntry, "row");
                                        if (row != null) {
                                            // 手动调用 ExpandableNotificationRow 的 expandNotification 方法, 让它展开
                                            Object expandNotification = ReflexUtil.runMethod(row, "expandNotification", new Object[]{});
                                            if (! (expandNotification instanceof Exception)) {
                                                // todo 设置横幅通知 超时时间 com.android.systemui.statusbar.phone.HeadsUpManagerPhone.HeadsUpEntryPhone#updateEntry
                                                ReflexUtil.runMethod(param.args[0], "setExpanded", new Object[]{false}, boolean.class);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
//        XposedBridge.log(LOG_PREV + "hook -- setSystemExpanded 完成");
    }

    // com.android.systemui.statusbar.notification.icon.IconManager#setIcon
    private void setIcon(ClassLoader classLoader) {
        final Class<?> clazz = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.icon.IconManager", classLoader);
        final Class<?> args0 = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.collection.NotificationEntry", classLoader);
        final Class<?> args1 = XposedHelpers.findClass(
                "com.android.internal.statusbar.StatusBarIcon", classLoader);
        final Class<?> args2 = XposedHelpers.findClass(
                "com.android.systemui.statusbar.StatusBarIconView", classLoader);
        //Hook有参构造函数，修改参数
        XposedHelpers.findAndHookMethod(clazz, "setIcon",
                args0, args1, args2
                , new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (HookConstant.globalConfigDao.read && HookConstant.globalConfigDao.showColoredIcons) {
                            View iconView = (View) param.args[2];
                            @SuppressLint("DiscouragedApi")
                            int preLTag = AndroidAppHelper.currentApplication().getResources().getIdentifier("icon_is_pre_L", "id", "com.android.systemui");
                            iconView.setTag(preLTag, true);
                        }
                    }
                });
        // com.android.systemui.statusbar.StatusBarIconView#updateIconColor 状态栏图标视图
        XposedHelpers.findAndHookMethod(
                XposedHelpers.findClass(
                        "com.android.systemui.statusbar.StatusBarIconView", classLoader)
                , "updateIconColor"
                , new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (HookConstant.globalConfigDao.read && HookConstant.globalConfigDao.showColoredIcons) {
                            try {
                                // 如果图标不是灰度的 进行转换
                                StatusBarNotification sbNotification = (StatusBarNotification) ReflexUtil.getField4Obj(param.thisObject, "mNotification");
                                if ( null != sbNotification ) {
                                    Context mContext = (Context) ReflexUtil.getField4Obj(sbNotification, "mContext");
                                    if ("android".equals(sbNotification.getPackageName())) {
                                        return;
                                    }
//                                    Context context = mContext.createPackageContext(sbNotification.getPackageName(), Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
//                                    Bitmap bitmap4Icon = getBitmap4Icon(sbNotification.getNotification().getSmallIcon(), context);
                                    // 没有
//                                    Object getColorUtil = ReflexUtil.runMethod(param.thisObject, "getColorUtil", new Object[]{});
                                    Class<?> ContrastColorUtilClass = XposedHelpers.findClass(
                                            "com.android.internal.util.ContrastColorUtil", classLoader);
                                    Object contrastColorUtil = ReflexUtil.runStaticMethod(ContrastColorUtilClass, "getInstance", new Object[]{mContext}, Context.class);
                                    Boolean isGrayscaleIcon = (Boolean) ReflexUtil.runMethod(contrastColorUtil, "isGrayscaleIcon",
                                            new Object[]{mContext, sbNotification.getNotification().getSmallIcon()}, Context.class, Icon.class);
                                    if ( ! isGrayscaleIcon ) {
                                        ReflexUtil.setField4Obj("mCurrentSetColor", param.thisObject, 0);
                                    }
                                }
                            } catch ( Exception e) {
                                XposedBridge.log(LOG_PREV + "mCurrentSetColor 错误");
                                XposedBridge.log(e);
                            }
                        }
                    }
                });

        final Class<?> standardTemplateParamsClass = XposedHelpers.findClass(
                "android.app.Notification.StandardTemplateParams", classLoader);
        // android.app.Notification.StandardTemplateParams
        // 设置原始图标颜色
        XposedHelpers.findAndHookMethod(Notification.Builder.class, "processSmallIconColor",
                Icon.class, RemoteViews.class, standardTemplateParamsClass
                , new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (HookConstant.globalConfigDao.read && HookConstant.globalConfigDao.showColoredIcons) {
                            Context mContext = (Context) ReflexUtil.getField4Obj(param.thisObject, "mContext");
                            Icon smallIcon = (Icon) param.args[0];
                            RemoteViews contentView = (RemoteViews) param.args[1];
                            Object getColorUtil = ReflexUtil.runMethod(param.thisObject, "getColorUtil", new Object[]{});
                            Boolean isGrayscaleIcon = (Boolean) ReflexUtil.runMethod(getColorUtil, "isGrayscaleIcon", new Object[]{mContext, smallIcon}, Context.class, Icon.class);
                            if (null != getColorUtil && ! isGrayscaleIcon) {
//                            if (! isGrayscaleIcon) {
                                contentView.setInt(android.R.id.icon, "setOriginalIconColor", android.R.color.transparent);
                                param.setResult(true);
                            }
                        }
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (HookConstant.globalConfigDao.read && HookConstant.globalConfigDao.showColoredIcons) {
                            Context context = (Context) ReflexUtil.getField4Obj(param.thisObject, "mContext");
                            Icon smallIcon = (Icon) param.args[0];
                            RemoteViews contentView = (RemoteViews) param.args[1];
//                            boolean isGrayscaleIcon = new ImageUtils().isGrayscale(getBitmap4Icon(smallIcon, context));
                            Object getColorUtil = ReflexUtil.runMethod(param.thisObject, "getColorUtil", new Object[]{});
                            Boolean isGrayscaleIcon = (Boolean) ReflexUtil.runMethod(getColorUtil, "isGrayscaleIcon", new Object[]{context, smallIcon}, Context.class, Icon.class);
                            if (! isGrayscaleIcon) {
//                                contentView.setInt(android.R.id.icon, "setBackgroundColor", android.R.color.transparent);
                                // 设置背景透明
                                contentView.setInt(android.R.id.icon, "setBackgroundResource",  android.R.color.transparent);
                                //  去掉padding达到取消外围圆圈背景的目的
                                contentView.setViewPadding(android.R.id.icon, 0,0,0,0);
                                // android.R.id.left_icon
                                @SuppressLint("DiscouragedApi")
                                int left_icon = AndroidAppHelper.currentApplication().getResources().getIdentifier("left_icon", "id", "com.android.systemui");
                                contentView.setViewPadding(left_icon, 0,0,0,0);
                            }
                        }
                    }
                });

    }


    // com.android.systemui.statusbar.notification.collection.inflation.NotificationRowBinderImpl#inflateViews // 安卓11及以上
    // com.android.systemui.statusbar.notification.collection.NotificationRowBinderImpl#inflateViews // 安卓10
    private void inflateViews(ClassLoader classLoader) {
        // 尝试寻找 inflateViews

        Class<?> clazz = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.notification.collection.inflation.NotificationRowBinderImpl", classLoader);
        if ( clazz == null ) {
            clazz = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.collection.NotificationRowBinderImpl", classLoader);
        }
        final Class<?> args0 = XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.collection.NotificationEntry", classLoader);

        XC_MethodHook xc_methodHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!HookConstant.globalConfigDao.read) {
                    readConfig(AndroidAppHelper.currentApplication());
                }
                try {
                    for (Object arg : param.args) {
                        if (arg.getClass() == args0) {
//                            StatusBarNotification statusBarNotification = (StatusBarNotification) ReflexUtil.getField4Obj(arg, "mSbn");
                            StatusBarNotification statusBarNotification = (StatusBarNotification) ReflexUtil.getField4ObjByClass(arg.getClass(), arg, StatusBarNotification.class);
                            Context mContext = (Context) ReflexUtil.getField4Obj(param.thisObject, "mContext");
                            Context context = mContext.createPackageContext(statusBarNotification.getPackageName(), Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                            fixNotificationIcon(statusBarNotification, context);
                        }
                    }
                } catch (Exception e) {
                    XposedBridge.log(e);
                }
            }
        };

        Method[] declaredMethods = clazz.getDeclaredMethods();
        List<Method> inflateViewsMethods = Arrays.stream(declaredMethods).filter(method -> method.getName().equals("inflateViews")).collect(Collectors.toList());
        if (inflateViewsMethods.size() > 0) {
            for (Method inflateViewsMethod : inflateViewsMethods) {
                Class<?>[] parameterTypes = inflateViewsMethod.getParameterTypes();
                List<Class<?>> classes = Arrays.asList(parameterTypes);
                // 找到
                if (classes.size() > 0 && classes.contains(args0)) {
                    Method m = XposedHelpers.findMethodExact(clazz, inflateViewsMethod.getName(), parameterTypes);
                    XposedBridge.hookMethod(m, xc_methodHook);
                    XposedBridge.log(LOG_PREV + "inflateViews, Hook完成!!!");
                    return;
                }

            }
        }

        XposedBridge.log(LOG_PREV + "inflateViews, Hook 失败!!!");


    }

    private void readConfig(Context context) {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            Uri uri = Uri.parse("content://com.xeasy.noticefix.provider.IconDataContentProvider");
            Cursor query = contentResolver.query(uri, null, null, null);
            query.moveToNext();
            String globalConfig = query.getString(0);
            String iconFunc = query.getString(1);
            String libIconList = query.getString(2);
            String customIconList = query.getString(3);
            query.close();
            // 解析赋值给全局变量
            HookConstant.globalConfigDao = gson.fromJson(globalConfig, GlobalConfigDao.class);
            // 2
            Type type = new TypeToken<List<IconFuncDao.IconFuncStatus>>() {
            }.getType();
            HookConstant.iconFuncStatuses = gson.fromJson(iconFunc, type);
            Collections.sort(HookConstant.iconFuncStatuses);
            // 3
            Type type2 = new TypeToken<Map<String, IconLibBean>>() {
            }.getType();
            HookConstant.iconLibBeanMap = gson.fromJson(libIconList, type2);
            // 4
            Type type3 = new TypeToken<Map<String, CustomIconBean>>() {
            }.getType();
            HookConstant.customIconBeanMap = gson.fromJson(customIconList, type3);
//            Toast.makeText(context, "读取图标成功!!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
//            Toast.makeText(context, "读取图标资源失败!!", Toast.LENGTH_SHORT).show();
            XposedBridge.log(LOG_PREV + "读取图标资源失败, (原因可能是NoticeFix应用没有自启权限, 也可能是刚开机唤不醒)");
            XposedBridge.log(e);
        }
    }


    public static void fixNotificationIcon(StatusBarNotification statusBarNotification, Context context) {

        try {
            Notification notification = statusBarNotification.getNotification();
            String packageName = statusBarNotification.getPackageName();

            // 判断不处理名单
            CustomIconBean customIconBean = HookConstant.customIconBeanMap.get(packageName);
            // 是选择了不处理的app
            if (customIconBean != null && customIconBean.noHandle) {
                // 判断是不是推送
                String opPkg = statusBarNotification.getOpPkg();
                boolean isProxy = !opPkg.equals(packageName);
                // 但是 1 不是推送 直接g
                if (!isProxy) {
                    return;
                }
                // 2 是推送 但是  始终处理推送通知 的开关关了 直接g
                if (!HookConstant.globalConfigDao.alwaysHandleProxyNotice) {
                    return;
                }
            }

            Icon smallIcon = notification.getSmallIcon();
            XposedBridge.log(LOG_PREV + " fixNotificationIcon packageName ===  " + packageName);
            // 跳过灰度图
            if (HookConstant.globalConfigDao.skipGrayscale) {
                try {
                    Bitmap bitmap = getBitmap4Icon(smallIcon, context);
                    if (new ImageUtils().isGrayscale(bitmap)) {
                        return;
                    }
                } catch (Exception e) {
                    XposedBridge.log(LOG_PREV + "获取通知图标失败 ===  " + packageName);
                }
            }
            for (IconFuncDao.IconFuncStatus iconFuncStatus : HookConstant.iconFuncStatuses) {
                if (iconFuncStatus.active) {
                    // 使用库
                    if (iconFuncStatus.iconFuncId == IconFunc.LIB_FIX.funcId) {
                        IconLibBean iconLibBean = HookConstant.iconLibBeanMap.get(packageName);
                        if (iconLibBean != null) {
                            // 重新生成图标
                            Icon newIcon = Icon.createWithBitmap(ImageTools.base64ToBitmap(iconLibBean.iconBitmap));
                            // 反射赋值
                            ImageTools.setSmallIcon(newIcon, notification);
                            return;
                        }

                    }
                    // 使用自定义
                    if (iconFuncStatus.iconFuncId == IconFunc.CUSTOM_FIX.funcId) {
//                        CustomIconBean customIconBean = HookConstant.customIconBeanMap.get(packageName);
                        if (customIconBean != null && customIconBean.iconBase64 != null && !customIconBean.iconBase64.isEmpty()) {
                            // 重新生成图标
                            Icon newIcon = Icon.createWithBitmap(ImageTools.base64ToBitmap(customIconBean.iconBase64));
                            // 反射赋值
                            ImageTools.setSmallIcon(newIcon, notification);
                            return;
                        }
                    }
                    // 使用 算法
                    if (iconFuncStatus.iconFuncId == IconFunc.AUTO_FIX.funcId) {
                        // 不是灰度才转换 不然算法么有意义
                        Bitmap bitmap = getBitmap4Icon(smallIcon, context);
                        if (!new ImageUtils().isGrayscale(bitmap)) {
                            // 转换为单色位图
                            Bitmap bitmap1 = ImageTools.getSinglePic(bitmap);
                            // 重新生成图标
                            Icon newIcon = Icon.createWithBitmap(bitmap1);
                            // 反射赋值
                            ImageTools.setSmallIcon(newIcon, notification);
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            XposedBridge.log(LOG_PREV + "修改图标错误");
            XposedBridge.log(e);
        }
    }


    private static Bitmap getBitmap4Icon(Icon smallIcon, Context context) {
        if (smallIcon.getType() == Icon.TYPE_RESOURCE) {
            return ImageTools.getBitmap(context, smallIcon.getResId());
        }
        if (smallIcon.getType() == Icon.TYPE_BITMAP || smallIcon.getType() == Icon.TYPE_ADAPTIVE_BITMAP) {
            return (Bitmap) ReflexUtil.getField4Obj(smallIcon, "mObj1");
        }
        if (smallIcon.getType() == Icon.TYPE_URI || smallIcon.getType() == Icon.TYPE_URI_ADAPTIVE_BITMAP) {
            String url = (String) ReflexUtil.getField4Obj(smallIcon, "mString1");
            return BitmapFactory.decodeFile(url);
        }
        if (smallIcon.getType() == Icon.TYPE_DATA) {
            byte[] mObj1s = (byte[]) ReflexUtil.getField4Obj(smallIcon, "mObj1");
            int mInt1 = (int) Objects.requireNonNull(ReflexUtil.getField4Obj(smallIcon, "mInt1"));
            int mInt2 = (int) Objects.requireNonNull(ReflexUtil.getField4Obj(smallIcon, "mInt2"));
            return BitmapFactory.decodeByteArray(mObj1s, mInt2, mInt1);
        }
        Drawable drawable = smallIcon.loadDrawable(context);
        return ImageTools.toBitmap(drawable);
    }


}
