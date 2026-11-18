package pl.michalmatu.aicallbridge.developerrelay;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import java.lang.reflect.Method;

/**
 * One-shot ADB shell verification that Android Binder can reach a DUMP-gated,
 * app-owned provider despite OEM SELinux Unix socket restrictions.
 */
public final class S8ShellBinderProbeClient {
    private S8ShellBinderProbeClient() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0 || Process.myUid() != 2000)
            throw new SecurityException("shell_uid_required");
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Method systemMain = threadClass.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        Object thread = systemMain.invoke(null);
        Method getSystemContext = threadClass.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        Context system = (Context) getSystemContext.invoke(thread);
        Uri uri = Uri.parse("content://pl.michalmatu.aicallbridge.s8lab.s8shellprobe");
        Bundle response = system.getContentResolver().call(uri, "ping", null, null);
        System.out.println("S8_SHELL_BINDER_PROVIDER_REACHABLE="
            + (response != null && response.getBoolean("shell_uid_verified", false)));
    }
}
