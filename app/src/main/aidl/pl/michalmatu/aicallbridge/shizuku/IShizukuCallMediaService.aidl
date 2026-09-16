package pl.michalmatu.aicallbridge.shizuku;

import android.os.ParcelFileDescriptor;

interface IShizukuCallMediaService {
    int getProcessUid();
    int getProcessPid();

    void prepare(int sampleRate);
    void startMedia();
    ParcelFileDescriptor takeDownlinkReadEnd();
    ParcelFileDescriptor takeUplinkWriteEnd();

    boolean heartbeat();
    boolean hasPreparedSession();
    boolean hasActiveSession();
    void abortNow();

    // Shizuku UserService destroy transaction. See Shizuku API UserService contract.
    void destroy() = 16777114;
}
