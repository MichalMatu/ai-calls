package pl.michalmatu.aicallbridge.session;

import android.os.ParcelFileDescriptor;

/**
 * ADB-authorized Android Binder interface; no Shizuku manager or SIP.
 * Media is transferred only through two owned PCM16LE pipe descriptors.
 */
interface IGsmShellMediaService {
    int getShellUid() = 1;
    void prepare(long generation, int sampleRate) = 2;
    void startMedia(long generation) = 3;
    ParcelFileDescriptor takeDownlinkReadEnd(long generation) = 4;
    ParcelFileDescriptor takeUplinkWriteEnd(long generation) = 5;
    boolean heartbeat(long generation) = 6;
    oneway void abortNow(long generation) = 7;
}
