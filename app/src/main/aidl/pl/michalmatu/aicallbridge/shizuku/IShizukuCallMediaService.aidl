package pl.michalmatu.aicallbridge.shizuku;

import android.os.ParcelFileDescriptor;

interface IShizukuCallMediaService {
    int getProcessUid() = 1;
    int getProcessPid() = 2;

    void prepare(int sampleRate) = 3;
    void startMedia() = 4;
    ParcelFileDescriptor takeDownlinkReadEnd() = 5;
    ParcelFileDescriptor takeUplinkWriteEnd() = 6;

    boolean heartbeat() = 7;
    boolean hasPreparedSession() = 8;
    boolean hasActiveSession() = 9;
    void abortNow() = 10;

    // Shizuku UserService destroy transaction. See Shizuku API UserService contract.
    void destroy() = 16777114;
}
