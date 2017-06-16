package com.bmapleaf.utils;

import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Created by ZhangMing on 2017/06/06.
 */

public class ObjectPool<T> {
    private static final String TAG = "ObjectPool";
    private String poolName;
    private Queue<T> objects;
    private ObjectFactory<T> objectFactory;

    public ObjectPool(@Nullable ObjectFactory<T> factory, @Nullable String poolName) {
        objects = new ArrayDeque<>();
        this.objectFactory = factory;
        this.poolName = null != poolName && !poolName.isEmpty() ? poolName : TAG;
    }

    public T acquire() {
        if (objects.isEmpty()) {
            T t = objectFactory.newObject();
            Log.d(poolName, "new " + t.getClass().getSimpleName());
            return t;
        } else {
            return objects.poll();
        }
    }

    public void release(T object) {
        if (!objects.contains(object)) {
            objects.add(object);
        }
    }

    public void clear() {
        objects.clear();
    }

    public interface ObjectFactory<V> {
        V newObject();
    }
}
