package cn.xfxuezhang.jdck.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
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

    // ==================== 豆子检测 ====================

    public static class BeanResult {
        private int index;
        private String pin = "";
        private String nickname = "";
        private String remarks = "";
        private boolean login = true;
        private int beanCount = 0;
        private int todayIncomeBean = 0;
        private int yesterdayIncomeBean = 0;
        private int yesterdayExpenseBean = 0;
        private String message = "";

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public String getPin() { return pin; }
        public void setPin(String pin) { this.pin = pin; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public String getRemarks() { return remarks; }
        public void setRemarks(String remarks) { this.remarks = remarks; }
        public boolean isLogin() { return login; }
        public void setLogin(boolean login) { this.login = login; }
        public int getBeanCount() { return beanCount; }
        public void setBeanCount(int beanCount) { this.beanCount = beanCount; }
        public int getTodayIncomeBean() { return todayIncomeBean; }
        public void setTodayIncomeBean(int todayIncomeBean) { this.todayIncomeBean = todayIncomeBean; }
        public int getYesterdayIncomeBean() { return yesterdayIncomeBean; }
        public void setYesterdayIncomeBean(int yesterdayIncomeBean) { this.yesterdayIncomeBean = yesterdayIncomeBean; }
        public int getYesterdayExpenseBean() { return yesterdayExpenseBean; }
        public void setYesterdayExpenseBean(int yesterdayExpenseBean) { this.yesterdayExpenseBean = yesterdayExpenseBean; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    private static final String JD_BEAN_UA =
            "jdapp;iPhone;9.4.4;14.3;network/4g;Mozilla/5.0 (iPhone; CPU iPhone OS 14_3 like Mac OS X) "
                    + "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148;supportJDSHWK/1";

    /**
     * 豆子检测：参考 jd_bean_info.js
     */
    public static BeanResult checkBean(String cookie) {
        BeanResult result = new BeanResult();
        Map<String, Object> cookieMap = formatCookies(cookie);
        String pin = (String) cookieMap.get("pt_pin");
        String decodedPin = pin == null ? "" : pin;
        try {
            if (pin != null) {
                decodedPin = java.net.URLDecoder.decode(pin, "UTF-8");
            }
        } catch (Exception ignored) {
        }
        result.setPin(decodedPin);
        try {
            fillTotalBean(cookie, result);
            if (!result.isLogin()) {
                return result;
            }
            fillBeanDetail(cookie, result);
        } catch (Exception e) {
            result.setMessage(e.getMessage() == null ? "查询异常" : e.getMessage());
        }
        return result;
    }

    private static void fillTotalBean(String cookie, BeanResult result) throws IOException {
        Map<String, Object> headers = new HashMap<>();
        headers.put("Cookie", cookie);
        headers.put("Referer", "https://home.m.jd.com/myJd/newhome.action?sceneval=2&ufc=&");
        headers.put("User-Agent", JD_BEAN_UA);
        HttpUtil.ResEntity resEntity = HttpUtil.doGet(
                "https://me-api.jd.com/user_new/info/GetJDUserInfoUnion", headers, null, null);
        if (resEntity.getStatusCode() != 200 || resEntity.getResponse() == null) {
            result.setMessage("用户信息HTTP " + resEntity.getStatusCode());
            return;
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        String retcode = res.getString("retcode");
        if ("1001".equals(retcode)) {
            result.setLogin(false);
            result.setMessage("Cookie已失效");
            return;
        }
        if ("0".equals(retcode) && res.getJSONObject("data") != null) {
            JSONObject data = res.getJSONObject("data");
            JSONObject userInfo = data.getJSONObject("userInfo");
            if (userInfo != null) {
                JSONObject baseInfo = userInfo.getJSONObject("baseInfo");
                if (baseInfo != null) {
                    String nick = baseInfo.getString("nickname");
                    result.setNickname(nick == null ? "" : nick);
                }
            }
            JSONObject assetInfo = data.getJSONObject("assetInfo");
            if (assetInfo != null) {
                Integer beanNum = assetInfo.getInteger("beanNum");
                if (beanNum != null) {
                    result.setBeanCount(beanNum);
                }
            }
        }
    }

    private static void fillBeanDetail(String cookie, BeanResult result) throws IOException {
        // 参照脚本：北京时间零点时间戳
        long now = System.currentTimeMillis();
        long todayStart = (now + 28800000L) / 86400000L * 86400000L - 28800000L;
        long yesterdayStart = todayStart - 24L * 60 * 60 * 1000;

        int todayIncome = 0;
        int yesterdayIncome = 0;
        int yesterdayExpense = 0;
        boolean stop = false;

        for (int page = 1; page <= 30 && !stop; page++) {
            JSONObject bodyObj = new JSONObject();
            bodyObj.put("pageSize", "20");
            bodyObj.put("page", String.valueOf(page));
            String bodyStr = "body=" + URLEncoder.encode(bodyObj.toJSONString(), "UTF-8") + "&appid=ld";

            Map<String, Object> headers = new HashMap<>();
            headers.put("Cookie", cookie);
            headers.put("Host", "api.m.jd.com");
            headers.put("Content-Type", "application/x-www-form-urlencoded");
            headers.put("User-Agent", JD_BEAN_UA);

            HttpUtil.ResEntity resp = HttpUtil.doPostRaw(
                    "https://api.m.jd.com/client.action?functionId=getJingBeanBalanceDetail",
                    headers, bodyStr);
            if (resp.getStatusCode() != 200 || resp.getResponse() == null) {
                result.setMessage("京豆流水HTTP " + resp.getStatusCode());
                return;
            }
            JSONObject data;
            try {
                data = JSON.parseObject(resp.getResponse());
            } catch (Exception e) {
                result.setMessage("京豆流水解析失败");
                return;
            }
            String code = data.getString("code");
            if ("3".equals(code)) {
                result.setLogin(false);
                result.setMessage("Cookie已失效");
                return;
            }
            if (!"0".equals(code)) {
                result.setMessage("京豆流水返回码 " + code);
                return;
            }
            JSONArray detailList = data.getJSONArray("detailList");
            if (detailList == null || detailList.isEmpty()) {
                break;
            }
            for (int i = 0; i < detailList.size(); i++) {
                JSONObject item = detailList.getJSONObject(i);
                String dateStr = item.getString("date");
                if (dateStr == null) {
                    continue;
                }
                long ts;
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT+08:00"));
                    ts = sdf.parse(dateStr).getTime();
                } catch (Exception e) {
                    continue;
                }
                int amount = 0;
                try {
                    amount = Integer.parseInt(item.getString("amount"));
                } catch (Exception ignored) {
                }
                String eventMsg = item.getString("eventMassage");
                if (eventMsg == null) eventMsg = "";
                if (ts >= todayStart) {
                    if (amount > 0
                            && !eventMsg.contains("退还")
                            && !eventMsg.contains("物流")
                            && !eventMsg.contains("扣赠")) {
                        todayIncome += amount;
                    }
                } else if (ts >= yesterdayStart) {
                    if (!eventMsg.contains("退还") && !eventMsg.contains("扣赠")) {
                        if (amount > 0) {
                            yesterdayIncome += amount;
                        } else if (amount < 0) {
                            yesterdayExpense += amount;
                        }
                    }
                } else {
                    stop = true;
                    break;
                }
            }
        }
        result.setTodayIncomeBean(todayIncome);
        result.setYesterdayIncomeBean(yesterdayIncome);
        result.setYesterdayExpenseBean(yesterdayExpense);
    }
}
