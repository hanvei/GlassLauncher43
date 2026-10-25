package tv.glasslauncher43;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.io.File;
import java.io.FilenameFilter;

public class MainActivity extends Activity {
  private Screen screen;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setFlags(1024,1024);
    screen=new Screen();
    setContentView(screen);
  }
  @Override protected void onResume() { super.onResume(); if(screen!=null) screen.reload(); }
  @Override public void onBackPressed() { if(screen!=null && screen.home()) return; super.onBackPressed(); }
  private int handledKey=-1;
  @Override public boolean dispatchKeyEvent(KeyEvent e) {
    if(screen!=null && e.getAction()==KeyEvent.ACTION_DOWN) screen.revealHint();
    if(screen!=null && e.getAction()==KeyEvent.ACTION_DOWN && screen.handleKey(e.getKeyCode(),e)) {
      handledKey=e.getKeyCode(); return true;
    }
    if(e.getAction()==KeyEvent.ACTION_UP && e.getKeyCode()==handledKey) {
      handledKey=-1; return true;
    }
    return super.dispatchKeyEvent(e);
  }

  static class App {
    String label,pkg;
    Drawable icon;
    Intent intent;
  }

  class Screen extends View {
    final Paint p=new Paint(3);
    final int[] colors={0xffe45182,0xff178bca,0xff2584d2,0xffef9019,0xffe35a19,
                        0xff21aa68,0xff673db0,0xffba3a57,0xff267db7,0xff9d4cae};
    final RectF r=new RectF();
    final ArrayList<App> all=new ArrayList<App>();
    final ArrayList<App> featured=new ArrayList<App>();
    final ArrayList<App> rest=new ArrayList<App>();
    final Path rear=new Path(),front=new Path();
    final Shader sky=new LinearGradient(0,0,0,710,0xff649aca,0xffdbe8e7,Shader.TileMode.CLAMP);
    final Shader sand=new LinearGradient(0,570,0,1080,0xffe9b978,0xff77563e,Shader.TileMode.CLAMP);
    final Runnable clock=new Runnable(){ public void run(){ refreshWallpaper();invalidate(); } };
    final SharedPreferences layout=getSharedPreferences("home_layout",MODE_PRIVATE);
    final SharedPreferences wallpaperPrefs=getSharedPreferences("wallpaper_rotation",MODE_PRIVATE);
    final File wallpaperDir=new File(Environment.getExternalStorageDirectory(),"壁纸");
    final long wallpaperInterval=30*60*1000L;
    Bitmap wallpaper;
    String wallpaperPath;
    boolean wallpaperLoading=false;
    final Runnable clearNotice=new Runnable(){ public void run(){ notice=null; invalidate(); } };
    final Runnable hideHint=new Runnable(){ public void run(){ hintVisible=false; invalidate(); } };
    boolean appsPage=false,editMode=false,removeFocus=false,appActions=false,libraryEdit=false,hintVisible=false;
    int homeAction=0,libraryAction=0;
    String notice;
    int homeFocus=0,appFocus=0;
    float scale=1,dx=0,dy=0;

    Screen() {
      super(MainActivity.this);
      setFocusable(true);
      setFocusableInTouchMode(true);
      requestFocus();
      rear.moveTo(0,650);rear.cubicTo(330,545,475,635,700,640);
      rear.cubicTo(985,662,1060,480,1260,555);
      rear.cubicTo(1500,645,1595,420,1920,580);
      rear.lineTo(1920,1080);rear.lineTo(0,1080);rear.close();
      front.moveTo(0,610);front.cubicTo(330,675,585,690,800,668);
      front.cubicTo(1090,600,1270,750,1540,553);
      front.cubicTo(1730,600,1830,560,1920,620);
      front.lineTo(1920,1080);front.lineTo(0,1080);front.close();
      reload();
    }

    void reload() {
      all.clear();
      PackageManager pm=getPackageManager();
      HashSet<String> seen=new HashSet<String>();
      collect(pm,Intent.CATEGORY_LEANBACK_LAUNCHER,seen);
      collect(pm,Intent.CATEGORY_LAUNCHER,seen);
      Collections.sort(all,new Comparator<App>(){ public int compare(App a,App b){return a.label.compareToIgnoreCase(b.label);} });
      ArrayList<App> ranked=new ArrayList<App>(all);
      Collections.sort(ranked,new Comparator<App>(){ public int compare(App a,App b){
        int d=rank(a)-rank(b);return d!=0?d:a.label.compareToIgnoreCase(b.label);
      }});
      featured.clear();
      if(layout.contains("order")) {
        String[] saved=layout.getString("order","").split(",");
        for(String pkg:saved) for(App a:all) {
          if(a.pkg.equals(pkg)&&!featured.contains(a)&&featured.size()<5) featured.add(a);
        }
      } else {
        for(int i=0;i<ranked.size()&&i<5;i++) featured.add(ranked.get(i));
      }
      refreshRest();
      refreshWallpaper();
      invalidate();
    }

    void refreshWallpaper(){
      if(wallpaperLoading)return;
      if(!wallpaperDir.exists())wallpaperDir.mkdirs();
      File[] files=wallpaperDir.listFiles(new FilenameFilter(){
        public boolean accept(File dir,String name){
          String lower=name.toLowerCase(Locale.US);
          return lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")||lower.endsWith(".webp");
        }
      });
      if(files==null||files.length==0){
        if(wallpaper!=null){wallpaper.recycle();wallpaper=null;wallpaperPath=null;invalidate();}
        return;
      }
      java.util.Arrays.sort(files,new Comparator<File>(){
        public int compare(File a,File b){return a.getName().compareToIgnoreCase(b.getName());}
      });
      long now=System.currentTimeMillis();
      long last=wallpaperPrefs.getLong("last_switch",0);
      int index=wallpaperPrefs.getInt("index",0);
      if(index<0||index>=files.length)index=0;
      if(last>0&&(now<last||now-last>=wallpaperInterval)){
        index=(index+1)%files.length;last=now;
      }else if(last==0)last=now;
      wallpaperPrefs.edit().putInt("index",index).putLong("last_switch",last).apply();
      final File selected=files[index];
      if(selected.getAbsolutePath().equals(wallpaperPath)&&wallpaper!=null)return;
      final int selectedIndex=index;
      wallpaperLoading=true;
      new Thread(new Runnable(){public void run(){
        Bitmap decoded=null;
        try{
          BitmapFactory.Options bounds=new BitmapFactory.Options();
          bounds.inJustDecodeBounds=true;
          BitmapFactory.decodeFile(selected.getAbsolutePath(),bounds);
          if(bounds.outWidth>0&&bounds.outHeight>0){
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inPreferredConfig=Bitmap.Config.RGB_565;
            options.inSampleSize=1;
            while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1920)
              options.inSampleSize*=2;
            decoded=BitmapFactory.decodeFile(selected.getAbsolutePath(),options);
          }
        }catch(OutOfMemoryError ignored){}catch(Exception ignored){}
        final Bitmap ready=decoded;
        post(new Runnable(){public void run(){
          wallpaperLoading=false;
          if(ready==null){
            wallpaperPrefs.edit().putInt("index",(selectedIndex+1)).putLong("last_switch",0).apply();
            return;
          }
          Bitmap old=wallpaper;
          wallpaper=ready;wallpaperPath=selected.getAbsolutePath();
          if(old!=null&&old!=ready)old.recycle();
          invalidate();
        }});
      }},"wallpaper-loader").start();
    }

    void collect(PackageManager pm,String category,HashSet<String> seen) {
      Intent q=new Intent(Intent.ACTION_MAIN);q.addCategory(category);
      List<ResolveInfo> found=pm.queryIntentActivities(q,0);
      for(ResolveInfo info:found) {
        if(info.activityInfo==null) continue;
        String pkg=info.activityInfo.packageName;
        if(pkg.equals(getPackageName())||!seen.add(pkg)) continue;
        App a=new App();a.pkg=pkg;
        CharSequence label=info.loadLabel(pm);a.label=label==null?pkg:label.toString();
        a.icon=info.loadIcon(pm);
        a.intent=new Intent(Intent.ACTION_MAIN);a.intent.addCategory(category);
        a.intent.setClassName(pkg,info.activityInfo.name);
        all.add(a);
      }
    }

    int rank(App a) {
      String s=a.label.toLowerCase(Locale.US);
      if(a.pkg.equals("com.letv.android.tv.letvlive")||s.contains("电视")||s.equals("tv")) return 0;
      if(a.pkg.equals("com.github.tvbox.mfans")||s.contains("视频")||s.contains("video")) return 1;
      if(s.contains("音乐")||s.contains("music")) return 2;
      if(a.pkg.equals("com.dangbeimarket")||s.contains("市场")||s.contains("store")) return 3;
      if(s.contains("设置")||s.contains("setting")) return 4;
      return 10;
    }

    void refreshRest(){
      rest.clear();for(App a:all)if(!featured.contains(a))rest.add(a);
      if(layout.contains("library_order")){
        ArrayList<App> ordered=new ArrayList<App>();
        String[] saved=layout.getString("library_order","").split(",");
        for(String pkg:saved)for(App a:rest)if(a.pkg.equals(pkg)&&!ordered.contains(a))ordered.add(a);
        for(App a:rest)if(!ordered.contains(a))ordered.add(a);
        rest.clear();rest.addAll(ordered);
      }
      homeFocus=Math.min(homeFocus,Math.max(0,featured.size()-1));
      appFocus=Math.min(appFocus,Math.max(0,rest.size()-1));
    }
    void saveLibraryOrder(){
      StringBuilder value=new StringBuilder();
      for(App a:rest){if(value.length()>0)value.append(',');value.append(a.pkg);}
      layout.edit().putString("library_order",value.toString()).apply();
      invalidate();
    }
    void saveOrder(){
      StringBuilder value=new StringBuilder();
      for(App a:featured){if(value.length()>0)value.append(',');value.append(a.pkg);}
      layout.edit().putString("order",value.toString()).apply();
      refreshRest();invalidate();
    }
    void message(String value){
      notice=value;removeCallbacks(clearNotice);postDelayed(clearNotice,2500);invalidate();
    }
    void revealHint(){
      hintVisible=true;removeCallbacks(hideHint);postDelayed(hideHint,5000);invalidate();
    }
    void uninstall(App app){
      Intent intent=new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+app.pkg));
      if(intent.resolveActivity(getPackageManager())==null){message("系统没有可用的卸载界面");return;}
      try{startActivity(intent);}catch(Exception ignored){message("无法打开系统卸载界面");}
    }
    boolean home(){
      if(libraryEdit){libraryEdit=false;invalidate();return true;}
      if(appActions){appActions=false;invalidate();return true;}
      if(editMode){editMode=false;removeFocus=false;invalidate();return true;}
      if(!appsPage)return false;appsPage=false;invalidate();return true;
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
      super.onSizeChanged(w,h,ow,oh);
      scale=Math.min(w/1920f,h/1080f);dx=(w-1920*scale)/2;dy=(h-1080*scale)/2;
    }
    @Override protected void onDraw(Canvas c){
      c.drawColor(0xff202329);c.save();c.translate(dx,dy);c.scale(scale,scale);
      if(appsPage)drawApps(c);else drawHome(c);
      c.restore();removeCallbacks(clock);postDelayed(clock,30000);
    }

    void drawHome(Canvas c){
      if(wallpaper!=null&&!wallpaper.isRecycled()){
        float imageRatio=wallpaper.getWidth()/(float)wallpaper.getHeight();
        float screenRatio=1920f/1080f;
        int cropW=wallpaper.getWidth(),cropH=wallpaper.getHeight();
        if(imageRatio>screenRatio)cropW=(int)(cropH*screenRatio);
        else cropH=(int)(cropW/screenRatio);
        int left=(wallpaper.getWidth()-cropW)/2,top=(wallpaper.getHeight()-cropH)/2;
        p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(Color.WHITE);
        c.drawBitmap(wallpaper,new Rect(left,top,left+cropW,top+cropH),new Rect(0,0,1920,1080),p);
        p.setColor(0x26000000);c.drawRect(0,0,1920,1080,p);
      }else{
        p.setStyle(Paint.Style.FILL);p.setShader(sky);c.drawRect(0,0,1920,1080,p);p.setShader(null);
        p.setColor(0xffac7855);c.drawPath(rear,p);
        p.setShader(sand);c.drawPath(front,p);p.setShader(null);
      }
      p.setColor(0x70415369);round(c,110,180,710,465,28);
      p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0x99ffffff);
      round(c,110,180,710,465,28);p.setStyle(Paint.Style.FILL);
      text(c,DateFormat.format("HH:mm",new Date()).toString(),165,327,100,Color.WHITE,false);
      text(c,DateFormat.format("yyyy/MM/dd  EEEE",new Date()).toString(),170,390,31,Color.WHITE,false);
      text(c,editMode?"编辑首页":"Glass Home",1710,72,31,Color.WHITE,true);
      int n=featured.size(),start=(1920-n*280-Math.max(0,n-1)*40)/2;
      for(int i=0;i<n;i++)tile(c,featured.get(i),start+i*320,772,280,180,i==homeFocus,true,i);
      if(n==0)text(c,"首页暂无图标，按下进入全部应用添加",960,855,40,Color.WHITE,true);
      if(editMode){
        text(c,removeFocus?"← → 选择操作     ↑ 返回图标":"← → 移动图标     ↓ 图标操作     确定完成",960,714,30,Color.WHITE,true);
        action(c,610,982,930,1052,"从首页移除",removeFocus&&homeAction==0);
        action(c,990,982,1310,1052,"卸载应用",removeFocus&&homeAction==1);
      }else if(hintVisible)text(c,"↓ 更多应用     菜单键编辑首页",960,1030,30,Color.WHITE,true);
      if(notice!=null)text(c,notice,960,680,31,Color.WHITE,true);
    }

    void drawApps(Canvas c){
      c.drawColor(0xff22262c);
      text(c,libraryEdit?"移动应用":"全部应用",110,93,48,Color.WHITE,false);
      int page=appFocus/20,total=Math.max(1,(rest.size()+19)/20);
      text(c,(page+1)+" / "+total,1790,90,31,0xffbec7ce,true);
      for(int i=0;i<20&&page*20+i<rest.size();i++){
        int index=page*20+i;
        tile(c,rest.get(index),110+(i%5)*347,150+(i/5)*211,305,174,index==appFocus,false,i);
      }
      if(rest.isEmpty())text(c,"其他应用将在这里显示",960,550,42,Color.WHITE,true);
      if(libraryEdit)text(c,"方向键移动图标     确定完成",960,1040,28,Color.WHITE,true);
      else if(appActions){
        action(c,390,982,740,1052,"移动位置",libraryAction==0);
        action(c,785,982,1135,1052,"添加到首页",libraryAction==1);
        action(c,1180,982,1530,1052,"卸载应用",libraryAction==2);
      }else text(c,"↑ 返回首页     菜单键：图标操作",960,1040,28,0xffb5bdc6,true);
      if(notice!=null)text(c,notice,960,1000,30,Color.WHITE,true);
    }

    void tile(Canvas c,App a,int x,int y,int w,int h,boolean focused,boolean top,int i){
      p.setStyle(Paint.Style.FILL);p.setShader(null);
      p.setColor(colors[(top?i:i+(i/5)*3)%colors.length]);
      round(c,x,y,x+w,y+h,20);
      if(focused){
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(6);p.setColor(Color.WHITE);
        round(c,x-5,y-5,x+w+5,y+h+5,24);p.setStyle(Paint.Style.FILL);
      }
      int size=top?116:108;
      if(a.icon!=null){
        a.icon.setBounds(x+(w-size)/2,y+12,x+(w+size)/2,y+12+size);
        a.icon.draw(c);
      }
      fitted(c,a.label,x+w/2f,y+h-19,w-25,top?30:28);
    }
    void action(Canvas c,int l,int t,int right,int bottom,String label,boolean chosen){
      p.setStyle(Paint.Style.FILL);p.setShader(null);
      p.setColor(chosen?0xffedf4ff:0xdd1c2936);round(c,l,t,right,bottom,16);
      text(c,label,(l+right)/2f,bottom-23,29,chosen?0xff172436:Color.WHITE,true);
    }

    void fitted(Canvas c,String label,float cx,float y,int max,float size){
      p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(Color.WHITE);p.setTextSize(size);
      String s=label;
      while(s.length()>1&&p.measureText(s+"…")>max)s=s.substring(0,s.length()-1);
      if(!s.equals(label))s+="…";
      c.drawText(s,cx-p.measureText(s)/2,y,p);
    }
    void text(Canvas c,String s,float x,float y,float size,int color,boolean center){
      p.setStyle(Paint.Style.FILL);p.setShader(null);p.setColor(color);p.setTextSize(size);
      c.drawText(s,center?x-p.measureText(s)/2:x,y,p);
    }
    void round(Canvas c,float l,float t,float right,float bottom,float rad){
      r.set(l,t,right,bottom);c.drawRoundRect(r,rad,rad,p);
    }
    void open(App a){try{startActivity(a.intent);}catch(Exception ignored){}}

    boolean handleKey(int key,KeyEvent e){
      if(key==KeyEvent.KEYCODE_BACK)return home();
      if(appsPage){
        if(libraryEdit){
          if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){
            if(e.getRepeatCount()==0)libraryEdit=false;
          }else{
            int target=appFocus;
            if(key==KeyEvent.KEYCODE_DPAD_UP)target-=5;
            else if(key==KeyEvent.KEYCODE_DPAD_DOWN)target+=5;
            else if(key==KeyEvent.KEYCODE_DPAD_LEFT)target--;
            else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)target++;
            else return false;
            if(target>=0&&target<rest.size()){
              Collections.swap(rest,appFocus,target);appFocus=target;saveLibraryOrder();
            }
          }
        }else if(appActions){
          if(key==KeyEvent.KEYCODE_MENU){appActions=false;}
          else if(key==KeyEvent.KEYCODE_DPAD_LEFT)libraryAction=Math.max(0,libraryAction-1);
          else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)libraryAction=Math.min(2,libraryAction+1);
          else if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){
            if(e.getRepeatCount()==0&&!rest.isEmpty()){
              App selected=rest.get(appFocus);
              appActions=false;
              if(libraryAction==0)libraryEdit=true;
              else if(libraryAction==1){
                if(featured.size()>=5)message("首页已满，请先移除一个图标");
                else {featured.add(selected);saveOrder();message("已添加到首页");}
              }else uninstall(selected);
            }
          }else return false;
        }else if(key==KeyEvent.KEYCODE_MENU){
          if(e.getRepeatCount()==0&&!rest.isEmpty()){appActions=true;libraryAction=0;}
        }else if(key==KeyEvent.KEYCODE_DPAD_UP){
          if(appFocus<5)return home();appFocus-=5;
        }else if(key==KeyEvent.KEYCODE_DPAD_DOWN)appFocus=Math.min(Math.max(0,rest.size()-1),appFocus+5);
        else if(key==KeyEvent.KEYCODE_DPAD_LEFT)appFocus=Math.max(0,appFocus-1);
        else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)appFocus=Math.min(Math.max(0,rest.size()-1),appFocus+1);
        else if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){
          if(e.getRepeatCount()==0&&!rest.isEmpty())open(rest.get(appFocus));
        }else return false;
      }else if(editMode){
        if(key==KeyEvent.KEYCODE_MENU){editMode=false;removeFocus=false;}
        else if(key==KeyEvent.KEYCODE_DPAD_DOWN){removeFocus=true;homeAction=0;}
        else if(key==KeyEvent.KEYCODE_DPAD_UP)removeFocus=false;
        else if(key==KeyEvent.KEYCODE_DPAD_LEFT){
          if(removeFocus)homeAction=0;
          else if(homeFocus>0){Collections.swap(featured,homeFocus,homeFocus-1);homeFocus--;saveOrder();}
        }else if(key==KeyEvent.KEYCODE_DPAD_RIGHT){
          if(removeFocus)homeAction=1;
          else if(homeFocus<featured.size()-1){Collections.swap(featured,homeFocus,homeFocus+1);homeFocus++;saveOrder();}
        }else if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){
          if(e.getRepeatCount()==0){
            if(removeFocus&&!featured.isEmpty()){
              App selected=featured.get(homeFocus);
              removeFocus=false;editMode=false;
              if(homeAction==0){featured.remove(homeFocus);saveOrder();}
              else uninstall(selected);
            }else editMode=false;
          }
        }else return false;
      }else{
        if(key==KeyEvent.KEYCODE_MENU){if(e.getRepeatCount()==0&& !featured.isEmpty())editMode=true;}
        else if(key==KeyEvent.KEYCODE_DPAD_DOWN){appsPage=true;appFocus=0;}
        else if(key==KeyEvent.KEYCODE_DPAD_LEFT)homeFocus=Math.max(0,homeFocus-1);
        else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)homeFocus=Math.min(Math.max(0,featured.size()-1),homeFocus+1);
        else if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){
          if(e.getRepeatCount()==0&&!featured.isEmpty())open(featured.get(homeFocus));
        }else return false;
      }
      invalidate();return true;
    }
  }
}
