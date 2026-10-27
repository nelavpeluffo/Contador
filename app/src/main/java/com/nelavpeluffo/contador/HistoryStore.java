package com.nelavpeluffo.contador;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Guarda en el teléfono las rutinas finalizadas. */
final class HistoryStore {

    static final class Entry {
        long startedAt;   // hora de inicio (reloj del teléfono, ms)
        long finishedAt;  // hora de finalización; también sirve de id
        long totalMs;     // tiempo total cronometrado
        int blocks;       // bloques completos
        int exercises;    // ejercicios por ronda
        int blockMin;     // minutos por bloque
    }

    private static final String KEY = "history";
    private final SharedPreferences prefs;

    HistoryStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /** Más recientes primero. */
    List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.startedAt = o.optLong("startedAt");
                e.finishedAt = o.optLong("finishedAt");
                e.totalMs = o.optLong("totalMs");
                e.blocks = o.optInt("blocks");
                e.exercises = Math.max(1, o.optInt("exercises", 1));
                e.blockMin = Math.max(1, o.optInt("blockMin", 1));
                out.add(e);
            }
        } catch (JSONException ignored) {
        }
        out.sort((a, b) -> Long.compare(b.finishedAt, a.finishedAt));
        return out;
    }

    void add(Entry e) {
        List<Entry> all = load();
        all.add(0, e);
        save(all);
    }

    void remove(long finishedAt) {
        List<Entry> all = load();
        all.removeIf(e -> e.finishedAt == finishedAt);
        save(all);
    }

    private void save(List<Entry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("startedAt", e.startedAt);
                o.put("finishedAt", e.finishedAt);
                o.put("totalMs", e.totalMs);
                o.put("blocks", e.blocks);
                o.put("exercises", e.exercises);
                o.put("blockMin", e.blockMin);
                arr.put(o);
            }
        } catch (JSONException ignored) {
        }
        prefs.edit().putString(KEY, arr.toString()).apply();
    }
}
