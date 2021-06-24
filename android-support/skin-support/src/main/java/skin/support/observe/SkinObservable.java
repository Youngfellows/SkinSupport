package skin.support.observe;

import android.util.Log;

import java.util.ArrayList;

/**
 * Created by ximsfei on 2017/1/10.
 */

public class SkinObservable {
    protected String TAG = this.getClass().getSimpleName();
    private final ArrayList<SkinObserver> observers;

    public SkinObservable() {
        observers = new ArrayList<>();
    }

    public synchronized void addObserver(SkinObserver o) {
        if (o == null)
            throw new NullPointerException();
        if (!observers.contains(o)) {
            observers.add(o);
        }
    }

    public synchronized void deleteObserver(SkinObserver o) {
        observers.remove(o);
    }

    public void notifyUpdateSkin() {
        notifyUpdateSkin(null);
    }

    public void notifyUpdateSkin(Object arg) {
        Log.d(TAG, "notifyUpdateSkin:: arge:" + arg);
        SkinObserver[] arrLocal;

        synchronized (this) {
            arrLocal = observers.toArray(new SkinObserver[observers.size()]);
        }
        Log.d(TAG, "notifyUpdateSkin:: observers.size():" + observers.size());
        for (int i = arrLocal.length - 1; i >= 0; i--) {
            Log.d(TAG, "notifyUpdateSkin:: " + i + "," + arrLocal[i]);
            arrLocal[i].updateSkin(this, arg);
        }
    }

    public synchronized void deleteObservers() {
        observers.clear();
    }

    public synchronized int countObservers() {
        return observers.size();
    }
}
