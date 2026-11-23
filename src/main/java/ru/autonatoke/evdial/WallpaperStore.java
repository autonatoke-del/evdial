package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import android.view.View;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Imports only the one user-selected image. No gallery permission, cloud upload, or EXIF retention. */
final class WallpaperStore {
 private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final WeakHashMap<View,Long> PENDING=new WeakHashMap<>();
 private static long sequence;private static volatile Bitmap cached;private static volatile long cachedRevision=-1;
 interface Result {void done(boolean success,String message);}
 private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("drive",0);}
 private static File file(Context c){return file(c,UiProfiles.id(c));}
 private static File file(Context c,String profile){
  File scoped=new File(c.getFilesDir(),"evdial-wallpaper-"+profile+".jpg");
  File old=new File(c.getFilesDir(),"evdial-wallpaper.jpg");
  return UiProfiles.EV.equals(profile)&&!scoped.isFile()&&old.isFile()?old:scoped;
 }
 static boolean enabled(Context c){String id=UiProfiles.id(c);
  return prefs(c).getBoolean("custom_wallpaper_"+id,UiProfiles.EV.equals(id)&&prefs(c).getBoolean("custom_wallpaper",false))&&file(c).isFile();
 }
 static void cancel(View target){PENDING.remove(target);}
 static void importPhoto(Activity a,Uri uri,Result result){
  Context app=a.getApplicationContext();WeakReference<Activity> owner=new WeakReference<>(a);
  final String activeProfile=UiProfiles.id(a);
  WORK.execute(()->{
   File input=null;Bitmap bitmap=null;String error=null;boolean ok=false;
   try{
    if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("Выберите фотографию через системное окно");
    input=File.createTempFile("ev-photo-",".tmp",app.getCacheDir());
    try(InputStream in=app.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(input)){
     if(in==null)throw new IOException("Фото недоступно");byte[] buf=new byte[16384];int n;long size=0;
     while((n=in.read(buf))!=-1){size+=n;if(size>24L*1024*1024)throw new IOException("Выберите фото размером до 24 МБ");out.write(buf,0,n);}
    }
    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(input.getPath(),bounds);
    if(bounds.outWidth<1||bounds.outHeight<1)throw new IOException("Этот формат изображения не поддерживается");
    BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=1;
    while(Math.max(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>2048)opts.inSampleSize*=2;
    bitmap=BitmapFactory.decodeFile(input.getPath(),opts);if(bitmap==null)throw new IOException("Не удалось прочитать фото");
    int orientation=ExifInterface.ORIENTATION_NORMAL;
    try{orientation=new ExifInterface(input.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
    Matrix m=new Matrix();switch(orientation){
     case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:m.setScale(-1,1);break;
     case ExifInterface.ORIENTATION_ROTATE_180:m.setRotate(180);break;
     case ExifInterface.ORIENTATION_FLIP_VERTICAL:m.setScale(1,-1);break;
     case ExifInterface.ORIENTATION_TRANSPOSE:m.setRotate(90);m.postScale(-1,1);break;
     case ExifInterface.ORIENTATION_ROTATE_90:m.setRotate(90);break;
     case ExifInterface.ORIENTATION_TRANSVERSE:m.setRotate(-90);m.postScale(-1,1);break;
     case ExifInterface.ORIENTATION_ROTATE_270:m.setRotate(-90);break;
    }
    if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
    AtomicFile destination=new AtomicFile(file(app,activeProfile));FileOutputStream out=null;
    try{out=destination.startWrite();if(!bitmap.compress(Bitmap.CompressFormat.JPEG,91,out))throw new IOException("Не удалось сохранить фото");destination.finishWrite(out);}
    catch(Exception failed){destination.failWrite(out);throw failed;}
    long rev=System.currentTimeMillis();cached=bitmap;cachedRevision=rev;bitmap=null;
    prefs(app).edit().putBoolean("custom_wallpaper_"+activeProfile,true)
     .putLong("wallpaper_revision_"+activeProfile,rev).putInt("contrast_"+activeProfile,2).apply();ok=true;
   }catch(Exception|OutOfMemoryError ex){error=ex.getMessage();if(error==null||error.length()>120)error="Не удалось открыть изображение. Выберите другое фото.";}
   finally{if(input!=null)input.delete();if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();}
   final boolean success=ok;final String message=error;
   MAIN.post(()->{Activity target=owner.get();if(target!=null&&!target.isFinishing()&&!target.isDestroyed())result.done(success,message);});
  });
 }
 static void apply(Activity a,View target,int mode){
  if(a==null||target==null)return;Context app=a.getApplicationContext();String profile=UiProfiles.id(a);long rev=prefs(app).getLong("wallpaper_revision_"+profile,prefs(app).getLong("wallpaper_revision",0));
  final long token=++sequence;PENDING.put(target,token);
  if(cached!=null&&cachedRevision==rev){target.setBackground(new PhotoBackground(cached,mode));return;}
  target.setBackgroundColor(0xff091920);WeakReference<View> view=new WeakReference<>(target);
  WORK.execute(()->{
   Bitmap b=null;try{BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;b=BitmapFactory.decodeFile(file(app,profile).getPath(),o);}catch(RuntimeException|OutOfMemoryError ignored){}
   final Bitmap decoded=b;
   MAIN.post(()->{View v=view.get();if(v==null||!Long.valueOf(token).equals(PENDING.get(v))||!enabled(app)||rev!=prefs(app).getLong("wallpaper_revision_"+profile,prefs(app).getLong("wallpaper_revision",0)))return;
    if(decoded!=null){cached=decoded;cachedRevision=rev;v.setBackground(new PhotoBackground(decoded,mode));}
   });
  });
 }
 private static final class PhotoBackground extends Drawable {
  private final Bitmap bitmap;private final int mode;private final Paint p=new Paint(3);private int alpha=255;
  PhotoBackground(Bitmap bitmap,int mode){this.bitmap=bitmap;this.mode=mode;}
  @Override public void draw(Canvas c){Rect b=getBounds();if(b.isEmpty())return;int save=c.saveLayerAlpha(b.left,b.top,b.right,b.bottom,alpha);
   p.setColor(0xff091920);c.drawRect(b,p);if(mode>0&&bitmap!=null){float s=Math.max(b.width()/(float)bitmap.getWidth(),b.height()/(float)bitmap.getHeight());float w=bitmap.getWidth()*s,h=bitmap.getHeight()*s;
    p.setColor(Color.WHITE);c.drawBitmap(bitmap,null,new RectF(b.centerX()-w/2,b.centerY()-h/2,b.centerX()+w/2,b.centerY()+h/2),p);
    p.setColor(mode==2?0x55051018:0x99051018);c.drawRect(b,p);}
   c.restoreToCount(save);
  }
  @Override public void setAlpha(int value){alpha=value;invalidateSelf();} @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
