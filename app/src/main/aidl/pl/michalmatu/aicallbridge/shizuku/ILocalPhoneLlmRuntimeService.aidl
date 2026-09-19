package pl.michalmatu.aicallbridge.shizuku;

interface ILocalPhoneLlmRuntimeService {
    int getProcessUid() = 1;
    boolean ensureReady() = 2;
    boolean isReady() = 3;
    void stopServer() = 4;

    // Shizuku UserService destroy transaction. See Shizuku API UserService contract.
    void destroy() = 16777114;
}
