package pl.michalmatu.aicallbridge.session;

import android.os.ParcelFileDescriptor;

/**
 * ADB-authorized Android Binder interface; no Shizuku manager or SIP.
 * Media is transferred only through two owned PCM16LE pipe descriptors.
 */
interface IGsmShellMediaService {
    int getShellUid() = 1;
    void prepare(int sampleRate) = 2;
    void startMedia() = 3;
    ParcelFileDescriptor takeDownlinkReadEnd() = 4;
    ParcelFileDescriptor takeUplinkWriteEnd() = 5;
    boolean heartbeat() = 6;
    void abortNow() = 7;
}
