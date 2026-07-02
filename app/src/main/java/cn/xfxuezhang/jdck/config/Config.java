package cn.xfxuezhang.jdck.config;

import android.webkit.WebView;
import cn.xfxuezhang.jdck.entity.QlEnv;
import cn.xfxuezhang.jdck.entity.QlInfo;
import lombok.Data;

import java.util.List;

/**
 * @author XanderYe
 * @description:
 * @date 2021/8/19 20:36
 */
@Data
public class Config {

    private static final Config CONFIG = new Config();

    private WebView webView;

    private QlInfo qlInfo;

    private List<QlEnv> qlEnvList;

    private Config() {
    }

    public static Config getInstance() {
        return CONFIG;
    }
}
