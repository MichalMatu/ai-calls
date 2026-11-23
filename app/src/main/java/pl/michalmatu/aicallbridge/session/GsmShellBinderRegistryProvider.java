package pl.michalmatu.aicallbridge.session;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * DUMP-protected registration point for an authorized
 * ADB-launched shell Binder. Unlike an exported general IPC API, no operation
 * can be called by other application UIDs, and registration does not start PCM.
 *
 * This provider runs in the app process; the runtime reads the same
 * in-process registry. A dead helper loses authority without fallback.
 */
public final class GsmShellBinderRegistryProvider extends ContentProvider {
    public static final String AUTHORITY = GsmShellMediaProtocol.S8_PACKAGE + ".gsmbinder";

    public static String authorityFor(String packageName) {
        return GsmShellMediaProtocol.authorityFor(packageName);
    }

    private static final Object LOCK = new Object();
    private static IBinder registeredBinder;
    private static IGsmShellMediaService registeredService;
    private static IBinder.DeathRecipient deathRecipient;

    @Override public boolean onCreate() { return true; }

    public static IGsmShellMediaService availableService() {
        synchronized (LOCK) {
            return registeredBinder != null && registeredBinder.isBinderAlive()
                ? registeredService : null;
        }
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != GsmShellMediaProtocol.SHELL_UID
            || getContext() == null
            || !GsmShellMediaProtocol.supportedPackage(getContext().getPackageName())) {
            throw new SecurityException("s8_gsm_shell_registration_denied");
        }
        if ("status".equals(method)) {
            Bundle response = new Bundle();
            response.putBoolean("host_registered", availableService() != null);
            return response;
        }
        if (!"register".equals(method)) {
            throw new IllegalArgumentException("s8_gsm_unknown_method");
        }
        IBinder candidate = extras == null ? null : extras.getBinder("service");
        if (candidate == null || !candidate.isBinderAlive()) {
            throw new SecurityException("s8_gsm_binder_missing");
        }
        try {
            if (!IGsmShellMediaService.class.getName().equals(
                candidate.getInterfaceDescriptor())
                || IGsmShellMediaService.Stub.asInterface(candidate).getShellUid()
                    != GsmShellMediaProtocol.SHELL_UID) {
                throw new SecurityException("s8_gsm_binder_identity_invalid");
            }
        } catch (RemoteException failure) {
            throw new IllegalStateException("s8_gsm_binder_identity_unavailable", failure);
        }
        IGsmShellMediaService service = IGsmShellMediaService.Stub.asInterface(candidate);
        IBinder.DeathRecipient recipient = () -> forgetIfCurrent(candidate);
        try {
            candidate.linkToDeath(recipient, 0);
        } catch (RemoteException failure) {
            throw new IllegalStateException("s8_gsm_binder_died_during_registration", failure);
        }
        synchronized (LOCK) {
            if (registeredBinder != null && registeredBinder.isBinderAlive()) {
                // One host at a time. A second shell process cannot silently
                // replace an active media owner.
                candidate.unlinkToDeath(recipient, 0);
                throw new IllegalStateException("s8_gsm_host_already_registered");
            }
            forgetLocked();
            registeredBinder = candidate;
            registeredService = service;
            deathRecipient = recipient;
        }
        Bundle response = new Bundle();
        response.putBoolean("host_registered", true);
        return response;
    }

    private static void forgetIfCurrent(IBinder dead) {
        synchronized (LOCK) {
            if (registeredBinder == dead) forgetLocked();
        }
    }

    private static void forgetLocked() {
        if (registeredBinder != null && deathRecipient != null) {
            registeredBinder.unlinkToDeath(deathRecipient, 0);
        }
        registeredBinder = null;
        registeredService = null;
        deathRecipient = null;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("no_query");
    }
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("no_insert");
    }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        throw new UnsupportedOperationException("no_update");
    }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("no_delete");
    }
}
