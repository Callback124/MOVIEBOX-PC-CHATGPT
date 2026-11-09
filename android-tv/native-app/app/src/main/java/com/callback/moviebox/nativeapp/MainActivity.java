package com.callback.moviebox.nativeapp;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.text.TextUtils;
import com.bumptech.glide.Glide;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
 private final int BG=0xff141414, PANEL=0xff181818, WHITE=0xffffffff, MUTED=0xffc5c5c5, RED=0xffe50914;
 private MovieBoxRepository api; private StoryTvRepository storyApi; private FreeReelsRepository freeReelsApi;
 private FrameLayout root; private LinearLayout content, top; private final ArrayList<View> heroDots=new ArrayList<>();
 private ScrollView pageScroll;
 private JSONArray slides=new JSONArray();
 private int slideIndex=0;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private Runnable slideTask;
 private TextView heroTitle,heroMeta,heroDesc;
 private ImageView heroImage;
 private FrameLayout heroFrame;
 private static final String PREFS="moviebox_native_library";
 private String detailPath="";
 private ArrayList<View> previousPageViews=null; private int previousScrollY=0; private boolean previousTopVisible=true; private ArrayList<View> homePageViews=null; private String activeSearchKeyword=""; private int nextSearchPage=1; private boolean searchLoading=false,searchHasMore=false; private LinearLayout searchGrid=null,searchCurrentRow=null; private int searchRowItems=0; private final HashSet<String> storySearchSeen=new HashSet<>();
 private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(1024,1024);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512);api=new MovieBoxRepository(this);storyApi=new StoryTvRepository();freeReelsApi=new FreeReelsRepository();buildShell();if(getSelectedServer()==3)showStoryTvHome();else if(getSelectedServer()==4)showFreeReelsHome();else loadHome();}
 private GradientDrawable bg(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(3));if(stroke!=0)d.setStroke(dp(2),stroke);return d;}
 private TextView txt(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);return t;}
 private void buildShell(){
  root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);
  pageScroll=new ScrollView(this);pageScroll.setFillViewport(false);pageScroll.setVerticalScrollBarEnabled(false);
  root.addView(pageScroll,new FrameLayout.LayoutParams(-1,-1));
  content=new LinearLayout(this);content.setOrientation(1);content.setPadding(0,0,0,dp(55));pageScroll.addView(content);
  top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(42),dp(8),dp(42),dp(8));top.setBackgroundColor(0x00000000);
  FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,dp(66),Gravity.TOP);root.addView(top,topLp);
  TextView logo=txt("MOVIEBOX",25,RED,true);logo.setLetterSpacing(.06f);top.addView(logo,new LinearLayout.LayoutParams(dp(190),-2));
  View spacer=new View(this);top.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
  iconNav("search",()->showSearch());iconNav("list",()->showSaved("mylist"));iconNav("history",()->showSaved("history"));iconNav("settings",()->showSettings());
 }
 private void iconNav(String kind,Runnable action){FrameLayout tile=new FrameLayout(this);tile.setFocusable(true);tile.setClickable(true);tile.setContentDescription(kind);tile.setBackground(iconTile(false));IconView icon=new IconView(kind);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER);tile.addView(icon,ip);tile.setOnClickListener(w->action.run());tile.setOnFocusChangeListener((w,f)->{tile.setBackground(iconTile(f));tile.animate().scaleX(f?1.04f:1f).scaleY(f?1.04f:1f).setDuration(110).start();tile.setElevation(f?dp(8):0);icon.invalidate();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(44),dp(44));p.leftMargin=dp(7);top.addView(tile,p);}
 private void runApi(java.util.concurrent.Callable<JSONObject> call,java.util.function.Consumer<JSONObject> done){
  content.removeAllViews();FrameLayout loading=new FrameLayout(this);content.addView(loading,new LinearLayout.LayoutParams(-1,Math.max(dp(300),getResources().getDisplayMetrics().heightPixels-dp(66))));
  ProgressBar spinner=new ProgressBar(this);spinner.setIndeterminate(true);if(android.os.Build.VERSION.SDK_INT>=21)spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(RED));loading.addView(spinner,new FrameLayout.LayoutParams(dp(54),dp(54),Gravity.CENTER));
  long started=System.currentTimeMillis();new Thread(()->{try{JSONObject j=call.call();long wait=Math.max(0,950-(System.currentTimeMillis()-started));runOnUiThread(()->handler.postDelayed(()->done.accept(j),wait));}catch(Exception e){runOnUiThread(()->showError(e));}}).start();
 }
 private void showError(Exception e){content.removeAllViews();TextView t=txt("MovieBox could not be loaded",24,WHITE,true);t.setPadding(dp(50),dp(50),0,dp(12));content.addView(t);TextView m=txt(String.valueOf(e.getMessage()),15,MUTED,false);m.setPadding(dp(50),0,dp(50),dp(20));content.addView(m);focusText("RETRY",()->loadHome());}
 private JSONObject merged(JSONObject o){JSONObject s=o==null?null:o.optJSONObject("subject");if(s==null)return o==null?new JSONObject():o;JSONObject n=new JSONObject();try{Iterator<String>it=s.keys();while(it.hasNext()){String k=it.next();n.put(k,s.get(k));}it=o.keys();while(it.hasNext()){String k=it.next();n.put(k,o.get(k));}}catch(Exception ignored){}return n;}
 private String id(JSONObject raw){JSONObject o=merged(raw);return o.optString("subjectId",o.optString("subject_id",o.optString("id","")));}
 private String title(JSONObject raw){JSONObject o=merged(raw);return o.optString("title",o.optString("name",o.optString("subjectName",o.optString("originalTitle","Untitled"))));}
 private String poster(JSONObject raw){JSONObject o=merged(raw);return pick(o,"coverVertical","verticalCover","poster","posterUrl","cover","coverUrl","imageUrl","image","thumbnail","pic","url","src");}
 private String backdrop(JSONObject raw){JSONObject o=merged(raw);String u=pick(raw,"image","banner","bannerImage","coverHorizontal","horizontalCover","backdrop","backdropUrl","url","src");if(u.isEmpty())u=pick(o,"coverHorizontal","horizontalCover","backdrop","backdropUrl","stills","cover","image","url","src");if(u.isEmpty())u=poster(raw);return u;}
 private String pick(JSONObject o,String...keys){if(o==null)return "";for(String k:keys){Object value=o.opt(k);String u=imageUrl(value);if(!u.isEmpty())return u;}return "";}
 private String imageUrl(Object value){if(value==null||value==JSONObject.NULL)return "";if(value instanceof String){String v=((String)value).trim();if(v.startsWith("//"))return "https:"+v;if(v.startsWith("http://")||v.startsWith("https://"))return v;return "";}if(value instanceof JSONObject){JSONObject j=(JSONObject)value;String[] keys={"url","src","imageUrl","fileUrl","path","original","large","medium","small","cover","image"};for(String k:keys){String u=imageUrl(j.opt(k));if(!u.isEmpty())return u;}}if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++){String u=imageUrl(a.opt(i));if(!u.isEmpty())return u;}}return "";}
 private String year(JSONObject raw){String d=merged(raw).optString("releaseDate",merged(raw).optString("release_date",merged(raw).optString("year","")));java.util.regex.Matcher m=java.util.regex.Pattern.compile("(19|20)\\d{2}").matcher(d);return m.find()?m.group():"";}
 private String description(JSONObject raw){JSONObject o=merged(raw);return o.optString("description",o.optString("introduction",o.optString("overview","")));}
 private void loadHome(){top.setVisibility(View.VISIBLE);if(top.getChildAt(0) instanceof TextView)((TextView)top.getChildAt(0)).setText("MOVIEBOX");if(slideTask!=null)handler.removeCallbacks(slideTask);if(getSelectedServer()==3){showStoryTvHome();return;}if(getSelectedServer()==4){showFreeReelsHome();return;}if(getSelectedServer()!=0){showComingSoonServer();return;}runApi(()->api.home(),j->{content.removeAllViews();JSONObject data=j.optJSONObject("data");JSONArray ops=data==null?null:data.optJSONArray("operatingList");if(ops==null){showError(new Exception("Home feed missing operatingList"));return;}slides=new JSONArray();for(int oi=0;oi<ops.length();oi++){JSONObject op=ops.optJSONObject(oi);if(op==null)continue;JSONObject banner=op.optJSONObject("banner");JSONArray raw=banner==null?null:banner.optJSONArray("items");if(raw==null)raw=op.optJSONArray("bannerItems");if(raw==null)raw=op.optJSONArray("banners");if(raw!=null)for(int k=0;k<raw.length()&&slides.length()<7;k++){JSONObject item=raw.optJSONObject(k);if(item!=null&&(!backdrop(item).isEmpty()||!poster(item).isEmpty()))slides.put(item);}if(slides.length()>=7)break;}if(slides.length()>0)addHero();for(int i=0;i<ops.length();i++){JSONObject op=ops.optJSONObject(i);if(op==null)continue;JSONArray a=op.optJSONArray("subjects");if(a==null||a.length()==0)continue;String heading=op.optString("title",op.optString("name",op.optString("subjectTitle",op.optString("label","Recommended"))));addHomeSection(heading,a);}if(content.getChildCount()==0)content.addView(txt("No MovieBox titles available.",20,WHITE,true));});}
 private void addHero(){slideIndex=0;heroDots.clear();heroFrame=new FrameLayout(this);heroFrame.setBackgroundColor(PANEL);content.addView(heroFrame,new LinearLayout.LayoutParams(-1,dp(500)));heroImage=new ImageView(this);heroImage.setScaleType(ImageView.ScaleType.CENTER_CROP);heroFrame.addView(heroImage,new FrameLayout.LayoutParams(-1,-1));LinearLayout bottomShade=new LinearLayout(this);bottomShade.setOrientation(1);bottomShade.setPadding(dp(54),dp(76),dp(54),dp(42));bottomShade.setGravity(Gravity.BOTTOM);bottomShade.setBackgroundColor(0x00000000);heroFrame.addView(bottomShade,new FrameLayout.LayoutParams(-1,-1));heroTitle=txt("",38,WHITE,true);heroTitle.setMaxLines(2);heroTitle.setEllipsize(TextUtils.TruncateAt.END);bottomShade.addView(heroTitle);heroMeta=txt("",14,0xff46d369,true);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(10);bottomShade.addView(heroMeta,mp);heroDesc=txt("",16,WHITE,false);heroDesc.setMaxLines(3);heroDesc.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(dp(610),-2);dpv.topMargin=dp(10);bottomShade.addView(heroDesc,dpv);LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(52));ap.topMargin=dp(18);bottomShade.addView(actions,ap);TextView watch=button("▶  WATCH",true,()->{JSONObject o=slides.optJSONObject(slideIndex);if(o!=null)openDetail(id(o));});actions.addView(watch,new LinearLayout.LayoutParams(dp(170),dp(48)));TextView more=button("ⓘ  MORE INFO",false,()->{JSONObject o=slides.optJSONObject(slideIndex);if(o!=null)openDetail(id(o));});LinearLayout.LayoutParams moreLp=new LinearLayout.LayoutParams(dp(180),dp(48));moreLp.leftMargin=dp(12);actions.addView(more,moreLp);LinearLayout dots=new LinearLayout(this);dots.setGravity(Gravity.CENTER);FrameLayout.LayoutParams dotLp=new FrameLayout.LayoutParams(-2,dp(38),Gravity.RIGHT|Gravity.BOTTOM);dotLp.setMargins(0,0,dp(48),dp(22));heroFrame.addView(dots,dotLp);for(int i=0;i<slides.length();i++){final int ix=i;TextView dot=txt("●",12,i==0?WHITE:0xffaaaaaa,true);dot.setGravity(Gravity.CENTER);dot.setFocusable(true);dot.setFocusableInTouchMode(false);dot.setBackground(bg(i==0?RED:0x66000000,0));dot.setOnClickListener(v->setSlide(ix));dot.setOnFocusChangeListener((v,f)->{if(f)setSlide(ix);dot.setBackground(bg(f?0xff555555:(slideIndex==ix?RED:0x66000000),f?WHITE:0));});dot.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_LEFT){setSlide(Math.max(0,ix-1));heroDots.get(Math.max(0,ix-1)).requestFocus();return true;}if(key==KeyEvent.KEYCODE_DPAD_RIGHT){setSlide(Math.min(heroDots.size()-1,ix+1));heroDots.get(Math.min(heroDots.size()-1,ix+1)).requestFocus();return true;}return false;});heroDots.add(dot);LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(dp(26),dp(26));dlp.leftMargin=dp(5);dots.addView(dot,dlp);}setSlide(0);slideTask=()->{if(slides.length()>1){setSlide((slideIndex+1)%slides.length());handler.postDelayed(slideTask,7000);}};if(slides.length()>1)handler.postDelayed(slideTask,7000);heroFrame.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==KeyEvent.KEYCODE_DPAD_LEFT){setSlide(Math.max(0,slideIndex-1));return true;}if(key==KeyEvent.KEYCODE_DPAD_RIGHT){setSlide(Math.min(slides.length()-1,slideIndex+1));return true;}return false;});}
 private GradientDrawable iconTile(boolean focused){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{focused?0xff3b1116:0xff20090c,focused?0xff160608:0xff100507});d.setCornerRadius(dp(9));d.setStroke(dp(focused?2:1),focused?0xffff626b:0xff641b23);return d;}
 private class IconView extends View {
  private final String kind; private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  IconView(String k){super(MainActivity.this);kind=k;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  private void line(Canvas c,float x1,float y1,float x2,float y2){c.drawLine(x1,y1,x2,y2,p);}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float z=Math.min(getWidth(),getHeight())/48f;c.save();c.translate((getWidth()-48*z)/2f,(getHeight()-48*z)/2f);c.scale(z,z);
   p.setColor(0xffff626b);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setShadowLayer(3f,0,0,0x99ff2938);
   if(kind.equals("search")){c.drawCircle(20,19,10.5f,p);line(c,28,27,37,36);}
   else if(kind.equals("list")){Path b=new Path();b.moveTo(14,8);b.lineTo(34,8);b.lineTo(34,39);b.lineTo(24,31);b.lineTo(14,39);b.close();c.drawPath(b,p);}
   else if(kind.equals("history")){c.drawArc(12,12,38,38,-58,300,false,p);Path a=new Path();a.moveTo(8,10);a.lineTo(8,20);a.lineTo(18,20);c.drawPath(a,p);line(c,25,17,25,25);line(c,25,25,31,29);c.drawCircle(25,25,1.5f,p);}
   else{Path gear=new Path();for(int i=0;i<32;i++){double a=-Math.PI/2+i*Math.PI/16;float r=(i%4==0||i%4==1)?18:14.5f;float x=24+(float)Math.cos(a)*r,y=24+(float)Math.sin(a)*r;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();c.drawPath(gear,p);c.drawCircle(24,24,5.5f,p);}
   p.clearShadowLayer();c.restore();
  }
 }
 private TextView button(String label,boolean primary,Runnable run){TextView b=txt(label,16,primary?0xff000000:WHITE,true);b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setBackground(bg(primary?WHITE:0x99555555,0));b.setOnFocusChangeListener((v,f)->b.setBackground(bg(f?0xffdddddd:(primary?WHITE:0x99555555),f?RED:0)));b.setOnClickListener(v->run.run());return b;}
 private void preloadHeroImages(){if(isFinishing()||slides.length()<2)return;int w=Math.max(1,getResources().getDisplayMetrics().widthPixels),h=dp(500);for(int n=1;n<Math.min(3,slides.length());n++){JSONObject next=slides.optJSONObject((slideIndex+n)%slides.length());if(next==null)continue;String u=backdrop(next);if(u.isEmpty())u=poster(next);if(!u.isEmpty())Glide.with(this).load(u).override(w,h).centerCrop().priority(com.bumptech.glide.Priority.LOW).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).preload();}}
 private void setSlide(int i){if(slides.length()==0)return;slideIndex=Math.max(0,Math.min(i,slides.length()-1));for(int k=0;k<heroDots.size();k++){TextView d=(TextView)heroDots.get(k);d.setTextColor(k==slideIndex?WHITE:0xffaaaaaa);d.setBackground(bg(k==slideIndex?RED:0x66000000,d.hasFocus()?WHITE:0));}JSONObject o=slides.optJSONObject(slideIndex);String image=backdrop(o);if(image.isEmpty())image=poster(o);heroImage.setBackgroundColor(0xff171014);int hw=Math.max(1,getResources().getDisplayMetrics().widthPixels);int hh=dp(500);Glide.with(this).load(image).override(hw,hh).centerCrop().dontAnimate().priority(com.bumptech.glide.Priority.IMMEDIATE).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(heroImage);preloadHeroImages();heroTitle.setText(title(o));String y=year(o),rating=merged(o).optString("imdbRatingValue","");heroMeta.setText((rating.isEmpty()?"Featured":rating+" ★")+"    "+(y.isEmpty()?"":y+"    ")+"HD");heroDesc.setText(description(o));}
 private void addSection(String heading,JSONArray items){TextView h=txt(heading,20,0xffe5e5e5,true);h.setPadding(dp(24),dp(24),dp(24),dp(8));content.addView(h);int count=items.length(),cols=10;int screenDp=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);int horizontal=dp(48),gap=dp(8);int cardW=Math.max(dp(72),(getResources().getDisplayMetrics().widthPixels-horizontal-gap*(cols-1))/cols);int rows=(count+cols-1)/cols;int index=0;for(int r=0;r<rows;r++){LinearLayout line=new LinearLayout(this);line.setOrientation(0);line.setGravity(Gravity.TOP);line.setPadding(dp(24),dp(8),dp(24),dp(8));content.addView(line,new LinearLayout.LayoutParams(-1,-2));for(int c=0;c<cols&&index<count;c++,index++){JSONObject o=items.optJSONObject(index);if(o!=null)addCard(line,o,cardW,gap);}}}
 private void addHomeSection(String heading,JSONArray items){TextView h=txt(heading,20,0xffe5e5e5,true);h.setPadding(dp(24),dp(24),dp(24),dp(8));content.addView(h);int count=items.length(),cols=6;int horizontal=dp(48),gap=dp(8);int cardW=Math.max(dp(72),(getResources().getDisplayMetrics().widthPixels-horizontal-gap*(cols-1))/cols);int rows=(count+cols-1)/cols;int index=0;for(int r=0;r<rows;r++){LinearLayout line=new LinearLayout(this);line.setOrientation(0);line.setGravity(Gravity.TOP);line.setPadding(dp(24),dp(8),dp(24),dp(8));content.addView(line,new LinearLayout.LayoutParams(-1,-2));for(int c=0;c<cols&&index<count;c++,index++){JSONObject o=items.optJSONObject(index);if(o!=null)addCard(line,o,cardW,gap);}}}
 private void addCard(LinearLayout line,JSONObject o,int width,int gap){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setFocusable(true);card.setFocusableInTouchMode(false);card.setClickable(true);card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);card.setPadding(dp(2),dp(2),dp(2),dp(4));card.setBackground(bg(PANEL,0));card.setClipChildren(false);card.setClipToPadding(false);ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(0xff252525);card.addView(image,new LinearLayout.LayoutParams(width,(int)(width*1.5f)));String p=poster(o);if(!p.isEmpty())Glide.with(this).load(p).override(width*2,(int)(width*3)).placeholder(new android.graphics.drawable.ColorDrawable(0xff252525)).error(new android.graphics.drawable.ColorDrawable(0xff333333)).centerCrop().dontAnimate().priority(com.bumptech.glide.Priority.HIGH).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.AUTOMATIC).into(image);else{TextView fallback=txt("NO IMAGE",10,MUTED,true);fallback.setGravity(Gravity.CENTER);card.addView(fallback,new LinearLayout.LayoutParams(width,(int)(width*1.5f)));}TextView name=txt(title(o),12,WHITE,true);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(3),dp(5),dp(3),0);card.addView(name);TextView yr=txt(year(o),10,MUTED,false);yr.setPadding(dp(3),0,dp(3),0);card.addView(yr);card.setOnFocusChangeListener((v,f)->{card.animate().cancel();card.animate().scaleX(f?1.07f:1f).scaleY(f?1.07f:1f).setDuration(90).start();card.setElevation(f?dp(12):0);card.setBackground(bg(f?0xff303030:PANEL,f?RED:0));});card.setOnHoverListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_HOVER_ENTER&&!card.hasFocus())card.requestFocus();return false;});card.setOnClickListener(v->{if(getSelectedServer()==3)openStoryDetail(o,o);else openDetail(id(o));});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,-2);lp.rightMargin=gap;line.addView(card,lp);}
 private void focusText(String s,Runnable r){TextView v=txt(s,16,RED,true);v.setPadding(dp(50),dp(18),dp(50),dp(18));v.setFocusable(true);v.setOnClickListener(w->r.run());content.addView(v);}
 private Handler searchHandler=new Handler(Looper.getMainLooper()); private Runnable suggestionTask; private int suggestionRequest=0;
 private JSONArray searchArray(JSONObject response){JSONObject d=response==null?null:response.optJSONObject("data");if(d==null)return new JSONArray();JSONArray a=d.optJSONArray("items");if(a==null)a=d.optJSONArray("list");if(a==null)a=d.optJSONArray("subjects");return a==null?new JSONArray():a;}
 private JSONArray storySearchArray(JSONObject response){JSONObject d=response==null?null:response.optJSONObject("data");JSONArray a=d==null?null:d.optJSONArray("content");return a==null?new JSONArray():a;}
 private JSONObject storySearchItem(JSONObject source){JSONObject o=new JSONObject();try{o.put("id",source.optString("id"));o.put("title",source.optString("title"));o.put("imageUrl",source.optString("imageUrl"));o.put("numOfEpisodes",source.optInt("numOfEpisodes"));JSONObject meta=source.optJSONObject("metadata");o.put("genre",meta==null?"Drama":meta.optString("genre","Drama"));o.put("subjectType",7);o.put("source","storytv");}catch(Exception ignored){}return o;}
 private void showSearch(){
  if(homePageViews==null){homePageViews=new ArrayList<>();for(int i=0;i<content.getChildCount();i++)homePageViews.add(content.getChildAt(i));}
  top.setVisibility(View.GONE);content.removeAllViews();
  final boolean storySearch=getSelectedServer()==3;final boolean freeReelsSearch=getSelectedServer()==4;
  TextView h=txt(storySearch?"Search Story TV Dramas":freeReelsSearch?"Search Free Reels Dramas":"Search Movies & Series",28,WHITE,true);h.setPadding(dp(50),dp(35),0,dp(20));content.addView(h);
  EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(WHITE);input.setHintTextColor(MUTED);input.setHint(storySearch?"Search Story TV…":freeReelsSearch?"Search Free Reels…":"Search MovieBox…");input.setTextSize(18);input.setPadding(dp(18),0,dp(18),0);input.setBackground(bg(PANEL,0xff555555));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(720),dp(58));ip.leftMargin=dp(50);content.addView(input,ip);
  LinearLayout suggestions=new LinearLayout(this);suggestions.setOrientation(1);suggestions.setPadding(dp(50),dp(6),dp(50),dp(8));content.addView(suggestions,new LinearLayout.LayoutParams(dp(820),-2));
   LinearLayout recentHead=new LinearLayout(this);recentHead.setGravity(Gravity.CENTER_VERTICAL);recentHead.setPadding(dp(50),dp(18),dp(30),dp(10));content.addView(recentHead,new LinearLayout.LayoutParams(-1,-2));
   TextView recentTitle=txt("Recent",20,WHITE,true);recentHead.addView(recentTitle,new LinearLayout.LayoutParams(0,-2,1));
   TextView clearSearch=txt("Clear",14,WHITE,true);clearSearch.setGravity(Gravity.CENTER);clearSearch.setPadding(dp(18),dp(9),dp(18),dp(9));clearSearch.setBackground(bg(PANEL,dp(9)));clearSearch.setFocusable(true);clearSearch.setOnFocusChangeListener((v,focus)->clearSearch.setBackground(bg(focus?0xff444444:PANEL,dp(9))));recentHead.addView(clearSearch);clearSearch.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Clear search history?").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(scopedKey("search_history"),"[]").apply();showSearch();}).show());
   LinearLayout recentChips=new LinearLayout(this);recentChips.setPadding(dp(50),0,dp(30),dp(12));content.addView(recentChips,new LinearLayout.LayoutParams(-1,-2));
   JSONArray recent=readCollection("search_history");for(int ri=0;ri<recent.length();ri++){String word=recent.optString(ri,"");if(word.isEmpty())continue;TextView chip=txt(word,15,WHITE,false);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(18),dp(10),dp(18),dp(10));chip.setBackground(bg(PANEL,0));chip.setFocusable(true);chip.setFocusableInTouchMode(false);chip.setOnFocusChangeListener((v,focus)->chip.setBackground(bg(focus?0xff3b171a:PANEL,focus?RED:0)));chip.setOnClickListener(v->{input.setText(word);input.setSelection(word.length());search(word,1);});LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(46));cp.rightMargin=dp(8);recentChips.addView(chip,cp);}
  TextView hint=txt("Type at least 2 characters for suggestions.",13,MUTED,false);hint.setPadding(dp(50),dp(10),0,0);content.addView(hint);
  input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int st,int c,int f){}public void onTextChanged(CharSequence x,int st,int before,int count){
   if(suggestionTask!=null)searchHandler.removeCallbacks(suggestionTask);final String q=x.toString().trim();final int request=++suggestionRequest;suggestions.removeAllViews();if(q.length()<2)return;
   suggestionTask=()->{new Thread(()->{try{JSONObject response=storySearch?storyApi.search(q):freeReelsSearch?freeReelsApi.suggestions(q):api.suggest(q);JSONArray raw=storySearch?storySearchArray(response):freeReelsSearch?freeReelsKeywords(response):searchArray(response);ArrayList<String> words=new ArrayList<>();HashSet<String> unique=new HashSet<>();for(int i=0;i<raw.length()&&words.size()<8;i++){Object item=raw.opt(i);String word=item instanceof String?(String)item:item instanceof JSONObject?((JSONObject)item).optString("keyword",((JSONObject)item).optString("word",((JSONObject)item).optString("title",""))):"";if(!word.trim().isEmpty()&&unique.add(word.trim().toLowerCase()))words.add(word.trim());if(storySearch&&item instanceof JSONObject){JSONArray tags=((JSONObject)item).optJSONObject("metadata")==null?null:((JSONObject)item).optJSONObject("metadata").optJSONArray("tags");if(tags!=null)for(int k=0;k<tags.length()&&words.size()<8;k++){String tag=tags.optString(k,"").trim();if(!tag.isEmpty()&&unique.add(tag.toLowerCase()))words.add(tag);}}}runOnUiThread(()->{if(request!=suggestionRequest||!input.getText().toString().trim().equals(q))return;suggestions.removeAllViews();for(String word:words){TextView option=txt(word,16,WHITE,false);option.setGravity(Gravity.CENTER_VERTICAL);option.setPadding(dp(14),dp(10),dp(14),dp(10));option.setFocusable(true);option.setFocusableInTouchMode(false);option.setBackground(bg(PANEL,0));option.setOnFocusChangeListener((v,focus)->option.setBackground(bg(focus?0xff3b171a:PANEL,focus?RED:0)));option.setOnClickListener(v->{input.setText(word);input.setSelection(word.length());suggestionRequest++;suggestions.removeAllViews();search(word,1);});suggestions.addView(option,new LinearLayout.LayoutParams(-1,dp(44)));}if(words.isEmpty()){TextView none=txt("No suggestions found",13,MUTED,false);suggestions.addView(none);}});}catch(Exception e){runOnUiThread(()->{if(request==suggestionRequest){suggestions.removeAllViews();TextView err=txt("Suggestions unavailable: "+e.getMessage(),12,MUTED,false);suggestions.addView(err);}});}}).start();};
   searchHandler.postDelayed(suggestionTask,250);
  }public void afterTextChanged(android.text.Editable e){}});
  input.setOnEditorActionListener((v,action,event)->{String q=input.getText().toString().trim();if(!q.isEmpty()){if(suggestionTask!=null)searchHandler.removeCallbacks(suggestionTask);suggestionRequest++;search(q,1);}return true;});
  input.setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN&&suggestions.getChildCount()>0){suggestions.getChildAt(0).requestFocus();return true;}return false;});
  input.requestFocus();
 }
 private void search(String q){search(q,1);}
 private void search(String q,int page){
  if(getSelectedServer()==4){freeReelsSearch(q,page);return;}
  if(q==null||q.trim().isEmpty())return;
  final String keyword=q.trim();
  if(page==1){JSONArray old=readCollection("search_history"),next=new JSONArray();next.put(keyword);for(int si=0;si<old.length();si++){String prior=old.optString(si,"");if(!prior.equalsIgnoreCase(keyword)&&!prior.isEmpty()&&next.length()<12)next.put(prior);}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(scopedKey("search_history"),next.toString()).apply();}
  if(page==1){
   activeSearchKeyword=keyword;nextSearchPage=1;searchLoading=true;searchHasMore=false;searchGrid=null;searchCurrentRow=null;searchRowItems=0;storySearchSeen.clear();
   content.removeAllViews();TextView loading=txt(getSelectedServer()==3?"Searching Story TV…":"Searching MovieBox…",16,MUTED,false);loading.setPadding(dp(48),dp(28),0,dp(12));content.addView(loading);
  }else{if(searchLoading||!searchHasMore)return;searchLoading=true;}
  final int requestedPage=page;
  new Thread(()->{try{
   boolean storyMode=getSelectedServer()==3;JSONObject response=storyMode?storyApi.search(keyword,requestedPage,50):api.search(keyword,requestedPage);JSONArray items;if(storyMode){JSONArray raw=storySearchArray(response);items=new JSONArray();for(int i=0;i<raw.length();i++)items.put(storySearchItem(raw.optJSONObject(i)));}else items=searchArray(response);
   JSONObject data=response.optJSONObject("data");JSONObject pager=data==null?null:data.optJSONObject("pager");
   boolean more;
   if(storyMode){
    boolean hasMoreFlag=data!=null&&(data.has("hasMore")||data.has("has_more"));
    more=pager!=null?pager.optBoolean("hasMore",requestedPage<pager.optInt("totalPages",requestedPage)):
      hasMoreFlag?(data.optBoolean("hasMore",data.optBoolean("has_more",false))):
      items.length()>=50;
   }else more=pager!=null?pager.optBoolean("hasMore",requestedPage<pager.optInt("totalPages",requestedPage)):items.length()>=24;
   runOnUiThread(()->{
    if(!keyword.equals(activeSearchKeyword))return;
    if(requestedPage==1){content.removeAllViews();searchGrid=new LinearLayout(this);searchGrid.setOrientation(1);searchGrid.setPadding(0,dp(8),0,dp(20));content.addView(searchGrid,new LinearLayout.LayoutParams(-1,-2));}
    if(items.length()==0&&requestedPage==1){TextView empty=txt("No results found for “"+keyword+"”.",17,MUTED,false);empty.setPadding(dp(48),dp(30),0,0);content.addView(empty);searchHasMore=false;searchLoading=false;return;}
    appendSearchItems(items);searchHasMore=more;nextSearchPage=requestedPage+1;searchLoading=false;
    pageScroll.setOnScrollChangeListener((View v,int sx,int sy,int osx,int osy)->{
     if(activeSearchKeyword.isEmpty()||!searchHasMore||searchLoading)return;
     View child=pageScroll.getChildAt(0);if(child!=null&&child.getBottom()-sy-pageScroll.getHeight()<dp(650))search(activeSearchKeyword,nextSearchPage);
    });
   });
  }catch(Exception e){runOnUiThread(()->{searchLoading=false;if(requestedPage==1)content.removeAllViews();TextView error=txt("Search failed: "+e.getMessage(),16,WHITE,false);error.setPadding(dp(48),dp(28),dp(48),dp(12));content.addView(error);focusText("RETRY SEARCH",()->search(keyword,requestedPage));});}}).start();
 }
 private void appendSearchItems(JSONArray items){
  if(searchGrid==null){searchGrid=new LinearLayout(this);searchGrid.setOrientation(1);content.addView(searchGrid,new LinearLayout.LayoutParams(-1,-2));}
  int cols=6;int gap=dp(8);int horizontal=dp(48);int cardW=Math.max(dp(42),(getResources().getDisplayMetrics().widthPixels-horizontal-gap*(cols-1))/cols);
  for(int i=0;i<items.length();i++){
   JSONObject item=items.optJSONObject(i);if(item==null)continue;
   if("storytv".equals(item.optString("source"))){
    String unique=item.optString("id",item.optString("title","")).trim();
    if(unique.isEmpty()||!storySearchSeen.add(unique))continue;
   }
   if(searchCurrentRow==null||searchRowItems>=cols){
    searchCurrentRow=new LinearLayout(this);searchCurrentRow.setOrientation(0);searchCurrentRow.setGravity(Gravity.TOP);searchCurrentRow.setPadding(dp(24),dp(8),dp(24),dp(8));
    searchGrid.addView(searchCurrentRow,new LinearLayout.LayoutParams(-1,-2));searchRowItems=0;
   }
   addCard(searchCurrentRow,item,cardW,gap);searchRowItems++;
  }
 }
 private int contentType(JSONObject raw){
  JSONObject o=merged(raw);int type=o.optInt("subjectType",o.optInt("subject_type",o.optInt("type",-1)));
  String id=id(raw),corner=o.optString("corner","").toUpperCase(),genre=o.optString("genre","").toUpperCase();
  if(type==7||corner.contains("SHORT")||genre.contains("SHORT"))return 7;
  if(type==5||genre.contains("ANIME")||genre.contains("ANIMATION"))return 5;
  if(type==2||type==4||corner.contains("EP")||corner.contains("SEASON")||genre.contains("SERIES")||genre.contains("TV SHOW"))return 2;
  if(type==3||genre.contains("DOCUMENTARY"))return 3;
  if(type==6||genre.contains("MUSIC")||genre.contains("CONCERT"))return 6;
  if(type==9||genre.contains("SPORT"))return 9;
  if(type==0||id.isEmpty()||id.equals("0"))return 0;
  return 1;
 }
 private boolean isSeries(JSONObject raw){int t=contentType(raw);return t==2||t==4||t==5||t==7;}
 private String detailImage(JSONObject o,String...keys){String u=pick(o,keys);if(!u.isEmpty())return u;return backdrop(o);}
 private TextView detailText(String value,int size,int color,boolean bold){TextView t=txt(value,size,color,bold);t.setFocusable(false);return t;}
 private void detailHeading(String label){TextView h=detailText(label,21,WHITE,true);h.setPadding(dp(48),dp(26),dp(30),dp(12));content.addView(h);}
 private void detailAction(LinearLayout row,String label,boolean primary,Runnable action){TextView v=button(label,primary,action);v.setTextSize(15);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(178),dp(48));p.rightMargin=dp(12);row.addView(v,p);}
 private void openDetail(String subjectId){
  if(previousPageViews==null){previousPageViews=new ArrayList<>();for(int i=0;i<content.getChildCount();i++)previousPageViews.add(content.getChildAt(i));content.removeAllViews();previousScrollY=pageScroll.getScrollY();previousTopVisible=top.getVisibility()==View.VISIBLE;}
  if(slideTask!=null)handler.removeCallbacks(slideTask);top.setVisibility(View.GONE);
  runApi(()->api.detail(subjectId),j->{
   content.removeAllViews();JSONObject data=j.optJSONObject("data");if(data==null)data=new JSONObject();
   JSONObject subject=data.optJSONObject("subject");if(subject==null)subject=data;
   JSONObject resource=data.optJSONObject("resource");if(resource==null)resource=new JSONObject();
   final String detailPath=subject.optString("detailPath",""),name=title(subject);
   saveCollection("history",subject);
   final boolean series=isSeries(subject);final int kind=contentType(subject);
   String cover=pick(subject,"coverVertical","verticalCover","poster","posterUrl","cover","coverUrl","image");
   String hero=pick(subject,"stills","coverHorizontal","horizontalCover","backdrop","backdropUrl","banner","image");
   if(hero.isEmpty())hero=cover;
   String yr=year(subject),rating=subject.optString("imdbRatingValue",""),duration=subject.optString("duration","");
   final JSONArray seasons=resource.optJSONArray("seasons")!=null?resource.optJSONArray("seasons"):new JSONArray();
   final JSONArray dubs=subject.optJSONArray("dubs")!=null?subject.optJSONArray("dubs"):new JSONArray();
   JSONArray stars=data.optJSONArray("stars");if(stars==null)stars=new JSONArray();
   String synopsis=description(subject);
   // Web-app style cinematic backdrop and poster/info composition.
   FrameLayout heroPanel=new FrameLayout(this);heroPanel.setBackgroundColor(0xff171717);
   content.addView(heroPanel,new LinearLayout.LayoutParams(-1,dp(430)));
   ImageView backdropView=new ImageView(this);backdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
   heroPanel.addView(backdropView,new FrameLayout.LayoutParams(-1,-1));
   if(!hero.isEmpty())Glide.with(this).load(hero).override(getResources().getDisplayMetrics().widthPixels,dp(430)).centerCrop().dontAnimate().priority(com.bumptech.glide.Priority.IMMEDIATE).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(backdropView);
   // Warm the detail-page image cache in parallel so lower sections appear quickly while scrolling.
   if(!cover.isEmpty())Glide.with(this).load(cover).override(dp(380),dp(570)).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).preload();
   if(!hero.isEmpty()&&!hero.equals(cover))Glide.with(this).load(hero).override(getResources().getDisplayMetrics().widthPixels,dp(430)).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).preload();
   for(int pi=0;pi<stars.length();pi++){JSONObject person=stars.optJSONObject(pi);if(person!=null){String au=person.optString("avatarUrl","");if(!au.isEmpty())Glide.with(this).load(au).override(dp(188),dp(248)).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).preload();}}
   View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x22000000,0x66101010,0xff141414}));heroPanel.addView(shade,new FrameLayout.LayoutParams(-1,-1));
   LinearLayout body=new LinearLayout(this);body.setGravity(Gravity.BOTTOM);body.setPadding(dp(48),dp(30),dp(48),dp(26));body.setOrientation(0);
   heroPanel.addView(body,new FrameLayout.LayoutParams(-1,-1));
   ImageView posterView=new ImageView(this);posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);posterView.setBackground(bg(PANEL,0xff333333));
   LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(190),dp(285));pp.rightMargin=dp(30);body.addView(posterView,pp);
   if(!cover.isEmpty())Glide.with(this).load(cover).override(dp(380),dp(570)).centerCrop().diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(posterView);
   LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setGravity(Gravity.BOTTOM);body.addView(info,new LinearLayout.LayoutParams(0,-2,1));
   TextView titleView=detailText(name+(yr.isEmpty()?"":" ("+yr+")"),32,WHITE,true);titleView.setMaxLines(2);info.addView(titleView);
   LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(0,dp(10),0,dp(8));info.addView(meta);
   if(!rating.isEmpty())meta.addView(detailText("★ "+rating,16,0xffffd45c,true),new LinearLayout.LayoutParams(-2,-2));
   if(!yr.isEmpty())meta.addView(detailText("   "+yr,15,0xffdddddd,false));
   if(!duration.isEmpty())meta.addView(detailText("   "+duration,15,0xffdddddd,false));
   meta.addView(detailText(series?"   "+(kind==7?"Short Drama":"Series"):"   Movie",14,0xffdddddd,false));
   String genre=subject.optString("genre",subject.optString("genres",""));
   if(!genre.isEmpty()){TextView g=detailText(genre,14,0xffcccccc,false);g.setMaxLines(2);info.addView(g);}
   if(!synopsis.isEmpty()){TextView d=detailText(synopsis,15,0xffeeeeee,false);d.setMaxLines(4);d.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2);dp1.topMargin=dp(10);info.addView(d,dp1);}
   LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,dp(54));al.topMargin=dp(16);info.addView(actions,al);
   final int firstSe=series?Math.max(1,seasons.length()>0?seasons.optJSONObject(0)==null?1:seasons.optJSONObject(0).optInt("se",1):1):0;
   detailAction(actions,"▶  "+(series?"WATCH EPISODE 1":"WATCH NOW"),true,()->launchPlayer(subjectId,name,detailPath,series?firstSe:0,series?1:0,dubs,seasons,""));
   final JSONObject savedSubject=subject;
   detailAction(actions,"＋  MY LIST",false,()->toggleSaved("mylist",savedSubject));
   // Audio language choices use a fixed-column grid so no dub chip can overflow horizontally.
   if(dubs.length()>0){
    detailHeading("Audio Languages");
    GridLayout langs=new GridLayout(this);langs.setColumnCount(4);langs.setUseDefaultMargins(false);
    langs.setPadding(dp(48),0,dp(30),dp(8));
    content.addView(langs,new LinearLayout.LayoutParams(-1,-2));
    int dubCell=0;
    for(int i=0;i<dubs.length();i++){
     JSONObject dub=dubs.optJSONObject(i);if(dub==null)continue;
     String label=dub.optString("lanName",dub.optString("lanCode",dub.optString("title","Audio")));
     String did=dub.optString("subjectId",subjectId),dpath=dub.optString("detailPath",detailPath);
     String dubTitle=dub.optString("title",dub.optString("name",name));
     TextView chip=txt(label,14,WHITE,true);chip.setGravity(Gravity.CENTER);chip.setMaxLines(2);chip.setEllipsize(TextUtils.TruncateAt.END);
     chip.setPadding(dp(8),dp(6),dp(8),dp(6));chip.setFocusable(true);chip.setFocusableInTouchMode(false);
     chip.setBackground(bg(0xff292929,0xff444444));
     chip.setOnFocusChangeListener((v,focus)->chip.setBackground(bg(focus?0xff3b171a:0xff292929,focus?RED:0xff444444)));
     chip.setOnClickListener(v->launchPlayer(did,name,dpath,series?firstSe:0,series?1:0,dubs,seasons,dubTitle));
     GridLayout.LayoutParams chipLp=new GridLayout.LayoutParams(GridLayout.spec(dubCell/4),GridLayout.spec(dubCell%4,1f));
     chipLp.width=0;chipLp.height=dp(52);chipLp.setMargins(0,0,dp(10),dp(10));langs.addView(chip,chipLp);dubCell++;
    }
   }
   if(!synopsis.isEmpty()){detailHeading("Synopsis");TextView desc=detailText(synopsis,16,0xffcccccc,false);desc.setPadding(dp(48),0,dp(55),dp(8));content.addView(desc);}
   if(stars.length()>0){
    detailHeading("Cast");int castCols=10;int castCount=stars.length();
    for(int base=0;base<castCount;base+=castCols){LinearLayout castGridRow=new LinearLayout(this);castGridRow.setGravity(Gravity.TOP);castGridRow.setPadding(dp(48),0,dp(35),dp(12));content.addView(castGridRow,new LinearLayout.LayoutParams(-1,-2));
     for(int k=base;k<Math.min(castCount,base+castCols);k++){JSONObject person=stars.optJSONObject(k);if(person==null)continue;
      LinearLayout personBox=new LinearLayout(this);personBox.setOrientation(1);personBox.setGravity(Gravity.CENTER_HORIZONTAL);int castAvailable=getResources().getDisplayMetrics().widthPixels-dp(48+35)-dp(12)*(castCols-1);int castCardWidth=Math.max(dp(48),castAvailable/castCols);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(castCardWidth,-2);cp.rightMargin=dp(12);castGridRow.addView(personBox,cp);
      ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setBackgroundColor(0xff252525);personBox.addView(avatar,new LinearLayout.LayoutParams(Math.min(dp(94),castCardWidth),dp(124)));
      String au=person.optString("avatarUrl","");if(!au.isEmpty())Glide.with(this).load(au).override(dp(188),dp(248)).centerCrop().dontAnimate().priority(com.bumptech.glide.Priority.HIGH).diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(avatar);
      TextView pn=detailText(person.optString("name",""),12,WHITE,true);pn.setMaxLines(1);pn.setEllipsize(TextUtils.TruncateAt.END);pn.setGravity(Gravity.CENTER);personBox.addView(pn);
      TextView role=detailText(person.optString("character",""),10,MUTED,false);role.setMaxLines(1);role.setEllipsize(TextUtils.TruncateAt.END);role.setGravity(Gravity.CENTER);personBox.addView(role);
     }
    }
   }
   if(series&&seasons.length()>0){detailHeading("Episodes");LinearLayout seasonBar=new LinearLayout(this);seasonBar.setPadding(dp(48),0,dp(48),dp(12));content.addView(seasonBar);Spinner selector=new Spinner(this);ArrayList<String> labels=new ArrayList<>();ArrayList<Integer> seasonNos=new ArrayList<>();for(int i=0;i<seasons.length();i++){JSONObject se=seasons.optJSONObject(i);if(se==null)continue;int sn=se.optInt("se",se.optInt("season",i+1));int count=se.optInt("maxEp",se.optInt("maxEpisode",se.optInt("episodeCount",se.optInt("totalEpisodes",0))));if(count<=0)count=1;seasonNos.add(sn);labels.add("Season "+sn+"  ·  "+count+" Episodes");}ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);selector.setAdapter(adapter);seasonBar.addView(selector,new LinearLayout.LayoutParams(dp(310),dp(48)));
    LinearLayout episodeGrid=new LinearLayout(this);episodeGrid.setOrientation(1);episodeGrid.setPadding(dp(48),0,dp(48),dp(35));content.addView(episodeGrid);
    HorizontalScrollView rangeScroll=new HorizontalScrollView(this);rangeScroll.setHorizontalScrollBarEnabled(false);LinearLayout rangeTabs=new LinearLayout(this);rangeTabs.setGravity(Gravity.CENTER_VERTICAL);rangeScroll.addView(rangeTabs,new HorizontalScrollView.LayoutParams(-2,dp(48)));episodeGrid.addView(rangeScroll,new LinearLayout.LayoutParams(-1,dp(52)));
    int[] selectedRange={0};
    Runnable[] render={null};render[0]=()->{if(episodeGrid.getChildCount()>1)episodeGrid.removeViews(1,episodeGrid.getChildCount()-1);int pos=Math.max(0,selector.getSelectedItemPosition());JSONObject se=null;for(int x=0;x<seasons.length();x++){JSONObject q=seasons.optJSONObject(x);if(q!=null&&q.optInt("se",q.optInt("season",x+1))==seasonNos.get(pos)){se=q;break;}}if(se==null)return;int max=Math.max(1,se.optInt("maxEp",se.optInt("maxEpisode",se.optInt("episodeCount",se.optInt("totalEpisodes",1)))));int from=selectedRange[0]*100+1,to=Math.min(max,from+99);int cols=5;for(int base=from;base<=to;base+=cols){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(72));rp.bottomMargin=dp(8);episodeGrid.addView(row,rp);for(int ep=base;ep<=Math.min(to,base+cols-1);ep++){final int episode=ep,season=seasonNos.get(pos);TextView cell=txt("EPISODE "+ep,13,WHITE,true);cell.setGravity(Gravity.CENTER);cell.setFocusable(true);cell.setBackground(bg(0xff202020,0xff383838));cell.setOnFocusChangeListener((v,focus)->cell.setBackground(bg(focus?0xff353535:0xff202020,focus?0xff777777:0xff383838)));cell.setOnClickListener(v->launchPlayer(subjectId,name+" · S"+season+" E"+episode,detailPath,season,episode,dubs,seasons,""));LinearLayout.LayoutParams epLp=new LinearLayout.LayoutParams(0,dp(64),1);epLp.rightMargin=dp(8);row.addView(cell,epLp);}}};if(!labels.isEmpty()){selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){selectedRange[0]=0;rangeTabs.removeAllViews();JSONObject selected=null;for(int x=0;x<seasons.length();x++){JSONObject q=seasons.optJSONObject(x);if(q!=null&&q.optInt("se",q.optInt("season",x+1))==seasonNos.get(pos)){selected=q;break;}}int max=selected==null?1:Math.max(1,selected.optInt("maxEp",selected.optInt("maxEpisode",selected.optInt("episodeCount",selected.optInt("totalEpisodes",1)))));int ranges=(max+99)/100;if(ranges>1){for(int r=0;r<ranges;r++){final int range=r;TextView tab=detailText((r*100+1)+"-"+Math.min(max,(r+1)*100),13,WHITE,true);tab.setGravity(Gravity.CENTER);tab.setPadding(dp(18),0,dp(18),0);tab.setFocusable(true);tab.setBackground(bg(r==0?0xff353535:PANEL,r==0?0xff777777:0));tab.setOnClickListener(vv->{selectedRange[0]=range;for(int k=0;k<rangeTabs.getChildCount();k++)rangeTabs.getChildAt(k).setBackground(bg(k==range?0xff353535:PANEL,k==range?0xff777777:0));render[0].run();});LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-2,dp(40));tlp.rightMargin=dp(8);rangeTabs.addView(tab,tlp);}}render[0].run();}public void onNothingSelected(android.widget.AdapterView<?>p){}});if(!labels.isEmpty())selector.setSelection(0);}}
   else if(series){TextView empty=detailText("Episode information is not available for this title.",14,MUTED,false);empty.setPadding(dp(48),0,dp(48),dp(30));content.addView(empty);}
   // Keep related titles below the core details, only when the API provides them.
   JSONArray related=resource.optJSONArray("recommendations");if(related==null)related=resource.optJSONArray("subjects");if(related!=null&&related.length()>0)addSection("You May Also Like",related);
  });
 }
 private void addHeroDetail(JSONObject o){FrameLayout f=new FrameLayout(this);content.addView(f,new LinearLayout.LayoutParams(-1,dp(340)));ImageView im=new ImageView(this);im.setScaleType(ImageView.ScaleType.CENTER_CROP);f.addView(im,new FrameLayout.LayoutParams(-1,-1));String u=backdrop(o);if(u.isEmpty())u=poster(o);Glide.with(this).load(u).centerCrop().into(im);TextView t=txt(title(o),32,WHITE,true);t.setGravity(Gravity.BOTTOM);t.setPadding(dp(50),0,0,dp(32));t.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{0xee141414,0x00141414}));f.addView(t,new FrameLayout.LayoutParams(-1,-1));}
 private JSONArray buildEpisodeSequence(JSONArray seasons){
  JSONArray sequence=new JSONArray();
  if(seasons==null)return sequence;
  for(int i=0;i<seasons.length();i++){
   JSONObject s=seasons.optJSONObject(i);if(s==null)continue;
   int sn=s.optInt("se",s.optInt("season",i+1));
   int max=s.optInt("maxEp",s.optInt("maxEpisode",s.optInt("episodeCount",s.optInt("totalEpisodes",0))));
   if(max<1)continue;
   for(int ep=1;ep<=max;ep++)try{sequence.put(new JSONObject().put("se",sn).put("ep",ep));}catch(Exception ignored){}
  }
  return sequence;
 }
 private void launchPlayer(String id,String name,String path,int season,int episode,JSONArray dubs,JSONArray seasons,String dubTitle){
  Intent i=new Intent(this,PlayerActivity.class);i.putExtra("movieBox",true);i.putExtra("subjectId",id);i.putExtra("detailPath",path);i.putExtra("token",api.token());i.putExtra("season",season);i.putExtra("episode",episode);i.putExtra("title",name);i.putExtra("dubTitle",dubTitle==null?"":dubTitle);
  i.putExtra("dubs",dubs==null?"[]":dubs.toString());i.putExtra("episodeSequence",buildEpisodeSequence(seasons).toString());startActivity(i);
 }
 private String scopedKey(String key){return "server_"+getSelectedServer()+"_"+key;}
 private JSONArray readCollection(String key){try{return new JSONArray(getSharedPreferences(PREFS,MODE_PRIVATE).getString(scopedKey(key),"[]"));}catch(Exception e){return new JSONArray();}}
 private void saveCollection(String key,JSONObject item){if(item==null)return;String itemId=id(item);if(itemId.isEmpty())return;JSONArray old=readCollection(key),next=new JSONArray();try{next.put(new JSONObject(item.toString()));for(int i=0;i<old.length();i++){JSONObject o=old.optJSONObject(i);if(o!=null&&!id(o).equals(itemId))next.put(o);}}catch(Exception ignored){}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(scopedKey(key),next.toString()).apply();}
 private void toggleSaved(String key,JSONObject item){String itemId=id(item);JSONArray old=readCollection(key),next=new JSONArray();boolean removed=false;for(int i=0;i<old.length();i++){JSONObject o=old.optJSONObject(i);if(o==null)continue;if(id(o).equals(itemId)){removed=true;continue;}next.put(o);}if(!removed)next.put(item);getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(scopedKey(key),next.toString()).apply();Toast.makeText(this,removed?"Removed from My List":"Added to My List",Toast.LENGTH_SHORT).show();}
 private void showSaved(String key){
  top.setVisibility(View.GONE);if(previousPageViews==null){previousPageViews=new ArrayList<>();for(int i=0;i<content.getChildCount();i++)previousPageViews.add(content.getChildAt(i));previousScrollY=pageScroll.getScrollY();previousTopVisible=true;}
  content.removeAllViews();LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(48),dp(24),dp(48),dp(16));content.addView(heading,new LinearLayout.LayoutParams(-1,-2));
  TextView h=txt(key.equals("mylist")?"My List":"Watch History",26,WHITE,true);heading.addView(h,new LinearLayout.LayoutParams(0,-2,1));
  TextView clear=txt("Clear All",14,WHITE,true);clear.setGravity(Gravity.CENTER);clear.setPadding(dp(18),dp(10),dp(18),dp(10));clear.setBackground(bg(PANEL,dp(9)));clear.setFocusable(true);clear.setOnFocusChangeListener((v,focus)->clear.setBackground(bg(focus?0xff444444:PANEL,dp(9))));heading.addView(clear);clear.setOnClickListener(v->{new AlertDialog.Builder(this).setTitle("Clear all?").setMessage(key.equals("mylist")?"Remove all titles from My List?":"Clear your entire watch history?").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(scopedKey(key),"[]").apply();showSaved(key);}).show();});
  JSONArray items=readCollection(key);if(items.length()==0){TextView empty=txt(key.equals("mylist")?"Your list is empty. Add titles using + MY LIST.":"No watch history yet. Play a title to add it here.",16,MUTED,false);empty.setPadding(dp(48),dp(12),0,0);content.addView(empty);return;}
  for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(48),dp(8),dp(48),dp(8));row.setFocusable(true);row.setBackground(bg(PANEL,0));content.addView(row,new LinearLayout.LayoutParams(-1,dp(112)));
   ImageView posterView=new ImageView(this);posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);row.addView(posterView,new LinearLayout.LayoutParams(dp(62),dp(94)));String image=poster(item);if(!image.isEmpty())Glide.with(this).load(image).centerCrop().into(posterView);
   LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(0,-1,1);ilp.leftMargin=dp(18);row.addView(info,ilp);info.addView(txt(title(item),17,WHITE,true));info.addView(txt(year(item),13,MUTED,false));
   TextView play=txt("▶ Play",13,WHITE,true);play.setGravity(Gravity.CENTER);play.setPadding(dp(14),dp(10),dp(14),dp(10));play.setBackground(bg(PANEL,dp(9)));play.setFocusable(true);LinearLayout.LayoutParams playLp=new LinearLayout.LayoutParams(-2,dp(42));playLp.rightMargin=dp(8);row.addView(play,playLp);
   TextView remove=txt("Remove",13,WHITE,true);remove.setGravity(Gravity.CENTER);remove.setPadding(dp(12),dp(10),dp(12),dp(10));remove.setBackground(bg(PANEL,dp(9)));remove.setFocusable(true);row.addView(remove,new LinearLayout.LayoutParams(-2,dp(42)));
   View.OnFocusChangeListener buttonFocus=(v,focus)->v.setBackground(bg(focus?0xff444444:PANEL,dp(9)));play.setOnFocusChangeListener(buttonFocus);remove.setOnFocusChangeListener(buttonFocus);row.setOnFocusChangeListener((v,focus)->row.setBackground(bg(focus?0xff303030:PANEL,focus?RED:0)));
   View.OnClickListener open=v->openDetail(id(item));info.setOnClickListener(open);posterView.setOnClickListener(open);play.setOnClickListener(open);row.setOnClickListener(open);
   remove.setOnClickListener(v->{JSONArray updated=new JSONArray();for(int k=0;k<items.length();k++){JSONObject entry=items.optJSONObject(k);if(entry!=null&&!id(entry).equals(id(item)))updated.put(entry);}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(key,updated.toString()).apply();showSaved(key);});
  }
 }
 private static final String SERVER_PREF="selected_home_server";
 private static final String[] SERVER_NAMES={"MovieBox","Vskits","Lookr","Story TV","Free Reels","Jio Saavn"};
 private static final String[] SERVER_LOGOS={
  "https://h5-static.aoneroom.com/ssrStatic/mbOfficial/public/_nuxt/image-1.apJjVir2.svg",
  "https://vskit.online/logo.svg",
  "https://videodownloader.site/assets/brand/omnisave-icon-hd.png?x-oss-process=image/resize%2Cw_600",
  "https://yt3.googleusercontent.com/4tx8ZJlfylOHvpYvtj0vOBCfOeO_stx64_-45CuTSQ2cR_5kfUjbqnkHazUgaJ8a6VJQ0vpfabE=s900-c-k-c0x00ffffff-no-rj",
  "https://play-lh.googleusercontent.com/1ybMEp7xw4kTY-K7ocih3vcbTEyCEqaxerVB6tDT8WcE3k5zCRR-jhKwS6Ct0UXIorczCIat3wTV0QLiuoLPdbY",
  "https://downloadr2.apkmirror.com/wp-content/uploads/2018/12/5c067c2072240.png"
 };
 private int getSelectedServer(){return getSharedPreferences(PREFS,MODE_PRIVATE).getInt(SERVER_PREF,0);}
 private void showSettings(){
  final int active=getSelectedServer();
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(18),dp(12),dp(18),dp(12));
  GridLayout grid=new GridLayout(this);grid.setColumnCount(3);grid.setUseDefaultMargins(false);
  panel.addView(grid,new LinearLayout.LayoutParams(-1,-2));
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Choose Server").setView(panel).setNegativeButton("CLOSE",null).create();
  for(int i=0;i<SERVER_NAMES.length;i++){
   final int index=i;
   LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setGravity(Gravity.CENTER);card.setFocusable(true);card.setFocusableInTouchMode(false);card.setClickable(true);
   card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setBackground(bg(0xff202020,0xff383838));
   ImageView logo=new ImageView(this);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setBackgroundColor(0xff303030);
   card.addView(logo,new LinearLayout.LayoutParams(dp(82),dp(82)));
   Glide.with(this).load(SERVER_LOGOS[i]).placeholder(new android.graphics.drawable.ColorDrawable(0xff303030)).error(new android.graphics.drawable.ColorDrawable(0xff303030)).fitCenter().into(logo);
   TextView name=txt(SERVER_NAMES[i],13,WHITE,true);name.setGravity(Gravity.CENTER);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);
   LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(-1,-2);nameLp.topMargin=dp(8);card.addView(name,nameLp);
   card.setOnFocusChangeListener((v,focus)->{card.setBackground(bg(focus?0xff3b171a:0xff202020,focus?0xffff626b:(index==getSelectedServer()?0xffff626b:0xff383838)));card.animate().scaleX(focus?1.06f:1f).scaleY(focus?1.06f:1f).setDuration(100).start();card.setElevation(focus?dp(9):0);});
   card.setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN&&(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)){card.performClick();return true;}return false;});
   card.setOnClickListener(v->{getSharedPreferences(PREFS,MODE_PRIVATE).edit().putInt(SERVER_PREF,index).apply();dialog.dismiss();if(index==0)loadHome();else if(index==3)showStoryTvHome();else if(index==4)showFreeReelsHome();else showComingSoonServer();});
   GridLayout.LayoutParams cell=new GridLayout.LayoutParams(GridLayout.spec(i/3),GridLayout.spec(i%3));
   cell.width=dp(142);cell.height=dp(142);cell.setMargins(dp(7),dp(7),dp(7),dp(7));grid.addView(card,cell);
  }
  TextView connected=txt(SERVER_NAMES[active]+" server is connected",14,0xff61d98b,true);connected.setGravity(Gravity.CENTER);
  LinearLayout.LayoutParams connectedLp=new LinearLayout.LayoutParams(-1,dp(42));connectedLp.topMargin=dp(6);panel.addView(connected,connectedLp);
  dialog.setOnShowListener(d->{Window w=dialog.getWindow();if(w!=null)w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(70),dp(540)),-2);grid.getChildAt(Math.max(0,Math.min(active,grid.getChildCount()-1))).requestFocus();});
  dialog.show();
 }


 private String freeHomeNext="",freeHomeModule="";private boolean freeHomeLoading=false,freeHomeHasMore=true;private LinearLayout freeHomeGrid,freeHomeRow;private int freeHomeRowCount=0,freeHomeRequest=0;
 private String freeSearchNext="";private boolean freeSearchHasMore=false,freeSearchLoading=false;private LinearLayout freeSearchGrid,freeSearchRow;private int freeSearchRowCount=0;private final HashSet<String> freeSearchSeen=new HashSet<>();

 private JSONArray freeReelsKeywords(JSONObject response){JSONObject d=response==null?null:response.optJSONObject("data");JSONArray a=d==null?null:d.optJSONArray("keywords");return a==null?new JSONArray():a;}
 private JSONArray freeReelsItems(JSONObject response){
  JSONArray out=new JSONArray();JSONObject d=response==null?null:response.optJSONObject("data");JSONArray source=d==null?null:d.optJSONArray("items");if(source==null&&d!=null)source=d.optJSONArray("list");if(source==null&&d!=null)source=d.optJSONArray("dramas");if(source==null)return out;
  for(int i=0;i<source.length();i++){JSONObject entry=source.optJSONObject(i);if(entry==null)continue;
   if(entry.has("series_id")||entry.has("series_key")||entry.has("key")||entry.has("id")){out.put(entry);continue;}
   JSONArray nested=entry.optJSONArray("items");if(nested!=null)for(int k=0;k<nested.length();k++){JSONObject item=nested.optJSONObject(k);if(item!=null)out.put(item);}
  }return out;
 }
 private JSONObject freeReelsPageInfo(JSONObject response){JSONObject d=response==null?null:response.optJSONObject("data");JSONObject p=d==null?null:d.optJSONObject("page_info");if(p==null&&d!=null)p=d.optJSONObject("pageInfo");return p==null?new JSONObject():p;}
 private String freeReelsNext(JSONObject response){JSONObject p=freeReelsPageInfo(response);return p.optString("next",p.optString("next_cursor",""));}
 private boolean freeReelsHasMore(JSONObject response){JSONObject p=freeReelsPageInfo(response);return p.optBoolean("has_more",p.optBoolean("hasMore",false));}
 private void showFreeReelsHome(){
  top.setVisibility(View.VISIBLE);if(top.getChildAt(0) instanceof TextView)((TextView)top.getChildAt(0)).setText("FREE REELS");
  if(slideTask!=null)handler.removeCallbacks(slideTask);content.removeAllViews();pageScroll.scrollTo(0,0);freeHomeNext="";freeHomeModule="";freeHomeHasMore=true;freeHomeLoading=false;freeHomeRow=null;freeHomeRowCount=0;freeHomeRequest++;
  TextView heading=txt("Free Reels",25,WHITE,true);heading.setPadding(dp(48),dp(25),0,dp(8));content.addView(heading);
  freeHomeGrid=new LinearLayout(this);freeHomeGrid.setOrientation(1);freeHomeGrid.setPadding(0,dp(4),0,dp(25));content.addView(freeHomeGrid,new LinearLayout.LayoutParams(-1,-2));
  loadFreeReelsHome(false);
  pageScroll.setOnScrollChangeListener((View v,int sx,int sy,int osx,int osy)->{View child=pageScroll.getChildAt(0);if(child!=null&&freeHomeHasMore&&!freeHomeLoading&&child.getBottom()-sy-pageScroll.getHeight()<dp(550))loadFreeReelsHome(true);});
 }
 private void loadFreeReelsHome(boolean next){
  if(freeHomeLoading|| (next&&!freeHomeHasMore))return;freeHomeLoading=true;final int request=freeHomeRequest;final String cursor=freeHomeNext,module=freeHomeModule;
  new Thread(()->{try{JSONObject response=next?freeReelsApi.homeFeed(cursor,module):freeReelsApi.home();JSONObject data=response.optJSONObject("data");String newNext=freeReelsNext(response);boolean more=freeReelsHasMore(response);JSONArray items=freeReelsItems(response);String mod=module;if(!next&&data!=null){JSONArray modules=data.optJSONArray("items");if(modules!=null&&modules.length()>0){JSONObject first=modules.optJSONObject(0);if(first!=null)mod=first.optString("module_key","");}}final String cursorOut=newNext,moduleOut=mod;final boolean moreOut=more;runOnUiThread(()->{if(getSelectedServer()!=4||request!=freeHomeRequest)return;if(!next)freeHomeGrid.removeAllViews();appendFreeReelsCards(freeHomeGrid,items,false);freeHomeNext=cursorOut;freeHomeModule=moduleOut;freeHomeHasMore=moreOut&&!cursorOut.isEmpty()&&!cursorOut.equals(cursor);freeHomeLoading=false;freeHomeGrid.post(()->{View child=pageScroll.getChildAt(0);if(getSelectedServer()==4&&freeHomeHasMore&&!freeHomeLoading&&child!=null&&child.getBottom()-pageScroll.getScrollY()-pageScroll.getHeight()<dp(550))loadFreeReelsHome(true);});});}catch(Exception e){runOnUiThread(()->{if(request!=freeHomeRequest)return;freeHomeLoading=false;TextView error=txt("Free Reels feed unavailable: "+e.getMessage(),14,WHITE,false);error.setPadding(dp(48),dp(18),dp(48),dp(12));freeHomeGrid.addView(error);if(!next)focusText("RETRY",()->loadFreeReelsHome(false));});}}).start();
 }
 private void appendFreeReelsCards(LinearLayout grid,JSONArray items,boolean searchResults){
  int cols=6;int width=Math.max(dp(42),(getResources().getDisplayMetrics().widthPixels-dp(60)-dp(6)*5)/6);
  for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item==null)continue;String key=item.optString("series_id",item.optString("series_key",item.optString("key",item.optString("id",""))));if(key.isEmpty())continue;if(searchResults&&!freeSearchSeen.add(key))continue;
   if(searchResults){if(freeSearchRow==null||freeSearchRowCount>=cols){freeSearchRow=new LinearLayout(this);freeSearchRow.setOrientation(0);freeSearchRow.setGravity(Gravity.TOP);freeSearchRow.setPadding(dp(24),dp(8),dp(24),dp(8));grid.addView(freeSearchRow,new LinearLayout.LayoutParams(-1,-2));freeSearchRowCount=0;}}
   else if(freeHomeRow==null||freeHomeRowCount>=cols){freeHomeRow=new LinearLayout(this);freeHomeRow.setOrientation(0);freeHomeRow.setGravity(Gravity.TOP);freeHomeRow.setPadding(dp(24),dp(8),dp(24),dp(8));grid.addView(freeHomeRow,new LinearLayout.LayoutParams(-1,-2));freeHomeRowCount=0;}
   LinearLayout row=searchResults?freeSearchRow:freeHomeRow;LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setFocusable(true);card.setClickable(true);card.setPadding(dp(3),dp(3),dp(3),dp(8));card.setBackground(bg(PANEL,0));
   FrameLayout posterFrame=new FrameLayout(this);posterFrame.setBackgroundColor(0xff252525);card.addView(posterFrame,new LinearLayout.LayoutParams(-1,(int)(width*16f/9f)));
   ImageView posterView=new ImageView(this);posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);posterFrame.addView(posterView,new FrameLayout.LayoutParams(-1,-1));
   String image=item.optString("cover","");if(image.startsWith("//"))image="https:"+image;if(!image.isEmpty())Glide.with(this).load(image).placeholder(new android.graphics.drawable.ColorDrawable(0xff252525)).centerCrop().into(posterView);
   
   TextView name=txt(item.optString("title",item.optString("name","Untitled Drama")),11,WHITE,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(3),dp(5),dp(3),0);card.addView(name);
   card.setOnFocusChangeListener((v,focus)->{card.setBackground(bg(focus?0xff302024:PANEL,focus?RED:0));card.animate().scaleX(focus?1.035f:1f).scaleY(focus?1.035f:1f).setDuration(100).start();card.setElevation(focus?dp(8):0);});
   card.setOnClickListener(v->openFreeReelsDetail(item));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,-2);lp.rightMargin=dp(6);lp.bottomMargin=dp(12);row.addView(card,lp);if(searchResults)freeSearchRowCount++;else freeHomeRowCount++;
  }
 }
 private void openFreeReelsDetail(JSONObject item){
  if(previousPageViews==null){previousPageViews=new ArrayList<>();for(int i=0;i<content.getChildCount();i++)previousPageViews.add(content.getChildAt(i));previousScrollY=pageScroll.getScrollY();previousTopVisible=top.getVisibility()==View.VISIBLE;}
  top.setVisibility(View.GONE);pageScroll.setOnScrollChangeListener(null);content.removeAllViews();pageScroll.scrollTo(0,0);
  TextView loading=txt("Loading drama details…",17,MUTED,false);loading.setPadding(dp(48),dp(35),dp(48),dp(25));content.addView(loading);
  String seriesId=item.optString("series_id",item.optString("series_key",item.optString("key","")));
  new Thread(()->{try{
   JSONObject response=freeReelsApi.dramaInfo(seriesId);JSONObject data=response.optJSONObject("data");JSONObject info=data==null?null:data.optJSONObject("info");if(info==null)throw new Exception("API response did not contain data.info");
   JSONArray episodes=info.optJSONArray("episode_list");if(episodes==null)episodes=new JSONArray();final JSONObject drama=info;final JSONArray eps=episodes;
   runOnUiThread(()->renderFreeReelsDetail(drama,eps));
  }catch(Exception e){runOnUiThread(()->{content.removeAllViews();TextView error=txt("FreeReels details unavailable: "+e.getMessage(),14,WHITE,false);error.setPadding(dp(48),dp(25),dp(48),dp(15));content.addView(error);TextView retry=button("RETRY",false,()->openFreeReelsDetail(item));content.addView(retry,new LinearLayout.LayoutParams(dp(150),dp(48)));});}}).start();
 }
 private void renderFreeReelsDetail(JSONObject info,JSONArray episodes){
  content.removeAllViews();String name=info.optString("name","Untitled Drama"),image=info.optString("cover","");
  JSONObject metadata=new JSONObject();try{JSONArray tags=info.optJSONArray("series_tag");if(tags!=null)metadata.put("genre",tags);}catch(Exception ignored){}int episodeCount=info.optInt("episode_count",episodes.length());
  String genre=storyValue(metadata.opt("genre"));
  FrameLayout hero=new FrameLayout(this);hero.setBackgroundColor(0xff171717);content.addView(hero,new LinearLayout.LayoutParams(-1,dp(430)));
  ImageView backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);hero.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
  if(!image.isEmpty())Glide.with(this).load(image).override(getResources().getDisplayMetrics().widthPixels,dp(430)).centerCrop().dontAnimate().priority(com.bumptech.glide.Priority.IMMEDIATE).into(backdrop);
  View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x22000000,0x66101010,0xff141414}));hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout body=new LinearLayout(this);body.setGravity(Gravity.BOTTOM);body.setPadding(dp(48),dp(30),dp(48),dp(26));body.setOrientation(0);hero.addView(body,new FrameLayout.LayoutParams(-1,-1));
  ImageView posterView=new ImageView(this);posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);posterView.setBackground(bg(PANEL,0xff333333));
  LinearLayout.LayoutParams posterLp=new LinearLayout.LayoutParams(dp(190),dp(285));posterLp.rightMargin=dp(30);body.addView(posterView,posterLp);
  if(!image.isEmpty())Glide.with(this).load(image).override(dp(380),dp(570)).centerCrop().diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(posterView);
  LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setGravity(Gravity.BOTTOM);body.addView(info,new LinearLayout.LayoutParams(0,-2,1));
  TextView titleView=detailText(name,32,WHITE,true);titleView.setMaxLines(2);info.addView(titleView);
  LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(0,dp(10),0,dp(8));info.addView(meta);
  if(episodeCount>0)meta.addView(detailText(episodeCount+" Episodes",16,0xffdddddd,true));
  if(!genre.isEmpty())meta.addView(detailText("   "+genre,15,0xffdddddd,false));
  String synopsis=info.optString("desc","");
  if(!synopsis.isEmpty()){TextView desc=detailText(synopsis,15,0xffeeeeee,false);desc.setMaxLines(4);desc.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams descLp=new LinearLayout.LayoutParams(-1,-2);descLp.topMargin=dp(10);info.addView(desc,descLp);}
  LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(-1,dp(54));actionLp.topMargin=dp(16);info.addView(actions,actionLp);
  final JSONArray finalEpisodes=episodes;
  final int firstEpisode=finalEpisodes.length()>0?Math.max(1,finalEpisodes.optJSONObject(0)==null?1:finalEpisodes.optJSONObject(0).optInt("index",1)):1;
  detailAction(actions,"▶  WATCH NOW",true,()->launchFreeReelsEpisode(info,finalEpisodes,firstEpisode));
  detailAction(actions,"＋  MY LIST",false,()->toggleSaved("mylist",info));
  if(!synopsis.isEmpty()){detailHeading("Synopsis");TextView desc=detailText(synopsis,16,0xffcccccc,false);desc.setPadding(dp(48),0,dp(55),dp(8));content.addView(desc);}
  if(metadata!=null){Iterator<String> mk=metadata.keys();while(mk.hasNext()){String key=mk.next();if(key.equalsIgnoreCase("genre"))continue;String value=storyValue(metadata.opt(key));if(value.isEmpty())continue;detailHeading(key.replace('_',' ').toUpperCase());TextView val=detailText(value,15,0xffcccccc,false);val.setPadding(dp(48),0,dp(48),dp(10));content.addView(val);}}
  if(episodeRows.length()>0){
   detailHeading("Episodes");
   TextView episodeSummary=detailText(episodeCount+" episodes  ·  "+name,13,MUTED,false);episodeSummary.setPadding(dp(48),0,dp(48),dp(10));content.addView(episodeSummary);
   HorizontalScrollView rangeScroll=new HorizontalScrollView(this);rangeScroll.setHorizontalScrollBarEnabled(false);LinearLayout rangeTabs=new LinearLayout(this);rangeTabs.setGravity(Gravity.CENTER_VERTICAL);rangeScroll.addView(rangeTabs,new HorizontalScrollView.LayoutParams(-2,dp(48)));LinearLayout.LayoutParams rangeLp=new LinearLayout.LayoutParams(-1,dp(52));rangeLp.leftMargin=dp(48);rangeLp.rightMargin=dp(48);content.addView(rangeScroll,rangeLp);
   LinearLayout episodeGrid=new LinearLayout(this);episodeGrid.setOrientation(1);episodeGrid.setPadding(dp(48),0,dp(48),dp(25));content.addView(episodeGrid);
   int rangeTotal=(finalEpisodes.length()+99)/100;int[] selectedRange={0};Runnable[] drawEpisodes={null};drawEpisodes[0]=()->{episodeGrid.removeAllViews();int from=selectedRange[0]*100,to=Math.min(finalEpisodes.length(),from+100);for(int base=from;base<to;base+=5){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,0,0,dp(8));episodeGrid.addView(row,new LinearLayout.LayoutParams(-1,dp(76)));for(int i=base;i<Math.min(to,base+5);i++){JSONObject ep=finalEpisodes.optJSONObject(i);if(ep==null)continue;int index=ep.optInt("index",i+1);String epTitle=ep.optString("epTitle","Episode "+index);boolean locked=ep.optBoolean("lock",false),watched=ep.optBoolean("isWtch",false);LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setGravity(Gravity.CENTER);tile.setFocusable(true);tile.setBackground(bg(0xff202020,0xff383838));TextView number=detailText("EPISODE "+index,13,WHITE,true);number.setGravity(Gravity.CENTER);tile.addView(number);tile.setOnFocusChangeListener((v,focus)->tile.setBackground(bg(focus?0xff3b171a:0xff202020,focus?RED:0xff383838)));tile.setOnClickListener(v->launchFreeReelsEpisode(info,finalEpisodes,index));LinearLayout.LayoutParams tileLp=new LinearLayout.LayoutParams(0,dp(76),1);tileLp.rightMargin=dp(8);row.addView(tile,tileLp);}}};
   if(rangeTotal>1){for(int r=0;r<rangeTotal;r++){final int range=r;TextView tab=detailText((r*100+1)+"-"+Math.min(finalEpisodes.length(),(r+1)*100),13,WHITE,true);tab.setGravity(Gravity.CENTER);tab.setPadding(dp(18),0,dp(18),0);tab.setFocusable(true);tab.setBackground(bg(r==0?0xff3b171a:PANEL,r==0?RED:0));tab.setOnClickListener(v->{selectedRange[0]=range;for(int k=0;k<rangeTabs.getChildCount();k++)rangeTabs.getChildAt(k).setBackground(bg(k==range?0xff3b171a:PANEL,k==range?RED:0));drawEpisodes[0].run();});LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-2,dp(40));tlp.rightMargin=dp(8);rangeTabs.addView(tab,tlp);}}
   drawEpisodes[0].run();
  }else{detailHeading("Episodes");TextView empty=detailText("No episode entries were returned by FreeReels for this title.",14,MUTED,false);empty.setPadding(dp(48),0,dp(48),dp(25));content.addView(empty);}
  // Back navigation is handled by the Android back key.
 }
 private void launchFreeReelsEpisode(JSONObject info,JSONArray episodes,int episodeIndex){
  JSONObject chosen=null;for(int i=0;i<episodes.length();i++){JSONObject ep=episodes.optJSONObject(i);if(ep!=null&&ep.optInt("index",i+1)==episodeIndex){chosen=ep;break;}}
  if(chosen==null){new AlertDialog.Builder(this).setTitle("Episode unavailable").setMessage("FreeReels did not return this episode.").setPositiveButton("OK",null).show();return;}
  String stream=chosen.optString("external_audio_h264_m3u8","");if(stream.isEmpty())stream=chosen.optString("video_url","");if(stream.isEmpty())stream=chosen.optString("m3u8_url","");if(stream.isEmpty())stream=chosen.optString("external_audio_h265_m3u8","");
  if(stream.isEmpty()){new AlertDialog.Builder(this).setTitle("Stream unavailable").setMessage("No playable URL was returned for Episode "+episodeIndex).setPositiveButton("OK",null).show();return;}
  Intent intent=new Intent(this,PlayerActivity.class);intent.putExtra("freeReels",true);intent.putExtra("url",stream);intent.putExtra("title",info.optString("name","FreeReels"));intent.putExtra("episode",episodeIndex);intent.putExtra("episodeCount",episodes.length());intent.putExtra("freeReelsEpisodes",episodes.toString());startActivity(intent);
 }
 private void freeReelsSearch(String query,int page){
  if(query==null||query.trim().isEmpty())return;String keyword=query.trim();
  if(page==1){activeSearchKeyword=keyword;freeSearchNext="";freeSearchHasMore=false;freeSearchLoading=true;freeSearchGrid=null;freeSearchRow=null;freeSearchRowCount=0;freeSearchSeen.clear();content.removeAllViews();TextView load=txt("Searching Free Reels…",16,MUTED,false);load.setPadding(dp(48),dp(28),0,dp(12));content.addView(load);}
  else{if(freeSearchLoading||!freeSearchHasMore)return;freeSearchLoading=true;}
  final String cursor=page==1?"":freeSearchNext;final String requested=keyword;
  new Thread(()->{try{JSONObject response=freeReelsApi.search(requested,cursor);String next=freeReelsNext(response);boolean more=freeReelsHasMore(response);JSONArray items=freeReelsItems(response);runOnUiThread(()->{if(getSelectedServer()!=4||!requested.equals(activeSearchKeyword))return;if(page==1){content.removeAllViews();freeSearchGrid=new LinearLayout(this);freeSearchGrid.setOrientation(1);freeSearchGrid.setPadding(0,dp(8),0,dp(25));content.addView(freeSearchGrid,new LinearLayout.LayoutParams(-1,-2));}if(items.length()==0&&page==1){TextView empty=txt("No results found for “"+requested+"”.",17,MUTED,false);empty.setPadding(dp(48),dp(30),0,0);content.addView(empty);}else appendFreeReelsCards(freeSearchGrid,items,true);freeSearchNext=next;freeSearchHasMore=more&&!next.isEmpty()&&!next.equals(cursor);freeSearchLoading=false;freeSearchGrid.post(()->{View child=pageScroll.getChildAt(0);if(getSelectedServer()==4&&freeSearchHasMore&&!freeSearchLoading&&child!=null&&child.getBottom()-pageScroll.getScrollY()-pageScroll.getHeight()<dp(650))freeReelsSearch(activeSearchKeyword,2);});pageScroll.setOnScrollChangeListener((View v,int sx,int sy,int osx,int osy)->{View child=pageScroll.getChildAt(0);if(child!=null&&freeSearchHasMore&&!freeSearchLoading&&child.getBottom()-sy-pageScroll.getHeight()<dp(650))freeReelsSearch(activeSearchKeyword,2);});});}catch(Exception e){runOnUiThread(()->{freeSearchLoading=false;TextView error=txt("Free Reels search failed: "+e.getMessage(),14,WHITE,false);error.setPadding(dp(48),dp(25),dp(48),dp(12));content.addView(error);});}}).start();
 }

 private int storyLangId=2,storyPage=0,storyRequestId=0,storyRowItems=0; private boolean storyLoading=false,storyHasMore=true; private LinearLayout storyGrid,storyCurrentRow; private View storyLoadingOverlay;
 private String[] storyLangNames={"Hindi","Telugu","Tamil","Kannada","Malayalam"};
 private int[] storyLangIds={2,6,7,8,9};

 private void showStoryLoading(boolean show){
  if(!show){if(storyLoadingOverlay!=null){root.removeView(storyLoadingOverlay);storyLoadingOverlay=null;}return;}
  if(storyLoadingOverlay!=null)return;
  FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(0x66000000);overlay.setClickable(true);
  ProgressBar spinner=new ProgressBar(this);spinner.setIndeterminate(true);if(android.os.Build.VERSION.SDK_INT>=21)spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(RED));
  overlay.addView(spinner,new FrameLayout.LayoutParams(dp(54),dp(54),Gravity.CENTER));
  root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));storyLoadingOverlay=overlay;
 }
 private void showStoryTvHome(){
  if(slideTask!=null)handler.removeCallbacks(slideTask);
  top.setVisibility(View.VISIBLE);if(top.getChildAt(0) instanceof TextView)((TextView)top.getChildAt(0)).setText("STORY TV");
  pageScroll.setOnScrollChangeListener(null);content.removeAllViews();storyPage=0;storyHasMore=true;storyLoading=false;storyRequestId++;storyRowItems=0;storyCurrentRow=null;
  
  HorizontalScrollView languageScroll=new HorizontalScrollView(this);languageScroll.setHorizontalScrollBarEnabled(false);languageScroll.setFillViewport(true);LinearLayout chips=new LinearLayout(this);chips.setGravity(Gravity.CENTER);chips.setPadding(dp(24),dp(4),dp(24),dp(14));languageScroll.addView(chips,new HorizontalScrollView.LayoutParams(-1,-2));LinearLayout.LayoutParams languageLp=new LinearLayout.LayoutParams(-1,-2);languageLp.topMargin=dp(66);content.addView(languageScroll,languageLp);
  for(int i=0;i<storyLangIds.length;i++){final int ix=i;TextView chip=txt(storyLangNames[i],14,storyLangId==storyLangIds[i]?0xff000000:WHITE,true);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(18),dp(10),dp(18),dp(10));chip.setFocusable(true);chip.setBackground(bg(storyLangId==storyLangIds[i]?0xffff626b:PANEL,storyLangId==storyLangIds[i]?RED:0));chip.setOnFocusChangeListener((v,focus)->{if(focus)chip.setBackground(bg(0xff393939,RED));else chip.setBackground(bg(storyLangId==storyLangIds[ix]?0xffff626b:PANEL,storyLangId==storyLangIds[ix]?RED:0));});chip.setOnClickListener(v->changeStoryLanguage(storyLangIds[ix]));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(42));cp.rightMargin=dp(9);chips.addView(chip,cp);}
  storyGrid=new LinearLayout(this);storyGrid.setOrientation(1);storyGrid.setPadding(dp(30),dp(4),dp(30),dp(24));content.addView(storyGrid,new LinearLayout.LayoutParams(-1,-2));
  loadStoryPage(0);
 }
 private void changeStoryLanguage(int langId){
  if(storyLangId==langId)return;
  storyLangId=langId;storyPage=0;storyHasMore=true;storyLoading=false;storyRequestId++;storyRowItems=0;storyCurrentRow=null;
  // The feed loader selects this language and fetches its page sequentially.
  // Do not fire a second language-selection request in parallel: it can race the feed request.
  showStoryTvHome();
 }
 private JSONObject storyInfo(JSONObject item){JSONObject d=item==null?null:item.optJSONObject("data");JSONObject s=d==null?null:d.optJSONObject("showInfo");return s==null?new JSONObject():s;}
 private String storyPoster(JSONObject show){String u=show.optString("imageUrl","");if(u.startsWith("//"))u="https:"+u;return u;}
 private void loadStoryPage(int page){
  if(storyLoading||(!storyHasMore&&page>0))return;storyLoading=true;final int requestId=storyRequestId,requestedLang=storyLangId;
  if(page==0&&storyGrid.getChildCount()==0){FrameLayout loading=new FrameLayout(this);ProgressBar spinner=new ProgressBar(this);spinner.setIndeterminate(true);if(android.os.Build.VERSION.SDK_INT>=21)spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(RED));loading.addView(spinner,new FrameLayout.LayoutParams(dp(54),dp(54),Gravity.CENTER));storyGrid.addView(loading,new LinearLayout.LayoutParams(-1,Math.max(dp(250),getResources().getDisplayMetrics().heightPixels-dp(150))));}
  new Thread(()->{try{
   storyApi.selectLanguage(requestedLang);JSONObject response=storyApi.explore(page,50,requestedLang);JSONObject data=response.optJSONObject("data");JSONArray rows=data==null?null:data.optJSONArray("content");if(rows==null)rows=new JSONArray();final JSONArray result=rows;
   runOnUiThread(()->{
    if(getSelectedServer()!=3||requestId!=storyRequestId||requestedLang!=storyLangId)return;
    if(page==0){storyGrid.removeAllViews();storyRowItems=0;storyCurrentRow=null;}
    int count=0;for(int i=0;i<result.length();i++){JSONObject item=result.optJSONObject(i);JSONObject show=storyInfo(item);if(show.optString("id").isEmpty())continue;count++;if(storyCurrentRow==null||storyRowItems>=6){storyCurrentRow=new LinearLayout(this);storyCurrentRow.setGravity(Gravity.TOP);storyCurrentRow.setOrientation(0);storyGrid.addView(storyCurrentRow,new LinearLayout.LayoutParams(-1,-2));storyRowItems=0;}addStoryCard(storyCurrentRow,show,item);storyRowItems++;}

    storyHasMore=result.length()>=50;storyPage=page+1;storyLoading=false;
    if(page==0&&count==0){TextView empty=txt("No dramas were returned by Story TV.",16,MUTED,false);empty.setPadding(dp(8),dp(24),dp(8),dp(24));storyGrid.addView(empty);}
    pageScroll.setOnScrollChangeListener((View v,int sx,int sy,int osx,int osy)->{View child=pageScroll.getChildAt(0);if(child!=null&&storyHasMore&&!storyLoading&&child.getBottom()-sy-pageScroll.getHeight()<dp(500))loadStoryPage(storyPage);});
   });
  }catch(Exception e){runOnUiThread(()->{if(requestId!=storyRequestId||requestedLang!=storyLangId)return;storyLoading=false;storyGrid.removeAllViews();TextView error=txt("Story TV feed unavailable: "+e.getMessage(),15,WHITE,false);error.setPadding(dp(12),dp(25),dp(12),dp(15));storyGrid.addView(error);TextView retry=button("RETRY",false,()->loadStoryPage(page));storyGrid.addView(retry,new LinearLayout.LayoutParams(dp(150),dp(46)));});}}).start();
 }
 private void addStoryCard(LinearLayout row,JSONObject show,JSONObject raw){
  LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setFocusable(true);card.setClickable(true);card.setBackground(bg(PANEL,0));card.setPadding(dp(3),dp(3),dp(3),dp(8));
  int width=Math.max(dp(42),(getResources().getDisplayMetrics().widthPixels-dp(60)-dp(6)*5)/6);
  ImageView poster=new ImageView(this);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);poster.setBackgroundColor(0xff252525);card.addView(poster,new LinearLayout.LayoutParams(-1,(int)(width*16f/9f)));
  String image=storyPoster(show);if(!image.isEmpty())Glide.with(this).load(image).placeholder(new android.graphics.drawable.ColorDrawable(0xff252525)).error(new android.graphics.drawable.ColorDrawable(0xff333333)).centerCrop().into(poster);
  TextView name=txt(show.optString("title","Untitled Drama"),11,WHITE,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(3),dp(5),dp(3),0);card.addView(name);
  card.setOnFocusChangeListener((v,focus)->{card.setBackground(bg(focus?0xff302024:PANEL,focus?RED:0));card.animate().scaleX(focus?1.035f:1f).scaleY(focus?1.035f:1f).setDuration(100).start();card.setElevation(focus?dp(8):0);});
  card.setOnClickListener(v->openStoryDetail(show,raw));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,-2);lp.leftMargin=dp(3);lp.rightMargin=dp(3);lp.bottomMargin=dp(12);row.addView(card,lp);
 }

 private String storyValue(Object value){
  if(value==null||value==JSONObject.NULL)return "";
  if(value instanceof JSONArray){JSONArray a=(JSONArray)value;ArrayList<String> parts=new ArrayList<>();for(int i=0;i<a.length();i++){String v=storyValue(a.opt(i));if(!v.isEmpty())parts.add(v);}return android.text.TextUtils.join(", ",parts);}
  if(value instanceof JSONObject){JSONObject o=(JSONObject)value;ArrayList<String> parts=new ArrayList<>();Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();String v=storyValue(o.opt(k));if(!v.isEmpty())parts.add(k+": "+v);}return android.text.TextUtils.join(" · ",parts);}
  String text=String.valueOf(value).trim();return text.equals("null")||text.equals("[]")||text.equals("{}")?"":text;
 }

 private void openStoryDetail(JSONObject show,JSONObject raw){
  if(previousPageViews==null){previousPageViews=new ArrayList<>();for(int i=0;i<content.getChildCount();i++)previousPageViews.add(content.getChildAt(i));content.removeAllViews();previousScrollY=pageScroll.getScrollY();previousTopVisible=top.getVisibility()==View.VISIBLE;}
  if(slideTask!=null)handler.removeCallbacks(slideTask);top.setVisibility(View.GONE);pageScroll.setOnScrollChangeListener(null);content.removeAllViews();pageScroll.scrollTo(0,0);
  final String showId=show.optString("id",raw.optString("showId",""));
  new Thread(()->{try{JSONObject episodeResponse=storyApi.episodeList(showId);JSONObject episodeData=episodeResponse.optJSONObject("data");if(episodeData==null)episodeData=episodeResponse;final JSONObject episodesInfo=episodeData;runOnUiThread(()->renderStoryDetail(show,raw,episodesInfo));}
   catch(Exception e){runOnUiThread(()->{renderStoryDetail(show,raw,new JSONObject());TextView error=detailText("Episode list could not be loaded: "+e.getMessage(),13,0xffff7777,false);error.setPadding(dp(48),dp(8),dp(48),dp(18));content.addView(error);});}
  }).start();
 }
 private void renderStoryDetail(JSONObject show,JSONObject raw,JSONObject episodeInfo){
  content.removeAllViews();String name=show.optString("title","Untitled Drama"),image=storyPoster(show);
  JSONObject metadata=show.optJSONObject("metadata");int episodeCount=episodeInfo.optInt("epCount",show.optInt("numOfEpisodes",0));
  String genre=storyValue(metadata==null?null:metadata.opt("genre"));
  FrameLayout hero=new FrameLayout(this);hero.setBackgroundColor(0xff171717);content.addView(hero,new LinearLayout.LayoutParams(-1,dp(430)));
  ImageView backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);hero.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
  if(!image.isEmpty())Glide.with(this).load(image).override(getResources().getDisplayMetrics().widthPixels,dp(430)).centerCrop().dontAnimate().priority(com.bumptech.glide.Priority.IMMEDIATE).into(backdrop);
  View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x22000000,0x66101010,0xff141414}));hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout body=new LinearLayout(this);body.setGravity(Gravity.BOTTOM);body.setPadding(dp(48),dp(30),dp(48),dp(26));body.setOrientation(0);hero.addView(body,new FrameLayout.LayoutParams(-1,-1));
  ImageView posterView=new ImageView(this);posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);posterView.setBackground(bg(PANEL,0xff333333));
  LinearLayout.LayoutParams posterLp=new LinearLayout.LayoutParams(dp(190),dp(285));posterLp.rightMargin=dp(30);body.addView(posterView,posterLp);
  if(!image.isEmpty())Glide.with(this).load(image).override(dp(380),dp(570)).centerCrop().diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL).into(posterView);
  LinearLayout info=new LinearLayout(this);info.setOrientation(1);info.setGravity(Gravity.BOTTOM);body.addView(info,new LinearLayout.LayoutParams(0,-2,1));
  TextView titleView=detailText(name,32,WHITE,true);titleView.setMaxLines(2);info.addView(titleView);
  LinearLayout meta=new LinearLayout(this);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(0,dp(10),0,dp(8));info.addView(meta);
  if(episodeCount>0)meta.addView(detailText(episodeCount+" Episodes",16,0xffdddddd,true));
  if(!genre.isEmpty())meta.addView(detailText("   "+genre,15,0xffdddddd,false));
  String synopsis=show.optString("description",show.optString("introduction",show.optString("overview","")));
  if(!synopsis.isEmpty()){TextView desc=detailText(synopsis,15,0xffeeeeee,false);desc.setMaxLines(4);desc.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams descLp=new LinearLayout.LayoutParams(-1,-2);descLp.topMargin=dp(10);info.addView(desc,descLp);}
  LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams actionLp=new LinearLayout.LayoutParams(-1,dp(54));actionLp.topMargin=dp(16);info.addView(actions,actionLp);
  JSONArray episodeRows=episodeInfo.optJSONArray("data");if(episodeRows==null)episodeRows=episodeInfo.optJSONArray("episodes");if(episodeRows==null)episodeRows=new JSONArray();final JSONArray finalEpisodes=episodeRows;
  final int firstEpisode=finalEpisodes.length()>0?Math.max(1,finalEpisodes.optJSONObject(0)==null?1:finalEpisodes.optJSONObject(0).optInt("index",1)):1;
  detailAction(actions,"▶  WATCH NOW",true,()->launchStoryEpisode(show,finalEpisodes,firstEpisode));
  detailAction(actions,"＋  MY LIST",false,()->toggleSaved("mylist",show));
  if(!synopsis.isEmpty()){detailHeading("Synopsis");TextView desc=detailText(synopsis,16,0xffcccccc,false);desc.setPadding(dp(48),0,dp(55),dp(8));content.addView(desc);}
  if(metadata!=null){Iterator<String> mk=metadata.keys();while(mk.hasNext()){String key=mk.next();if(key.equalsIgnoreCase("genre"))continue;String value=storyValue(metadata.opt(key));if(value.isEmpty())continue;detailHeading(key.replace('_',' ').toUpperCase());TextView val=detailText(value,15,0xffcccccc,false);val.setPadding(dp(48),0,dp(48),dp(10));content.addView(val);}}
  if(episodeRows.length()>0){
   detailHeading("Episodes");
   TextView episodeSummary=detailText(episodeCount+" episodes  ·  "+episodeInfo.optString("showTitle",name),13,MUTED,false);episodeSummary.setPadding(dp(48),0,dp(48),dp(10));content.addView(episodeSummary);
   HorizontalScrollView rangeScroll=new HorizontalScrollView(this);rangeScroll.setHorizontalScrollBarEnabled(false);LinearLayout rangeTabs=new LinearLayout(this);rangeTabs.setGravity(Gravity.CENTER_VERTICAL);rangeScroll.addView(rangeTabs,new HorizontalScrollView.LayoutParams(-2,dp(48)));LinearLayout.LayoutParams rangeLp=new LinearLayout.LayoutParams(-1,dp(52));rangeLp.leftMargin=dp(48);rangeLp.rightMargin=dp(48);content.addView(rangeScroll,rangeLp);
   LinearLayout episodeGrid=new LinearLayout(this);episodeGrid.setOrientation(1);episodeGrid.setPadding(dp(48),0,dp(48),dp(25));content.addView(episodeGrid);
   int rangeTotal=(finalEpisodes.length()+99)/100;int[] selectedRange={0};Runnable[] drawEpisodes={null};drawEpisodes[0]=()->{episodeGrid.removeAllViews();int from=selectedRange[0]*100,to=Math.min(finalEpisodes.length(),from+100);for(int base=from;base<to;base+=5){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,0,0,dp(8));episodeGrid.addView(row,new LinearLayout.LayoutParams(-1,dp(76)));for(int i=base;i<Math.min(to,base+5);i++){JSONObject ep=finalEpisodes.optJSONObject(i);if(ep==null)continue;int index=ep.optInt("index",i+1);String epTitle=ep.optString("epTitle","Episode "+index);boolean locked=ep.optBoolean("lock",false),watched=ep.optBoolean("isWtch",false);LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setGravity(Gravity.CENTER);tile.setFocusable(true);tile.setBackground(bg(0xff202020,0xff383838));TextView number=detailText("EPISODE "+index,13,WHITE,true);number.setGravity(Gravity.CENTER);tile.addView(number);tile.setOnFocusChangeListener((v,focus)->tile.setBackground(bg(focus?0xff3b171a:0xff202020,focus?RED:0xff383838)));tile.setOnClickListener(v->launchStoryEpisode(show,finalEpisodes,index));LinearLayout.LayoutParams tileLp=new LinearLayout.LayoutParams(0,dp(76),1);tileLp.rightMargin=dp(8);row.addView(tile,tileLp);}}};
   if(rangeTotal>1){for(int r=0;r<rangeTotal;r++){final int range=r;TextView tab=detailText((r*100+1)+"-"+Math.min(finalEpisodes.length(),(r+1)*100),13,WHITE,true);tab.setGravity(Gravity.CENTER);tab.setPadding(dp(18),0,dp(18),0);tab.setFocusable(true);tab.setBackground(bg(r==0?0xff3b171a:PANEL,r==0?RED:0));tab.setOnClickListener(v->{selectedRange[0]=range;for(int k=0;k<rangeTabs.getChildCount();k++)rangeTabs.getChildAt(k).setBackground(bg(k==range?0xff3b171a:PANEL,k==range?RED:0));drawEpisodes[0].run();});LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-2,dp(40));tlp.rightMargin=dp(8);rangeTabs.addView(tab,tlp);}}
   drawEpisodes[0].run();
  }else{detailHeading("Episodes");TextView empty=detailText("No episode entries were returned by Story TV for this title.",14,MUTED,false);empty.setPadding(dp(48),0,dp(48),dp(25));content.addView(empty);}
  // Back navigation is handled by the Android back key.
 }
 private void launchStoryEpisode(JSONObject show,JSONArray episodes,int episodeIndex){
  JSONObject chosen=null;for(int i=0;i<episodes.length();i++){JSONObject ep=episodes.optJSONObject(i);if(ep!=null&&ep.optInt("index",-1)==episodeIndex){chosen=ep;break;}}
  if(chosen==null){new AlertDialog.Builder(this).setTitle("Episode unavailable").setMessage("Story TV did not return this episode.").setPositiveButton("OK",null).show();return;}
  if(chosen.optBoolean("lock",false)){new AlertDialog.Builder(this).setTitle("Episode locked").setMessage("This episode is locked on Story TV.").setPositiveButton("OK",null).show();return;}
  Intent intent=new Intent(this,PlayerActivity.class);intent.putExtra("storyTv",true);intent.putExtra("storyShowId",show.optString("id",show.optString("showId","")));intent.putExtra("storyEpisode",episodeIndex);intent.putExtra("storyEpisodeCount",Math.max(episodeIndex,show.optInt("numOfEpisodes",episodes.length())));intent.putExtra("title",show.optString("title","Story TV"));intent.putExtra("episodeTitle",chosen.optString("epTitle","Episode "+episodeIndex));startActivity(intent);
 }

 private void showComingSoonServer(){
  top.setVisibility(View.VISIBLE);if(slideTask!=null)handler.removeCallbacks(slideTask);content.removeAllViews();
  int selected=getSelectedServer();if(selected<0||selected>=SERVER_NAMES.length)selected=0;
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setGravity(Gravity.CENTER);panel.setPadding(dp(30),dp(34),dp(30),dp(34));content.addView(panel,new LinearLayout.LayoutParams(-1,Math.max(dp(420),getResources().getDisplayMetrics().heightPixels-dp(90))));
  ImageView logo=new ImageView(this);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);panel.addView(logo,new LinearLayout.LayoutParams(dp(150),dp(150)));
  Glide.with(this).load(SERVER_LOGOS[selected]).placeholder(new android.graphics.drawable.ColorDrawable(0xff242424)).error(new android.graphics.drawable.ColorDrawable(0xff242424)).fitCenter().into(logo);
  TextView name=txt(SERVER_NAMES[selected],27,WHITE,true);name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(18);panel.addView(name,np);
  TextView soon=txt("Homepage is not configured yet.",16,MUTED,true);soon.setGravity(Gravity.CENTER);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(12);panel.addView(soon,sp);
  TextView note=txt("This server homepage is being prepared.",14,MUTED,false);note.setGravity(Gravity.CENTER);LinearLayout.LayoutParams noteLp=new LinearLayout.LayoutParams(-1,-2);noteLp.topMargin=dp(8);panel.addView(note,noteLp);
  TextView change=button("CHOOSE ANOTHER SERVER",false,()->showSettings());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(270),dp(48));cp.topMargin=dp(26);panel.addView(change,cp);
 }
 @Override public void onBackPressed(){if(slideTask!=null)handler.removeCallbacks(slideTask);if(previousPageViews!=null){content.removeAllViews();for(View v:previousPageViews){if(v.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)v.getParent()).removeView(v);content.addView(v);}previousPageViews=null;top.setVisibility(previousTopVisible?View.VISIBLE:View.GONE);pageScroll.post(()->pageScroll.scrollTo(0,previousScrollY));return;}if(homePageViews!=null){content.removeAllViews();for(View v:homePageViews){if(v.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)v.getParent()).removeView(v);content.addView(v);}homePageViews=null;activeSearchKeyword="";searchHasMore=false;searchLoading=false;pageScroll.setOnScrollChangeListener(null);top.setVisibility(View.VISIBLE);pageScroll.post(()->pageScroll.scrollTo(0,0));return;}new AlertDialog.Builder(this).setTitle("Exit MovieBox?").setMessage("Do you want to close the application?").setNegativeButton("No",(d,w)->d.dismiss()).setPositiveButton("Yes",(d,w)->{if(slideTask!=null)handler.removeCallbacks(slideTask);finish();}).show();}
 @Override protected void onDestroy(){super.onDestroy();if(slideTask!=null)handler.removeCallbacks(slideTask);}
}