package cn.xfxuezhang.jdck.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import cn.xfxuezhang.jdck.R;
import cn.xfxuezhang.jdck.config.Config;
import cn.xfxuezhang.jdck.entity.QlEnv;
import cn.xfxuezhang.jdck.entity.QlInfo;
import cn.xfxuezhang.jdck.util.QinglongUtil;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @origin XanderYe
 * @author yclown
 * @description:
 * @date 2024/3/22 11:00
 */
public class LoginActivity extends AppCompatActivity {

    private Context context;

    private SharedPreferences config;

    private EditText addressText, usernameText, passwordText;

    private Button loginBtn, cancelBtn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        context = this;

        // 配置存储
        config = getSharedPreferences("CONFIG", Context.MODE_PRIVATE);
        String qlJSON = config.getString("qlJSON", null);
        QlInfo qlInfo = new QlInfo("", "", "", "");
        if (qlJSON != null) {
            qlInfo = JSON.parseObject(qlJSON, QlInfo.class);
            Config.getInstance().setQlInfo(qlInfo);
        }

        addressText = findViewById(R.id.addressText);
        usernameText = findViewById(R.id.usernameText);
        passwordText = findViewById(R.id.passwordText);
        addressText.setText(qlInfo.getAddress());
        usernameText.setText(qlInfo.getUsername());
        passwordText.setText(qlInfo.getPassword());


        loginBtn = findViewById(R.id.loginBtn);
        loginBtn.setOnClickListener(v -> {
            String addr = addressText.getEditableText().toString();
            String user = usernameText.getEditableText().toString();
            String pwd = passwordText.getEditableText().toString();

            if (StringUtils.isBlank(addr)) {
                Toast.makeText(this, "地址不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            if (StringUtils.isAnyBlank(user, pwd)) {
                Toast.makeText(this, "请输入必要参数", Toast.LENGTH_SHORT).show();
                return;
            }
            QlInfo qlInfo2 = new QlInfo();
            qlInfo2.setAddress(addr);
            qlInfo2.setUsername(user);
            qlInfo2.setPassword(pwd);

            ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
            singleThreadExecutor.execute(() -> {
                Looper.prepare();
                try {
                    String tk = QinglongUtil.login(qlInfo2);
                    if (StringUtils.isBlank(tk)) {
                        Toast.makeText(this, "登录失败，token为空", Toast.LENGTH_SHORT).show();
                        Looper.loop();
                        return;
                    }
                    qlInfo2.setToken(tk);
                    loginSuccess(qlInfo2);
                    //登陆后 更新环境变量
                    List<QlEnv> qlEnvList = QinglongUtil.getEnvList(qlInfo2,"");
                    Config.getInstance().setQlEnvList(qlEnvList);

                } catch (IOException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                } finally {
                    Looper.loop();
                }
            });
            singleThreadExecutor.shutdown();
        });

        cancelBtn = findViewById(R.id.cancelBtn);
        cancelBtn.setOnClickListener(v -> {
            this.finish();
        });
    }

    private void loginSuccess(QlInfo qlInfo) throws IOException {
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
        // 存储内存
        Config.getInstance().setQlInfo(qlInfo);
        // 数据持久化
        SharedPreferences.Editor edit = config.edit();
        edit.putString("qlJSON", JSON.toJSONString(qlInfo));
        edit.apply();
        this.finish();
    }
}
