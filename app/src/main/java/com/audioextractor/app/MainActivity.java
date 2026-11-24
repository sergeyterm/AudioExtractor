package com.audioextractor.app;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

public final class MainActivity extends Activity {
    private static final int INK = 0xff142b27, MUTED = 0xff526760, BG = 0xfff3f6f5, ACCENT = 0xff006b59, SOFT = 0xffdcefe8;
    private static final int[] RATES = {128, 192, 256, 320};
    private final Handler ui = new Handler(Looper.getMainLooper());
    private EditText url;
    private Button paste, start, cancel, open, share;
    private TextView status, result, progressLabel;
    private RadioGroup rates;
    private CheckBox rights;
    private ProgressBar progress;
    private LinearLayout resultCard;
    private ExtractService.State lastState;
    private String resultUri = "";
    private boolean launching;
    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); ui.postDelayed(this, 300); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setBackgroundColor(BG);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        FrameLayout center = new FrameLayout(this) {
            @Override protected void onMeasure(int w, int h) {
                if (getChildCount() > 0) getChildAt(0).getLayoutParams().width = Math.min(MeasureSpec.getSize(w), dp(650));
                super.onMeasure(w, h);
            }
        };
        LinearLayout root = vertical(); root.setPadding(dp(20), dp(24), dp(20), dp(30));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        center.addView(root, rp); scroll.addView(center, new ScrollView.LayoutParams(-1,-2)); setContentView(scroll);
        scroll.requestApplyInsets();

        TextView eyebrow = label("A U D I O   /   E X T R A C T O R", 12, ACCENT, true); add(root, eyebrow, 0);
        TextView headline = label("Видео → MP3", 34, INK, true); add(root, headline, 7);
        add(root, label("Вся аудиодорожка. Сразу на вашем телефоне.", 15, MUTED, false), 5);

        LinearLayout inputCard = card(); add(root, inputCard, 24);
        add(inputCard, label("01   Источник видео", 17, INK, true), 0);
        add(inputCard, label("Вставьте ссылку на доступное для скачивания видео.", 14, MUTED, false), 8);
        url = new EditText(this); url.setId(1001); url.setTextSize(16); url.setTextColor(INK); url.setHintTextColor(MUTED);
        url.setHint("https://…"); url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        url.setMinLines(2); url.setMaxLines(4); url.setGravity(Gravity.TOP | Gravity.START); url.setPadding(dp(14),dp(13),dp(14),dp(13));
        url.setBackground(shape(BG, 13, 0xffcedbd5)); add(inputCard, url, 13);
        paste = button("Вставить ссылку", false); add(inputCard, paste, 9);
        paste.setOnClickListener(v -> {
            ClipboardManager cb = getSystemService(ClipboardManager.class);
            ClipData clip = cb.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) { toast("Буфер обмена пуст"); return; }
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            String link = AudioRules.extractUrl(text == null ? "" : text.toString());
            if (link.isEmpty()) { toast("В буфере нет HTTPS-ссылки"); return; }
            url.setText(link); url.setSelection(url.length());
        });
        root.setFocusableInTouchMode(true); root.requestFocus();

        LinearLayout quality = card(); add(root, quality, 14);
        add(quality, label("02   Качество MP3",17,INK,true), 0);
        add(quality, label("Битрейт выходного файла, кбит/с",14,MUTED,false),8);
        rates = new RadioGroup(this); rates.setOrientation(RadioGroup.VERTICAL); rates.setId(1002);
        String[] descriptions = {"128  ·  компактный файл", "192  ·  стандартное качество", "256  ·  высокое качество", "320  ·  максимальный битрейт MP3"};
        for (int i = 0; i < RATES.length; i++) {
            RadioButton rb = new RadioButton(this); rb.setId(2000 + i); rb.setText(descriptions[i]); rb.setTextSize(15); rb.setTextColor(INK);
            rb.setButtonTintList(ColorStateList.valueOf(ACCENT)); rb.setMinHeight(dp(44)); rates.addView(rb, new RadioGroup.LayoutParams(-1,-2));
        }
        rates.check(2002); add(quality, rates, 8);
        add(quality,label("Повышение битрейта не восстанавливает качество исходного аудио.",12,MUTED,false),5);

        rights = new CheckBox(this); rights.setId(1003); rights.setText("У меня есть право скачать и сохранить аудио."); rights.setTextColor(INK); rights.setTextSize(14); rights.setButtonTintList(ColorStateList.valueOf(ACCENT)); rights.setMinHeight(dp(48));
        add(root, rights, 13);
        rights.setOnCheckedChangeListener((b, checked) -> updateEnabled());
        start = button("Извлечь и сохранить MP3", true); add(root, start, 9); start.setOnClickListener(v -> begin());
        cancel = button("Отменить обработку", false); add(root, cancel, 8); cancel.setVisibility(View.GONE);
        cancel.setOnClickListener(v -> {
            cancel.setEnabled(false);
            try { startService(new Intent(this, ExtractService.class).setAction(ExtractService.CANCEL)); }
            catch (RuntimeException e) { toast("Не удалось отправить команду отмены"); }
        });

        LinearLayout processing = card(); add(root, processing, 14);
        add(processing, label("03   Обработка",17,INK,true),0);
        status = label("Вставьте ссылку, чтобы начать.",15,INK,false); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(processing,status,10);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT)); progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(SOFT)); progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,dp(10)); pp.topMargin = dp(12); processing.addView(progress,pp);
        progressLabel = label("",12,MUTED,false); add(processing,progressLabel,7);

        resultCard = card(); add(root, resultCard, 14); resultCard.setVisibility(View.GONE);
        add(resultCard,label("Последний сохранённый MP3",17,INK,true),0);
        result = label("",15,INK,false); add(resultCard,result,10);
        open = button("Открыть аудио",true); add(resultCard,open,13); open.setOnClickListener(v -> openResult(false));
        share = button("Поделиться файлом",false); add(resultCard,share,8); share.setOnClickListener(v -> openResult(true));
        add(root,label("Папка: внутренняя память → Music → AudioExtractor\nОбработка на устройстве • Без передачи ссылки на сервер сборки",12,MUTED,false),18);
        add(root,label("Audio Extractor  1.0.0  /  ARM64",12,MUTED,false),12);
        if (saved == null) handleShare(getIntent());
        if (saved != null) { url.setText(saved.getString("url", "")); rates.check(saved.getInt("quality",2002)); rights.setChecked(saved.getBoolean("rights")); }
        refresh();
    }
    @Override protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b); b.putString("url",url.getText().toString()); b.putInt("quality",rates.getCheckedRadioButtonId()); b.putBoolean("rights",rights.isChecked());
    }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); handleShare(i); }
    private void handleShare(Intent i) {
        if (Intent.ACTION_SEND.equals(i.getAction()) && "text/plain".equals(i.getType())) {
            String text = i.getStringExtra(Intent.EXTRA_TEXT); String link = AudioRules.extractUrl(text);
            if (!link.isEmpty()) { url.setText(link); url.setSelection(url.length()); } else toast("В сообщении нет HTTPS-ссылки");
        }
    }
    private void begin() {
        if (ExtractService.state.busy() || ExtractService.requested || launching || !rights.isChecked()) return;
        final String link;
        try { link = AudioRules.validateUrl(url.getText().toString()); }
        catch (RuntimeException e) { url.setError(e.getMessage()); return; }
        int selected = rates.getCheckedRadioButtonId() - 2000;
        int k = RATES[Math.max(0,Math.min(RATES.length - 1,selected))];
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            && !getPreferences(MODE_PRIVATE).getBoolean("askedNotifications",false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("askedNotifications",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},7);
            return;
        }
        launching = true; ExtractService.requested = true; updateEnabled();
        try {
            startForegroundService(new Intent(this,ExtractService.class).setAction(ExtractService.START).putExtra("url",link).putExtra("bitrate",k));
            status.setText("Запуск обработки…");
        } catch (RuntimeException e) { ExtractService.requested = false; toast("Не удалось запустить обработку"); }
        finally { launching = false; }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults); if (requestCode == 7 && !isFinishing()) begin();
    }
    @Override protected void onStart() { super.onStart(); ui.removeCallbacks(tick); ui.post(tick); }
    @Override protected void onStop() { ui.removeCallbacks(tick); super.onStop(); }
    private void refresh() {
        ExtractService.State s = ExtractService.state;
        if (s != lastState) {
            lastState = s; status.setText(s.text()); progress.setVisibility(s.busy() || s.progress()==100 ? View.VISIBLE:View.GONE);
            progress.setIndeterminate(s.busy() && s.progress()<0); if (s.progress()>=0) progress.setProgress(s.progress());
            progressLabel.setText(s.busy() ? "Можно свернуть приложение или выключить экран." : s.progress()==100 ? "Файл готов к воспроизведению." : "");
            SharedPreferences prefs = getSharedPreferences(ExtractService.PREFS, MODE_PRIVATE);
            resultUri = s.uri().isEmpty() ? prefs.getString("uri", "") : s.uri();
            String name = s.uri().isEmpty() ? prefs.getString("name", "") : s.name();
            long duration = s.uri().isEmpty() ? prefs.getLong("duration",0) : s.duration();
            long bytes = s.uri().isEmpty() ? prefs.getLong("bytes",0) : s.bytes();
            resultCard.setVisibility(resultUri.isEmpty() ? View.GONE:View.VISIBLE);
            result.setText(name + "\n" + AudioRules.duration(duration) + "  ·  " + String.format(java.util.Locale.getDefault(),"%.1f МБ",bytes / 1048576.0));
        }
        updateEnabled();
    }
    private void updateEnabled() {
        if (start == null) return;
        boolean busy = ExtractService.state.busy() || ExtractService.requested || launching;
        start.setEnabled(!busy && rights.isChecked()); start.setAlpha(start.isEnabled()?1f:.48f);
        url.setEnabled(!busy); paste.setEnabled(!busy); rights.setEnabled(!busy);
        for (int i=0;i<rates.getChildCount();i++) rates.getChildAt(i).setEnabled(!busy);
        cancel.setVisibility(busy ? View.VISIBLE:View.GONE); cancel.setEnabled(ExtractService.state.busy() && !ExtractService.state.text().startsWith("Отмена") && !ExtractService.state.text().startsWith("Остановка"));
    }
    private void openResult(boolean send) {
        if (resultUri.isEmpty()) return;
        Uri uri = Uri.parse(resultUri);
        Intent i = send ? new Intent(Intent.ACTION_SEND).setType("audio/mpeg").putExtra(Intent.EXTRA_STREAM,uri).setClipData(ClipData.newRawUri("MP3",uri))
            : new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"audio/mpeg");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivity(Intent.createChooser(i,send?"Поделиться MP3":"Открыть MP3")); }
        catch (RuntimeException e) { toast("Не удалось открыть файл. Проверьте папку Music/AudioExtractor."); }
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private int dp(float v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private LinearLayout vertical() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout card() { LinearLayout l=vertical(); l.setPadding(dp(17),dp(17),dp(17),dp(17)); l.setBackground(shape(Color.WHITE,19,0xffdce5e1)); return l; }
    private TextView label(String text,int size,int color,boolean bold) { TextView t=new TextView(this); t.setText(text); t.setTextSize(size); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setLineSpacing(dp(2),1); return t; }
    private void add(LinearLayout l,View v,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(top); l.addView(v,p); }
    private GradientDrawable shape(int fill,int radius,int stroke) { GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); if(stroke!=0)d.setStroke(dp(1),stroke); return d; }
    private Button button(String text,boolean primary) {
        Button b=new Button(this); b.setAllCaps(false); b.setText(text); b.setTextSize(15); b.setTextColor(primary?Color.WHITE:ACCENT); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setMinHeight(dp(50)); b.setPadding(dp(12),dp(10),dp(12),dp(10)); b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x33ffffff:0x22006b59),shape(primary?ACCENT:SOFT,13,0),null)); return b;
    }
}
