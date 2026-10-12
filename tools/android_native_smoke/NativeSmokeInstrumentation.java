package xyz.fieldatlas.testnative;

import android.app.Instrumentation;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Runs the optimized normal APK's kept JNI boundary under its real application UID. */
public final class NativeSmokeInstrumentation extends Instrumentation {
    private boolean fullPack;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        fullPack = arguments != null && "true".equals(arguments.getString("fullPack"));
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        File fixture = null;
        Object instance = null;
        Method close = null;
        long handle = 0;
        int outcome = -1;
        try {
            fixture = File.createTempFile("native-verification-", ".sqlite", getTargetContext().getCacheDir());
            try (InputStream in = getContext().getAssets().open("fixture.sqlite");
                 FileOutputStream out = new FileOutputStream(fixture)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            Class<?> bridge = getTargetContext().getClassLoader().loadClass("xyz.fieldatlas.research.LeviathanNative");
            instance = bridge.getField("INSTANCE").get(null);
            if (!Boolean.TRUE.equals(bridge.getMethod("getAvailable").invoke(instance)))
                throw new AssertionError("Installed APK could not load native search");
            Method open = bridge.getMethod("open", String.class);
            Method search = bridge.getMethod("search", long.class, String.class, int.class);
            close = bridge.getMethod("close", long.class);
            for (int i = 0; i < 5; i++) {
                handle = (Long) open.invoke(instance, fixture.getAbsolutePath());
                JSONArray hits = new JSONArray((String) search.invoke(instance, handle, "Alto pump", 5));
                boolean found = false;
                for (int j = 0; j < hits.length(); j++) {
                    if (hits.getJSONObject(j).getString("text").contains("F-27")) found = true;
                    if (hits.getJSONObject(j).getString("id").isEmpty()) throw new AssertionError("Missing source id");
                }
                if (!found) throw new AssertionError("Full fixture passage not returned");
                close.invoke(instance, handle);
                try {
                    search.invoke(instance, handle, "Alto pump", 5);
                    throw new AssertionError("Closed handle was accepted");
                } catch (InvocationTargetException expected) {
                    if (!(expected.getCause() instanceof IllegalStateException)) throw expected;
                }
                handle = 0;
            }
            int fullQueries = 0;
            if (fullPack) {
                File files = getTargetContext().getFilesDir();
                JSONArray registry = new JSONArray(new String(Files.readAllBytes(new File(files, "asset-registry.json").toPath()), StandardCharsets.UTF_8));
                File database = null;
                for (int i = 0; i < registry.length(); i++) {
                    JSONObject asset = registry.getJSONObject(i);
                    if (!"wikipedia-encyclopedia".equals(asset.getString("id"))) continue;
                    if (database != null) throw new AssertionError("Ambiguous encyclopedia version");
                    if (!"KNOWLEDGE".equals(asset.getString("type"))) throw new AssertionError("Wrong pack type");
                    File root = new File(asset.getString("rootPath")).getCanonicalFile();
                    File allowed = new File(files, "packs/wikipedia-encyclopedia").getCanonicalFile();
                    if (!allowed.equals(root.getParentFile())) throw new AssertionError("Unexpected pack location");
                    database = new File(root, "content.sqlite");
                }
                if (database == null || !database.isFile()) throw new AssertionError("Consolidated pack not installed");
                JSONObject expected;
                try (InputStream input = getContext().getAssets().open("expected-results.json")) {
                    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192]; int read;
                    while ((read = input.read(buffer)) != -1) bytes.write(buffer, 0, read);
                    expected = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                }
                handle = (Long) open.invoke(instance, database.getAbsolutePath());
                JSONArray rows = expected.getJSONArray("rows");
                if (rows.length() != 12) throw new AssertionError("Unexpected parity fixture size");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String query = row.getJSONObject("question").getString("query");
                    JSONArray ids = row.getJSONArray("results").getJSONObject(0).getJSONArray("ids");
                    JSONArray hits = new JSONArray((String) search.invoke(instance, handle, query, 5));
                    if (hits.length() != ids.length()) throw new AssertionError("Result count differs: " + query);
                    for (int j = 0; j < hits.length(); j++) {
                        JSONObject hit = hits.getJSONObject(j);
                        if (!ids.getString(j).equals(hit.getString("id"))) throw new AssertionError("Ranked source ID differs: " + query);
                        for (String field : new String[]{"document_id", "title", "source", "text"})
                            if (hit.getString(field).isEmpty()) throw new AssertionError("Missing hydrated field: " + field);
                    }
                    fullQueries++;
                }
                close.invoke(instance, handle);
                handle = 0;
            }
            result.putString("stream", "PASS: optimized installed APK JNI loads, searches, preserves source IDs and rejects closed handles under app UID. Full installed-pack parity queries: " + fullQueries + ". No library or History writes.\n");
        } catch (Throwable error) {
            result.putString("stream", "FAIL: " + error.toString() + "\n");
            outcome = 1;
        } finally {
            if (handle != 0 && close != null) try { close.invoke(instance, handle); } catch (Exception ignored) { }
            if (fixture != null) fixture.delete();
        }
        finish(outcome, result);
    }
}
