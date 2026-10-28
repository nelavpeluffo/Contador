package com.nelavpeluffo.contador;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla dividida en dos contadores sincronizados:
 *  - Arriba: tiempo total de la rutina.
 *  - Abajo: bloques que se repiten solos, alternando dos duraciones
 *    (Tiempo 1 y Tiempo 2, en minutos).
 *
 * Con 1 ejercicio, cada bloque alterna el fondo turquesa / turquesa 15% más oscuro.
 * Con 2 a 5 ejercicios, cada bloque es el ejercicio siguiente (cada uno con su color)
 * y una ronda se completa al terminar el último.
 *
 * Menú ☰ (arriba a la derecha): historial de rutinas finalizadas, con opción de eliminar.
 */
public class MainActivity extends Activity {

    private static final long TICK_MS = 50;
    private static final int MIN_BLOCK_MIN = 1, MAX_BLOCK_MIN = 60;

    private static final int IDLE = 0, RUNNING = 1, PAUSED = 2, FINISHED = 3;

    // Fondo y color de texto de cada bloque.
    // 0 = turquesa, 1 = el mismo turquesa con 15% menos de luminosidad.
    private static final int[] BLOCK_BG = {0xFF2DD4BF, 0xFF1F9687, 0xFFFBBF24, 0xFFFB7185, 0xFF7C3AED};
    private static final int[] BLOCK_FG = {0xFF042F2B, 0xFFFFFFFF, 0xFF2B1B00, 0xFF3B0A14, 0xFFFFFFFF};

    private static final int TOTAL_ACCENT = 0xFF60A5FA;
    private static final int DARK = 0xFF0B0E13;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8B95A5;
    private static final int CARD = 0xFF1F2631;

    private static final Locale ES = new Locale("es", "AR");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            if (state == RUNNING) {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    // --- Configuración ---
    private int blockMinA = 1;   // Tiempo 1: bloques 1, 3, 5...
    private int blockMinB = 1;   // Tiempo 2: bloques 2, 4, 6...
    private int exercises = 1;

    // --- Estado compartido ---
    private int state = IDLE;
    private long startedAtWall;
    private long totalAccumMs;
    private long totalStartedAt;
    private long blockRemainingMs = 60_000;
    private long blockEndsAt;
    private int blocksDone;
    private long lastBeepSecond = -1;

    // --- Vistas ---
    private View topHalf, topRunning, topSummary, bottomHalf, configBox, scrim, drawer;
    private TextView totalTime, intervalTime, exerciseLabel, roundLabel;
    private TextView summaryTotal, summaryInterval, detailTotal, detailRounds, detailConfig;
    private TextView captionA, captionB, captionExercises, minusA, valueA, plusA, minusB, valueB, plusB;
    private final TextView[] exCircles = new TextView[5];
    private ImageView totalPlay, totalStop, intervalPlay, intervalStop, intervalReset;
    private ProgressBar intervalProgress;
    private LinearLayout historyList;

    private int renderedColor = -1;
    private int renderedState = -1;
    private boolean playShowsPause;
    private boolean drawerOpen;
    private int bottomBasePad;

    private ToneGenerator tone;
    private Vibrator vibrator;
    private float density;
    private SharedPreferences prefs;
    private HistoryStore history;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        density = getResources().getDisplayMetrics().density;

        prefs = getSharedPreferences("config", MODE_PRIVATE);
        history = new HistoryStore(prefs);
        blockMinA = clamp(prefs.getInt("blockMinA", prefs.getInt("blockMin", 1)), MIN_BLOCK_MIN, MAX_BLOCK_MIN);
        blockMinB = clamp(prefs.getInt("blockMinB", blockMinA), MIN_BLOCK_MIN, MAX_BLOCK_MIN);
        exercises = clamp(prefs.getInt("exercises", 1), 1, 5);
        blockRemainingMs = blockLenMs(0);

        topHalf = findViewById(R.id.topHalf);
        topRunning = findViewById(R.id.topRunning);
        topSummary = findViewById(R.id.topSummary);
        bottomHalf = findViewById(R.id.bottomHalf);
        configBox = findViewById(R.id.configBox);
        scrim = findViewById(R.id.scrim);
        drawer = findViewById(R.id.drawer);
        historyList = findViewById(R.id.historyList);
        totalTime = findViewById(R.id.totalTime);
        intervalTime = findViewById(R.id.intervalTime);
        exerciseLabel = findViewById(R.id.exerciseLabel);
        roundLabel = findViewById(R.id.roundLabel);
        summaryTotal = findViewById(R.id.summaryTotal);
        summaryInterval = findViewById(R.id.summaryInterval);
        detailTotal = findViewById(R.id.detailTotal);
        detailRounds = findViewById(R.id.detailRounds);
        detailConfig = findViewById(R.id.detailConfig);
        captionA = findViewById(R.id.captionA);
        captionB = findViewById(R.id.captionB);
        captionExercises = findViewById(R.id.captionExercises);
        minusA = findViewById(R.id.minusA);
        valueA = findViewById(R.id.valueA);
        plusA = findViewById(R.id.plusA);
        minusB = findViewById(R.id.minusB);
        valueB = findViewById(R.id.valueB);
        plusB = findViewById(R.id.plusB);
        totalPlay = findViewById(R.id.totalPlay);
        totalStop = findViewById(R.id.totalStop);
        intervalPlay = findViewById(R.id.intervalPlay);
        intervalStop = findViewById(R.id.intervalStop);
        intervalReset = findViewById(R.id.intervalReset);
        intervalProgress = findViewById(R.id.intervalProgress);

        setupEdgeToEdge();
        buildExerciseCircles();

        // Los dos contadores comparten play/pausa y finalizar.
        totalPlay.setOnClickListener(v -> togglePlay());
        intervalPlay.setOnClickListener(v -> togglePlay());
        totalTime.setOnClickListener(v -> togglePlay());
        intervalTime.setOnClickListener(v -> togglePlay());
        totalStop.setOnClickListener(v -> finishRoutine());
        intervalStop.setOnClickListener(v -> finishRoutine());
        intervalReset.setOnClickListener(v -> resetBlock());

        minusA.setOnClickListener(v -> changeMinutes(true, -1));
        plusA.setOnClickListener(v -> changeMinutes(true, +1));
        minusB.setOnClickListener(v -> changeMinutes(false, -1));
        plusB.setOnClickListener(v -> changeMinutes(false, +1));

        findViewById(R.id.menuButton).setOnClickListener(v -> openDrawer());
        findViewById(R.id.drawerClose).setOnClickListener(v -> closeDrawer());
        scrim.setOnClickListener(v -> closeDrawer());

        totalPlay.setBackground(shape(TOTAL_ACCENT, 0, false));
        totalPlay.setImageTintList(ColorStateList.valueOf(DARK));
        totalStop.setBackground(shape(0, 0x55FFFFFF, false));
        totalStop.setImageTintList(ColorStateList.valueOf(WHITE));

        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        } catch (RuntimeException e) {
            tone = null;
        }
        vibrator = getSystemService(Vibrator.class);

        if (savedInstanceState != null) {
            restore(savedInstanceState);
        }

        render();
        ensureTicking();
    }

    /** Dibuja detrás de las barras del sistema y deja espacio para ellas. */
    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        bottomBasePad = bottomHalf.getPaddingBottom();
        findViewById(R.id.root).setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            topHalf.setPadding(0, top, 0, 0);
            bottomHalf.setPadding(bottomHalf.getPaddingLeft(), bottomHalf.getPaddingTop(),
                    bottomHalf.getPaddingRight(), bottomBasePad + bottom);
            drawer.setPadding(0, top, 0, bottom);
            return insets;
        });
    }

    private void buildExerciseCircles() {
        LinearLayout row = findViewById(R.id.exerciseRow);
        int size = Math.round(40 * density);
        for (int i = 0; i < 5; i++) {
            final int n = i + 1;
            TextView c = new TextView(this);
            c.setText(String.valueOf(n));
            c.setGravity(Gravity.CENTER);
            c.setTextSize(16);
            c.setTypeface(Typeface.DEFAULT_BOLD);
            c.setClickable(true);
            c.setFocusable(true);
            c.setContentDescription(n == 1 ? "1 ejercicio" : n + " ejercicios");
            c.setOnClickListener(v -> selectExercises(n));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, size, 1f);
            lp.leftMargin = Math.round(3 * density);
            lp.rightMargin = Math.round(3 * density);
            row.addView(c, lp);
            exCircles[i] = c;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureTicking();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        if (tone != null) {
            tone.release();
            tone = null;
        }
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (drawerOpen) {
            closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    // ================= Acciones =================

    /** Play/pausa: siempre afecta a los dos contadores juntos. */
    private void togglePlay() {
        long now = SystemClock.elapsedRealtime();
        if (state == RUNNING) {
            totalAccumMs += now - totalStartedAt;
            blockRemainingMs = Math.max(0, blockEndsAt - now);
            state = PAUSED;
        } else {
            if (state == IDLE || state == FINISHED) {
                // Rutina nueva desde cero.
                totalAccumMs = 0;
                blocksDone = 0;
                blockRemainingMs = blockLenMs(0);
                lastBeepSecond = -1;
                startedAtWall = System.currentTimeMillis();
            }
            totalStartedAt = now;
            blockEndsAt = now + blockRemainingMs;
            state = RUNNING;
        }
        ensureTicking();
        render();
    }

    /** Finalizar: detiene los dos contadores, guarda en el historial y muestra el resumen. */
    private void finishRoutine() {
        if (state != RUNNING && state != PAUSED) return;
        long now = SystemClock.elapsedRealtime();
        if (state == RUNNING) {
            totalAccumMs += now - totalStartedAt;
            blockRemainingMs = Math.max(0, blockEndsAt - now);
        }
        state = FINISHED;
        handler.removeCallbacks(ticker);
        beep(ToneGenerator.TONE_PROP_ACK, 400);

        HistoryStore.Entry e = new HistoryStore.Entry();
        e.startedAt = startedAtWall;
        e.finishedAt = System.currentTimeMillis();
        e.totalMs = totalAccumMs;
        e.blocks = blocksDone;
        e.exercises = exercises;
        e.blockMinA = blockMinA;
        e.blockMinB = blockMinB;
        history.add(e);

        render();
    }

    /** Reiniciar solo el bloque de abajo; el tiempo total sigue. */
    private void resetBlock() {
        if (state != RUNNING && state != PAUSED) return;
        blockRemainingMs = blockLenMs(blocksDone);
        if (state == RUNNING) {
            blockEndsAt = SystemClock.elapsedRealtime() + blockRemainingMs;
        }
        lastBeepSecond = -1;
        render();
    }

    private void changeMinutes(boolean first, int delta) {
        if (state == RUNNING || state == PAUSED) return;
        if (first) {
            blockMinA = clamp(blockMinA + delta, MIN_BLOCK_MIN, MAX_BLOCK_MIN);
        } else {
            blockMinB = clamp(blockMinB + delta, MIN_BLOCK_MIN, MAX_BLOCK_MIN);
        }
        blockRemainingMs = blockLenMs(0);
        prefs.edit().putInt("blockMinA", blockMinA).putInt("blockMinB", blockMinB).apply();
        renderedColor = -1;
        render();
    }

    private void selectExercises(int n) {
        if (state == RUNNING || state == PAUSED) return;
        exercises = n;
        prefs.edit().putInt("exercises", n).apply();
        renderedColor = -1;
        render();
    }

    // ================= Loop =================

    private void ensureTicking() {
        handler.removeCallbacks(ticker);
        if (state == RUNNING) {
            handler.post(ticker);
        }
    }

    private void tick() {
        if (state != RUNNING) return;
        long now = SystemClock.elapsedRealtime();
        long remaining = blockEndsAt - now;
        if (remaining <= 0) {
            boolean roundDone = false;
            while (remaining <= 0) {
                blocksDone++;
                if (blocksDone % exercises == 0) roundDone = true;
                long len = blockLenMs(blocksDone);
                blockEndsAt += len;
                remaining += len;
            }
            lastBeepSecond = -1;
            signalBlockEnd(roundDone && exercises > 1);
        } else {
            long sec = (remaining + 999) / 1000;
            if (sec <= 3 && sec != lastBeepSecond) {
                lastBeepSecond = sec;
                beep(ToneGenerator.TONE_PROP_BEEP, 150);
            }
        }
        render();
    }

    // ================= Pantalla =================

    private boolean active() {
        return state == RUNNING || state == PAUSED;
    }

    /** Duración del bloque n (0, 1, 2...): alterna Tiempo 1 / Tiempo 2. */
    private long blockLenMs(int n) {
        return (n % 2 == 0 ? blockMinA : blockMinB) * 60_000L;
    }

    /** Índice de color del bloque actual. */
    private int colorIndex() {
        if (!active()) return 0;
        if (exercises == 1) return blocksDone % 2;
        return blocksDone % exercises;
    }

    private void render() {
        long now = SystemClock.elapsedRealtime();
        boolean active = active();

        // --- Arriba ---
        long total = totalAccumMs + (state == RUNNING ? now - totalStartedAt : 0);
        if (state != renderedState) {
            topRunning.setVisibility(state == FINISHED ? View.GONE : View.VISIBLE);
            topSummary.setVisibility(state == FINISHED ? View.VISIBLE : View.GONE);
            configBox.setVisibility(active ? View.GONE : View.VISIBLE);
            setEnabled(totalStop, active);
            setEnabled(intervalStop, active);
            setEnabled(intervalReset, active);
            if (state == FINISHED) fillSummary();
        }
        totalTime.setText(formatTime(total));

        boolean showPause = state == RUNNING;
        if (showPause != playShowsPause || state != renderedState) {
            playShowsPause = showPause;
            int icon = showPause ? R.drawable.ic_pause : R.drawable.ic_play;
            totalPlay.setImageResource(icon);
            intervalPlay.setImageResource(icon);
        }

        // --- Abajo ---
        long len = blockLenMs(active ? blocksDone : 0);
        long remaining = state == RUNNING ? Math.max(0, blockEndsAt - now)
                : (active ? blockRemainingMs : len);
        intervalTime.setText(formatCountdown(remaining));
        intervalProgress.setProgress((int) (1000 * remaining / len));
        valueA.setText(blockMinA + " min");
        valueB.setText(blockMinB + " min");

        if (active) {
            int ex = blocksDone % exercises + 1;
            int round = blocksDone / exercises + 1;
            exerciseLabel.setText(exercises > 1
                    ? String.format(Locale.US, "EJERCICIO %d/%d", ex, exercises)
                    : String.format(Locale.US, "BLOQUE %d", blocksDone + 1));
            roundLabel.setText(String.format(Locale.US, "RONDA %d", round));
        } else {
            exerciseLabel.setText(state == FINISHED ? "NUEVA RUTINA" : "PREPARADO");
            roundLabel.setText("");
        }

        int color = colorIndex();
        if (color != renderedColor || state != renderedState) {
            applyBottomColors(color);
            renderedColor = color;
        }
        renderedState = state;
    }

    private void applyBottomColors(int idx) {
        int bg = BLOCK_BG[idx];
        int fg = BLOCK_FG[idx];
        int fgSoft = withAlpha(fg, 0x66);

        bottomHalf.setBackgroundColor(bg);
        intervalTime.setTextColor(fg);
        exerciseLabel.setTextColor(fg);
        roundLabel.setTextColor(withAlpha(fg, 0xCC));
        captionA.setTextColor(withAlpha(fg, 0xB3));
        captionB.setTextColor(withAlpha(fg, 0xB3));
        captionExercises.setTextColor(withAlpha(fg, 0xB3));
        valueA.setTextColor(fg);
        valueB.setTextColor(fg);
        intervalProgress.setProgressTintList(ColorStateList.valueOf(fg));
        intervalProgress.setProgressBackgroundTintList(ColorStateList.valueOf(withAlpha(fg, 0x33)));

        intervalPlay.setBackground(shape(fg, 0, false));
        intervalPlay.setImageTintList(ColorStateList.valueOf(bg));
        intervalStop.setBackground(shape(0, fgSoft, false));
        intervalStop.setImageTintList(ColorStateList.valueOf(fg));
        intervalReset.setBackground(shape(0, fgSoft, false));
        intervalReset.setImageTintList(ColorStateList.valueOf(fg));

        for (TextView b : new TextView[]{minusA, plusA, minusB, plusB}) {
            styleOption(b, false, bg, fg, fgSoft, true);
        }
        setEnabled(minusA, blockMinA > MIN_BLOCK_MIN);
        setEnabled(plusA, blockMinA < MAX_BLOCK_MIN);
        setEnabled(minusB, blockMinB > MIN_BLOCK_MIN);
        setEnabled(plusB, blockMinB < MAX_BLOCK_MIN);
        for (int i = 0; i < 5; i++) {
            styleOption(exCircles[i], i + 1 == exercises, bg, fg, fgSoft, true);
        }
    }

    private void styleOption(TextView v, boolean selected, int bg, int fg, int fgSoft, boolean circle) {
        v.setBackground(selected ? shape(fg, 0, circle) : shape(0, fgSoft, circle));
        v.setTextColor(selected ? bg : fg);
    }

    private void fillSummary() {
        String total = formatTime(totalAccumMs);
        summaryTotal.setText(total);
        summaryInterval.setText(formatCountdown(blockRemainingMs));
        detailTotal.setText(total);
        detailRounds.setText(roundsText(blocksDone, exercises));
        detailConfig.setText(configText(exercises, blockMinA, blockMinB, blocksDone));
    }

    private void setEnabled(View v, boolean enabled) {
        v.setEnabled(enabled);
        v.setAlpha(enabled ? 1f : 0.3f);
    }

    // ================= Historial =================

    private void openDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        buildHistory();
        scrim.setVisibility(View.VISIBLE);
        scrim.animate().alpha(1f).setDuration(220).start();
        drawer.setVisibility(View.VISIBLE);
        drawer.post(() -> {
            drawer.setTranslationX(drawer.getWidth());
            drawer.animate().translationX(0).setDuration(220).start();
        });
    }

    private void closeDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        scrim.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> scrim.setVisibility(View.GONE)).start();
        drawer.animate().translationX(drawer.getWidth()).setDuration(200)
                .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
    }

    private void buildHistory() {
        historyList.removeAllViews();
        List<HistoryStore.Entry> entries = history.load();
        if (entries.isEmpty()) {
            TextView empty = text("Todavía no hay rutinas finalizadas.", 15, MUTED, false);
            empty.setPadding(4, dp(16), 4, 0);
            historyList.addView(empty);
            return;
        }
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE d 'de' MMMM", ES);
        SimpleDateFormat hourFmt = new SimpleDateFormat("HH:mm", ES);
        String lastDay = null;
        for (HistoryStore.Entry e : entries) {
            String day = dayLabel(e.finishedAt, dayFmt);
            if (!day.equals(lastDay)) {
                TextView h = text(day.toUpperCase(ES), 12, MUTED, true);
                h.setLetterSpacing(0.12f);
                h.setPadding(dp(4), dp(lastDay == null ? 4 : 18), 0, dp(8));
                historyList.addView(h);
                lastDay = day;
            }
            historyList.addView(historyCard(e, hourFmt));
        }
    }

    private View historyCard(HistoryStore.Entry e, SimpleDateFormat hourFmt) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        card.setBackground(bg);
        card.setPadding(dp(14), dp(12), dp(4), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(8);
        card.setLayoutParams(clp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        String hours = e.startedAt > 0
                ? hourFmt.format(new Date(e.startedAt)) + " – " + hourFmt.format(new Date(e.finishedAt))
                : hourFmt.format(new Date(e.finishedAt));
        texts.addView(text(hours, 16, WHITE, true));
        TextView main = text("Total " + formatTime(e.totalMs) + " · Rondas " + roundsText(e.blocks, e.exercises),
                14, WHITE, false);
        main.setPadding(0, dp(4), 0, 0);
        texts.addView(main);
        TextView cfg = text(configText(e.exercises, e.blockMinA, e.blockMinB, e.blocks), 12, MUTED, false);
        cfg.setPadding(0, dp(2), 0, 0);
        texts.addView(cfg);
        card.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView del = new ImageView(this);
        del.setImageResource(R.drawable.ic_delete);
        del.setImageTintList(ColorStateList.valueOf(MUTED));
        del.setPadding(dp(12), dp(12), dp(12), dp(12));
        del.setBackgroundResource(android.R.drawable.list_selector_background);
        del.setContentDescription("Eliminar");
        del.setOnClickListener(v -> confirmDelete(e));
        card.addView(del, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return card;
    }

    private void confirmDelete(HistoryStore.Entry e) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Eliminar rutina")
                .setMessage("¿Eliminar del historial la rutina de "
                        + formatTime(e.totalMs) + "?")
                .setPositiveButton("Eliminar", (d, w) -> {
                    history.remove(e.finishedAt);
                    buildHistory();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static String dayLabel(long when, SimpleDateFormat dayFmt) {
        Calendar c = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(when);
        if (sameDay(c, t)) return "Hoy";
        c.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(c, t)) return "Ayer";
        return dayFmt.format(new Date(when));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    // ================= Avisos =================

    private void signalBlockEnd(boolean roundDone) {
        beep(ToneGenerator.TONE_PROP_BEEP2, 600);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (roundDone) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 150, 300}, -1));
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    private void beep(int toneType, int durationMs) {
        if (tone != null) {
            try {
                tone.startTone(toneType, durationMs);
            } catch (RuntimeException ignored) {
            }
        }
    }

    // ================= Utilidades =================

    private static String roundsText(int blocks, int exercises) {
        int rounds = blocks / exercises;
        int extra = blocks % exercises;
        String r = String.valueOf(rounds);
        if (extra > 0) {
            r += extra == 1 ? " + 1 ejercicio" : " + " + extra + " ejercicios";
        }
        return r;
    }

    private static String configText(int exercises, int minA, int minB, int blocks) {
        String times = minA == minB
                ? String.format(Locale.US, "bloques de %d min", minA)
                : String.format(Locale.US, "bloques de %d y %d min", minA, minB);
        return String.format(Locale.US, "%d %s · %s · %d %s",
                exercises, exercises == 1 ? "ejercicio" : "ejercicios", times,
                blocks, blocks == 1 ? "bloque completo" : "bloques completos");
    }

    /** Tiempo total: 05:23 o 1:05:23. */
    private static String formatTime(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }

    /** Cuenta regresiva del bloque: 1:30 o, en el último minuto, 45. */
    private static String formatCountdown(long ms) {
        long sec = (ms + 999) / 1000;
        if (sec >= 60) {
            return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
        }
        return String.valueOf(sec);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private int dp(int v) {
        return Math.round(v * density);
    }

    private Drawable shape(int fill, int stroke, boolean circle) {
        GradientDrawable g = new GradientDrawable();
        if (circle) {
            g.setShape(GradientDrawable.OVAL);
        } else {
            g.setCornerRadius(100 * density);
        }
        g.setColor(fill);
        if (stroke != 0) {
            g.setStroke(Math.round(2 * density), stroke);
        }
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), g, null);
    }

    // ================= Guardar / restaurar =================

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("state", state);
        out.putLong("startedAtWall", startedAtWall);
        out.putLong("totalAccumMs", totalAccumMs);
        out.putLong("totalStartedAt", totalStartedAt);
        out.putLong("blockRemainingMs", blockRemainingMs);
        out.putLong("blockEndsAt", blockEndsAt);
        out.putInt("blocksDone", blocksDone);
    }

    private void restore(Bundle in) {
        state = in.getInt("state", IDLE);
        startedAtWall = in.getLong("startedAtWall");
        totalAccumMs = in.getLong("totalAccumMs");
        totalStartedAt = in.getLong("totalStartedAt");
        blockRemainingMs = in.getLong("blockRemainingMs", blockLenMs(0));
        blockEndsAt = in.getLong("blockEndsAt");
        blocksDone = in.getInt("blocksDone");
    }
}
