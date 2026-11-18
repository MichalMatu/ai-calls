package pl.michalmatu.aicallbridge.session;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Shell-only access to the platform's external ContentProvider interface.
 * Ordinary ContentResolver fails from an unregistered app_process on Android
 * 16; this mirrors the system 'content call' path, not Shizuku.
 */
public final class GsmShellExternalProviderClient implements AutoCloseable {
    private final Object manager;
    private final Object provider;
    private final IBinder token;
    private final Method call;
    private boolean closed;

    private GsmShellExternalProviderClient(Object manager, Object provider,
                                           IBinder token, Method call) {
        this.manager = manager;
        this.provider = provider;
        this.token = token;
        this.call = call;
    }

    public static GsmShellExternalProviderClient acquire() throws Exception {
        if (Process.myUid() != GsmShellMediaProtocol.SHELL_UID)
            throw new SecurityException("gsm_shell_uid_required");
        Class<?> activityManager = Class.forName("android.app.ActivityManager");
        Method getService = activityManager.getDeclaredMethod("getService");
        getService.setAccessible(true);
        Object manager = getService.invoke(null);
        IBinder token = new Binder();
        Method acquire = find(manager.getClass(), "getContentProviderExternal", 4);
        Object holder = acquire.invoke(manager,
            GsmShellBinderRegistryProvider.AUTHORITY, 0, token, "*aicalls-gsm*");
        if (holder == null) throw new IllegalStateException("gsm_shell_provider_not_found");
        try {
            Field field = holder.getClass().getDeclaredField("provider");
            field.setAccessible(true);
            Object provider = field.get(holder);
            return new GsmShellExternalProviderClient(
                manager, provider, token, find(provider.getClass(), "call", 5));
        } catch (Throwable error) {
            find(manager.getClass(), "removeContentProviderExternalAsUser", 3).invoke(
                manager, GsmShellBinderRegistryProvider.AUTHORITY, token, 0);
            throw error;
        }
    }

    public boolean register(IBinder service) throws Exception {
        Bundle extras = new Bundle();
        extras.putBinder("service", service);
        Bundle reply = invoke("register", extras);
        return reply != null && reply.getBoolean("host_registered", false);
    }

    public boolean stillRegistered() throws Exception {
        Bundle reply = invoke("status", null);
        return reply != null && reply.getBoolean("host_registered", false);
    }

    private synchronized Bundle invoke(String operation, Bundle extras) throws Exception {
        if (closed) throw new IllegalStateException("gsm_shell_provider_closed");
        Class<?> attributionClass = Class.forName("android.content.AttributionSource");
        Constructor<?> ctor = attributionClass.getDeclaredConstructor(
            int.class, String.class, String.class);
        ctor.setAccessible(true);
        Object attribution = ctor.newInstance(
            GsmShellMediaProtocol.SHELL_UID, "com.android.shell", null);
        return (Bundle) call.invoke(provider, attribution,
            GsmShellBinderRegistryProvider.AUTHORITY, operation, null, extras);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            find(manager.getClass(), "removeContentProviderExternalAsUser", 3).invoke(
                manager, GsmShellBinderRegistryProvider.AUTHORITY, token, 0);
        } catch (Exception ignored) {
            // Closing the host always aborts PCM, even if Android Binder died.
        }
    }

    private static Method find(Class<?> cls, String name, int count)
        throws NoSuchMethodException {
        for (Method method : cls.getMethods()) {
            if (name.equals(method.getName()) && method.getParameterCount() == count) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(name + "_" + count);
    }
}
