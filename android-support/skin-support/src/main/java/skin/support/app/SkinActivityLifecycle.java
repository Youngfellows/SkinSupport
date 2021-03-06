package skin.support.app;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.view.LayoutInflaterCompat;
import android.util.Log;
import android.view.LayoutInflater;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import skin.support.SkinCompatManager;
import skin.support.annotation.Skinable;
import skin.support.content.res.SkinCompatResources;
import skin.support.observe.SkinObservable;
import skin.support.observe.SkinObserver;
import skin.support.utils.Slog;
import skin.support.widget.SkinCompatSupportable;
import skin.support.content.res.SkinCompatThemeUtils;

import static skin.support.widget.SkinCompatHelper.INVALID_ID;
import static skin.support.widget.SkinCompatHelper.checkResourceId;

public class SkinActivityLifecycle implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "SkinActivityLifecycle";
    private static volatile SkinActivityLifecycle sInstance = null;
    private WeakHashMap<Context, SkinCompatDelegate> mSkinDelegateMap;
    private WeakHashMap<Context, LazySkinObserver> mSkinObserverMap;
    /**
     * 用于记录当前Activity，在换肤后，立即刷新当前Activity以及非Activity创建的View。
     */
    private WeakReference<Activity> mCurActivityRef;

    public static SkinActivityLifecycle init(Application application) {
        if (sInstance == null) {
            synchronized (SkinActivityLifecycle.class) {
                if (sInstance == null) {
                    sInstance = new SkinActivityLifecycle(application);
                }
            }
        }
        return sInstance;
    }

    private SkinActivityLifecycle(Application application) {
        application.registerActivityLifecycleCallbacks(this);
        installLayoutFactory(application);
        SkinCompatManager.getInstance().addObserver(getObserver(application));
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (isContextSkinEnable(activity)) {
            installLayoutFactory(activity);
            updateStatusBarColor(activity);
            updateWindowBackground(activity);
            if (activity instanceof SkinCompatSupportable) {
                ((SkinCompatSupportable) activity).applySkin();
            }
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        mCurActivityRef = new WeakReference<>(activity);
        if (isContextSkinEnable(activity)) {
            LazySkinObserver observer = getObserver(activity);
            SkinCompatManager.getInstance().addObserver(observer);
            observer.updateSkinIfNeeded();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (isContextSkinEnable(activity)) {
            SkinCompatManager.getInstance().deleteObserver(getObserver(activity));
            mSkinObserverMap.remove(activity);
            mSkinDelegateMap.remove(activity);
        }
    }

    private void installLayoutFactory(Context context) {
        try {
            LayoutInflater layoutInflater = LayoutInflater.from(context);
            LayoutInflaterCompat.setFactory(layoutInflater, getSkinDelegate(context));
        } catch (Exception e) {
            Slog.i("SkinActivity", "A factory has already been set on this LayoutInflater");
        }
    }

    private SkinCompatDelegate getSkinDelegate(Context context) {
        if (mSkinDelegateMap == null) {
            mSkinDelegateMap = new WeakHashMap<>();
        }

        SkinCompatDelegate mSkinDelegate = mSkinDelegateMap.get(context);
        if (mSkinDelegate == null) {
            mSkinDelegate = SkinCompatDelegate.create(context);
            mSkinDelegateMap.put(context, mSkinDelegate);
        }
        return mSkinDelegate;
    }

    private LazySkinObserver getObserver(final Context context) {
        if (mSkinObserverMap == null) {
            mSkinObserverMap = new WeakHashMap<>();
        }
        LazySkinObserver observer = mSkinObserverMap.get(context);
        if (observer == null) {
            observer = new LazySkinObserver(context);
            mSkinObserverMap.put(context, observer);
        }
        return observer;
    }

    private void updateStatusBarColor(Activity activity) {
        if (SkinCompatManager.getInstance().isSkinStatusBarColorEnable()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int statusBarColorResId = SkinCompatThemeUtils.getStatusBarColorResId(activity);
            int colorPrimaryDarkResId = SkinCompatThemeUtils.getColorPrimaryDarkResId(activity);
            if (checkResourceId(statusBarColorResId) != INVALID_ID) {
                activity.getWindow().setStatusBarColor(SkinCompatResources.getColor(activity, statusBarColorResId));
            } else if (checkResourceId(colorPrimaryDarkResId) != INVALID_ID) {
                activity.getWindow().setStatusBarColor(SkinCompatResources.getColor(activity, colorPrimaryDarkResId));
            }
        }
    }

    private void updateWindowBackground(Activity activity) {
        if (SkinCompatManager.getInstance().isSkinWindowBackgroundEnable()) {
            int windowBackgroundResId = SkinCompatThemeUtils.getWindowBackgroundResId(activity);
            if (checkResourceId(windowBackgroundResId) != INVALID_ID) {
                Drawable drawable = SkinCompatResources.getDrawableCompat(activity, windowBackgroundResId);
                if (drawable != null) {
                    activity.getWindow().setBackgroundDrawable(drawable);
                }
            }
        }
    }

    private boolean isContextSkinEnable(Context context) {
        return SkinCompatManager.getInstance().isSkinAllActivityEnable()
                || context.getClass().getAnnotation(Skinable.class) != null
                || context instanceof SkinCompatSupportable;
    }

    private class LazySkinObserver implements SkinObserver {
        private String TAG = this.getClass().getSimpleName();
        private final Context mContext;
        private boolean mMarkNeedUpdate = false;

        LazySkinObserver(Context context) {
            mContext = context;
        }

        @Override
        public void updateSkin(SkinObservable observable, Object o) {
            Log.d(TAG, "updateSkin::  Lazy Skin Observer ...");
            // 当前Activity，或者非Activity，立即刷新，否则延迟到下次onResume方法中刷新。
            if (mCurActivityRef == null
                    || mContext == mCurActivityRef.get()
                    || !(mContext instanceof Activity)) {
                Log.d(TAG, "updateSkin:: update skin force  ");
                updateSkinForce();
            } else {
                mMarkNeedUpdate = true;
            }
        }

        void updateSkinIfNeeded() {
            if (mMarkNeedUpdate) {
                updateSkinForce();
            }
        }

        void updateSkinForce() {
            if (Slog.DEBUG) {
                Slog.i(TAG, "Context: " + mContext + " updateSkinForce");
            }
            if (mContext == null) {
                return;
            }
            if (mContext instanceof Activity && isContextSkinEnable(mContext)) {
                updateStatusBarColor((Activity) mContext);
                updateWindowBackground((Activity) mContext);
            }
            getSkinDelegate(mContext).applySkin();
            if (mContext instanceof SkinCompatSupportable) {
                ((SkinCompatSupportable) mContext).applySkin();
            }
            mMarkNeedUpdate = false;
        }
    }
}