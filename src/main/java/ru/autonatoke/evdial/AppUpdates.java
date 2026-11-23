package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.Call;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Website-distributed preview builds only. No background checks or unattended installs. */
public final class AppUpdates extends Activity {
    private static final int UNKNOWN_SOURCE = 6220;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean stopped;
    private volatile HttpURLConnection connection;
    private TextView state, details, checkButton, downloadButton, installButton;
    private Candidate offered;
    private File ready;
    private boolean busy;
    private long installed;
    private String installedName;

    static void open(Activity activity) { activity.startActivity(new Intent(activity, AppUpdates.class)); }
    static String version(Activity activity) {
        try { return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return "—"; }
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            PackageInfo own = getPackageManager().getPackageInfo(getPackageName(), 0);
            installed = code(own); installedName = own.versionName;
        } catch (PackageManager.NameNotFoundException e) { finish(); return; }
        getWindow().setStatusBarColor(0xff091821); getWindow().setNavigationBarColor(0xff091821);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(0xff091821); scroll.setFillViewport(true);
        LinearLayout root = ElectricUi.column(this); root.setPadding(dp(18), dp(10), dp(18), dp(28)); scroll.addView(root); setContentView(scroll);
        add(root, ElectricUi.button(this, tr("‹  К телефону", "‹  Back to phone"), this::finish), 0);
        add(root, ElectricUi.text(this, tr("Обновления EVDial", "EVDial updates"), 28, ElectricUi.WHITE, true), 22);
        add(root, ElectricUi.text(this, tr("Установлена версия ", "Installed version ") + installedName, 16, ElectricUi.CYAN, true), 10);
        add(root, ElectricUi.text(this, tr("Канал: тестовые версии с нашего сервера", "Channel: previews from our server"), 14, ElectricUi.MUTED, false), 6);
        state = ElectricUi.text(this, tr("Нажмите «Проверить обновления».", "Tap Check for updates."), 18, ElectricUi.WHITE, true); add(root, state, 22);
        details = ElectricUi.text(this, "", 15, ElectricUi.MUTED, false); add(root, details, 12);
        checkButton = ElectricUi.button(this, tr("Проверить обновления", "Check for updates"), this::check); add(root, checkButton, 20);
        downloadButton = ElectricUi.button(this, tr("Скачать обновление", "Download update"), this::confirmDownload); add(root, downloadButton, 10);
        installButton = ElectricUi.button(this, tr("Установить обновление", "Install update"), this::confirmInstall); add(root, installButton, 10);
        add(root, ElectricUi.button(this, tr("Страница EVDial ↗", "EVDial page ↗"), () -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(UpdateRules.BASE))); }
            catch (ActivityNotFoundException e) { state.setText(tr("На устройстве нет браузера.", "No browser is available.")); }
        }), 10);
        add(root, ElectricUi.text(this, tr(
            "Загрузка только по вашему нажатию. Перед установкой проверяются файл, версия и цифровая подпись. Android попросит подтверждение. Во время звонка установка недоступна. Настройки приложения не удаляются.\n\nКонтакты, номера, записи разговоров и фотографии на сервер обновлений не отправляются. Не закрывайте этот экран во время загрузки; при обрыве её можно запустить заново.",
            "Downloads start only when you tap. The file, version and signing certificate are checked before Android asks for installation confirmation. Installation is blocked during calls. App settings are not deleted.\n\nContacts, numbers, recordings and photos are never sent to the update server. Keep this screen open while downloading; interrupted downloads can be restarted."), 13, ElectricUi.MUTED, false), 22);
        controls(); restore();
    }
    private void restore() {
        String json = getPreferences(MODE_PRIVATE).getString("pending", "");
        if (json.isEmpty()) return;
        try {
            Candidate c = new Candidate(new JSONObject(json));
            if (!UpdateRules.newer(installed, c.version)) { getPreferences(MODE_PRIVATE).edit().remove("pending").apply(); return; }
            File f = apk(c);
            if (f.isFile()) { offered = c; ready = f; showOffer(); state.setText(tr("Обновление загружено. Перед установкой файл будет проверен повторно.", "Update downloaded. It will be verified again before installation.")); controls(); }
        } catch (Exception ignored) { getPreferences(MODE_PRIVATE).edit().remove("pending").apply(); }
    }
    private void check() {
        if (busy) return;
        setBusy(true); state.setText(tr("Проверяю версию на сервере…", "Checking the server…"));
        worker.execute(() -> {
            try {
                Candidate c = new Candidate(new JSONObject(fetchFeed()));
                post(() -> {
                    offered = c; ready = null; setBusy(false);
                    if (c.minSdk > Build.VERSION.SDK_INT) { state.setText(tr("Новая версия пока несовместима с этим Android.", "This update requires a newer Android version.")); offered = null; details.setText(""); }
                    else if (!UpdateRules.newer(installed, c.version)) { state.setText(c.version == installed ? tr("У вас последняя версия.", "You have the latest version.") : tr("У вас более новая тестовая версия. Понижение не предлагается.", "Your preview is newer. Downgrades are not offered.")); offered = null; details.setText(""); }
                    else { showOffer(); state.setText(tr("Доступно обновление ", "Update available: ") + c.name); }
                    controls();
                });
            } catch (Exception e) { fail(tr("Не удалось проверить обновление. Проверьте интернет и попробуйте ещё раз. Установленная версия продолжает работать.", "Could not check for updates. Check your connection and try again. Your installed app is unaffected.")); }
        });
    }
    private void showOffer() {
        if (offered == null) return;
        details.setText(tr("Версия ", "Version ") + offered.name + " · " + String.format(Locale.getDefault(), "%.1f", offered.bytes / 1000000.0) + " MB\n\n" + offered.notes);
    }
    private void confirmDownload() {
        if (busy || offered == null || !UpdateRules.newer(installed, offered.version)) return;
        new StationSheet.Builder(this).setTitle(tr("Скачать обновление?", "Download update?"))
            .setMessage(tr("Будет использован интернет: ", "Download size: ") + String.format(Locale.getDefault(), "%.1f MB", offered.bytes / 1000000.0))
            .setPositiveButton(tr("Скачать", "Download"), (d, w) -> download()).setNegativeButton(tr("Отмена", "Cancel"), null).show();
    }
    private void download() {
        if (busy || offered == null) return;
        final Candidate c = offered; ready = null; setBusy(true); state.setText(tr("Загружаю…", "Downloading…"));
        worker.execute(() -> {
            File temporary = null;
            try {
                File folder = directory();
                if (folder.getUsableSpace() < c.bytes + 10L * 1024 * 1024) throw new IOException("Not enough storage");
                temporary = File.createTempFile("evdial-", ".part", folder);
                HttpURLConnection h = connect(c.url);
                long length = h.getContentLengthLong();
                if (length != -1 && length != c.bytes) throw new IOException("Unexpected content length");
                long total = 0; int last = -1;
                try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[32768]; int count;
                    while ((count = in.read(buffer)) != -1) {
                        ensureActive(); total += count;
                        if (total > c.bytes) throw new IOException("Oversized APK");
                        out.write(buffer, 0, count);
                        int progress = (int) (total * 100 / c.bytes);
                        if (progress != last) { last = progress; post(() -> state.setText(tr("Загрузка: ", "Downloading: ") + progress + "%")); }
                    }
                    out.getFD().sync();
                } finally { h.disconnect(); connection = null; }
                if (total != c.bytes) throw new IOException("Incomplete APK");
                verify(temporary, c); ensureActive();
                File target = apk(c);
                if (!temporary.renameTo(target)) throw new IOException("Cannot save APK");
                temporary = null;
                getPreferences(MODE_PRIVATE).edit().putString("pending", c.json).apply();
                post(() -> { ready = target; setBusy(false); state.setText(tr("Файл проверен. Можно установить обновление.", "Verified. Ready to install.")); controls(); });
            } catch (Exception e) { fail(tr("Загрузка не завершена или проверка не пройдена. Приложение не изменено. Освободите место, проверьте сеть и повторите загрузку.", "Download or verification failed. The app is unchanged. Check storage and connection, then retry.")); }
            finally { if (temporary != null && temporary.exists()) temporary.delete(); disconnect(); }
        });
    }
    private void confirmInstall() {
        if (busy || ready == null || offered == null) return;
        if (inCall()) { state.setText(tr("Сначала завершите звонок. Обновление подождёт.", "Finish your call first. The update can wait.")); return; }
        if (!getPackageManager().canRequestPackageInstalls()) {
            new StationSheet.Builder(this).setTitle(tr("Разрешение Android", "Android permission"))
                .setMessage(tr("Android просит разрешить установку обновлений из EVDial. Разрешите этот источник в следующем окне, вернитесь сюда и снова нажмите «Установить». Проверки Android не отключаются.", "Allow EVDial as an installation source on the next screen, then return and tap Install again. Android security checks remain enabled."))
                .setPositiveButton(tr("Открыть настройки", "Open settings"), (d, w) -> {
                    try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())), UNKNOWN_SOURCE); }
                    catch (ActivityNotFoundException | SecurityException e) { state.setText(tr("Откройте разрешение установки в настройках Android.", "Open installation permission in Android settings.")); }
                }).setNegativeButton(tr("Отмена", "Cancel"), null).show();
            return;
        }
        final Candidate c = offered; final File file = ready; setBusy(true);
        state.setText(tr("Повторная проверка перед установкой…", "Verifying before installation…"));
        worker.execute(() -> {
            try {
                verify(file, c);
                post(() -> {
                    setBusy(false);
                    if (inCall()) { state.setText(tr("Начался звонок. Установите обновление после разговора.", "A call started. Install after the call.")); return; }
                    try {
                        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", file);
                        Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(i);
                        state.setText(tr("Подтвердите обновление в окне Android. При отмене текущая версия сохранится.", "Confirm the update in Android. Cancelling keeps your current version."));
                    } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) { state.setText(tr("Не удалось открыть установщик Android. Используйте страницу EVDial.", "Could not open the Android installer. Use the EVDial page.")); }
                });
            } catch (Exception e) { post(() -> { ready = null; setBusy(false); state.setText(tr("Файл не прошёл проверку. Скачайте обновление заново.", "Verification failed. Download the update again.")); controls(); }); }
        });
    }
    private boolean inCall() {
        if (MobileCalls.live != null) for (Call c : MobileCalls.live.calls) if (c.getState() != Call.STATE_DISCONNECTED) return true;
        Phone phone = (Phone) getApplication(); if (phone.active != null) return true;
        AudioManager audio = getSystemService(AudioManager.class);
        return audio != null && (audio.getMode() == AudioManager.MODE_IN_CALL || audio.getMode() == AudioManager.MODE_IN_COMMUNICATION);
    }
    private String fetchFeed() throws Exception {
        HttpURLConnection h = connect(UpdateRules.FEED);
        try (InputStream in = h.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) != -1) { ensureActive(); if (out.size() + n > 65536) throw new IOException("Oversized manifest"); out.write(b, 0, n); }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally { h.disconnect(); connection = null; }
    }
    private HttpURLConnection connect(String address) throws Exception {
        ensureActive();
        if (!UpdateRules.FEED.equals(address)) UpdateRules.apkUrl(address);
        HttpURLConnection h = (HttpURLConnection) new URL(address).openConnection(); connection = h;
        h.setConnectTimeout(15000); h.setReadTimeout(25000); h.setInstanceFollowRedirects(false); h.setUseCaches(false);
        h.setRequestProperty("User-Agent", "EVDial-Updater/1"); h.setRequestProperty("Accept-Encoding", "identity");
        int status = h.getResponseCode();
        if (status != 200) { h.disconnect(); connection = null; throw new IOException("HTTP " + status); }
        return h;
    }
    private void verify(File file, Candidate c) throws Exception {
        ensureActive();
        if (!UpdateRules.newer(installed, c.version) || !file.isFile() || file.length() != c.bytes) throw new IOException("Invalid update file");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) { byte[] b = new byte[32768]; int n; while ((n = in.read(b)) != -1) { ensureActive(); digest.update(b, 0, n); } }
        if (!c.sha.equals(toHex(digest.digest()))) throw new IOException("SHA-256 mismatch");
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageManager pm = getPackageManager();
        PackageInfo downloaded = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        PackageInfo own = pm.getPackageInfo(getPackageName(), flags);
        if (downloaded == null || !getPackageName().equals(downloaded.packageName) || code(downloaded) != c.version || !UpdateRules.newer(code(own), c.version)) throw new IOException("Wrong package/version");
        if (downloaded.applicationInfo == null || downloaded.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) throw new IOException("Unsupported Android");
        if (!UpdateRules.SIGNER.equals(signer(downloaded)) || !UpdateRules.SIGNER.equals(signer(own))) throw new IOException("Signer mismatch");
        // Android's package installer performs the final cryptographic APK signature verification.
    }
    private static String signer(PackageInfo info) throws Exception {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 ? (info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()) : info.signatures;
        if (signatures == null || signatures.length != 1) throw new IOException("Unexpected signer set");
        return toHex(MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()));
    }
    private static String toHex(byte[] bytes) { StringBuilder b = new StringBuilder(); for (byte v : bytes) b.append(String.format(Locale.ROOT, "%02x", v & 255)); return b.toString(); }
    private static long code(PackageInfo p) { return Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode; }
    private File directory() throws IOException { File f = new File(getCacheDir(), "updates"); if (!f.isDirectory() && !f.mkdirs()) throw new IOException("Cannot create update cache"); return f; }
    private File apk(Candidate c) throws IOException { return new File(directory(), "evdial-" + c.version + "-" + c.sha.substring(0, 12) + ".apk"); }
    private void ensureActive() throws IOException { if (stopped || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled"); }
    private void disconnect() { HttpURLConnection c = connection; connection = null; if (c != null) c.disconnect(); }
    private void fail(String message) { post(() -> { setBusy(false); state.setText(message); }); }
    private void post(Runnable r) { if (!stopped) runOnUiThread(() -> { if (!stopped && !isFinishing() && !isDestroyed()) r.run(); }); }
    private void setBusy(boolean value) { busy = value; controls(); }
    private void controls() { checkButton.setEnabled(!busy); downloadButton.setVisibility(offered != null && ready == null ? View.VISIBLE : View.GONE); downloadButton.setEnabled(!busy); installButton.setVisibility(ready != null ? View.VISIBLE : View.GONE); installButton.setEnabled(!busy); }
    @Override protected void onDestroy() { stopped = true; disconnect(); worker.shutdownNow(); super.onDestroy(); }
    private String tr(String ru, String en) { return ElectricUi.tr(this, ru, en); }
    private int dp(int n) { return ElectricUi.dp(this, n); }
    private void add(LinearLayout root, View view, int margin) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(margin); root.addView(view, p); }

    private static final class Candidate {
        final String name, sha, url, notes, json; final long version, bytes; final int minSdk;
        Candidate(JSONObject o) throws Exception {
            UpdateRules.require(o.getInt("schemaVersion") == 1 && "preview".equals(o.getString("channel")), "Unsupported update feed");
            version = o.getLong("versionCode"); bytes = o.getLong("sizeBytes"); minSdk = o.getInt("minSdk");
            name = o.getString("versionName"); sha = UpdateRules.hex(o.getString("sha256")); url = o.getString("apkUrl");
            notes = o.optString("changelog", "");
            UpdateRules.require(!name.isEmpty() && name.length() <= 80 && notes.length() <= 6000, "Invalid release description");
            UpdateRules.metadata(o.getString("packageName"), version, minSdk, bytes, sha, o.getString("signerSha256"), url);
            json = o.toString();
        }
    }
}
