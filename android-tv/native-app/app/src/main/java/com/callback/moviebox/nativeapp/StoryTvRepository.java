package com.callback.moviebox.nativeapp;

import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.*;
import java.net.URLEncoder;

public class StoryTvRepository {
 private static final String BASE="https://elitemods-tv.onrender.com";
 private JSONObject request(String path,String method,JSONObject body)throws Exception{
  HttpURLConnection c=(HttpURLConnection)new URL(BASE+path).openConnection();
  c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(20000);
  c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type","application/json");
  c.setRequestProperty("User-Agent","ktor-client");
  c.setRequestProperty("Authorization","Bearer eyJhbGciOiJIUzI1NiJ9.eyJjcmVhdGVkRGF0ZSI6IkZyaSBKdW4gMTIgMDc6MDQ6MTAgVVRDIDIwMjYiLCJzZXNzaW9uSWQiOiIxNTI0MDExNDMiLCJkZXZpY2VJZCI6IjI4MDhkNDliN2UwOGI3MTYiLCJleHAiOjE3ODE1MDcwNTB9._GKqF_5WtYkgAIJVhkt3L27t9fvgLkFtfgaOLSPsrOA");
  c.setRequestProperty("appVersion","62");c.setRequestProperty("os","Android 13 (API 33)");
  c.setRequestProperty("deviceId","a6229bad5c179d51");c.setRequestProperty("platform","0");
  c.setRequestProperty("iamsinu","iamsinu");c.setRequestProperty("network_type","WIFI");
  if(body!=null){c.setDoOutput(true);try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes("UTF-8"));}}
  int status=c.getResponseCode();InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
  String raw=new String(out.toByteArray(),"UTF-8");c.disconnect();
  JSONObject j=new JSONObject(raw);if(status<200||status>=300)throw new Exception("Story TV API HTTP "+status+": "+j.optString("message",raw));return j;
 }
 public JSONObject languages()throws Exception{return request("/userservice/v1/languages","GET",null);}
 public JSONObject selectLanguage(int langId)throws Exception{JSONObject b=new JSONObject();b.put("langId",langId);return request("/userservice/v1/language/select?langId="+langId,"POST",b);}
 public JSONObject explore(int page,int size)throws Exception{return explore(page,size,2);}
 public JSONObject explore(int page,int size,int langId)throws Exception{return request("/feedservice/v2/explore/shows?page="+page+"&size="+size+"&intCount=0&langId="+langId,"GET",null);}
 public JSONObject episodeList(String showId)throws Exception{return request("/feedservice/v1/episode/list/"+URLEncoder.encode(showId,"UTF-8"),"GET",null);}
 public JSONObject episodeMetadata(String showId,Integer cursor)throws Exception{String path="/feedservice/v1/episode/metadata/"+URLEncoder.encode(showId,"UTF-8");if(cursor!=null)path+="?cursor="+cursor+"&dir=NEXT";return request(path,"GET",null);}
 public JSONObject search(String q)throws Exception{return request("/searchservice/v1/search/"+URLEncoder.encode(q,"UTF-8"),"GET",null);}
}