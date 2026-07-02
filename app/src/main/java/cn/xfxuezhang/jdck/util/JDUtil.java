package cn.xfxuezhang.jdck.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author XanderYe
 * @description:
 * @date 2022/5/10 11:20
 */
public class JDUtil {

    public enum CheckStatus {
        VALID,
        INVALID,
        UNKNOWN
    }

    public static class CheckResult {
        private final CheckStatus status;
        private final String pin;
        private final String nickname;
        private final String message;

        public CheckResult(CheckStatus status, String pin, String nickname, String message) {
            this.status = status;
            this.pin = pin;
            this.nickname = nickname;
            this.message = message;
        }

        public CheckStatus getStatus() {
            return status;
        }

        public String getPin() {
            return pin;
        }

        public String getNickname() {
            return nickname;
        }

        public String getMessage() {
            return message;
        }
    }

    public static Map<String, Object> formatCookies(String cookieString) {
        Map<String, Object> cookieMap = new HashMap<>(16);
        if (cookieString != null && !"".equals(cookieString)) {
            String[] cookies = cookieString.split(";");
            if (cookies.length > 0) {
                for (String parameter : cookies) {
                    int eqIndex = parameter.indexOf("=");
                    if (eqIndex > -1) {
                        String k = parameter.substring(0, eqIndex).trim();
                        String v = parameter.substring(eqIndex + 1).trim();
                        if (!"".equals(v)) {
                            cookieMap.put(k, v);
                        }
                    }
                }
            }
        }
        return cookieMap;
    }

    public static CheckResult checkCookie(String cookie) {
        Map<String, Object> cookieMap = formatCookies(cookie);
        String pin = (String) cookieMap.get("pt_pin");
        if (pin == null || pin.length() == 0) {
            return new CheckResult(CheckStatus.UNKNOWN, "", "", "缺少pt_pin");
        }
        try {
            CheckResult userInfoResult = checkByUserInfo(cookie, pin);
            if (userInfoResult.getStatus() != CheckStatus.UNKNOWN) {
                return userInfoResult;
            }
            return checkByIsLogin(cookie, pin);
        } catch (Exception e) {
            return new CheckResult(CheckStatus.UNKNOWN, pin, "", e.getMessage());
        }
    }

    private static CheckResult checkByUserInfo(String cookie, String pin) throws IOException {
        Map<String, Object> headers = new HashMap<>();
        headers.put("Cookie", cookie);
        headers.put("Referer", "https://home.m.jd.com/");
        HttpUtil.ResEntity resEntity = HttpUtil.doGet("https://me-api.jd.com/user_new/info/GetJDUserInfoUnion", headers, null, null);
        if (resEntity.getStatusCode() != 200 || resEntity.getResponse() == null) {
            return new CheckResult(CheckStatus.UNKNOWN, pin, "", "用户信息接口HTTP " + resEntity.getStatusCode());
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        String retCode = res.getString("retcode");
        if ("1001".equals(retCode)) {
            return new CheckResult(CheckStatus.INVALID, pin, "", "Cookie已失效");
        }
        if ("0".equals(retCode)) {
            JSONObject data = res.getJSONObject("data");
            JSONObject userInfo = data == null ? null : data.getJSONObject("userInfo");
            if (userInfo != null) {
                String nickname = userInfo.getString("baseInfo.nickname");
                if (nickname == null) {
                    JSONObject baseInfo = userInfo.getJSONObject("baseInfo");
                    nickname = baseInfo == null ? "" : baseInfo.getString("nickname");
                }
                return new CheckResult(CheckStatus.VALID, pin, nickname == null ? "" : nickname, "Cookie有效");
            }
        }
        return new CheckResult(CheckStatus.UNKNOWN, pin, "", "用户信息接口返回未知状态");
    }

    private static CheckResult checkByIsLogin(String cookie, String pin) throws IOException {
        Map<String, Object> headers = new HashMap<>();
        headers.put("Cookie", cookie);
        headers.put("Referer", "https://plogin.m.jd.com/");
        HttpUtil.ResEntity resEntity = HttpUtil.doGet("https://plogin.m.jd.com/cgi-bin/ml/islogin", headers, null, null);
        if (resEntity.getStatusCode() != 200 || resEntity.getResponse() == null) {
            return new CheckResult(CheckStatus.UNKNOWN, pin, "", "登录态接口HTTP " + resEntity.getStatusCode());
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        String isLogin = res.getString("islogin");
        if ("1".equals(isLogin)) {
            return new CheckResult(CheckStatus.VALID, pin, "", "Cookie有效");
        }
        if ("0".equals(isLogin)) {
            return new CheckResult(CheckStatus.INVALID, pin, "", "Cookie已失效");
        }
        return new CheckResult(CheckStatus.UNKNOWN, pin, "", "登录态接口返回未知状态");
    }
}
