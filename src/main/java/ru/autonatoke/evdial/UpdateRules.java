package ru.autonatoke.evdial;

import java.net.URI;
import java.util.Locale;

/** Pure validation shared by the downloader and regression tests. No Android dependencies. */
final class UpdateRules {
    static final String PACKAGE = "ru.autonatoke.evdial.design";
    static final String BASE = "https://autonatoke.ru/evdial/";
    static final String FEED = BASE + "updates/preview.json";
    static final String SIGNER = "46c68bc8334a0da65a298fe0c348765cce6cda99c225474f488151e5fc9ba3d5";
    static final long MAX_APK_BYTES = 100L * 1024 * 1024;
    private UpdateRules() {}

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    static String hex(String value) {
        String result = value == null ? "" : value.toLowerCase(Locale.ROOT);
        require(result.matches("[0-9a-f]{64}"), "Invalid SHA-256");
        return result;
    }
    static void apkUrl(String value) {
        URI u;
        try { u = new URI(value); } catch (Exception e) { throw new IllegalArgumentException("Invalid update URL"); }
        String p = u.getRawPath();
        require("https".equals(u.getScheme()) && "autonatoke.ru".equals(u.getHost()), "Untrusted update host");
        require(u.getPort() == -1 || u.getPort() == 443, "Invalid update port");
        require(u.getUserInfo() == null && u.getFragment() == null && u.getQuery() == null, "Invalid update URL fields");
        require(p != null && p.startsWith("/evdial/releases/") && p.endsWith(".apk"), "Invalid update path");
        require(!p.contains("%") && !p.contains("..") && !p.contains("//") && !p.contains("\\"), "Unsafe update path");
    }
    static void metadata(String packageName, long version, int minSdk, long bytes, String sha, String signer, String url) {
        require(PACKAGE.equals(packageName), "Wrong package");
        require(version > 0 && version <= Integer.MAX_VALUE, "Invalid version");
        require(minSdk >= 26 && minSdk <= 100, "Invalid Android version");
        require(bytes >= 1024 && bytes <= MAX_APK_BYTES, "Invalid download size");
        hex(sha);
        require(SIGNER.equals(hex(signer)), "Wrong signer");
        apkUrl(url);
    }
    static boolean newer(long installed, long offered) { return offered > installed; }
}
