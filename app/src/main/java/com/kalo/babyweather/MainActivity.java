package com.kalo.babyweather;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private TextView weatherView, adviceView, statusView, detailView;
    private Spinner citySpinner;
    private Button refreshBtn;
    private ImageView photoView;
    private int currentBand = 3;
    private LinearLayout optLayout;
    private android.widget.EditText apiBaseInput, apiKeyInput, apiModelInput;
    private Button aiBtn;
    private int currentOpt = 0;
    private String currentAsset = null;
    private LinearLayout shopLayout;
    private Outfit currentOutfit;
    private ExecutorServiceHolder execHolder = new ExecutorServiceHolder();
    private Handler main = new Handler(Looper.getMainLooper());

    static class ExecutorServiceHolder { java.util.concurrent.ExecutorService e = java.util.concurrent.Executors.newSingleThreadExecutor(); }

    private final String[] cityNames = {"📍 使用手机定位","北京","上海","天津","重庆","广州","深圳","杭州","南京","苏州","成都","武汉","西安","长沙","郑州","合肥","青岛","大连","厦门","昆明","哈尔滨","乌鲁木齐"};
    private final double[][] cityCoords = {{0,0},{39.90,116.40},{31.23,121.47},{39.13,117.20},{29.56,106.55},{23.13,113.26},{22.55,114.06},{30.27,120.15},{32.06,118.80},{31.30,120.58},{30.66,104.06},{30.59,114.31},{34.34,108.94},{28.23,112.94},{34.75,113.63},{31.86,117.28},{36.07,120.38},{38.91,121.61},{24.48,118.09},{25.04,102.71},{45.80,126.53},{43.83,87.62}};

    // ---------- data ----------
    static class Item { String label, spec, keyword; Item(String l,String s,String k){label=l;spec=s;keyword=k;} }
    static class Option { String name, asset; java.util.List<Item> items = new java.util.ArrayList<>(); }
    static class Outfit {
        int band, topColor, outerColor, bottomColor, shoeColor;
        int sleeve = 1;      // 0 short, 1 long
        int pants = 2;       // 0 shorts, 1 cropped, 2 long, 3 thick
        boolean hasOuter=false, puffy=false, scarf=false, gloves=false, rainy=false;
        int hat = 0;         // 0 none, 1 sunhat, 2 cap, 3 winter hat
        String summary = "";
        List<Item> items = new ArrayList<>();
        List<Option> options = new ArrayList<>();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 40, 36, 36);
        root.setBackgroundColor(Color.parseColor("#FFF8F0"));

        TextView title = new TextView(this);
        title.setText("👶 宝宝穿衣助手");
        title.setTextSize(26); title.setTextColor(Color.parseColor("#222222")); title.setGravity(Gravity.CENTER);
        root.addView(title);

        int age = Calendar.getInstance().get(Calendar.YEAR) - 2021;
        TextView ageView = new TextView(this);
        ageView.setText("2021年出生 · 现在约 " + age + " 岁（幼儿园大班阶段）\n尺码参考：身高105–115cm多穿120码，鞋约28–29码（以实测身高脚长为准）");
        ageView.setTextSize(13); ageView.setTextColor(Color.parseColor("#666666")); ageView.setGravity(Gravity.CENTER); ageView.setPadding(0,12,0,20);
        root.addView(ageView);

        TextView cityLabel = new TextView(this); cityLabel.setText("选择城市："); cityLabel.setTextSize(15); root.addView(cityLabel);
        citySpinner = new Spinner(this);
        citySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cityNames));
        citySpinner.setSelection(2);
        root.addView(citySpinner);

        refreshBtn = new Button(this);
        refreshBtn.setText("🌤  获取今日天气并生成穿衣建议");
        refreshBtn.setTextSize(16);
        refreshBtn.setOnClickListener(v -> loadWeather());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0,20,0,12); refreshBtn.setLayoutParams(bp);
        root.addView(refreshBtn);

        aiBtn = new Button(this);
        aiBtn.setText("🤖  获取当日推荐（AI 大模型）");
        aiBtn.setTextSize(16);
        aiBtn.setOnClickListener(v -> fetchAiRecommendation());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2); ap.setMargins(0,0,0,12); aiBtn.setLayoutParams(ap);
        root.addView(aiBtn);

        statusView = new TextView(this); statusView.setText("正在准备…"); statusView.setTextSize(13); statusView.setTextColor(Color.parseColor("#888888")); root.addView(statusView);

        weatherView = makeCard(root, "今日天气");

        // ---- visual baby ----
        TextView vh = new TextView(this); vh.setText("\n今日造型（穿在宝宝身上就是这样）"); vh.setTextSize(18); vh.setTextColor(Color.parseColor("#E67E22")); root.addView(vh);
        android.widget.FrameLayout photoFrame = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2); fp.setMargins(0,8,0,4); photoFrame.setLayoutParams(fp);
        photoView = new ImageView(this);
        photoView.setBackgroundColor(Color.WHITE);
        photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photoView.setAdjustViewBounds(true);
        photoView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(-1, 1100));
        photoFrame.addView(photoView);
        Button shuffleBtn = new Button(this);
        shuffleBtn.setText("🔀 换一套");
        shuffleBtn.setTextSize(14);
        android.widget.FrameLayout.LayoutParams shp = new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP);
        shp.setMargins(0, 16, 16, 0); shuffleBtn.setLayoutParams(shp);
        shuffleBtn.setOnClickListener(v -> shuffleOption());
        photoFrame.addView(shuffleBtn);
        root.addView(photoFrame);
        TextView hint = new TextView(this); hint.setText("👆 点一下照片可以放大，放大后双指捏合缩放、拖动看细节（写实模特图）"); hint.setTextSize(13); hint.setTextColor(Color.parseColor("#888888")); hint.setGravity(Gravity.CENTER); root.addView(hint);
        photoView.setOnClickListener(v -> showZoom());
        TextView oh = new TextView(this); oh.setText("\n换一套（同温度多套搭配，点着切换）："); oh.setTextSize(15); oh.setTextColor(Color.parseColor("#E67E22")); root.addView(oh);
        optLayout = new LinearLayout(this); optLayout.setOrientation(LinearLayout.VERTICAL); root.addView(optLayout);

        detailView = makeCard(root, "每一件具体是什么（照着买/照着穿）");
        adviceView = makeCard(root, "穿衣建议与提醒");

        TextView sh = new TextView(this); sh.setText("\n🛒 淘宝搜同款（点开直达搜索结果）"); sh.setTextSize(18); sh.setTextColor(Color.parseColor("#E67E22")); root.addView(sh);
        TextView sn = new TextView(this); sn.setText("给的是带尺码的搜索链接，不是单个商品链接 — 单品链接容易失效/下架，搜索结果是实时在售的，照关键词+120码挑就行。"); sn.setTextSize(12); sn.setTextColor(Color.parseColor("#999999")); root.addView(sn);
        shopLayout = new LinearLayout(this); shopLayout.setOrientation(LinearLayout.VERTICAL); root.addView(shopLayout);

        TextView ah = new TextView(this); ah.setText("\n⚙️ AI 模型设置（填一次保存，下次自动带出）"); ah.setTextSize(18); ah.setTextColor(Color.parseColor("#E67E22")); root.addView(ah);
        TextView an = new TextView(this); an.setText("填 OpenAI 兼容接口：地址如 https://api.deepseek.com/v1 或 https://api.openai.com/v1；模型如 deepseek-chat / gpt-4o-mini。Key 只存在本机，不上传别处。"); an.setTextSize(12); an.setTextColor(Color.parseColor("#999999")); root.addView(an);
        android.content.SharedPreferences cfg = getSharedPreferences("ai_cfg", MODE_PRIVATE);
        apiBaseInput = new android.widget.EditText(this); apiBaseInput.setHint("接口地址，如 https://api.deepseek.com/v1"); apiBaseInput.setTextSize(14); apiBaseInput.setSingleLine(true); apiBaseInput.setText(cfg.getString("base", "")); root.addView(apiBaseInput);
        apiKeyInput = new android.widget.EditText(this); apiKeyInput.setHint("API Key"); apiKeyInput.setTextSize(14); apiKeyInput.setSingleLine(true); apiKeyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD); apiKeyInput.setText(cfg.getString("key", "")); root.addView(apiKeyInput);
        apiModelInput = new android.widget.EditText(this); apiModelInput.setHint("模型名，如 deepseek-chat"); apiModelInput.setTextSize(14); apiModelInput.setSingleLine(true); apiModelInput.setText(cfg.getString("model", "")); root.addView(apiModelInput);
        Button saveCfg = new Button(this); saveCfg.setText("保存 AI 设置"); saveCfg.setTextSize(14);
        saveCfg.setOnClickListener(v -> { cfg.edit().putString("base", apiBaseInput.getText().toString().trim()).putString("key", apiKeyInput.getText().toString().trim()).putString("model", apiModelInput.getText().toString().trim()).apply(); Toast.makeText(this, "AI 设置已保存", Toast.LENGTH_SHORT).show(); });
        root.addView(saveCfg);

        TextView foot = new TextView(this);
        foot.setText("\n小原则：5岁孩子跑动多，一般比大人少穿一件或穿一样多就行；摸后背/后颈，温热不出汗就是刚好。\n天气数据：Open-Meteo（免费公开天气，无需注册）");
        foot.setTextSize(12); foot.setTextColor(Color.parseColor("#999999")); root.addView(foot);

        ScrollView sv = new ScrollView(this); sv.addView(root); setContentView(sv);

        // placeholder outfit so the baby is dressed before weather loads
        applyOutfit(outfitFor(15, false, 0, 0, 3));

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        loadWeather();
    }

    private TextView makeCard(LinearLayout root, String heading) {
        TextView h = new TextView(this); h.setText("\n" + heading); h.setTextSize(18); h.setTextColor(Color.parseColor("#E67E22")); root.addView(h);
        TextView t = new TextView(this); t.setText("—"); t.setTextSize(16); t.setTextColor(Color.parseColor("#333333"));
        t.setPadding(24,20,24,20); t.setBackgroundColor(Color.WHITE); t.setLineSpacing(6,1.0f); root.addView(t);
        return t;
    }

    private void showZoom() {
        if (currentOutfit == null) return;
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout lay = new LinearLayout(this); lay.setOrientation(LinearLayout.VERTICAL); lay.setBackgroundColor(Color.WHITE); lay.setPadding(24,32,24,24);
        TextView h = new TextView(this); h.setText("今日造型放大图 — 双指缩放 / 拖动"); h.setTextSize(17); h.setGravity(Gravity.CENTER); lay.addView(h);
        TouchImageView big = new TouchImageView(this); big.setImageBitmap(currentAsset != null ? loadAssetBitmap(currentAsset) : loadBandBitmap(currentBand)); big.setBackgroundColor(Color.parseColor("#FFF8F0"));
        lay.addView(big, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView det = new TextView(this); det.setText(currentOutfit != null && !currentOutfit.options.isEmpty() ? detailFor(currentOutfit, currentOutfit.options.get(currentOpt)) : detailText(currentOutfit)); det.setTextSize(15); det.setPadding(16,16,16,16); lay.addView(det);
        Button close = new Button(this); close.setText("关闭"); close.setOnClickListener(v -> d.dismiss()); lay.addView(close);
        d.setContentView(lay); d.show();
    }

    // ---------- outfit logic ----------
    private Outfit outfitFor(double day, boolean rainy, double uv, double wind, double tmin) {
        Outfit o = new Outfit(); o.rainy = rainy;
        if (day >= 28) {
            o.band=0; o.sleeve=0; o.pants=0; o.hat=1;
            o.summary="奶黄+浅粉：小飞袖T恤+裙裤+草帽";
            o.items.add(new Item("上衣","奶黄色小飞袖纯棉T恤，120码，飞袖设计，薄款透气 — 图上这件","女童小飞袖T恤 120码 夏"));
            o.items.add(new Item("下装","浅粉色百褶裙裤，120码，外面是裙子、里面是短裤，跑跳不走光 — 图上这条","女童裙裤 百褶 120码 夏"));
            o.items.add(new Item("鞋","白色包头凉鞋，约28码，魔术贴、软底防滑 — 图上这双","女童包头凉鞋 白色 28码"));
            o.items.add(new Item("帽子","草编遮阳帽，帽围约52cm，宽檐防晒 — 图上这顶","女童草帽 遮阳 52cm"));
        } else if (day >= 23) {
            o.band=1; o.sleeve=0; o.pants=1; o.hat=2;
            o.summary="清爽学院风：白T+牛仔背带裙+粉帽子";
            o.items.add(new Item("上衣","白色纯棉短袖T恤，120码，薄款，穿在背带裙里面 — 图上里层这件","女童白色短袖T恤 120码"));
            o.items.add(new Item("裙装","浅蓝色牛仔背带裙，120码，A字版型、肩带可调，裙长到膝盖 — 图上这件","女童牛仔背带裙 120码"));
            o.items.add(new Item("鞋","白色网面小白鞋，约28码，魔术贴 — 图上这双","女童小白鞋 网面 28码"));
            o.items.add(new Item("帽子","浅粉色鸭舌帽，帽围约52cm — 图上这顶","女童鸭舌帽 粉色 52cm"));
            o.items.add(new Item("备用外套","早晚备薄防晒衣一件（最低"+Math.round(tmin)+"°C时套上），白色或奶黄色","女童防晒衣 薄款 120码"));
        } else if (day >= 18) {
            o.band=2; o.sleeve=1; o.pants=2;
            o.summary="海军风：条纹长袖+百褶裙+打底裤";
            o.items.add(new Item("上衣","白底藏青条纹长袖T恤，120码，纯棉中等厚度，海军风条纹 — 图上这件","女童条纹长袖T恤 120码"));
            o.items.add(new Item("下装","藏青色百褶半身裙，120码，裙长到膝盖，里面配黑色薄打底裤一条 — 图上这套","女童百褶裙 打底裤 120码"));
            o.items.add(new Item("鞋","白色运动鞋/小白鞋，约28码 — 图上这双","女童小白鞋 运动鞋 28码"));
            o.items.add(new Item("备用外套","薄款针织开衫一件，早晚套上，燕麦色/米白色","女童针织开衫 薄款 120码"));
        } else if (day >= 13) {
            o.band=3; o.sleeve=1; o.pants=2; o.hasOuter=false;
            o.summary="甜酷风：粉色连帽卫衣+米色背带裤";
            o.items.add(new Item("上衣","浅粉色连帽卫衣，120码，薄绒或毛圈款，宽松版型 — 图上这件","女童连帽卫衣 粉色 120码"));
            o.items.add(new Item("下装","米色灯芯绒背带长裤，120码，肩带可调，裤脚直筒 — 图上这条","女童灯芯绒背带裤 120码"));
            o.items.add(new Item("鞋","白色运动鞋，约28码，厚棉袜 — 图上这双","女童运动鞋 白色 28码"));
            o.items.add(new Item("备用外套","风大/早晚加卡其色薄风衣一件","女童薄风衣 120码"));
        } else if (day >= 8) {
            o.band=4; o.sleeve=1; o.pants=3; o.hasOuter=true;
            o.summary="焦糖系：泰迪绒外套+格纹裙+打底裤";
            o.items.add(new Item("内搭","米白色高领针织毛衣，120码，细针织、不扎脖子 — 图上里层这件","女童高领毛衣 米白 120码"));
            o.items.add(new Item("外套","焦糖棕色泰迪绒/羊羔绒外套，120码，翻领宽松款 — 图上外层这件","女童羊羔绒外套 120码"));
            o.items.add(new Item("下装","棕色格纹半身裙，120码，里面配黑色加绒打底裤一条 — 图上这套","女童格纹裙 加绒打底裤 120码"));
            o.items.add(new Item("鞋","棕色加绒短靴，约28–29码 — 图上这双","女童短靴 加绒 29码"));
        } else if (day >= 3) {
            o.band=5; o.sleeve=1; o.pants=3; o.hasOuter=true; o.puffy=true; o.hat=3; o.scarf=true;
            o.summary="藕粉系：粉色羽绒服+米白帽子围巾同色系";
            o.items.add(new Item("内搭","米白色毛衣/抓绒，120码，穿在羽绒服里面 — 图上里层","女童毛衣 米白 120码"));
            o.items.add(new Item("外套","藕粉色羽绒服，120码，中长款、连帽，90%白鸭绒 — 图上这件","女童羽绒服 粉色 中长款 120码"));
            o.items.add(new Item("下装","米色加绒长裤，120码，宽松直筒 — 图上这条","女童加绒长裤 米色 120码"));
            o.items.add(new Item("鞋帽","米白色雪地靴约29码 + 米白色针织帽 + 米白色围巾，同色系一套 — 图上这套","女童雪地靴 米白 针织帽 围巾"));
        } else {
            o.band=6; o.sleeve=1; o.pants=3; o.hasOuter=true; o.puffy=true; o.hat=3; o.scarf=true; o.gloves=true;
            o.summary="奶油系：奶白长款羽绒+同色帽围巾手套";
            o.items.add(new Item("内搭","保暖打底+厚毛衣，120码，两层，米白/燕麦色","女童保暖打底 厚毛衣 120码"));
            o.items.add(new Item("外套","奶白色长款厚羽绒服，120码，连帽、过膝款 — 图上这件","女童长款羽绒服 白色 120码"));
            o.items.add(new Item("下装","黑色加绒长裤，120码，外裤+保暖裤两层 — 图上这条","女童加绒长裤 黑色 120码"));
            o.items.add(new Item("鞋帽手套","白色雪地靴约29码 + 米白色针织帽 + 围巾 + 手套，同色系全套 — 图上这套","女童雪地靴 白色 手套 围巾 套装"));
        }
        o.items.add(0, underwearFor(o.band));
        if (rainy) o.items.add(new Item("雨具","儿童雨衣（书包位款）+ 雨伞，或雨靴一双，今天有雨必带","女童雨衣 书包位 雨靴"));
        if (wind >= 30) o.items.add(new Item("防风","风大：外套拉链拉到顶，帽子戴紧，别穿宽松灌风款","女童防风外套 120码"));
        return o;
    }

    private Item underwearFor(int band) {
        switch (band) {
            case 0: return new Item("内衣 · 第1层（最里层）","白色网眼薄棉小背心，120码，纯棉薄款，贴身吸汗；配浅色女童纯棉内裤一条 — 所有衣服都穿在它外面，领口露出的白边就是它","儿童背心 纯棉 内裤 120码 薄款");
            case 1: return new Item("内衣 · 第1层（最里层）","白色/浅色纯棉小背心，120码，薄款；配女童纯棉内裤一条 — 贴身层，短袖T恤穿在它外面","儿童纯棉背心 内裤套装 120码");
            case 2: return new Item("内衣 · 第1层（最里层）","纯棉薄打底衫/小背心，120码，圆领薄款；配女童纯棉内裤一条 — 长袖T恤穿在它外面","儿童纯棉打底衫 薄款 120码");
            case 3: return new Item("内衣 · 第1层（最里层）","纯棉秋衣，120码，薄款圆领打底；配女童纯棉内裤一条，秋裤/打底裤穿在长裤里面 — 卫衣穿在秋衣外面","儿童纯棉秋衣秋裤 120码 薄款");
            case 4: return new Item("内衣 · 第1层（最里层）","纯棉秋衣秋裤套装，120码，中厚款；秋裤是下装的第一层，外面再穿加绒长裤 — 毛衣穿在秋衣外面","儿童秋衣秋裤套装 120码 中厚");
            case 5: return new Item("内衣 · 第1层（最里层）","德绒保暖内衣套装（秋衣+秋裤），120码，自发热/德绒面料，贴身不扎 — 羽绒服里面的毛衣再穿在它外面","儿童德绒保暖内衣套装 120码");
            default: return new Item("内衣 · 第1层（最里层）","加绒/加厚保暖内衣套装（秋衣+秋裤），120码，最贴身一层 — 外面依次是毛衣、厚羽绒服、加绒外裤","儿童加绒保暖内衣套装 120码");
        }
    }

    private String detailText(Outfit o) {
        StringBuilder s = new StringBuilder();
        s.append("造型：").append(o.summary).append("\n");
        s.append("尺码参考：120码 / 鞋约28–29码（按孩子实际身高脚长）\n");
        s.append("穿的顺序：从里到外，先内衣，再内搭/上衣，再外套。\n\n");
        int n = 1;
        for (Item it : o.items) { s.append(n++).append(". ").append(it.label).append("：").append(it.spec).append("\n\n"); }
        return s.toString().trim();
    }

    private String detailFor(Outfit o, Option op) {
        StringBuilder s = new StringBuilder();
        s.append("这一套：").append(op.name).append("（").append(o.summary).append("）\n");
        s.append("尺码参考：120码 / 鞋约28–29码（按孩子实际身高脚长）\n");
        s.append("穿的顺序：从里到外，先内衣，再内搭/上衣，再外套。\n");
        if (op.asset == null) s.append("注：这一套暂无单独模特图，上方主图是同温度档的搭配1，穿法以清单为准。\n");
        s.append("\n");
        int n = 1;
        for (Item it : op.items) { s.append(n++).append(". ").append(it.label).append("：").append(it.spec).append("\n\n"); }
        return s.toString().trim();
    }
    private void addOpt(Outfit o, String name, String asset, Item... rest) {
        Option op = new Option(); op.name = name; op.asset = asset;
        op.items.add(underwearFor(o.band));
        for (Item i : rest) op.items.add(i);
        o.options.add(op);
    }
    private Item it(String l, String s, String k) { return new Item(l, s, k); }
    private void buildOptions(Outfit o) {
        // Option A is built from o.items (index 0 is underwear, skip it: addOpt re-adds it)
        String[] namesA = {"奶黄甜美风","牛仔背带裙风","海军学院风","粉色甜酷风","焦糖学院风","藕粉同色系","奶油全套风"};
        String assetA = (o.band >= 2 && o.band <= 5) ? ("opt" + o.band + "_0.jpg") : ("band" + o.band + ".jpg");
        java.util.List<Item> rest = o.items.subList(1, o.items.size());
        addOpt(o, namesA[o.band], assetA, rest.toArray(new Item[0]));
        switch (o.band) {
            case 0:
                addOpt(o, "碎花度假风", null, it("连衣裙","浅色碎花短袖连衣裙，120码，纯棉薄款，裙长到膝盖 — 照这个款式挑","女童碎花连衣裙 120码 夏"), it("安全裤+鞋","白色安全裤一条 + 白色包头凉鞋约28码","女童安全裤 凉鞋 28码"), it("帽子","草编遮阳帽，帽围约52cm","女童草帽 遮阳 52cm"));
                addOpt(o, "运动清爽风", null, it("上衣","白色速干短袖T恤，120码，网眼透气","女童速干短袖 120码"), it("下装","浅粉色运动短裤，120码，速干面料","女童运动短裤 120码"), it("鞋","白色网面运动鞋，约28码","女童网面运动鞋 28码"));
                addOpt(o, "背带裤风", null, it("上衣","白色纯棉短袖T恤，120码","女童白色短袖T恤 120码"), it("下装","浅蓝色牛仔背带短裤，120码，肩带可调","女童牛仔背带短裤 120码"), it("鞋","白色包头凉鞋，约28码","女童包头凉鞋 白色 28码"));
                break;
            case 1:
                addOpt(o, "碎花裙风", null, it("连衣裙","碎花短袖连衣裙，120码，薄棉，配安全裤","女童碎花连衣裙 120码"), it("鞋","白色凉鞋/小白鞋，约28码","女童凉鞋 白色 28码"), it("帽子","粉色鸭舌帽，帽围约52cm","女童鸭舌帽 粉色 52cm"));
                addOpt(o, "裙裤休闲风", null, it("上衣","浅粉色短袖T恤，120码","女童短袖T恤 粉色 120码"), it("下装","白色百褶裙裤，120码","女童裙裤 白色 120码"), it("鞋","白色网面运动鞋，约28码","女童网面运动鞋 28码"));
                addOpt(o, "条纹海军风", null, it("上衣","红白/蓝白条纹短袖T恤，120码","女童条纹短袖 120码"), it("下装","藏青色五分裤，120码","女童五分裤 藏青 120码"), it("鞋帽","白色运动鞋约28码 + 蓝色鸭舌帽","女童运动鞋 28码 鸭舌帽"));
                break;
            case 2:
                addOpt(o, "燕麦碎花风", "opt2_1.jpg", it("外套","燕麦色麻花针织开衫，120码，薄款 — 图上外层","女童针织开衫 燕麦色 120码"), it("连衣裙","浅色碎花连衣裙，120码，配白色打底裤 — 图上这件","女童碎花连衣裙 打底裤 120码"), it("鞋","米色玛丽珍鞋，约28码 — 图上这双","女童玛丽珍鞋 28码"));
                addOpt(o, "牛仔休闲风", "opt2_2.jpg", it("外套","浅蓝色牛仔外套，120码，薄款 — 图上这件","女童牛仔外套 120码"), it("上衣+下装","白色T恤 + 卡其色阔腿长裤，各120码 — 图上这套","女童阔腿裤 卡其 120码"), it("鞋帽","白色运动鞋约28码 + 粉色鸭舌帽 — 图上这套","女童运动鞋 28码 鸭舌帽 粉色"));
                addOpt(o, "森系卫衣风", "opt2_3.jpg", it("上衣","鼠尾草绿卫衣，120码，薄绒/毛圈 — 图上这件","女童卫衣 绿色 120码"), it("下装","米色灯芯绒半身裙 + 黑色打底裤，120码 — 图上这套","女童灯芯绒半身裙 打底裤 120码"), it("鞋","棕色乐福鞋，约28码 — 图上这双","女童乐福鞋 棕色 28码"));
                break;
            case 3:
                addOpt(o, "格纹贝雷风", "opt3_1.jpg", it("上衣","米色麻花针织毛衣，120码 — 图上这件","女童麻花毛衣 米色 120码"), it("下装","棕色格纹半身裙 + 黑色打底裤，120码 — 图上这套","女童格纹裙 打底裤 120码"), it("鞋帽","棕色短靴约28码 + 米色贝雷帽 — 图上这套","女童短靴 贝雷帽 120码"));
                addOpt(o, "运动棒球风", "opt3_2.jpg", it("外套","藏青色飞行员/棒球夹克，120码，防风款 — 图上这件","女童棒球服外套 120码"), it("内搭+下装","灰色卫衣 + 蓝色牛仔长裤，各120码 — 图上这套","女童卫衣 灰色 牛仔裤 120码"), it("鞋","白色运动鞋，约28码 — 图上这双","女童运动鞋 白色 28码"));
                addOpt(o, "风衣文艺风", "opt3_3.jpg", it("外套","米色/卡其薄风衣，120码，中长款 — 图上这件","女童风衣 120码"), it("内搭+下装","条纹长袖T恤 + 蓝色牛仔长裤，120码 — 图上这套","女童条纹长袖 牛仔裤 120码"), it("鞋","白色运动鞋，约28码","女童运动鞋 白色 28码"));
                break;
            case 4:
                addOpt(o, "藏青运动风", "opt4_1.jpg", it("外套","藏青色棉服/轻羽绒，120码，连帽款 — 图上这件","女童棉服 藏青 120码"), it("内搭+下装","浅粉色毛衣 + 灰色加绒运动裤，120码 — 图上这套","女童毛衣 粉色 加绒裤 120码"), it("鞋","白色保暖运动鞋，约28–29码 — 图上这双","女童保暖运动鞋 29码"));
                addOpt(o, "奶油针织风", "opt4_2.jpg", it("外套","象牙白色短款棉服/羽绒服，120码 — 图上这件","女童短款羽绒服 白色 120码"), it("连衣裙","米色针织连衣裙，120码，配黑色加绒打底裤 — 图上这套","女童针织连衣裙 打底裤 120码"), it("鞋","棕色短靴，约29码 — 图上这双","女童短靴 加绒 29码"));
                addOpt(o, "酒红大衣风", "opt4_3.jpg", it("外套","酒红色双排扣呢大衣，120码，中长款 — 图上这件","女童呢大衣 酒红 120码"), it("内搭+下装","米白色毛衣 + 黑色加绒长裤，120码 — 图上这套","女童毛衣 米白 加绒裤 120码"), it("鞋","棕色短靴，约29码 — 图上这双","女童短靴 棕色 29码"));
                break;
            case 5:
                addOpt(o, "藏青同色风", "opt5_1.jpg", it("外套","藏青色中长款羽绒服，120码，连帽 — 图上这件","女童羽绒服 藏青 中长款 120码"), it("下装","灰色加绒长裤，120码 — 图上这条","女童加绒长裤 灰色 120码"), it("鞋帽","米白色针织帽+围巾 + 黑色雪地靴约29码 — 图上这套","女童雪地靴 黑色 针织帽 围巾"));
                addOpt(o, "焦糖长款风", "opt5_2.jpg", it("外套","驼色长款棉服/羽绒服，120码 — 图上这件","女童长款羽绒服 驼色 120码"), it("下装","黑色加绒长裤，120码 — 图上这条","女童加绒长裤 黑色 120码"), it("鞋帽","粉色针织帽+围巾 + 棕色雪地靴约29码 — 图上这套","女童雪地靴 棕色 针织帽 粉色"));
                addOpt(o, "香芋紫风", "opt5_3.jpg", it("外套","浅紫色羽绒服，120码，中长款 — 图上这件","女童羽绒服 浅紫 120码"), it("下装","白色/米色加绒长裤，120码 — 图上这条","女童加绒长裤 白色 120码"), it("鞋帽","白色针织帽+围巾 + 粉色雪地靴约29码 — 图上这套","女童雪地靴 粉色 针织帽 白色"));
                break;
            default:
                addOpt(o, "藕粉长款风", null, it("外套","藕粉色长款厚羽绒服，120码，连帽 — 照这个款式挑","女童长款羽绒服 粉色 120码"), it("下装","黑色加绒长裤，120码","女童加绒长裤 黑色 120码"), it("鞋帽手套","白色雪地靴约29码 + 米白针织帽围巾手套","女童雪地靴 白色 围巾 手套"));
                addOpt(o, "藏青长款风", null, it("外套","藏青色长款厚羽绒服，120码，连帽","女童长款羽绒服 藏青 120码"), it("下装","黑色加绒长裤，120码","女童加绒长裤 黑色 120码"), it("鞋帽手套","黑色雪地靴约29码 + 米白针织帽围巾手套","女童雪地靴 黑色 针织帽 手套"));
                addOpt(o, "焦糖长款风", null, it("外套","驼色长款厚羽绒服，120码，连帽","女童长款羽绒服 驼色 120码"), it("下装","黑色加绒长裤，120码","女童加绒长裤 黑色 120码"), it("鞋帽手套","棕色雪地靴约29码 + 粉色针织帽围巾手套","女童雪地靴 棕色 针织帽 粉色"));
                break;
        }
        // rainy / wind extras go to every option's shop list via o.items extras already in option A only; append to all
    }
    private void applyOutfit(Outfit o) {
        if (o.options.isEmpty()) buildOptions(o);
        currentOutfit = o;
        currentBand = o.band;
        optLayout.removeAllViews();
        for (int i = 0; i < o.options.size(); i++) {
            final int idx = i;
            Button b = new Button(this);
            b.setText("搭配" + (i + 1) + "：" + o.options.get(i).name);
            b.setTextSize(15); b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            b.setOnClickListener(v -> renderOption(idx));
            optLayout.addView(b);
        }
        renderOption(0);
    }
    private void shuffleOption() {
        if (currentOutfit == null || currentOutfit.options.size() < 2) { Toast.makeText(this, "现在只有一套，没得换", Toast.LENGTH_SHORT).show(); return; }
        java.util.Random rnd = new java.util.Random();
        int pick = currentOpt; int guard = 0;
        while (pick == currentOpt && guard++ < 20) pick = rnd.nextInt(currentOutfit.options.size());
        renderOption(pick);
        Toast.makeText(this, "随机换到：搭配" + (pick + 1) + " " + currentOutfit.options.get(pick).name, Toast.LENGTH_SHORT).show();
    }
    private void renderOption(int idx) {
        if (currentOutfit == null || currentOutfit.options.isEmpty()) return;
        currentOpt = idx;
        Option op = currentOutfit.options.get(idx);
        currentAsset = op.asset;
        android.graphics.Bitmap bm = currentAsset != null ? loadAssetBitmap(currentAsset) : null;
        if (bm == null) bm = loadBandBitmap(currentOutfit.band);
        photoView.setImageBitmap(bm);
        detailView.setText(detailFor(currentOutfit, op));
        shopLayout.removeAllViews();
        for (Item it : op.items) {
            if (it.keyword == null || it.keyword.isEmpty()) continue;
            Button btn = new Button(this);
            btn.setText("🛒 " + it.label + "：淘宝搜「" + it.keyword + "」");
            btn.setTextSize(14); btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            final String kw = it.keyword;
            btn.setOnClickListener(v -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://s.taobao.com/search?q=" + Uri.encode(kw)))); }
                catch (Exception e) { Toast.makeText(this, "打不开淘宝，请检查网络/是否装了淘宝", Toast.LENGTH_SHORT).show(); }
            });
            shopLayout.addView(btn);
        }
    }

    // ---------- AI recommendation ----------
    private int bandOf(double day) {
        if (day >= 28) return 0; if (day >= 23) return 1; if (day >= 18) return 2;
        if (day >= 13) return 3; if (day >= 8) return 4; if (day >= 3) return 5; return 6;
    }
    private void fetchAiRecommendation() {
        String base = apiBaseInput.getText().toString().trim();
        String key = apiKeyInput.getText().toString().trim();
        String model = apiModelInput.getText().toString().trim();
        if (base.isEmpty() || key.isEmpty() || model.isEmpty()) {
            Toast.makeText(this, "请先在页面下方「AI 模型设置」里填好接口地址、Key、模型名并保存", Toast.LENGTH_LONG).show();
            return;
        }
        getSharedPreferences("ai_cfg", MODE_PRIVATE).edit().putString("base", base).putString("key", key).putString("model", model).apply();
        int idx = citySpinner.getSelectedItemPosition();
        aiBtn.setEnabled(false); statusView.setText("AI：正在获取天气并请求大模型…");
        execHolder.e.execute(() -> {
            try {
                double lat, lon; String city;
                if (idx == 0) {
                    double[] loc = getLocation();
                    if (loc == null) { lat = 31.23; lon = 121.47; city = "定位失败，暂用上海"; }
                    else { lat = loc[0]; lon = loc[1]; city = "当前位置"; }
                } else { lat = cityCoords[idx][0]; lon = cityCoords[idx][1]; city = cityNames[idx]; }
                String wurl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,uv_index_max,wind_speed_10m_max" +
                    "&timezone=auto&forecast_days=1";
                org.json.JSONObject w = new org.json.JSONObject(httpGet(wurl));
                org.json.JSONObject cur = w.getJSONObject("current");
                org.json.JSONObject daily = w.getJSONObject("daily");
                double tmax = daily.getJSONArray("temperature_2m_max").getDouble(0);
                double tmin = daily.getJSONArray("temperature_2m_min").getDouble(0);
                double rainP = daily.getJSONArray("precipitation_probability_max").optDouble(0, 0);
                double uv = daily.getJSONArray("uv_index_max").optDouble(0, 0);
                double wind = daily.getJSONArray("wind_speed_10m_max").optDouble(0, 0);
                double now = cur.getDouble("temperature_2m");
                double feels = cur.getDouble("apparent_temperature");
                int band = bandOf(tmax);
                boolean rainy = rainP >= 40;

                String prompt = "今天是" + new java.text.SimpleDateFormat("yyyy年M月d日").format(new java.util.Date()) + "，城市：" + city + "。"
                    + "天气：最高" + tmax + "°C，最低" + tmin + "°C，现在" + now + "°C，体感" + feels + "°C，降雨概率" + rainP + "%，最大风速" + wind + "km/h，紫外线指数" + uv + "。"
                    + "孩子：2021年出生的女童，约5岁，身高约110cm，穿120码，鞋约28-29码，幼儿园在园、跑动多。"
                    + "请结合当下最新流行的女童穿搭趋势（低饱和同色系、韩系/学院风等，不要红配橙这种撞色土味搭配），给出一套适合今天天气的完整穿衣推荐。"
                    + "要求：从内衣开始由内到外共5-7件，每件给出具体颜色、款式、材质厚度和尺码，并给出带「女童」和尺码的淘宝搜索关键词。"
                    + "只输出JSON，不要任何其他文字，格式：{\"name\":\"这套搭配的名字\",\"items\":[{\"label\":\"内衣\",\"spec\":\"具体描述\",\"keyword\":\"淘宝搜索关键词\"}]}";
                org.json.JSONObject msg1 = new org.json.JSONObject(); msg1.put("role", "system"); msg1.put("content", "你是专业的儿童穿搭顾问，只输出JSON。");
                org.json.JSONObject msg2 = new org.json.JSONObject(); msg2.put("role", "user"); msg2.put("content", prompt);
                org.json.JSONArray msgs = new org.json.JSONArray(); msgs.put(msg1); msgs.put(msg2);
                org.json.JSONObject body = new org.json.JSONObject(); body.put("model", model); body.put("messages", msgs); body.put("temperature", 0.7);

                String chatUrl = base.endsWith("/chat/completions") ? base : (base.endsWith("/") ? base + "chat/completions" : base + "/chat/completions");
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(chatUrl).openConnection();
                c.setRequestMethod("POST"); c.setConnectTimeout(15000); c.setReadTimeout(90000); c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + key);
                byte[] bytes = body.toString().getBytes("UTF-8");
                java.io.OutputStream os = c.getOutputStream(); os.write(bytes); os.close();
                int code = c.getResponseCode();
                java.io.InputStream is = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder(); String line; while ((line = r.readLine()) != null) sb.append(line); r.close();
                if (code < 200 || code >= 300) throw new Exception("模型接口返回 " + code + "：" + sb.toString().substring(0, Math.min(200, sb.length())));
                org.json.JSONObject resp = new org.json.JSONObject(sb.toString());
                String content = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim();
                if (content.startsWith("```")) { content = content.replaceAll("(?s)^```[a-zA-Z]*", "").replaceAll("```$", "").trim(); }
                org.json.JSONObject ai = new org.json.JSONObject(content);
                Option aop = new Option();
                aop.name = "🤖 AI：" + ai.optString("name", "当日推荐");
                aop.asset = "band" + band + ".jpg";
                org.json.JSONArray arr = ai.getJSONArray("items");
                for (int i = 0; i < arr.length(); i++) {
                    org.json.JSONObject o = arr.getJSONObject(i);
                    aop.items.add(new Item(o.optString("label", "单品"), o.optString("spec", ""), o.optString("keyword", "")));
                }
                if (aop.items.isEmpty()) throw new Exception("模型没返回清单");
                final Outfit outfit = outfitFor(tmax, rainy, uv, wind, tmin);
                buildOptions(outfit);
                outfit.options.add(aop);
                outfit.summary = "AI 大模型按今日天气实时生成（模特图为同温度档示意，穿法以清单为准）";
                final String wtext = city + " · 最高 " + Math.round(tmax) + "°C / 最低 " + Math.round(tmin) + "°C · 现在 " + now + "°C（体感 " + Math.round(feels) + "°C）· 降雨概率 " + Math.round(rainP) + "% · 风最大 " + Math.round(wind) + " km/h · 紫外线 " + uv;
                runOnUiThread(() -> {
                    weatherView.setText(wtext);
                    applyOutfit(outfit);
                    // rebuild option buttons to include the AI option
                    optLayout.removeAllViews();
                    for (int i = 0; i < outfit.options.size(); i++) {
                        final int ii = i;
                        Button b = new Button(this); b.setText("搭配" + (i + 1) + "：" + outfit.options.get(i).name); b.setTextSize(15); b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        b.setOnClickListener(v -> renderOption(ii)); optLayout.addView(b);
                    }
                    int pick = new java.util.Random().nextInt(outfit.options.size());
                    renderOption(pick);
                    adviceView.setText("AI 推荐是搭配" + outfit.options.size() + "（名字带🤖），加上本地素材库的 " + (outfit.options.size() - 1) + " 套，一共 " + outfit.options.size() + " 套备选。首屏是从中随机挑的一套展示，点照片右上角「🔀 换一套」可以随机换一套，下面的搭配按钮也能逐套看。注意：AI 内容由模型生成，出门前结合孩子实际冷热再微调。");
                    statusView.setText("AI 推荐已生成 · " + city);
                    aiBtn.setEnabled(true);
                });
            } catch (Exception e) {
                final String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> { statusView.setText("AI 推荐失败：" + msg); aiBtn.setEnabled(true); Toast.makeText(this, "AI 推荐失败：" + msg, Toast.LENGTH_LONG).show(); });
            }
        });
    }

    // ---------- weather ----------
    private void loadWeather() {
        int idx = citySpinner.getSelectedItemPosition();
        statusView.setText("正在获取天气…"); refreshBtn.setEnabled(false);
        execHolder.e.execute(() -> {
            try {
                double lat, lon; String city;
                if (idx == 0) {
                    double[] loc = getLocation();
                    if (loc == null) { lat = 31.23; lon = 121.47; city = "定位失败，暂用上海"; }
                    else { lat = loc[0]; lon = loc[1]; city = "当前位置"; }
                } else { lat = cityCoords[idx][0]; lon = cityCoords[idx][1]; city = cityNames[idx]; }
                String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                    "&daily=temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,uv_index_max,wind_speed_10m_max" +
                    "&timezone=auto&forecast_days=1";
                JSONObject j = new JSONObject(httpGet(url));
                JSONObject cur = j.getJSONObject("current"); JSONObject daily = j.getJSONObject("daily");
                double tcur = cur.getDouble("temperature_2m"), feels = cur.getDouble("apparent_temperature"), hum = cur.getDouble("relative_humidity_2m");
                int code = cur.getInt("weather_code");
                double tmax = daily.getJSONArray("temperature_2m_max").getDouble(0), tmin = daily.getJSONArray("temperature_2m_min").getDouble(0);
                int precip = daily.getJSONArray("precipitation_probability_max").isNull(0) ? 0 : daily.getJSONArray("precipitation_probability_max").getInt(0);
                double uv = daily.getJSONArray("uv_index_max").isNull(0) ? 0 : daily.getJSONArray("uv_index_max").getDouble(0);
                double windMax = daily.getJSONArray("wind_speed_10m_max").getDouble(0);
                boolean rainy = precip >= 40 || (code>=51&&code<=67) || (code>=80&&code<=82) || code>=95;
                String weatherText = city + "\n现在：" + Math.round(tcur) + "°C（体感 " + Math.round(feels) + "°C） " + codeText(code) + "\n今日：" + Math.round(tmin) + "°C ～ " + Math.round(tmax) + "°C，温差 " + Math.round(tmax-tmin) + "°C\n降雨概率最高：" + precip + "%   风：" + Math.round(windMax) + " km/h\n湿度：" + Math.round(hum) + "%   紫外线最高：" + String.format("%.1f", uv);
                String advice = buildAdvice(tmin, tmax, feels, code, precip, windMax, uv);
                Outfit outfit = outfitFor(tmax, rainy, uv, windMax, tmin);
                main.post(() -> { weatherView.setText(weatherText); adviceView.setText(advice); applyOutfit(outfit); statusView.setText("已更新 · " + city); refreshBtn.setEnabled(true); });
            } catch (Exception e) {
                main.post(() -> { statusView.setText("获取失败，请检查网络后重试：" + e.getMessage()); refreshBtn.setEnabled(true); });
            }
        });
    }

    private String buildAdvice(double tmin, double tmax, double feels, int code, int precip, double wind, double uv) {
        StringBuilder s = new StringBuilder();
        s.append(currentSummaryForTemp(tmax)).append("\n");
        boolean rainy = precip >= 40 || (code>=51&&code<=67) || (code>=80&&code<=82) || code>=95;
        if (rainy) s.append("\n• 有雨：带雨衣/雨伞，备一双替换袜子");
        if (code>=71&&code<=77||code==85||code==86) s.append("\n• 有雪：防滑保暖靴，手套必备");
        if (wind >= 30) s.append("\n• 风大：外套选防风款，戴帽子");
        if (uv >= 6) s.append("\n• 紫外线强：遮阳帽+儿童防晒，中午多在阴凉处");
        else if (uv >= 3 && tmax >= 20) s.append("\n• 紫外线中等：户外久了戴帽/涂防晒");
        if (tmax - tmin >= 8) s.append("\n• 温差大（").append(Math.round(tmax-tmin)).append("°C）：洋葱穿法，书包塞一件薄外套，早晚加、中午减");
        if (feels <= tmax - 4) s.append("\n• 体感明显更冷，按建议再加一层挡风");
        if (tmax >= 26) s.append("\n• 天热：带水杯，备汗巾和替换短袖，出汗及时擦干换掉");
        s.append("\n\n✅ 出门前摸后背：温热不出汗=刚好；发凉加一件，潮湿出汗减一件。");
        return s.toString();
    }
    private String currentSummaryForTemp(double day) {
        if (day>=28) return "今天热，按短袖短裤穿，重点防晒补水。";
        if (day>=23) return "今天暖和，短袖为主，早晚备薄外套。";
        if (day>=18) return "今天舒适，长袖+长裤，备薄外套应付早晚。";
        if (day>=13) return "今天偏凉，内搭+外套两层，裤子穿长裤。";
        if (day>=8)  return "今天冷，毛衣/抓绒+厚外套，下装加厚。";
        if (day>=3)  return "今天很冷，羽绒服为主，帽子围巾带上。";
        return "今天严寒，厚羽绒全套，帽子围巾手套齐全，尽量减少长时间户外。";
    }
    private String codeText(int c) {
        if (c==0) return "☀️ 晴"; if (c<=2) return "🌤 多云"; if (c==3) return "☁️ 阴";
        if (c==45||c==48) return "🌫 有雾"; if (c>=51&&c<=57) return "🌦 毛毛雨";
        if (c>=61&&c<=67) return "🌧 下雨"; if (c>=71&&c<=77) return "🌨 下雪";
        if (c>=80&&c<=82) return "🌧 阵雨"; if (c==85||c==86) return "🌨 阵雪"; if (c>=95) return "⛈ 雷雨"; return "";
    }
    private double[] getLocation() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm==null || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED) return null;
            Location best=null; for (String p: lm.getProviders(true)) { Location l=lm.getLastKnownLocation(p); if (l!=null&&(best==null||l.getTime()>best.getTime())) best=l; }
            return best==null?null:new double[]{best.getLatitude(), best.getLongitude()};
        } catch (Exception e) { return null; }
    }
    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestProperty("User-Agent","BabyWeather/1.1");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder(); String line; while((line=r.readLine())!=null) sb.append(line); r.close(); return sb.toString();
    }

    private android.graphics.Bitmap loadAssetBitmap(String name) {
        try {
            java.io.InputStream is = getAssets().open(name);
            android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(is); is.close(); return bm;
        } catch (Exception e) { return null; }
    }
    private android.graphics.Bitmap loadBandBitmap(int band) {
        try {
            java.io.InputStream is = getAssets().open("band" + band + ".jpg");
            android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(is); is.close(); return bm;
        } catch (Exception e) { return null; }
    }

    static class TouchImageView extends ImageView {
        private android.graphics.Matrix matrix = new android.graphics.Matrix();
        private android.graphics.Matrix saved = new android.graphics.Matrix();
        private ScaleGestureDetector sgd; private float lastX, lastY; private boolean drag=false;
        TouchImageView(Context ctx){ super(ctx); setScaleType(ScaleType.MATRIX);
            sgd = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener(){
                @Override public boolean onScale(ScaleGestureDetector d){ matrix.postScale(d.getScaleFactor(), d.getScaleFactor(), d.getFocusX(), d.getFocusY()); setImageMatrix(matrix); return true; } });
        }
        @Override protected void onLayout(boolean ch,int l,int t,int r,int b){ super.onLayout(ch,l,t,r,b);
            if (getDrawable()!=null && matrix.isIdentity()){ float vw=getWidth(), vh=getHeight(), dw=getDrawable().getIntrinsicWidth(), dh=getDrawable().getIntrinsicHeight();
                float s=Math.min(vw/dw, vh/dh); matrix.setScale(s,s); matrix.postTranslate((vw-dw*s)/2f,(vh-dh*s)/2f); setImageMatrix(matrix); } }
        @Override public boolean onTouchEvent(MotionEvent e){ sgd.onTouchEvent(e);
            switch(e.getActionMasked()){ case MotionEvent.ACTION_DOWN: lastX=e.getX(); lastY=e.getY(); drag=true; break;
                case MotionEvent.ACTION_MOVE: if(drag && e.getPointerCount()==1){ matrix.postTranslate(e.getX()-lastX, e.getY()-lastY); lastX=e.getX(); lastY=e.getY(); setImageMatrix(matrix);} break;
                case MotionEvent.ACTION_UP: drag=false; break; }
            return true; }
    }

    // ---------- cartoon baby view (kept as fallback, not shown) ----------
    static class BabyView extends View {
        Outfit outfit; boolean zoomable=false; float zoom=1f, dx=0f, dy=0f;
        ScaleGestureDetector sgd; float lastX, lastY; boolean dragging=false;
        BabyView(Context ctx){ super(ctx); sgd=new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener(){ @Override public boolean onScale(ScaleGestureDetector d){ zoom=Math.max(1f, Math.min(3.5f, zoom*d.getScaleFactor())); invalidate(); return true; } }); }
        void setOutfit(Outfit o){ outfit=o; invalidate(); }
        @Override public boolean onTouchEvent(MotionEvent e){
            if (!zoomable) return super.onTouchEvent(e);
            sgd.onTouchEvent(e);
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN: lastX=e.getX(); lastY=e.getY(); dragging=true; break;
                case MotionEvent.ACTION_MOVE: if(dragging && e.getPointerCount()==1 && zoom>1f){ dx+=e.getX()-lastX; dy+=e.getY()-lastY; lastX=e.getX(); lastY=e.getY(); invalidate(); } break;
                case MotionEvent.ACTION_UP: dragging=false; break;
            }
            return true;
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if (outfit==null) return;
            canvas.save();
            canvas.translate(dx, dy); canvas.scale(zoom, zoom, getWidth()/2f, getHeight()/2f);
            float W=getWidth(), H=getHeight();
            float s=Math.min(W/600f, H/820f); float ox=(W-600*s)/2f, oy=(H-820*s)/2f;
            canvas.translate(ox, oy); canvas.scale(s, s);
            drawBaby(canvas);
            canvas.restore();
        }
        private Paint p(int color){ Paint q=new Paint(Paint.ANTI_ALIAS_FLAG); q.setColor(color); q.setStyle(Paint.Style.FILL); return q; }
        private void drawBaby(Canvas c){
            Outfit o=outfit;
            int skin=Color.parseColor("#FFD9B3"), hair=Color.parseColor("#5D4037");
            // umbrella if rainy
            if (o.rainy){
                Paint up=p(Color.parseColor("#FF6B6B")); Path tri=new Path(); tri.moveTo(430,120); tri.lineTo(590,250); tri.lineTo(270,250); tri.close(); c.drawPath(tri, up);
                Paint lp=new Paint(Paint.ANTI_ALIAS_FLAG); lp.setColor(Color.DKGRAY); lp.setStrokeWidth(10); c.drawLine(430,120,430,430,lp);
            }
            // legs / pants
            if (o.pants==0){ // shorts + skin legs
                c.drawRect(225,520,295,600, p(o.bottomColor)); c.drawRect(305,520,375,600, p(o.bottomColor));
                c.drawRect(235,600,285,720, p(skin)); c.drawRect(315,600,365,720, p(skin));
            } else if (o.pants==1){
                c.drawRect(225,520,295,660, p(o.bottomColor)); c.drawRect(305,520,375,660, p(o.bottomColor));
                c.drawRect(235,660,285,720, p(skin)); c.drawRect(315,660,365,720, p(skin));
            } else {
                c.drawRect(225,520,295,725, p(o.bottomColor)); c.drawRect(305,520,375,725, p(o.bottomColor));
                if (o.pants==3){ Paint lp=new Paint(Paint.ANTI_ALIAS_FLAG); lp.setColor(Color.WHITE); lp.setStrokeWidth(4); c.drawLine(260,540,260,710,lp); c.drawLine(340,540,340,710,lp); }
            }
            // socks + shoes
            c.drawRect(232,720,288,742, p(Color.WHITE)); c.drawRect(312,720,368,742, p(Color.WHITE));
            boolean boots = o.band>=5 || o.band==4 && o.pants==3;
            if (o.band==0){ // sandals
                c.drawRoundRect(new RectF(215,742,295,775),12,12,p(o.shoeColor)); c.drawRoundRect(new RectF(305,742,385,775),12,12,p(o.shoeColor));
                c.drawRect(235,742,275,758,p(skin)); c.drawRect(325,742,365,758,p(skin));
            } else if (boots){
                c.drawRect(222,690,292,748, p(o.shoeColor)); c.drawRect(308,690,378,748, p(o.shoeColor));
                c.drawRoundRect(new RectF(215,742,300,778),12,12,p(o.shoeColor)); c.drawRoundRect(new RectF(300,742,385,778),12,12,p(o.shoeColor));
            } else {
                c.drawRoundRect(new RectF(215,738,300,778),14,14,p(o.shoeColor)); c.drawRoundRect(new RectF(300,738,385,778),14,14,p(o.shoeColor));
                Paint wp=new Paint(Paint.ANTI_ALIAS_FLAG); wp.setColor(Color.WHITE); wp.setStrokeWidth(5); c.drawLine(230,758,285,758,wp); c.drawLine(315,758,370,758,wp);
            }
            // arms (behind torso sides)
            int sleeveColor = o.hasOuter ? o.outerColor : o.topColor;
            if (o.sleeve==0){
                c.drawRect(165,285,220,360, p(o.topColor)); c.drawRect(380,285,435,360, p(o.topColor));
                c.drawRect(172,360,212,470, p(skin)); c.drawRect(388,360,428,470, p(skin));
            } else {
                c.drawRect(160,285,220,480, p(sleeveColor)); c.drawRect(380,285,440,480, p(sleeveColor));
            }
            // hands / gloves
            int handColor = o.gloves ? Color.parseColor("#B03A2E") : skin;
            c.drawCircle(190,492,24,p(handColor)); c.drawCircle(410,492,24,p(handColor));
            // undershirt collar peeking at neckline (layer 1)
            c.drawRoundRect(new RectF(252,248,348,282),14,14,p(Color.WHITE));
            // torso
            if (o.hasOuter){
                c.drawRoundRect(new RectF(205,270,395,540),36,36,p(o.outerColor));
                // inner top visible in middle
                Path inner=new Path(); inner.moveTo(262,272); inner.lineTo(338,272); inner.lineTo(322,540); inner.lineTo(278,540); inner.close(); c.drawPath(inner,p(o.topColor));
                Paint zp=new Paint(Paint.ANTI_ALIAS_FLAG); zp.setColor(Color.DKGRAY); zp.setStrokeWidth(4); c.drawLine(262,280,278,530,zp); c.drawLine(338,280,322,530,zp);
                if (o.puffy){ Paint lp=new Paint(Paint.ANTI_ALIAS_FLAG); lp.setColor(Color.argb(90,0,0,0)); lp.setStrokeWidth(5); for(int y=330;y<530;y+=48) c.drawLine(212,y,388,y,lp); }
            } else {
                c.drawRoundRect(new RectF(205,270,395,540),36,36,p(o.topColor));
                // cute print
                c.drawCircle(300,400,26,p(Color.WHITE)); c.drawCircle(292,394,5,p(Color.BLACK)); c.drawCircle(308,394,5,p(Color.BLACK));
                Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG); mp.setColor(Color.BLACK); mp.setStrokeWidth(4); mp.setStyle(Paint.Style.STROKE); c.drawArc(new RectF(288,398,312,418),15,150,false,mp);
            }
            // scarf
            if (o.scarf){ c.drawRoundRect(new RectF(238,238,362,282),18,18,p(Color.parseColor("#E74C3C"))); c.drawRect(330,282,362,360,p(Color.parseColor("#E74C3C"))); }
            // head
            c.drawCircle(300,165,88,p(skin));
            // hair (if no hat)
            if (o.hat==0){ Path hp=new Path(); hp.moveTo(212,165); hp.arcTo(new RectF(212,77,388,253),180,180); hp.close(); c.drawPath(hp,p(hair)); }
            // face
            c.drawCircle(268,170,8,p(Color.BLACK)); c.drawCircle(332,170,8,p(Color.BLACK));
            Paint blush=p(Color.argb(120,255,120,140)); c.drawCircle(238,195,14,blush); c.drawCircle(362,195,14,blush);
            Paint mp2=new Paint(Paint.ANTI_ALIAS_FLAG); mp2.setColor(Color.parseColor("#8D5524")); mp2.setStrokeWidth(5); mp2.setStyle(Paint.Style.STROKE); c.drawArc(new RectF(278,182,322,212),15,150,false,mp2);
            // hats
            if (o.hat==1){ // sun hat
                c.drawOval(new RectF(185,118,415,152),p(Color.parseColor("#F4D03F"))); Path dome=new Path(); dome.moveTo(232,122); dome.arcTo(new RectF(232,52,368,122),180,180); dome.close(); c.drawPath(dome,p(Color.parseColor("#F9E79F")));
            } else if (o.hat==2){ // cap
                Path dome=new Path(); dome.moveTo(222,132); dome.arcTo(new RectF(222,58,378,132),180,180); dome.close(); c.drawPath(dome,p(Color.parseColor("#2471A3")));
                c.drawRoundRect(new RectF(300,118,420,136),8,8,p(Color.parseColor("#2471A3")));
            } else if (o.hat==3){ // winter hat
                Path dome=new Path(); dome.moveTo(210,140); dome.arcTo(new RectF(210,50,390,140),180,180); dome.close(); c.drawPath(dome,p(Color.parseColor("#E74C3C")));
                c.drawRoundRect(new RectF(208,128,392,158),12,12,p(Color.parseColor("#C0392B"))); c.drawCircle(300,58,18,p(Color.WHITE));
            }
        }
    }
}
