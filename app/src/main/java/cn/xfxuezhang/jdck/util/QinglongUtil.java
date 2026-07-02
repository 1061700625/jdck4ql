package cn.xfxuezhang.jdck.util;

import cn.xfxuezhang.jdck.entity.QlEnv;
import cn.xfxuezhang.jdck.entity.QlInfo;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author XanderYe
 * @description:
 * @date 2022/5/11 14:04
 */
public class QinglongUtil {

    /**
     * 登录
     * @param qlInfo
     * @return java.lang.String
     * @description:
     * @date 2024/3/22 11:00
     */

    public static String login(QlInfo qlInfo) throws IOException {
        String url = qlInfo.getAddress();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        url += "/open/auth/token?client_id="+qlInfo.getUsername()+"&client_secret="+qlInfo.getPassword();

        HttpUtil.ResEntity resEntity = HttpUtil.doGet(url, null);
        if (resEntity.getStatusCode() != 200) {
            throw new IOException("服务器" + resEntity.getStatusCode() + "错误");
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        if (res.getInteger("code") != 200) {
            throw new IOException(res.getString("message"));
        }
        return res.getJSONObject("data").getString("token");
    }


    public static List<QlEnv> getEnvList(QlInfo qlInfo) throws IOException {
        String url = qlInfo.getAddress() + "/api/envs";
        url += "?searchValue=&t=" + System.currentTimeMillis();
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + qlInfo.getToken());
        HttpUtil.ResEntity resEntity = HttpUtil.doGet(url, headers, null, null);
        if (resEntity.getStatusCode() != 200) {
            throw new IOException("服务器" + resEntity.getStatusCode() + "错误");
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        if (res.getInteger("code") != 200) {
            throw new IOException(res.getString("message"));
        }
        return res.getJSONArray("data").toJavaList(QlEnv.class);
    }
    /**
     * 获取环境变量

     * @date 2024/3/22 11:00
     */
    public static List<QlEnv> getEnvList(QlInfo qlInfo,String key) throws IOException {
        String url = qlInfo.getAddress() + "/open/envs?searchValue="+key;
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + qlInfo.getToken());
        HttpUtil.ResEntity resEntity = HttpUtil.doGet(url, headers, null, null);
        if (resEntity.getStatusCode() != 200) {
            throw new IOException("服务器" + resEntity.getStatusCode() + "错误");
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        if (res.getInteger("code") != 200) {
            throw new IOException(res.getString("message"));
        }
        return res.getJSONArray("data").toJavaList(QlEnv.class);
    }
    /**
     * 更新环境变量
     * @param qlInfo
     * @param qlEnv
     * @return boolean
     * @author yclown
     * @description:
     * @date 2024/3/22 11:00
     */
    public static boolean saveEnv(QlInfo qlInfo, QlEnv qlEnv) throws IOException {
        String url = qlInfo.getAddress() + "/open/envs";;
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + qlInfo.getToken());
        JSONObject params = new JSONObject();
        params.put("name", qlEnv.getName());
        params.put("remarks", qlEnv.getRemarks());
        params.put("value", qlEnv.getValue());
        HttpUtil.ResEntity resEntity;
        if (qlEnv.get_id() != null) {
            // 更新
            params.put("id", qlEnv.get_id());
            resEntity = HttpUtil.doPutJSON(url, headers, null, params.toJSONString());

        } else {
            // 新增
            JSONArray adds=new JSONArray();
            adds.add(params);
            resEntity = HttpUtil.doPostJSON(url, headers, null, adds.toJSONString());
        }
        if (resEntity.getStatusCode() != 200) {
            throw new IOException("发送 服务器" + resEntity.getStatusCode() + "错误");
        }
        JSONObject res = JSON.parseObject(resEntity.getResponse());
        if (res.getInteger("code") != 200) {
            throw new IOException(res.getString("message"));
        }
        return true;
    }
    /**
     * 启用环境变量
     * @param qlInfo
     * @param qlEnv
     * @author yclown
     * @description:
     * @date 2024/3/22 11:00
     */
    public static void EableEnv(QlInfo qlInfo,QlEnv qlEnv) throws IOException {
        String url = qlInfo.getAddress() + "/open/envs/enable";;
        Map<String, Object> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + qlInfo.getToken());
        HttpUtil.ResEntity resEntity;
        if (qlEnv.get_id() != null) {
            JSONArray ja=new JSONArray();
            ja.add(qlEnv.get_id());

            resEntity = HttpUtil.doPutJSON(url, headers, null, ja.toJSONString());
            if (resEntity.getStatusCode() != 200) {
                throw new IOException("启用 服务器" + resEntity.getStatusCode() + "错误");
            }
            JSONObject res = JSON.parseObject(resEntity.getResponse());
            if (res.getInteger("code") != 200) {
                throw new IOException(res.getString("message"));
            }
        }
        return ;
    }
}
