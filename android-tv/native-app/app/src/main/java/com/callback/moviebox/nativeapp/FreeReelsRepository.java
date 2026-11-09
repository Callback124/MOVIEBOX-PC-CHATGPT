package com.callback.moviebox.nativeapp;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.zip.GZIPInputStream;
import java.net.HttpURLConnection;

public class FreeReelsRepository {
 private static final String BASE="https://apiv2.free-reels.com";
 private static final String AUTH="oauth_signature=bf505aa6bb04c96350296bed13d0a920,oauth_token=fyqFvwztTYc5x8v8egEYrfa88WfQCL5D,ts=1789800192295";
 private JSONObject request(String path,String method,JSONObject body)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();
  c.setRequestMethod(method);c.setConnectTimeout(20000);c.setReadTimeout(25000);
  c.setRequestProperty("content-type","application/json; charset=UTF-8");c.setRequestProperty("accept","application/json");
  c.setRequestProperty("authorization",AUTH);c.setRequestProperty("country","IN");c.setRequestProperty("timezone","+5");
  c.setRequestProperty("language","en-US");c.setRequestProperty("device-country","US");c.setRequestProperty("app-display-lang","en-US");
  c.setRequestProperty("device-language","en-US");c.setRequestProperty("device-id","BC7C2318F4322CFFE00CBDE5043E5644");
  c.setRequestProperty("android-id","bc29b75f90824a5c");c.setRequestProperty("appsflyer-id","1789721594808-3992160409787450647");
  c.setRequestProperty("gaid","dfa2b45c-0134-49be-87b0-47203e73a707");c.setRequestProperty("app-name","com.freereels.app");
  c.setRequestProperty("app-version","2.4.80");c.setRequestProperty("device","android");c.setRequestProperty("user-agent","okhttp/4.12.0");
  c.setRequestProperty("Accept-Encoding","gzip");
  if(body!=null){c.setDoOutput(true);try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}}
  int status=c.getResponseCode();InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();
  if(in==null)throw new IOException("FreeReels HTTP "+status+" (empty response)");
  if("gzip".equalsIgnoreCase(c.getContentEncoding()))in=new GZIPInputStream(in);
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();c.disconnect();
  String raw=new String(out.toByteArray(),"UTF-8");JSONObject j=new JSONObject(raw);
  int apiCode=j.optInt("code",0);if(status<200||status>=300||(apiCode!=0&&apiCode!=200))throw new Exception("FreeReels API error HTTP "+status+" code "+apiCode+": "+j.optString("message",j.optString("msg",raw)));
  return j;
 }
 public JSONObject home()throws Exception{return request("/frv2-api/homepage/v2/tab/index?tab_key=503&position_index=10000&rec_trigger=1&user_new_theater=false&is_app_coldstart=false","GET",null);}
 public JSONObject homeFeed(String next,String moduleKey)throws Exception{JSONObject b=new JSONObject();b.put("next",next);b.put("module_key",moduleKey);b.put("user_new_theater",false);return request("/frv2-api/homepage/v2/tab/feed","POST",b);}
 public JSONObject suggestions(String keyword)throws Exception{JSONObject b=new JSONObject();b.put("keyword",keyword);return request("/frv2-api/search/keywords","POST",b);}
 public JSONObject search(String keyword,String next)throws Exception{JSONObject b=new JSONObject();b.put("keyword",keyword);b.put("audio_tab_key","mix");b.put("next",next==null?"":next);return request("/frv2-api/search/drama","POST",b);}
}