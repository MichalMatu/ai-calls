package pl.michalmatu.aicallbridge.shizuku;

import android.system.Os;
import android.system.OsConstants;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shell-UID Shizuku UserService that owns the fixed phone-local llama.cpp runtime.
 *
 * <p>No command, model path, alias, or port is accepted from the app process. The service may only
 * manage the single pinned runtime below. It adopts an already-correct server, replaces a stale
 * server from the same managed directory/port, starts the pinned model, and verifies exact
 * {@code /props} identity before reporting readiness.</p>
 */
public final class LocalPhoneLlmRuntimeUserService extends ILocalPhoneLlmRuntimeService.Stub {
    private static final String RUNTIME_DIR = "/data/local/tmp/aicall-phone-llm";
    private static final String SERVER_PATH = RUNTIME_DIR + "/llama-server";
    private static final String MODEL_PATH = RUNTIME_DIR + "/model-1.5b.gguf";
    private static final String MODEL_ALIAS = "qwen-phone-1.5b";
    private static final String LOG_PATH = RUNTIME_DIR + "/llama-product-runtime.log";
    private static final int PORT = 18115;
    private static final long START_TIMEOUT_MS = 90_000L;
    private static final long STOP_GRACE_MS = 3_000L;
    private static final int MAX_PROPS_BYTES = 64 * 1024;

    private java.lang.Process childProcess;

    public LocalPhoneLlmRuntimeUserService() {
        // Required default constructor for Shizuku UserService.
    }

    @Override
    public int getProcessUid() {
        return android.os.Process.myUid();
    }

    @Override
    public synchronized boolean ensureReady() {
        requireShellUid();
        if (isReadyInternal()) {
            return true;
        }

        stopKnownServers();
        ensureRuntimeFiles();
        startPinnedServer();

        long deadline = android.os.SystemClock.elapsedRealtime() + START_TIMEOUT_MS;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (isReadyInternal()) {
                return true;
            }
            if (childProcess != null && hasExited(childProcess)) {
                int exitCode = childProcess.exitValue();
                childProcess = null;
                throw new IllegalStateException("local LLM server exited during startup: " + exitCode);
            }
            sleep(250L);
        }

        stopKnownServers();
        throw new IllegalStateException("local LLM server readiness timed out");
    }

    @Override
    public synchronized boolean isReady() {
        requireShellUid();
        return isReadyInternal();
    }

    @Override
    public synchronized void stopServer() {
        requireShellUid();
        stopKnownServers();
    }

    @Override
    public synchronized void destroy() {
        try {
            stopKnownServers();
        } finally {
            System.exit(0);
        }
    }

    private void requireShellUid() {
        if (android.os.Process.myUid() != 2000) {
            throw new SecurityException("local LLM runtime service requires shell UID");
        }
    }

    private void ensureRuntimeFiles() {
        File server = new File(SERVER_PATH);
        File model = new File(MODEL_PATH);
        if (!server.isFile() || !server.canExecute()) {
            throw new IllegalStateException("local LLM server binary is missing or not executable");
        }
        if (!model.isFile() || !model.canRead()) {
            throw new IllegalStateException("local LLM model is missing or unreadable");
        }
    }

    private void startPinnedServer() {
        List<String> command = new ArrayList<>();
        command.add(SERVER_PATH);
        command.add("-m");
        command.add(MODEL_PATH);
        command.add("--host");
        command.add("127.0.0.1");
        command.add("--port");
        command.add(Integer.toString(PORT));
        command.add("--alias");
        command.add(MODEL_ALIAS);
        command.add("-c");
        command.add("1024");
        command.add("-t");
        command.add("4");
        command.add("-np");
        command.add("1");

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(new File(RUNTIME_DIR));
            builder.redirectErrorStream(true);
            builder.environment().put("LD_LIBRARY_PATH", RUNTIME_DIR);
            childProcess = builder.start();
            pumpServerLog(childProcess);
        } catch (IOException error) {
            childProcess = null;
            throw new IllegalStateException("failed to start local LLM server", error);
        }
    }

    private void pumpServerLog(java.lang.Process process) {
        Thread thread = new Thread(() -> {
            try (
                InputStream input = process.getInputStream();
                FileOutputStream output = new FileOutputStream(LOG_PATH, true)
            ) {
                byte[] buffer = new byte[8 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        output.write(buffer, 0, read);
                        output.flush();
                    }
                }
            } catch (IOException ignored) {
                // The readiness/error path is reported through Binder; logging is best-effort only.
            }
        }, "aicall-llama-log");
        thread.setDaemon(true);
        thread.start();
    }

    private boolean isReadyInternal() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + PORT + "/props"
            ).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1_000);
            connection.setReadTimeout(2_000);
            connection.setUseCaches(false);
            if (connection.getResponseCode() != 200) {
                return false;
            }
            byte[] body = readBounded(connection.getInputStream(), MAX_PROPS_BYTES);
            JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return false;
            }
            JsonObject props = parsed.getAsJsonObject();
            return MODEL_ALIAS.equals(stringProperty(props, "model_alias"))
                && MODEL_PATH.equals(stringProperty(props, "model_path"));
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String stringProperty(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            total += read;
            if (total > maxBytes) {
                throw new IOException("local LLM props response is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void stopKnownServers() {
        java.lang.Process process = childProcess;
        childProcess = null;
        if (process != null) {
            try {
                process.destroy();
            } catch (Throwable ignored) {
                // PID-scoped cleanup below is the authoritative cleanup path.
            }
        }

        List<Integer> pids = managedServerPids();
        for (int pid : pids) {
            signal(pid, OsConstants.SIGTERM);
        }
        long deadline = android.os.SystemClock.elapsedRealtime() + STOP_GRACE_MS;
        while (android.os.SystemClock.elapsedRealtime() < deadline && anyAlive(pids)) {
            sleep(100L);
        }
        for (int pid : pids) {
            if (isAlive(pid)) {
                signal(pid, OsConstants.SIGKILL);
            }
        }
    }

    private static List<Integer> managedServerPids() {
        List<Integer> result = new ArrayList<>();
        File[] entries = new File("/proc").listFiles();
        if (entries == null) {
            return result;
        }
        for (File entry : entries) {
            int pid;
            try {
                pid = Integer.parseInt(entry.getName());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (pid == android.os.Process.myPid()) {
                continue;
            }
            String cmdline = readCmdline(pid);
            if (cmdline == null) {
                continue;
            }
            boolean llama = cmdline.contains("llama-server");
            boolean managedDir = cmdline.contains(RUNTIME_DIR + "/");
            boolean managedPort = cmdline.contains("--port\u0000" + PORT + "\u0000")
                || cmdline.endsWith("--port\u0000" + PORT);
            if (llama && managedDir && managedPort) {
                result.add(pid);
            }
        }
        return result;
    }

    private static String readCmdline(int pid) {
        try (FileInputStream input = new FileInputStream("/proc/" + pid + "/cmdline")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[2 * 1024];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                total += read;
                if (total > 32 * 1024) return null;
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static boolean anyAlive(List<Integer> pids) {
        for (int pid : pids) {
            if (isAlive(pid)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAlive(int pid) {
        return new File("/proc/" + pid).exists();
    }

    private static void signal(int pid, int signal) {
        try {
            Os.kill(pid, signal);
        } catch (Throwable ignored) {
            // Process may already have exited or no longer match its original PID.
        }
    }

    private static boolean hasExited(java.lang.Process process) {
        try {
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException running) {
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("local LLM runtime operation interrupted", error);
        }
    }
}
