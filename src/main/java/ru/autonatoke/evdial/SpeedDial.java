package ru.autonatoke.evdial;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Number assignments are local to this installation and use the selected outgoing line. */
final class SpeedDial {
    static final int PICK_PHONE = 46;
    private final Dialer host;
    private final SharedPreferences slots;
    int picking;

    SpeedDial(Dialer host) {
        this.host = host;
        slots = host.getSharedPreferences("speed_dial", Activity.MODE_PRIVATE);
    }

    void press(int key) {
        String number = slots.getString("number" + key, "");
        if (number.isEmpty()) edit(key);
        else host.callSpeedNumber(number);
    }

    void keys(String assignNumber) {
        String[] rows = new String[8];
        for (int i = 0; i < rows.length; i++) {
            int key = i + 2;
            String number = slots.getString("number" + key, "");
            String name = slots.getString("name" + key, "");
            rows[i] = key + "   " + (name.isEmpty() ? number.isEmpty() ? "Свободно" : number : name);
        }
        new StationSheet.Builder(host).setTitle("Быстрый набор")
                .setMessage(assignNumber.isEmpty() ? "Удерживайте цифру от 2 до 9, чтобы позвонить." :
                        "Выберите клавишу для номера " + assignNumber)
                .setItems(rows, (d,i) -> {
                    int key = i + 2;
                    if (assignNumber.isEmpty()) edit(key);
                    else if (slots.contains("number" + key))
                        new StationSheet.Builder(host).setTitle("Заменить номер клавиши " + key + "?")
                                .setMessage(assignNumber).setPositiveButton("Заменить", (s,b) -> assign(key,assignNumber,""))
                                .setNegativeButton("Отмена",null).show();
                    else assign(key,assignNumber,"");
                }).setNegativeButton("Закрыть",null).show();
    }

    private void edit(int key) {
        String old = slots.getString("number" + key, "");
        String[] actions = old.isEmpty() ? new String[]{"Выбрать контакт","Ввести номер"} :
                new String[]{"Выбрать контакт","Изменить номер","Освободить клавишу"};
        new StationSheet.Builder(host).setTitle("Клавиша " + key)
                .setMessage(old.isEmpty() ? "Назначьте номер для быстрого звонка." : old)
                .setItems(actions,(d,i) -> {
                    if(i==0) pick(key);
                    else if(i==1) enter(key,old);
                    else {
                        slots.edit().remove("number" + key).remove("name" + key).apply();
                        host.note("Клавиша " + key + " свободна");
                    }
                }).setNegativeButton("Отмена",null).show();
    }

    private void enter(int key, String old) {
        LinearLayout form = new LinearLayout(host);
        int margin = host.dp(8);
        form.setPadding(margin,margin,margin,margin);
        EditText field = new EditText(host);
        field.setHint("Номер телефона"); field.setSingleLine();
        field.setInputType(InputType.TYPE_CLASS_PHONE); field.setTextColor(Dialer.FG);
        field.setHintTextColor(Dialer.MUTED); field.setText(old); field.setSelection(field.length());
        form.addView(field,-1,-2);
        StationSheet page = new StationSheet.Builder(host).setTitle("Номер для клавиши " + key)
                .setView(form).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).show();
        page.getButton(-1).setOnClickListener(v -> {
            if (assign(key,field.getText().toString(),"")) page.dismiss();
        });
    }

    private void pick(int key) {
        picking = key;
        try { host.startActivityForResult(new Intent(Intent.ACTION_PICK,Phone.CONTENT_URI), PICK_PHONE); }
        catch(ActivityNotFoundException | SecurityException unsupported) {
            picking = 0;
            host.note("Выбор контакта недоступен. Введите номер.");
            enter(key,slots.getString("number" + key,""));
        }
    }

    void picked(int result, Intent data) {
        int key = picking;
        picking = 0;
        if (key < 2 || key > 9 || result != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) return;
        try (Cursor cursor = host.getContentResolver().query(uri,new String[]{Phone.NUMBER,Phone.DISPLAY_NAME},null,null,null)) {
            if (cursor != null && cursor.moveToFirst()) {
                assign(key,cursor.getString(0),cursor.getString(1));
                return;
            }
        } catch (RuntimeException ignored) {}
        host.note("Не удалось прочитать номер контакта. Введите его вручную.");
        enter(key,slots.getString("number" + key,""));
    }

    private boolean assign(int key,String raw,String name) {
        String number = Dialer.phoneDigits(raw);
        if (number.isEmpty() || host.emergency(number)) {
            host.note("Для быстрого набора нужен обычный номер или внутренний номер АТС");
            return false;
        }
        slots.edit().putString("number" + key, number).putString("name" + key, name == null ? "" : name).apply();
        host.note("Номер сохранён на клавише " + key);
        return true;
    }
}
