package com.callback.moviebox.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public final class MovieBoxRepository {
  private static final String[] BASES={"https://h5-api.aoneroom.com/wefeed-h5api-bff/","https://movieboxph.org/wefeed-h5api-bff/"};
  private static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36";
  private final SharedPreferences prefs;
  public MovieBoxRepository(Context c){prefs=c.getSharedPreferences("moviebox_native",Context.MODE_PRIVATE);}
  public String token(){return prefs.getString("token","");}
  private void saveToken(String t){if(t!=null&&!t.trim().isEmpty())prefs.edit().putString("token",t.trim().replaceFirst("(?i)^Bearer\\s+","")).apply();}

  // Every token-protected operation obtains a new session from /home first.
  public JSONObject home() throws Exception {
    prefs.edit().remove("token").apply(); // never fall back to a previously cached session
    Exception last=null;
    for(String b:BASES) try {
      HttpURLConnection c=connect(b+"home","GET",null,"");
      String xUser=c.getHeaderField("x-user");
      JSONObject j=read(c);
      String t="";
      if(xUser!=null&&!xUser.trim().isEmpty()){
        String header=xUser.trim();
        if(header.startsWith("{")){
          try{t=new JSONObject(header).optString("token","");}catch(Exception ignored){}
        }else{
          t=header;
        }
      }
      if(t.trim().isEmpty())t=j.optString("token","");
      if(t.trim().isEmpty())throw new IOException("Fresh MovieBox token missing from /home response x-user header");
      saveToken(t);
      return j;
    } catch(Exception e){last=e;}
    throw last==null?new IOException("MovieBox Home unavailable"):last;
  }

  public JSONObject suggest(String q)throws Exception{
    return request("subject/search-suggest","POST",new JSONObject().put("keyword",q),"",false);
  }
  public JSONObject search(String q,int page)throws Exception{
    JSONObject body=new JSONObject().put("keyword",q).put("page",Math.max(1,page)).put("perPage",24);
    return authorizedRequest("subject/search","POST",body,false);
  }
  public JSONObject detail(String id)throws Exception{
    return authorizedRequest("detail?subjectId="+URLEncoder.encode(id,"UTF-8"),"GET",null,false);
  }
  public JSONObject play(String id,String detailPath,int se,int ep)throws Exception{
    String p="subject/play?subjectId="+URLEncoder.encode(id,"UTF-8")+"&se="+Math.max(0,se)+"&ep="+Math.max(0,ep);
    if(detailPath!=null&&!detailPath.isEmpty())p+="&detailPath="+URLEncoder.encode(detailPath,"UTF-8");
    home(); // Match the web TV app: obtain a fresh /home session before every play request.
    try{return request(p,"GET",null,token(),true,detailPath);}
    catch(Exception first){
      if(!isInvalidToken(first))throw first;
      home();
      return request(p,"GET",null,token(),true,detailPath);
    }
  }
  private JSONObject authorizedRequest(String path,String method,JSONObject body,boolean playback)throws Exception{
    home(); // fresh x-user token for this request, not a cached token
    try{return request(path,method,body,token(),playback);}
    catch(Exception first){
      if(!isInvalidToken(first))throw first;
      home(); // one fresh-session retry if the server invalidates the first token
      return request(path,method,body,token(),playback);
    }
  }
  private boolean isInvalidToken(Exception e){
    String m=String.valueOf(e.getMessage()).toLowerCase(java.util.Locale.ROOT);
    return m.contains("invalid token")||m.contains("params_error");
  }
  private JSONObject request(String path,String method,JSONObject body,String token,boolean playback)throws Exception{
    return request(path,method,body,token,playback,null);
  }
  private JSONObject request(String path,String method,JSONObject body,String token,boolean playback,String detailPath)throws Exception{
    String[] bases=playback?new String[]{BASES[1],BASES[0]}:BASES; Exception last=null;
    for(String b:bases)try{return read(connect(b+path,method,body,token,detailPath));}catch(Exception e){last=e;}
    throw last==null?new IOException("MovieBox request failed"):last;
  }
  private String playbackReferer(String detailPath){
    String value=detailPath==null?"":detailPath.trim();
    if(value.isEmpty())return "https://movieboxph.org/";
    if(value.startsWith("https://")||value.startsWith("http://"))return value;
    if(value.startsWith("/"))return "https://movieboxph.org"+value;
    if(value.regionMatches(true,0,"play/",0,5))value=value.substring(5);
    return "https://movieboxph.org/play/"+value;
  }
  private HttpURLConnection connect(String url,String method,JSONObject body,String token)throws Exception{
    return connect(url,method,body,token,null);
  }
  private HttpURLConnection connect(String url,String method,JSONObject body,String token,String detailPath)throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
    c.setRequestMethod(method);c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setUseCaches(false);
    c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Origin","https://movieboxph.org");
    c.setRequestProperty("Referer",playbackReferer(detailPath));c.setRequestProperty("Accept","application/json, text/plain, */*");
    c.setRequestProperty("Accept-Language","en-US,en;q=0.9");c.setRequestProperty("x-request-lang","en");
    c.setRequestProperty("x-client-info","{\"timezone\":\"Asia/Calcutta\"}");
    c.setRequestProperty("Cache-Control","no-cache");c.setRequestProperty("Pragma","no-cache");
    c.setRequestProperty("Accept-Encoding","identity");
    if(token!=null&&!token.isEmpty()){String t=token.replaceFirst("(?i)^Bearer\\s+","");c.setRequestProperty("Authorization","Bearer "+t);c.setRequestProperty("x-user",t);}
    if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream o=c.getOutputStream()){o.write(bytes);}}
    return c;
  }
  private JSONObject read(HttpURLConnection c)throws Exception{
    int code=c.getResponseCode();InputStream in=code>=200&&code<300?c.getInputStream():c.getErrorStream();
    StringBuilder s=new StringBuilder();if(in!=null)try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)s.append(l);}
    c.disconnect();if(code<200||code>=300)throw new IOException("MovieBox HTTP "+code+" "+s);
    JSONObject j=new JSONObject(s.toString());if(j.has("code")&&j.optInt("code")!=0)throw new IOException("MovieBox API "+j.optString("reason")+" "+j.optString("message","API error"));
    return j;
  }
}
