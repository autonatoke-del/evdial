package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Native controls; the artwork is a background only. No fake controls are painted into an image. */
final class ElectricUi {
 static final int WHITE=0xfff1fbff, MUTED=0xffb9d6df, GREEN=0xff79f4a6, CYAN=0xff6eddfa;
 static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static String tr(Context c,String ru,String en){return c.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("ru")?ru:en;}
 static TextView text(Activity a,String s,int sp,int color,boolean bold){
  TextView v=new TextView(a);v.setText(s);v.setTextSize(UiProfiles.type(a,sp));v.setTextColor(color==WHITE?UiProfiles.text(a):color==MUTED?UiProfiles.muted(a):color==GREEN?UiProfiles.accent(a):color==CYAN?UiProfiles.secondary(a):color);
  v.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",android.graphics.Typeface.NORMAL));v.setIncludeFontPadding(false);return v;
 }
 static Drawable surface(Context a,int radius,boolean selected){
  int panel=UiProfiles.panel(a),base=UiProfiles.opacity(UiProfiles.active(a).base,UiProfiles.value(a,UiProfiles.PANEL));
  GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
    selected?new int[]{UiProfiles.opacity(UiProfiles.accent(a),Math.min(96,UiProfiles.value(a,UiProfiles.PANEL))),base}:new int[]{panel,base});
  g.setCornerRadius(dp(a,UiProfiles.corner(a,radius)));
  g.setStroke(dp(a,1),selected?UiProfiles.accent(a):UiProfiles.opacity(UiProfiles.secondary(a),Math.max(35,UiProfiles.value(a,UiProfiles.PANEL))));
  return new RippleDrawable(ColorStateList.valueOf(UiProfiles.opacity(UiProfiles.accent(a),26)),g,null);
 }
 static Drawable buttonSurface(Context a,int radius){
  GradientDrawable g=new GradientDrawable();g.setColor(UiProfiles.button(a));
  g.setCornerRadius(dp(a,UiProfiles.corner(a,radius)));
  g.setStroke(dp(a,1),UiProfiles.accent(a));
  return new RippleDrawable(ColorStateList.valueOf(UiProfiles.opacity(UiProfiles.accent(a),25)),g,null);
 }
 static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(android.widget.LinearLayout.VERTICAL);return l;}
 static TextView button(Activity a,String label,Runnable action){
  TextView v=text(a,label,16,WHITE,true);v.setGravity(Gravity.CENTER);v.setMinHeight(dp(a,52));
  v.setPadding(dp(a,14),dp(a,12),dp(a,14),dp(a,12));v.setBackground(buttonSurface(a,18));v.setOnClickListener(w->action.run());return v;
 }
 static LinearLayout shortcuts(Activity a){
  LinearLayout row=new LinearLayout(a);row.setBaselineAligned(false);
  String[] names={tr(a,"Браузер","Browser"),tr(a,"Зарядки","Charge"),"AI",tr(a,"Помощь","Help")};
  int[] icons={R.drawable.ev_globe,R.drawable.driver_station_map,R.drawable.driver_ai,R.drawable.driver_help};
  Runnable[] clicks={()->a.startActivity(new Intent(a,EvBrowserActivity.class)),()->Charging.open(a),()->EvLinks.diagnost(a),()->DriveTools.assistance(a)};
  for(int i=0;i<names.length;i++){
   final Runnable click=clicks[i];LinearLayout key=column(a);key.setGravity(Gravity.CENTER);
   key.setPadding(dp(a,4),dp(a,8),dp(a,4),dp(a,8));key.setMinimumHeight(dp(a,56));key.setBackground(buttonSurface(a,16));
   ImageView icon=new ImageView(a);icon.setImageResource(icons[i]);icon.setColorFilter(i%2==0?UiProfiles.secondary(a):UiProfiles.accent(a));key.addView(icon,dp(a,21),dp(a,21));
   TextView name=text(a,names[i],12,WHITE,true);name.setGravity(Gravity.CENTER);name.setPadding(0,dp(a,5),0,0);name.setMaxLines(1);
   key.addView(name,-1,-2);key.setContentDescription(names[i]);key.setOnClickListener(v->click.run());
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(i==0?0:dp(a,3),0,i==3?0:dp(a,3),0);row.addView(key,p);
  }return row;
 }
 static final class Banner extends Drawable {
  private static Bitmap picture;
  private final Paint p=new Paint(3);private final RectF rect=new RectF();private final float radius;private final Context context;
  Banner(Context c){context=c.getApplicationContext();radius=dp(c,UiProfiles.corner(c,22));if(picture==null)picture=BitmapFactory.decodeResource(c.getResources(),R.drawable.ev_banner);}
  @Override public void draw(Canvas canvas){
   Rect b=getBounds();if(b.isEmpty())return;int s=canvas.save();rect.set(b);Path clip=new Path();clip.addRoundRect(rect,radius,radius,Path.Direction.CW);canvas.clipPath(clip);
   UiProfiles.Preset skin=UiProfiles.active(context);
   p.setShader(null);p.setColor(skin.base);canvas.drawRect(rect,p);
   if(UiProfiles.EV.equals(skin.id)&&picture!=null){float scale=Math.max(b.width()/(float)picture.getWidth(),b.height()/(float)picture.getHeight());float w=picture.getWidth()*scale,h=picture.getHeight()*scale;
    canvas.drawBitmap(picture,null,new RectF(b.centerX()-w/2,b.centerY()-h/2,b.centerX()+w/2,b.centerY()+h/2),p);}
   if(!UiProfiles.EV.equals(skin.id)){
    p.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{skin.panel,skin.base,skin.panel},null,Shader.TileMode.CLAMP));
    canvas.drawRect(rect,p);p.setShader(null);
    p.setStrokeWidth(dp(context,2));p.setStyle(Paint.Style.STROKE);
    p.setColor(UiProfiles.opacity(skin.accent,38));
    for(int i=0;i<4;i++)canvas.drawLine(b.left+b.width()*(i+2)/7f,b.top,b.left+b.width()*(i+1)/7f,b.bottom,p);
    p.setStyle(Paint.Style.FILL);
   }else{p.setShader(new LinearGradient(b.left,b.top,b.right,b.top,new int[]{0xf0001118,0xb006171d,0x30071318},new float[]{0f,.6f,1f},Shader.TileMode.CLAMP));canvas.drawRect(rect,p);p.setShader(null);}
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(UiProfiles.accent(context));canvas.drawRoundRect(rect,radius,radius,p);p.setStyle(Paint.Style.FILL);canvas.restoreToCount(s);
  }
  @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter c){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
