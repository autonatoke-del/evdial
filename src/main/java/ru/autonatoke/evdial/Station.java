package ru.autonatoke.evdial;
import android.app.Activity;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
final class Station {
 static int dp(Activity a,int d){return Math.round(a.getResources().getDisplayMetrics().density*d);}
 static void ai(Activity a){EvLinks.diagnost(a);}
 static GradientDrawable plate(Activity a,int radius,int start,int end){
  GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{start,end});g.setCornerRadius(dp(a,radius));g.setStroke(dp(a,1),0xff345044);return g;
 }
}
