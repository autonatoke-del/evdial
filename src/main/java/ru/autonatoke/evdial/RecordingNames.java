package ru.autonatoke.evdial;

import java.time.*;
import java.util.Locale;
import java.util.regex.*;

final class RecordingNames {
    private RecordingNames() {}
    // Cube example: phone_20251207-100931_7xxxxxxxxxx.amr
    private static final Pattern STAMP=Pattern.compile("(?<![0-9])(20[0-9]{2})[-_.]?([01][0-9])[-_.]?([0-3][0-9])[ T_\\-]+([0-2][0-9])[:.\\-_]?([0-5][0-9])[:.\\-_]?([0-5][0-9])(?![0-9])");
    private static final Pattern NUMBER=Pattern.compile("(?<![a-zA-Z0-9])\\+?[0-9]{7,15}(?![a-zA-Z0-9])");
    static boolean audio(String name,String mime){
        return (mime!=null&&mime.startsWith("audio/")) || (name!=null&&name.toLowerCase(Locale.ROOT).matches(".*\\.(amr|awb|3gp|3gpp|m4a|aac|mp3|wav|ogg|opus|flac|mp4)"));
    }
    static String keyNumber(String value){
        if(value==null||!value.matches("[+0-9(). \\-]+"))return "";
        String n=value.replaceAll("[^0-9]","");
        if(n.length()==11 && n.startsWith("8") && !value.trim().startsWith("+"))n="7"+n.substring(1);
        return n;
    }
    static boolean matches(String filename,String target,long time,ZoneId zone){
        String key=keyNumber(target);
        if(key.length()<7 || filename==null || !filename.toLowerCase(Locale.ROOT).startsWith("phone_") || !audio(filename,null) || time<=0)return false;
        Matcher m=STAMP.matcher(filename);
        if(!m.find())return false;
        try{
            LocalDateTime date=LocalDateTime.of(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),
                    Integer.parseInt(m.group(4)),Integer.parseInt(m.group(5)),Integer.parseInt(m.group(6)));
            java.util.List<ZoneOffset> offsets=zone.getRules().getValidOffsets(date);
            if(offsets.size()!=1 || Math.abs(date.toInstant(offsets.get(0)).toEpochMilli()-time)>15000L)return false;
            String rest=filename.substring(0,m.start())+"_"+filename.substring(m.end());
            if(m.find())return false;
            Matcher n=NUMBER.matcher(rest);
            return n.find() && key.equals(keyNumber(n.group())) && !n.find();
        }catch(DateTimeException | NumberFormatException ignored){return false;}
    }
}
