package pl.michalmatu.aicallbridge.developerrelay;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.Process;
import android.os.ParcelFileDescriptor;

import java.nio.charset.StandardCharsets;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Read-only Android shell Binder external-provider test, matching the platform
 * 'content call' acquisition path. Does not use Shizuku or obtain PCM.
 */
public final class S8ShellBinderProbeClient {
    private static final String AUTHORITY =
        "pl.michalmatu.aicallbridge.s8lab.s8shellprobe";

    private S8ShellBinderProbeClient() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0 || Process.myUid() != 2000)
            throw new SecurityException("shell_uid_required");
        Object manager = null;
        IBinder token = new Binder();
        boolean acquired = false;
        try {
            Class<?> activityManager = Class.forName("android.app.ActivityManager");
            Method getService = activityManager.getDeclaredMethod("getService");
            getService.setAccessible(true);
            manager = getService.invoke(null);
            Method acquire = method(manager.getClass(), "getContentProviderExternal", 4);
            Object holder = acquire.invoke(manager, AUTHORITY, 0, token, "*aicalls-s8*");
            if (holder == null) throw new IllegalStateException("provider_unavailable");
            acquired = true;
            Field field = holder.getClass().getDeclaredField("provider");
            field.setAccessible(true);
            Object provider = field.get(holder);
            Class<?> attributionClass = Class.forName("android.content.AttributionSource");
            Constructor<?> attributionCtor = attributionClass.getDeclaredConstructor(
                int.class, String.class, String.class);
            attributionCtor.setAccessible(true);
            Object attribution = attributionCtor.newInstance(2000, "com.android.shell", null);
            Method call = method(provider.getClass(), "call", 5);
            Bundle response = (Bundle) call.invoke(provider,
                attribution, AUTHORITY, "ping", null, null);
            System.out.println("S8_SHELL_BINDER_PROVIDER_REACHABLE="
                + (response != null && response.getBoolean("shell_uid_verified", false)));
            Binder callback = new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel reply,
                                                        int flags) throws RemoteException {
                    if (code != IBinder.FIRST_CALL_TRANSACTION)
                        return super.onTransact(code, data, reply, flags);
                    data.enforceInterface("aicalls.s8.probe");
                    reply.writeNoException();
                    reply.writeInt(2026);
                    return true;
                }
            };
            Bundle callbackRequest = new Bundle();
            callbackRequest.putBinder("callback", callback);
            Bundle callbackResult = (Bundle) call.invoke(provider,
                attribution, AUTHORITY, "binder_probe", null, callbackRequest);
            System.out.println("S8_SHELL_BINDER_CALLBACK="
                + (callbackResult != null && callbackResult.getBoolean(
                    "binder_callback_verified", false)));
            Bundle fdResult = (Bundle) call.invoke(provider,
                attribution, AUTHORITY, "fd_probe", null, null);
            if (fdResult == null) throw new IllegalStateException("fd_probe_response_missing");
            ParcelFileDescriptor fd = fdResult.getParcelable("read_end", ParcelFileDescriptor.class);
            if (fd == null) throw new IllegalStateException("fd_probe_descriptor_missing");
            try (ParcelFileDescriptor.AutoCloseInputStream input =
                     new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                String value = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
                System.out.println("S8_SHELL_BINDER_FD_TRANSFER="
                    + "S8_FD_TRANSPORT_OK".equals(value));
            }
        } catch (Throwable error) {
            Throwable cause = error;
            while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
            System.out.println("S8_SHELL_BINDER_ERROR=" + cause.getClass().getSimpleName());
            System.exit(2);
        } finally {
            if (manager != null && acquired) {
                try {
                    method(manager.getClass(), "removeContentProviderExternalAsUser", 3)
                        .invoke(manager, AUTHORITY, token, 0);
                } catch (Exception ignored) {}
            }
        }
    }

    private static Method method(Class<?> cls, String name, int params)
        throws NoSuchMethodException {
        for (Method method : cls.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == params) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(name + "_" + params);
    }
}
