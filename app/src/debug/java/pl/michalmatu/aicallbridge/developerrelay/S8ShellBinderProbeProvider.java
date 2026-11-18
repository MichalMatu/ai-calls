package pl.michalmatu.aicallbridge.developerrelay;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/**
 * Isolated S8 debug-only, read-only Binder reachability probe.
 * Export is limited by android.permission.DUMP and checked against kernel
 * Binder UID. No audio, Call, command executor, or personal data is exposed.
 */
public final class S8ShellBinderProbeProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (Binder.getCallingUid() != 2000) {
            throw new SecurityException("s8_probe_shell_uid_required");
        }
        if (!"ping".equals(method)) {
            throw new IllegalArgumentException("s8_probe_unknown_method");
        }
        Bundle result = new Bundle();
        result.putBoolean("shell_uid_verified", true);
        result.putBoolean("app_provider_active", true);
        return result;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("no_data");
    }
    @Override public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("no_data");
    }
    @Override public int update(Uri uri, ContentValues values, String selection,
                                String[] selectionArgs) {
        throw new UnsupportedOperationException("no_data");
    }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("no_data");
    }
}
