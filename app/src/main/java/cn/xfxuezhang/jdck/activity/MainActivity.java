package cn.xfxuezhang.jdck.activity;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import cn.xfxuezhang.jdck.R;
import cn.xfxuezhang.jdck.config.Config;
import cn.xfxuezhang.jdck.entity.QlEnv;
import cn.xfxuezhang.jdck.entity.QlInfo;
import cn.xfxuezhang.jdck.receiver.SMSReceiver;
import cn.xfxuezhang.jdck.util.JDUtil;
import cn.xfxuezhang.jdck.util.QinglongUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private Context context;

    private Button addBtn, delBtn, inputBtn, getCookieBtn, checkCookieBtn, beanCheckBtn, clearCookieBtn;

    private Spinner phoneSpinner;

    private TextView qlStatusText;

    private WebView webView;

    private static final String JD_URL = "https://home.m.jd.com/myJd/home.action";

    private static final Pattern PHONE_PATTERN = Pattern.compile("1\\d{10}");

    private SharedPreferences config;

    private String cookie = null;

    private Set<String> phoneSet = new LinkedHashSet<>();

    private Boolean smsEnabled;
    private SMSReceiver smsReceiver;

    private boolean firstResume = true;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        // 配置存储
        config = getSharedPreferences("CONFIG", Context.MODE_PRIVATE);
        // 获取短信接收器配置
        smsEnabled = config.getBoolean("smsEnabled", false);
        if (smsEnabled) {
            // 注册接收器
            registerSMSReceiver();
        }

        webView = findViewById(R.id.webView);
        Config.getInstance().setWebView(webView);
        //支持javascript
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.getSettings().setSaveFormData(false);
        webView.getSettings().setGeolocationEnabled(false);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        //自适应屏幕
        webView.getSettings().setLoadWithOverviewMode(true);
        resetWebview();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                //添加Cookie获取操作
                CookieManager cookieManager = CookieManager.getInstance();
                cookie = cookieManager.getCookie(url);
                super.onPageFinished(view, url);
            }
        });
        // 配置账号下拉框
        phoneSpinner = findViewById(R.id.phoneSpinner);
        qlStatusText = findViewById(R.id.qlStatusText);
        updateQlStatus(getString(R.string.ql_status_not_login), false);
        String phoneStr = config.getString("phoneStr", null);
        if (phoneStr != null) {
            String[] phones = phoneStr.split("\r\n");
            phoneSet = Arrays.stream(phones)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            updatePhone();
        }
        // 添加按钮
        addBtn = findViewById(R.id.addBtn);
        addBtn.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final AlertDialog dialog = builder.create();
            View dialogView = View.inflate(context, R.layout.activity_phone, null);
            //设置对话框布局
            dialog.setView(dialogView);
            dialog.show();

            EditText phoneText = dialogView.findViewById(R.id.phoneText);
            EditText passwordText = dialogView.findViewById(R.id.passwordText);
            Button confirmBtn = dialogView.findViewById(R.id.confirmBtn);
            confirmBtn.setOnClickListener(v2 -> {
                String phone = phoneText.getText().toString().trim();
                String password = passwordText.getText().toString().trim();
                if (StringUtils.isBlank(phone)) {
                    Toast.makeText(this, "账号输入错误", Toast.LENGTH_SHORT).show();
                    return;
                }
                String account = StringUtils.isBlank(password) ? phone : phone + " " + password;
                boolean updated = upsertAccount(account);
                dialog.cancel();
                updatePhone();
                Toast.makeText(this, updated ? "账号已更新" : "添加成功", Toast.LENGTH_SHORT).show();
            });
        });
        // 删除按钮
        delBtn = findViewById(R.id.delBtn);
        delBtn.setOnClickListener(v -> {
            String selectedPhone = (String) phoneSpinner.getSelectedItem();
            if (selectedPhone == null) {
                Toast.makeText(this, "请先选择账号", Toast.LENGTH_SHORT).show();
                return;
            }
            phoneSet = phoneSet.stream()
                    .filter(phone -> !phone.equals(selectedPhone))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            // 更新账号
            updatePhone();
            Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
        });

        // 一键输入按钮
        inputBtn = findViewById(R.id.inputBtn);
        inputBtn.setOnClickListener(v -> {
            String selectedPhone = (String) phoneSpinner.getSelectedItem();
            if (selectedPhone == null) {
                Toast.makeText(this, "请先选择账号", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] info=  selectedPhone.split("\\s+", 2);
            String Phone=info[0];
            String Pwd=info.length > 1 ? info[1] : "";
            boolean hasPassword = StringUtils.isNotBlank(Pwd);
            if (hasPassword) {
                String tapPointJs = "(function(){";
                tapPointJs += "function isVisible(el){if(!el){return false;}var s=getComputedStyle(el);var r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0'&&r.width>0&&r.height>0;}";
                tapPointJs += "var el=document.querySelector('.planBLogin');";
                tapPointJs += "if(!isVisible(el)){var nodes=document.querySelectorAll('a,button,div,span,p');for(var i=0;i<nodes.length;i++){var text=(nodes[i].innerText||nodes[i].textContent||'').trim();if(isVisible(nodes[i])&&text==='账号密码登录'){el=nodes[i];break;}}}";
                tapPointJs += "if(!isVisible(el)){return null;}";
                tapPointJs += "var r=el.getBoundingClientRect();";
                tapPointJs += "return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2,vw:document.documentElement.clientWidth,vh:document.documentElement.clientHeight});";
                tapPointJs += "})()";
                webView.evaluateJavascript(tapPointJs, this::tapWebViewByJsResult);
            } else {
                String policyPointJs = "(function(){";
                policyPointJs += "function isVisible(el){if(!el){return false;}var s=getComputedStyle(el);var r=el.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&s.opacity!=='0'&&r.width>0&&r.height>0;}";
                policyPointJs += "var el=document.querySelector('.policy_tip-checkbox')||document.querySelector('input[type=\"checkbox\"]');";
                policyPointJs += "if(isVisible(el)){var r=el.getBoundingClientRect();return JSON.stringify({x:r.left+r.width/2,y:r.top+r.height/2,vw:document.documentElement.clientWidth,vh:document.documentElement.clientHeight});}";
                policyPointJs += "var nodes=document.querySelectorAll('a,button,div,span,p,label,i');";
                policyPointJs += "for(var i=0;i<nodes.length;i++){var text=(nodes[i].innerText||nodes[i].textContent||'').trim();";
                policyPointJs += "if(isVisible(nodes[i])&&(text.indexOf('我已阅读并同意')>-1||text.indexOf('同意协议并选择其他登录方式')>-1)){";
                policyPointJs += "var r=nodes[i].getBoundingClientRect();var x=Math.max(12,r.left-16);return JSON.stringify({x:x,y:r.top+r.height/2,vw:document.documentElement.clientWidth,vh:document.documentElement.clientHeight});";
                policyPointJs += "}}";
                policyPointJs += "return null;";
                policyPointJs += "})()";
                webView.evaluateJavascript(policyPointJs, this::tapWebViewByJsResult);
            }

            String execJs = "(function(){";
            execJs += "var account=" + JSON.toJSONString(Phone) + ";";
            execJs += "var password=" + JSON.toJSONString(Pwd) + ";";
            execJs += "var hasPassword=" + hasPassword + ";";
            execJs += "function isVisible(el){";
            execJs += "if(!el){return false;}";
            execJs += "var style=window.getComputedStyle(el);";
            execJs += "var rect=el.getBoundingClientRect();";
            execJs += "return style.display!=='none'&&style.visibility!=='hidden'&&style.opacity!=='0'&&rect.width>0&&rect.height>0;";
            execJs += "}";
            execJs += "function tap(el){";
            execJs += "if(!el){return;}";
            execJs += "try{el.scrollIntoView({block:'center',inline:'center'});}catch(e){}";
            execJs += "try{el.dispatchEvent(new TouchEvent('touchstart',{bubbles:true,cancelable:true}));}catch(e){}";
            execJs += "try{el.dispatchEvent(new TouchEvent('touchend',{bubbles:true,cancelable:true}));}catch(e){}";
            execJs += "try{el.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,cancelable:true}));}catch(e){}";
            execJs += "try{el.dispatchEvent(new MouseEvent('mouseup',{bubbles:true,cancelable:true}));}catch(e){}";
            execJs += "try{el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));}catch(e){}";
            execJs += "try{el.click();}catch(e){}";
            execJs += "}";
            execJs += "function clickPasswordLogin(){";
            execJs += "var planB=document.querySelector('.planBLogin');";
            execJs += "if(isVisible(planB)){tap(planB);return true;}";
            execJs += "var nodes=document.querySelectorAll('a,button,div,span,p');";
            execJs += "for(var i=0;i<nodes.length;i++){";
            execJs += "var text=(nodes[i].innerText||nodes[i].textContent||'').trim();";
            execJs += "if(isVisible(nodes[i])&&text==='账号密码登录'){";
            execJs += "var target=nodes[i].closest('a,button,[role=\"button\"]')||nodes[i];";
            execJs += "for(var j=0;j<4&&target;j++,target=target.parentElement){";
            execJs += "tap(target);";
            execJs += "if(isVisible(document.getElementById('pwd'))){return true;}";
            execJs += "}";
            execJs += "return true;";
            execJs += "}";
            execJs += "}";
            execJs += "return false;";
            execJs += "}";
            execJs += "function inputValue(el,value){";
            execJs += "if(!el){return;}";
            execJs += "el.value=value;";
            execJs += "var evt=new InputEvent('input',{inputType:'insertText',data:value,dataTransfer:null,isComposing:false});";
            execJs += "el.dispatchEvent(evt);";
            execJs += "}";
            execJs += "function clickPolicy(){";
            execJs += "var selectors=['.policy_tip-checkbox','.policy_tip .icon','.policy_tip','input[type=\"checkbox\"]'];";
            execJs += "for(var i=0;i<selectors.length;i++){var el=document.querySelector(selectors[i]);if(isVisible(el)){tap(el);return true;}}";
            execJs += "var nodes=document.querySelectorAll('a,button,div,span,p,label,i');";
            execJs += "for(var j=0;j<nodes.length;j++){";
            execJs += "var text=(nodes[j].innerText||nodes[j].textContent||'').trim();";
            execJs += "if(isVisible(nodes[j])&&(text.indexOf('同意协议')>-1||text.indexOf('我已阅读并同意')>-1)){";
            execJs += "var target=nodes[j].closest('label,a,button,[role=\"button\"]')||nodes[j];";
            execJs += "tap(target);return true;";
            execJs += "}";
            execJs += "}";
            execJs += "return false;";
            execJs += "}";
            execJs += "function fillLoginForm(){";
            execJs += "if(!hasPassword){";
            execJs += "var smsInput=document.getElementsByClassName('acc-input mobile J_ping')[0]||document.getElementById('username');";
            execJs += "if(!isVisible(smsInput)){return false;}";
            execJs += "inputValue(smsInput,account);";
            execJs += "return true;";
            execJs += "}";
            execJs += "clickPolicy();";
            execJs += "var username=document.getElementById('username');";
            execJs += "var pwd=document.getElementById('pwd');";
            execJs += "if(!isVisible(username)||!isVisible(pwd)){return false;}";
            execJs += "inputValue(username,account);";
            Matcher matcher = PHONE_PATTERN.matcher(Phone);
            if (matcher.matches()) {
                execJs += "inputValue(document.getElementsByClassName('acc-input mobile J_ping')[0],account);";
            }
            execJs += "inputValue(pwd,password);";
            execJs += "if(password){var loginBtn=document.querySelector('#app>div>a');if(isVisible(loginBtn)){tap(loginBtn);}}";
            execJs += "return true;";
            execJs += "}";
            execJs += "var retry=0;";
            execJs += "var timer=setInterval(function(){";
            execJs += "if(fillLoginForm()||retry++>20){clearInterval(timer);return;}";
            execJs += "clickPasswordLogin();";
            execJs += "},250);";
            execJs += "})();";

            webView.loadUrl("javascript:" + execJs);
        });
        // 获取cookie按钮
        getCookieBtn = findViewById(R.id.getCookieBtn);
        getCookieBtn.setOnClickListener(v -> {
            Map<String, Object> map = JDUtil.formatCookies(cookie);
            String ptKey = (String) map.get("pt_key");
            String ptPin = (String) map.get("pt_pin");
            if (StringUtils.isAnyBlank(ptKey, ptPin)) {
                Toast.makeText(this, "未获取到Cookie，请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            String selectedPhone = (String) phoneSpinner.getSelectedItem();
            String Phone="";
            if (selectedPhone != null) {
                String[] info=  selectedPhone.split("\\s+", 2);
                Phone=info[0];
            }

            String cookie = MessageFormat.format("pt_key={0};pt_pin={1};", ptKey, ptPin);
            QlInfo qlInfo = Config.getInstance().getQlInfo();
            copyToClipboard(cookie);
            if (qlInfo == null || qlInfo.getToken() == null) {
                Toast.makeText(this, "获取成功，已复制到剪切板", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "获取成功，已复制到剪切板，尝试自动更新青龙面板环境变量", Toast.LENGTH_SHORT).show();
                updateCookie(cookie,Phone);
            }
        });
        // 检测青龙面板中的JD_COOKIE
        checkCookieBtn = findViewById(R.id.checkCookieBtn);
        checkCookieBtn.setOnClickListener(v -> checkQlCookies());
        // 豆子检测按钮
        beanCheckBtn = findViewById(R.id.beanCheckBtn);
        beanCheckBtn.setOnClickListener(v -> checkQlBeans());
        // 重置cookie刷新页面按钮
        clearCookieBtn = findViewById(R.id.clearCookieBtn);
        clearCookieBtn.setOnClickListener(v -> {
//            updateCookie("pt_key=test;pt_pin=Yclown;");
            resetWebview();
        });

        // 检查token有效 同时获取环境变量
        checkQlLogin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        checkQlLogin();
    }

    @Override
    protected void onDestroy() {
        unregisterSMSReceiver();
        super.onDestroy();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        String text = smsEnabled ? "关闭短信识别" : "开启短信识别";
        menu.add(Menu.NONE, 1, 1, "青龙面板");
        menu.add(Menu.NONE, 2, 2, text);
        menu.add(Menu.NONE, 3, 3, "关于");
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case 1: {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            } break;
            case 2: {
                String toastText;
                if (smsEnabled) {
                    toastText = "关闭成功，请重启应用生效";
                } else {
                    String[] permissions = new String[]{"android.permission.RECEIVE_SMS", "android.permission.READ_SMS"};
                    boolean result = checkPermissionAllGranted(permissions);
                    if (!result) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setMessage("未获取到短信读取权限，是否跳转设置页面并开启权限（MIUI需要额外开启通知类短信权限）？");
                        builder.setPositiveButton("设置", (dialog, which) -> {
                            openSettings();
                        });
                        builder.create().show();
                        break;
                    }
                    toastText = "开启成功，请重启应用生效";
                }
                Toast.makeText(this, toastText, Toast.LENGTH_LONG).show();
                smsEnabled = !smsEnabled;
                String text = smsEnabled ? "关闭短信识别" : "开启短信识别";
                item.setTitle(text);
                SharedPreferences.Editor edit = config.edit();
                edit.putBoolean("smsEnabled", smsEnabled);
                edit.apply();
            } break;
            case 3: {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setMessage("本软件仅供学习参考所用，不得挪用他途，请于下载后24小时内删除。谢谢合作！\nhttps://github.com/1061700625/jdck4ql");
                builder.setPositiveButton("项目页面", (dialog, which) -> {
                    Uri uri = Uri.parse("https://github.com/1061700625/jdck4ql");
                    Intent intent = new Intent();
                    intent.setAction("android.intent.action.VIEW");
                    intent.setData(uri);
                    startActivity(intent);
                });
                builder.create().show();
            }
            break;
        }
        return true;
    }

    /**
     * 检查权限
     * @param permissions
     * @return boolean
     * @author XanderYe
     * @date 2022/12/30
     */
    private boolean checkPermissionAllGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 打开设置
     * @param
     * @return void
     * @author XanderYe
     * @date 2022/12/30
     */
    private void openSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * 注册短信接收器
     * @param
     * @return void
     * @author XanderYe
     * @date 2022/12/30
     */
    private void registerSMSReceiver() {
        smsReceiver = new SMSReceiver();
        IntentFilter filter = new IntentFilter();
        filter.setPriority(1000);
        filter.addAction(SMSReceiver.SMS_RECEIVED);
        registerReceiver(smsReceiver, filter);
    }

    /**
     * 取消注册短信接收器
     * @param
     * @return void
     * @author XanderYe
     * @date 2022/12/30
     */
    private void unregisterSMSReceiver() {
        if (smsReceiver != null) {
            unregisterReceiver(smsReceiver);
            smsReceiver = null;
        }
    }

    /**
     * 清空cookie并加载页面
     * @param
     * @return void
     * @author XanderYe
     * @date 2022/5/10
     */
    private void resetWebview() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        WebStorage.getInstance().deleteAllData();
        webView.loadUrl(JD_URL);
    }

    /**
     * 更新下拉和存储中的账号
     * @return void
     * @author XanderYe
     * @date 2022/5/10
     */
    private void updatePhone() {
        // 更新账号
        String newPhoneStr = phoneSet.stream().collect(Collectors.joining("\r\n"));
        SharedPreferences.Editor edit = config.edit();
        edit.putString("phoneStr", newPhoneStr);
        edit.apply();
        List<String> phones = new ArrayList<>(phoneSet);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, phones) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setText(getAccountName(getItem(position)));
                // Spinner 是白底，把文字统一改成黑色
                view.setTextColor(Color.parseColor("#212121"));
                view.setTextSize(15);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setText(getAccountName(getItem(position)));
                view.setTextColor(Color.parseColor("#212121"));
                return view;
            }
        };
        phoneSpinner.setAdapter(adapter);
        if (!phones.isEmpty()) {
            phoneSpinner.setSelection(0);
        }
    }

    private boolean upsertAccount(String account) {
        String accountName = getAccountName(account);
        boolean updated = false;
        LinkedHashSet<String> newPhoneSet = new LinkedHashSet<>();
        for (String savedAccount : phoneSet) {
            if (accountName.equals(getAccountName(savedAccount))) {
                newPhoneSet.add(account);
                updated = true;
            } else {
                newPhoneSet.add(savedAccount);
            }
        }
        if (!updated) {
            newPhoneSet.add(account);
        }
        phoneSet = newPhoneSet;
        return updated;
    }

    private void updateQlStatus(String text, boolean connected) {
        if (qlStatusText == null) {
            return;
        }
        qlStatusText.setText(text);
        qlStatusText.setTextColor(Color.parseColor(connected ? "#2E7D32" : "#616161"));
    }

    private String getQlStatusText(String prefix, QlInfo qlInfo) {
        if (qlInfo == null || StringUtils.isBlank(qlInfo.getAddress())) {
            return prefix;
        }
        return prefix + " · " + qlInfo.getAddress();
    }

    private String getAccountName(String account) {
        if (account == null) {
            return "";
        }
        return account.split("\\s+", 2)[0];
    }

    /**
     * 复制文字到剪切板
     * @param copyStr
     * @return boolean
     * @author XanderYe
     * @date 2022/5/10
     */
    private boolean copyToClipboard(String copyStr) {
        try {
            //获取剪贴板管理器
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            // 创建普通字符型ClipData
            ClipData mClipData = ClipData.newPlainText("Label", copyStr);
            // 将ClipData内容放到系统剪贴板里。
            cm.setPrimaryClip(mClipData);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkQlLogin() {
        String qlJSON = config.getString("qlJSON", null);
        if (qlJSON == null) {
            updateQlStatus(getString(R.string.ql_status_not_login), false);
            return;
        }
        QlInfo qlInfo = JSON.parseObject(qlJSON, QlInfo.class);
        updateQlStatus(getQlStatusText(getString(R.string.ql_status_checking), qlInfo), false);
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
        singleThreadExecutor.execute(() -> {
            Looper.prepare();


            try {
                List<QlEnv> qlEnvList = QinglongUtil.getEnvList(qlInfo,"");
                Config.getInstance().setQlEnvList(qlEnvList);
                Config.getInstance().setQlInfo(qlInfo);
                runOnUiThread(() -> updateQlStatus(getQlStatusText(getString(R.string.ql_status_connected), qlInfo), true));
                Toast.makeText(this, "青龙token有效", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                runOnUiThread(() -> updateQlStatus(getQlStatusText(getString(R.string.ql_status_expired), qlInfo), false));
                Toast.makeText(this, "青龙token已失效，请重新登录", Toast.LENGTH_SHORT).show();
            }
            Looper.loop();
        });
        singleThreadExecutor.shutdown();
    }

    private void tapWebViewByJsResult(String value) {
        if (StringUtils.isBlank(value) || "null".equals(value)) {
            return;
        }
        try {
            Object parsed = JSON.parse(value);
            if (!(parsed instanceof String)) {
                return;
            }
            JSONObject point = JSON.parseObject((String) parsed);
            float viewportWidth = point.getFloatValue("vw");
            float viewportHeight = point.getFloatValue("vh");
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                return;
            }
            float x = point.getFloatValue("x") * webView.getWidth() / viewportWidth;
            float y = point.getFloatValue("y") * webView.getHeight() / viewportHeight;
            dispatchWebViewTap(x, y);
        } catch (Exception ignored) {
        }
    }

    private void dispatchWebViewTap(float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 80, MotionEvent.ACTION_UP, x, y, 0);
        webView.dispatchTouchEvent(down);
        webView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private void checkQlCookies() {
        QlInfo qlInfo = Config.getInstance().getQlInfo();
        if (qlInfo == null || StringUtils.isBlank(qlInfo.getToken())) {
            Toast.makeText(this, "请先登录青龙面板", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "开始检测青龙CK", Toast.LENGTH_SHORT).show();
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
        singleThreadExecutor.execute(() -> {
            try {
                List<QlEnv> qlEnvList = QinglongUtil.getEnvList(qlInfo, "JD_COOKIE");
                Config.getInstance().setQlEnvList(qlEnvList);
                List<QlEnv> cookieEnvList = qlEnvList.stream()
                        .filter(env -> "JD_COOKIE".equals(env.getName()))
                        .filter(env -> StringUtils.isNotBlank(env.getValue()))
                        .collect(Collectors.toList());
                if (cookieEnvList.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "青龙中未找到JD_COOKIE", Toast.LENGTH_SHORT).show());
                    return;
                }

                List<JDUtil.CheckResult> results = new ArrayList<>();
                for (QlEnv qlEnv : cookieEnvList) {
                    JDUtil.CheckResult result = JDUtil.checkCookie(qlEnv.getValue());
                    // 若青龙 remarks 里存了手机号/备注，用它替代 pt_pin 展示更直观
                    if (StringUtils.isNotBlank(qlEnv.getRemarks())) {
                        result = new JDUtil.CheckResult(result.getStatus(), qlEnv.getRemarks(), result.getNickname(), result.getMessage());
                    }
                    results.add(result);
                }
                runOnUiThread(() -> showCookieCheckResult(results));
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
        singleThreadExecutor.shutdown();
    }

    /**
     * 豆子检测：读取青龙 JD_COOKIE 环境变量，逐个统计京豆信息
     * 参考 jd_bean_info.js 的实现：
     *  - 通过 GetJDUserInfoUnion 拿到 nickname 与当前总京豆
     *  - 通过 getJingBeanBalanceDetail 分页拉京豆流水，统计今日/昨日收支
     */
    private void checkQlBeans() {
        QlInfo qlInfo = Config.getInstance().getQlInfo();
        if (qlInfo == null || StringUtils.isBlank(qlInfo.getToken())) {
            Toast.makeText(this, "请先登录青龙面板", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "开始豆子检测（可能需要几秒）", Toast.LENGTH_SHORT).show();
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
        singleThreadExecutor.execute(() -> {
            try {
                List<QlEnv> qlEnvList = QinglongUtil.getEnvList(qlInfo, "JD_COOKIE");
                Config.getInstance().setQlEnvList(qlEnvList);
                List<QlEnv> cookieEnvList = qlEnvList.stream()
                        .filter(env -> "JD_COOKIE".equals(env.getName()))
                        .filter(env -> StringUtils.isNotBlank(env.getValue()))
                        .collect(Collectors.toList());
                if (cookieEnvList.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "青龙中未找到JD_COOKIE", Toast.LENGTH_SHORT).show());
                    return;
                }

                List<JDUtil.BeanResult> results = new ArrayList<>();
                int idx = 0;
                for (QlEnv qlEnv : cookieEnvList) {
                    idx++;
                    JDUtil.BeanResult r = JDUtil.checkBean(qlEnv.getValue());
                    r.setIndex(idx);
                    if (StringUtils.isNotBlank(qlEnv.getRemarks())) {
                        r.setRemarks(qlEnv.getRemarks());
                    }
                    results.add(r);
                }
                runOnUiThread(() -> showBeanCheckResult(results));
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
        singleThreadExecutor.shutdown();
    }

    private void showBeanCheckResult(List<JDUtil.BeanResult> results) {
        int total = 0;
        int validCount = 0;
        int invalidCount = 0;
        for (JDUtil.BeanResult r : results) {
            if (r.isLogin()) {
                validCount++;
                total += r.getTodayIncomeBean();
            } else {
                invalidCount++;
            }
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_check_result, null);
        TextView titleView = dialogView.findViewById(R.id.dialogTitle);
        TextView summaryView = dialogView.findViewById(R.id.dialogSummary);
        LinearLayout content = dialogView.findViewById(R.id.dialogContent);
        View headerView = dialogView.findViewById(R.id.dialogHeader);
        if (headerView != null) {
            headerView.setBackgroundColor(Color.parseColor("#FB8C00")); // 豆子橙
        }
        titleView.setText("豆子检测结果");
        summaryView.setText(MessageFormat.format(
                "共 {0} 个账号 · 有效 {1} · 失效 {2} · 今日总收入 {3} 京豆 🐶",
                results.size(), validCount, invalidCount, total));

        for (JDUtil.BeanResult r : results) {
            View item = inflater.inflate(R.layout.item_check_result, content, false);
            TextView icon = item.findViewById(R.id.itemStatusIcon);
            TextView title = item.findViewById(R.id.itemTitle);
            TextView subtitle = item.findViewById(R.id.itemSubtitle);

            String who = StringUtils.isNotBlank(r.getRemarks()) ? r.getRemarks() : r.getPin();
            if (StringUtils.isBlank(who)) {
                who = "账号" + r.getIndex();
            }

            if (!r.isLogin()) {
                icon.setText("×");
                icon.setBackgroundResource(R.drawable.bg_status_invalid);
                title.setText("账号 " + r.getIndex() + " · " + who);
                title.setTextColor(Color.parseColor("#D32F2F"));
                String msg = StringUtils.isNotBlank(r.getMessage()) ? r.getMessage() : "Cookie 已失效";
                subtitle.setText(msg);
            } else {
                icon.setText("√");
                icon.setBackgroundResource(R.drawable.bg_status_valid);
                StringBuilder t = new StringBuilder();
                t.append("账号 ").append(r.getIndex()).append(" · ").append(who);
                if (StringUtils.isNotBlank(r.getNickname())) {
                    t.append("（").append(r.getNickname()).append("）");
                }
                title.setText(t.toString());
                title.setTextColor(Color.parseColor("#212121"));

                StringBuilder s = new StringBuilder();
                s.append("当前京豆：").append(r.getBeanCount()).append("\n");
                s.append("今日收入：").append(r.getTodayIncomeBean()).append("\n");
                s.append("昨日收入：").append(r.getYesterdayIncomeBean())
                        .append("   昨日支出：").append(r.getYesterdayExpenseBean());
                subtitle.setText(s.toString());
            }
            content.addView(item);
        }

        new AlertDialog.Builder(context)
                .setView(dialogView)
                .setPositiveButton("确定", null)
                .show();
    }

    private void showCookieCheckResult(List<JDUtil.CheckResult> results) {
        int validCount = 0;
        int invalidCount = 0;
        int unknownCount = 0;
        for (JDUtil.CheckResult result : results) {
            switch (result.getStatus()) {
                case VALID:
                    validCount++;
                    break;
                case INVALID:
                    invalidCount++;
                    break;
                default:
                    unknownCount++;
                    break;
            }
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        View dialogView = inflater.inflate(R.layout.dialog_check_result, null);
        TextView titleView = dialogView.findViewById(R.id.dialogTitle);
        TextView summaryView = dialogView.findViewById(R.id.dialogSummary);
        LinearLayout content = dialogView.findViewById(R.id.dialogContent);
        View headerView = dialogView.findViewById(R.id.dialogHeader);
        if (headerView != null) {
            headerView.setBackgroundColor(Color.parseColor("#5E35B1")); // 检测CK紫
        }
        titleView.setText("CK 检测结果");
        summaryView.setText(MessageFormat.format(
                "共 {0} 个 · 有效 {1} · 失效 {2} · 未知 {3}",
                results.size(), validCount, invalidCount, unknownCount));

        int idx = 0;
        for (JDUtil.CheckResult result : results) {
            idx++;
            View item = inflater.inflate(R.layout.item_check_result, content, false);
            TextView icon = item.findViewById(R.id.itemStatusIcon);
            TextView title = item.findViewById(R.id.itemTitle);
            TextView subtitle = item.findViewById(R.id.itemSubtitle);

            String pin = StringUtils.isNotBlank(result.getPin()) ? result.getPin() : ("账号" + idx);
            StringBuilder t = new StringBuilder();
            t.append("账号 ").append(idx).append(" · ").append(pin);
            if (StringUtils.isNotBlank(result.getNickname())) {
                t.append("（").append(result.getNickname()).append("）");
            }
            title.setText(t.toString());

            switch (result.getStatus()) {
                case VALID:
                    icon.setText("√");
                    icon.setBackgroundResource(R.drawable.bg_status_valid);
                    title.setTextColor(Color.parseColor("#2E7D32"));
                    subtitle.setText(StringUtils.isNotBlank(result.getMessage())
                            ? result.getMessage() : "Cookie 有效");
                    break;
                case INVALID:
                    icon.setText("×");
                    icon.setBackgroundResource(R.drawable.bg_status_invalid);
                    title.setTextColor(Color.parseColor("#D32F2F"));
                    subtitle.setText(StringUtils.isNotBlank(result.getMessage())
                            ? result.getMessage() : "Cookie 已失效");
                    break;
                default:
                    icon.setText("?");
                    icon.setBackgroundResource(R.drawable.bg_status_unknown);
                    title.setTextColor(Color.parseColor("#F57C00"));
                    subtitle.setText(StringUtils.isNotBlank(result.getMessage())
                            ? result.getMessage() : "状态未知");
                    break;
            }
            content.addView(item);
        }

        new AlertDialog.Builder(context)
                .setView(dialogView)
                .setPositiveButton("确定", null)
                .show();
    }

    /**
     * 调用青龙接口更新cookie
     * @param cookie
     * @return void
     * @author XanderYe
     * @date 2022/5/11
     */
    private void updateCookie(String cookie,String phone) {
        Map<String, Object> map = JDUtil.formatCookies(cookie);
        String ptPin = (String) map.get("pt_pin");
        QlInfo qlInfo = Config.getInstance().getQlInfo();
        List<QlEnv> qlEnvList =Config.getInstance().getQlEnvList();
        if(qlEnvList==null){
            qlEnvList=new ArrayList<QlEnv>();
        }
        QlEnv targetEnv = null;
        for (QlEnv qlEnv : qlEnvList) {
            Map<String, Object> envMap = JDUtil.formatCookies(qlEnv.getValue());
            String tempPin = (String) envMap.get("pt_pin");
            if(ptPin.equals(tempPin)) {
                targetEnv = qlEnv;
                break;
            }
        }
        if (targetEnv == null) {
            targetEnv = new QlEnv();
            targetEnv.setName("JD_COOKIE");
        }
        targetEnv.setValue(cookie);
        targetEnv.setRemarks(phone);
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
        QlEnv finalTargetEnv = targetEnv;
        singleThreadExecutor.execute(() -> {
            Looper.prepare();
            try {
                boolean success = QinglongUtil.saveEnv(qlInfo, finalTargetEnv);
                QinglongUtil.EableEnv(qlInfo,finalTargetEnv);
                if (success) {
                    Toast.makeText(this, "更新cookie成功", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            Looper.loop();
        });
        singleThreadExecutor.shutdown();

    }

}
