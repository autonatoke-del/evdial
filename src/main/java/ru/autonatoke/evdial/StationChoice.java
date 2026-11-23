package ru.autonatoke.evdial;

import android.app.Activity;
import android.view.Gravity;
import android.widget.TextView;

final class StationChoice extends TextView {
    private int index;
    private final String[] choices;
    StationChoice(Activity activity,String title,String[] options,int selected) {
        super(activity);
        choices=options.clone();
        setTextSize(16);setTextColor(0xffc9f2d5);setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Station.dp(activity,14),Station.dp(activity,8),Station.dp(activity,14),Station.dp(activity,8));
        setMinHeight(Station.dp(activity,48));setBackground(Station.plate(activity,16,0xff1b2c20,0xff101d15));
        choose(selected);
        setOnClickListener(v->new StationSheet.Builder(activity).setTitle(title).setSingleChoiceItems(choices,index,(d,k)->choose(k)).setNegativeButton("Отмена",null).show());
    }
    private void choose(int value){index=Math.max(0,Math.min(choices.length-1,value));setText(choices[index]+"   ⌄");}
    int getSelectedItemPosition(){return index;}
    String getSelectedItem(){return choices[index];}
}
