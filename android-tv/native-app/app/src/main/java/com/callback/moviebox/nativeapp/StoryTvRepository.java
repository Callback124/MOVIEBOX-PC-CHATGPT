package com.callback.moviebox.nativeapp;

import org.json.JSONObject;
import org.json.JSONArray;
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
 public JSONObject search(String q)throws Exception{return search(q,0,20);}
 private boolean isIncompleteSearch(JSONObject response,int page,int requestedSize){
  JSONObject data=response==null?null:response.optJSONObject("data");if(data==null)return false;
  JSONArray content=data.optJSONArray("content");if(content==null)return false;
  int total=data.optInt("totalElements",-1);if(total<0)return false;
  int actualPage=data.optInt("number",page),actualSize=data.optInt("size",requestedSize);
  int expected=Math.min(Math.max(1,actualSize),Math.max(0,total-Math.max(0,actualPage)*Math.max(1,actualSize)));
  return content.length()<expected;
 }
 public JSONObject search(String q,int page,int size)throws Exception{
  String encoded=URLEncoder.encode(q==null?"":q.trim(),"UTF-8").replace("+","%20");
  String path="/searchservice/v1/search/"+encoded+"?page="+Math.max(0,page)+"&size="+Math.max(1,size);
  Exception last=null;
  for(int attempt=0;attempt<3;attempt++){
   try{
    JSONObject response=request(path,"GET",null);
    if(!isIncompleteSearch(response,Math.max(0,page),Math.max(1,size)))return response;
    last=new Exception("Story TV returned an incomplete search page; retrying");
   }catch(Exception e){last=e;}
   if(attempt<2)try{Thread.sleep(350L*(attempt+1));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw interrupted;}
  }
  if(last!=null)throw last;
  throw new Exception("Story TV search failed");
 }
}