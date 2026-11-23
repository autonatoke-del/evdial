package ru.autonatoke.evdial;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class StationSheet extends Dialog {
    private static final int SURFACE=0xff17211b, TEXT=0xffedf4ef, MUTED=0xffb1c2b6, ACCENT=0xff97edac;
    private final Activity activity;
    private final LinearLayout content;
    private TextView positive;

    private StationSheet(Activity activity, Builder builder) {
        super(activity);
        this.activity=activity;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(activity);
        GradientDrawable panel=new GradientDrawable();
        panel.setColor(UiProfiles.active(activity).panel);
        int radius=dp(UiProfiles.corner(activity,24));
        panel.setCornerRadii(new float[]{radius,radius,radius,radius,0,0,0,0});
        scroll.setBackground(panel);
        content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20),dp(10),dp(20),dp(12));
        content.setFocusableInTouchMode(true);

        View grip=new View(activity);
        GradientDrawable handle=new GradientDrawable();
        handle.setCornerRadius(dp(2));
        handle.setColor(UiProfiles.muted(activity));
        grip.setBackground(handle);
        LinearLayout.LayoutParams gripSize=new LinearLayout.LayoutParams(dp(32),dp(4));
        gripSize.gravity=Gravity.CENTER_HORIZONTAL;
        content.addView(grip,gripSize);
        if(builder.title!=null){
            TextView title=text(builder.title,23,TEXT);
            title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            title.setPadding(dp(4),dp(16),dp(4),dp(12));
            content.addView(title,-1,-2);
        }
        if(builder.message!=null){
            TextView message=text(builder.message,16,MUTED);
            message.setPadding(dp(4),0,dp(4),dp(12));
            content.addView(message,-1,-2);
        }
        View form=builder.view;
        if(form instanceof ScrollView && ((ScrollView)form).getChildCount()==1){
            ScrollView wrapper=(ScrollView)form;
            form=wrapper.getChildAt(0);
            wrapper.removeView(form);
        }
        if(form!=null)content.addView(form,-1,-2);
        if(builder.items!=null){
            for(int i=0;i<builder.items.length;i++){
                final int index=i;
                if(i!=0)divider();
                addRow(builder.items[i],builder.checked==i,()->{
                    dismiss();
                    if(builder.pick!=null)builder.pick.onClick(this,index);
                });
            }
        }
        if(builder.yes!=null){
            positive=action(builder.yes,true);
            positive.setOnClickListener(v->{
                dismiss();if(builder.ok!=null)builder.ok.onClick(this,BUTTON_POSITIVE);
            });
        }
        if(builder.no!=null){
            TextView cancel=action(builder.no,false);
            cancel.setOnClickListener(v->{
                dismiss();if(builder.cancel!=null)builder.cancel.onClick(this,BUTTON_NEGATIVE);
            });
        }
        scroll.addView(content);
        setContentView(scroll);
        Window window=getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setGravity(Gravity.BOTTOM);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setLayout(Math.min(activity.getResources().getDisplayMetrics().widthPixels,dp(550)),WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void addRow(String title, boolean selected, Runnable click){
        LinearLayout row=new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(4),dp(9),dp(4),dp(9));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4095c7a2),null,new ColorDrawable(Color.WHITE)));
        row.setFocusable(true);
        row.setSelected(selected);
        row.setOnClickListener(v->click.run());
        TextView label=text(title,16,selected?UiProfiles.accent(activity):UiProfiles.text(activity));
        row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        if(selected){
            TextView tick=text("✓",19,UiProfiles.accent(activity));
            tick.setPadding(dp(12),0,dp(4),0);
            tick.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(tick);
        }
        content.addView(row,-1,-2);
    }

    private TextView action(String title,boolean primary){
        TextView action=text(title,16,primary?0xff102017:MUTED);
        action.setMinHeight(dp(48));
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(12),dp(8),dp(12),dp(8));
        GradientDrawable fill=new GradientDrawable();
        fill.setColor(primary?UiProfiles.accent(activity):UiProfiles.button(activity));
        fill.setCornerRadius(dp(12));
        action.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4095c7a2),fill,null));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);
        layout.topMargin=dp(primary?12:5);
        content.addView(action,layout);
        return action;
    }

    private TextView text(String value,int size,int color){
        TextView view=new TextView(activity);
        view.setText(value);view.setTextSize(UiProfiles.type(activity,size));
        view.setTextColor(color==TEXT?UiProfiles.text(activity):color==MUTED?UiProfiles.muted(activity):color==ACCENT?UiProfiles.accent(activity):color);
        return view;
    }
    private void divider(){
        View line=new View(activity);
        line.setBackgroundColor(0xff303c33);
        content.addView(line,-1,1);
    }
    private int dp(int n){return Station.dp(activity,n);}
    TextView getButton(int ignored){return positive;}

    static final class Builder {
        final Activity a; String title,message,yes,no;String[] items;int checked=-1;View view; DialogInterface.OnClickListener pick,ok,cancel;
        Builder(Activity a){this.a=a;}Builder setTitle(String v){title=v;return this;}Builder setMessage(String v){message=v;return this;}Builder setView(View v){view=v;return this;}
        Builder setItems(String[] v,DialogInterface.OnClickListener c){items=v;pick=c;return this;}Builder setSingleChoiceItems(String[] v,int i,DialogInterface.OnClickListener c){checked=i;return setItems(v,c);}
        Builder setPositiveButton(String v,DialogInterface.OnClickListener c){yes=v;ok=c;return this;}Builder setNegativeButton(String v,DialogInterface.OnClickListener c){no=v;cancel=c;return this;}
        StationSheet create(){return new StationSheet(a,this);}StationSheet show(){StationSheet d=create();d.show();return d;}
    }
}
