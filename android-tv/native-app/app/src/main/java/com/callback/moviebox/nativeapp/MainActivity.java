package com.callback.moviebox.nativeapp;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.bumptech.glide.Glide;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
 private final int BG=Color.rgb(12,12,16), PANEL=Color.rgb(25,25,32), MINT=Color.rgb(183,240,223), WHITE=Color.WHITE, MUTED=0xffa7a7b2;
 private MovieBoxRepository api; private LinearLayout root,content,top; private String detailPath=""; private JSONArray currentItems=new JSONArray();
 private final Handler handler=new Handler(Looper.getMainLooper());
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);api=new MovieBoxRepository(this);build();loadHome();}
 private GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(10);if(stroke!=0)d.setStroke(2,stroke);return d;}
 private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
 private void build(){
  root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);setContentView(root);
  top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(28,12,28,12);root.addView(top,new LinearLayout.LayoutParams(-1,64));
  TextView logo=text("MOVIEBOX",22,MINT,true);top.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
  addTop("⌂  HOME",()->loadHome());addTop("⌕  SEARCH",()->showSearch());addTop("♡  MY LIST",()->showSaved("mylist"));addTop("◷  HISTORY",()->showSaved("history"));addTop("⚙",()->showSettings());
  ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));content=new LinearLayout(this);content.setOrientation(1);content.setPadding(28,8,28,40);scroll.addView(content);
 }
 private void addTop(String label,Runnable action){TextView v=text(label,14,WHITE,true);v.setFocusable(true);v.setPadding(14,12,14,12);v.setBackground(shape(PANEL,0));v.setOnClickListener(w->action.run());top.addView(v,new LinearLayout.LayoutParams(-2,46));}
 private void runApi(java.util.concurrent.Callable<JSONObject> call, java.util.function.Consumer<JSONObject> done){content.removeAllViews();content.addView(text("Loading MovieBox…",20,MINT,true));new Thread(()->{try{JSONObject j=call.call();runOnUiThread(()->done.accept(j));}catch(Exception e){runOnUiThread(()->{content.removeAllViews();content.addView(text("Unable to load MovieBox",22,WHITE,true));content.addView(text(e.getMessage(),15,MUTED,false));TextView retry=text("RETRY",16,MINT,true);retry.setFocusable(true);retry.setOnClickListener(v->loadHome());content.addView(retry);});}}).start();}
 private void loadHome(){runApi(()->api.home(),j->{content.removeAllViews();JSONObject data=j.optJSONObject("data");if(data==null){content.addView(text("No MovieBox data received",20,WHITE,true));return;}JSONArray ops=data.optJSONArray("operatingList");if(ops==null)return;
  if(ops.length()>0){JSONObject first=ops.optJSONObject(0);JSONObject banner=first==null?null:first.optJSONObject("banner");JSONArray slides=banner==null?null:banner.optJSONArray("items");if(slides!=null&&slides.length()>0){JSONObject item=slides.optJSONObject(0);if(item!=null)addHero(item);}}
  for(int i=1;i<ops.length();i++){JSONObject op=ops.optJSONObject(i);if(op==null)continue;JSONArray a=op.optJSONArray("subjects");if(a!=null&&a.length()>0)addRow(op.optString("title",op.optString("name","Recommended")),a);}
  if(content.getChildCount()==0)content.addView(text("Home feed is empty.",20,WHITE,true));
 });}
 private String id(JSONObject o){JSONObject s=o.optJSONObject("subject");if(s!=null)o=merge(s,o);return o.optString("subjectId",o.optString("subject_id",o.optString("id","")));}
 private JSONObject merge(JSONObject a,JSONObject b){JSONObject n=new JSONObject();try{java.util.Iterator<String>it=a.keys();while(it.hasNext()){String k=it.next();n.put(k,a.get(k));}it=b.keys();while(it.hasNext()){String k=it.next();n.put(k,b.get(k));}}catch(Exception ignored){}return n;}
 private String title(JSONObject o){JSONObject s=o.optJSONObject("subject");if(s!=null)o=merge(s,o);return o.optString("title",o.optString("name",o.optString("subjectName","Untitled")));}
 private String poster(JSONObject o){JSONObject s=o.optJSONObject("subject");if(s!=null)o=merge(s,o);return o.optString("coverVertical",o.optString("verticalCover",o.optString("poster",o.optString("cover",o.optString("image",""))));}
 private String backdrop(JSONObject o){return o.optString("coverHorizontal",o.optString("horizontalCover",o.optString("banner",poster(o)));}
 private void addHero(JSONObject o){FrameLayout hero=new FrameLayout(this);hero.setBackground(shape(PANEL,0));content.addView(hero,new LinearLayout.LayoutParams(-1,300));ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);hero.addView(image,new FrameLayout.LayoutParams(-1,-1));String url=backdrop(o);if(!url.isEmpty())Glide.with(this).load(url).into(image);LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setPadding(24,24,24,24);info.setGravity(Gravity.BOTTOM);hero.addView(info,new FrameLayout.LayoutParams(-1,-1));info.addView(text(title(o),28,WHITE,true));TextView watch=text("▶  WATCH NOW",16,MINT,true);watch.setPadding(0,12,0,0);watch.setFocusable(true);watch.setOnClickListener(v->openDetail(id(o)));info.addView(watch);}
 private void addRow(String name,JSONArray items){content.addView(text(name,22,WHITE,true),new LinearLayout.LayoutParams(-1,48));HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setOrientation(0);hsv.addView(row);content.addView(hsv,new LinearLayout.LayoutParams(-1,240));for(int i=0;i<items.length();i++){JSONObject o=items.optJSONObject(i);if(o==null)continue;String p=poster(o);if(p.isEmpty())continue;LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(5,5,5,5);card.setFocusable(true);card.setBackground(shape(PANEL,0));ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(iv,new LinearLayout.LayoutParams(128,178));Glide.with(this).load(p).into(iv);TextView nameV=text(title(o),13,WHITE,true);nameV.setMaxLines(2);card.addView(nameV);card.setOnClickListener(v->openDetail(id(o)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(138,-1);lp.setMargins(0,0,12,0);row.addView(card,lp);}}
 private void showSearch(){content.removeAllViews();content.addView(text("Search Movies & Series",25,WHITE,true));EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setHint("Enter title…");input.setBackground(shape(PANEL,MINT));content.addView(input,new LinearLayout.LayoutParams(560,54));TextView go=text("SEARCH",16,MINT,true);go.setFocusable(true);content.addView(go);go.setOnClickListener(v->search(input.getText().toString()));input.setOnEditorActionListener((v,a,e)->{search(input.getText().toString());return true;});input.requestFocus();}
 private void search(String q){if(q.trim().isEmpty())return;runApi(()->api.search(q,1),j->{content.removeAllViews();content.addView(text("Results for: "+q,22,WHITE,true));JSONObject d=j.optJSONObject("data");JSONArray a=d==null?null:d.optJSONArray("subjects");if(a==null&&d!=null)a=d.optJSONArray("items");if(a!=null)addRow("Search results",a);});}
 private void openDetail(String subjectId){if(subjectId.isEmpty())return;runApi(()->api.detail(subjectId),j->{content.removeAllViews();JSONObject d=j.optJSONObject("data");if(d==null)d=new JSONObject();String name=title(d);detailPath=d.optString("detailPath",d.optString("url",""));addHero(d);content.addView(text(name,26,WHITE,true));content.addView(text(d.optString("description",d.optString("introduction",d.optString("overview",""))),16,MUTED,false));TextView play=text("▶  PLAY",18,MINT,true);play.setFocusable(true);content.addView(play);play.setOnClickListener(v->launchPlayer(subjectId,name,detailPath,0,0));JSONArray eps=d.optJSONArray("episodes");if(eps!=null)addRow("Episodes",eps);else {JSONObject se=d.optJSONObject("season");if(se!=null){JSONArray list=se.optJSONArray("episodes");if(list!=null)addRow("Episodes",list);}}});}
 private void launchPlayer(String id,String name,String path,int season,int episode){Intent i=new Intent(this,PlayerActivity.class);i.putExtra("movieBox",true);i.putExtra("subjectId",id);i.putExtra("detailPath",path);i.putExtra("token",api.token());i.putExtra("season",season);i.putExtra("episode",episode);i.putExtra("title",name);startActivity(i);}
 private void showSaved(String key){content.removeAllViews();content.addView(text(key.equals("mylist")?"My List":"Watch History",24,WHITE,true));content.addView(text("Saved items will appear here after playback is connected.",16,MUTED,false));}
 private void showSettings(){new AlertDialog.Builder(this).setTitle("Playback server").setMessage("MovieBox server is selected. Additional servers are not available in the current web app.").setPositiveButton("OK",null).show();}
 @Override public void onBackPressed(){loadHome();}
}
