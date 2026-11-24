package ru.autonatoke.evdial;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SmartDialSearchTest {
    private static int checks;
    private static void check(boolean ok, String detail) {
        checks++;
        if (!ok) throw new AssertionError(detail);
    }

    private static SmartDialSearch.Entry person(String name, String number, boolean starred) {
        return new SmartDialSearch.Entry(name, number, "content://com.android.contacts/contacts/lookup/test", false, starred, 0);
    }

    private static SmartDialSearch.Entry recent(String name, String number, long when) {
        return new SmartDialSearch.Entry(name, number, "", true, false, when);
    }

    private static boolean has(SmartDialSearch.Matches matches, String number) {
        for (SmartDialSearch.Hit hit : matches.items)
            if (hit.entry.number.equals(number)) return true;
        return false;
    }

    public static void main(String[] unused) {
        check(SmartDialSearch.t9("Алина").equals("24452"), "Cyrillic T9 Alina");
        check(SmartDialSearch.t9("Игорь").equals("42569"), "Cyrillic T9 Igor");
        check(SmartDialSearch.t9("Петров").equals("536652"), "Cyrillic T9 surname");
        check(SmartDialSearch.t9("John").equals("5646"), "Latin John");
        check(SmartDialSearch.t9("Ёж").startsWith("33"), "Russian Ё maps to Е, Ж");
        check(SmartDialSearch.t9("Мария Иванова").contains(" "), "Word boundary survives");
        check(SmartDialSearch.digits("+7 (964) 770-72-38").equals("79647707238"), "Strip Russian formatting");
        check(SmartDialSearch.nationalDigits("8 (964) 770-72-38").equals("79647707238"), "National 8 alias");
        check(SmartDialSearch.nationalDigits("8964").equals("7964"), "Partial national prefix");
        check(SmartDialSearch.nationalDigits("+79").equals("79"), "Partial international prefix");
        check(SmartDialSearch.nationalDigits("abc").isEmpty(), "Do not infer numbers from alphabet");
        check(SmartDialSearch.search("2#",Collections.emptyList()).total==0, "Service codes disabled for lookup");

        SmartDialSearch.Entry alina=person("Алина", "+7 (916) 948-75-82", true);
        SmartDialSearch.Entry john=person("John Smith", "+7 964 770-72-38", false);
        SmartDialSearch.Entry recent1=recent("Алина", "8 916 948 75 82", 100);
        SmartDialSearch.Entry recent2=recent("Клиент", "+7 900 111-22-33", 200);
        List<SmartDialSearch.Entry> entries=Arrays.asList(alina,john,recent1,recent2);
        SmartDialSearch.Matches m=SmartDialSearch.search("+79",entries);
        check(m.total==3, "Deduplicate the same contact number from SIM history");
        check(has(m,alina.number),"Number-prefix match");
        check(has(m,john.number),"Another number prefix match");
        check(m.items.get(0).entry.starred,"Starred contact ranked first");
        check(SmartDialSearch.search("8916",entries).items.get(0).entry==alina,"Domestic 8 prefix finds +7 contact");
        check(SmartDialSearch.search("916948",entries).items.get(0).entry==alina,"Substring phone-number search");
        check(SmartDialSearch.search("24452",entries).items.get(0).entry==alina,"Cyrillic name T9 finds Alina");
        check(SmartDialSearch.search("5646",entries).items.get(0).entry==john,"Latin T9 name John");
        check(SmartDialSearch.search("76484",Collections.singletonList(person("Иван Смит","+1 212-555-0111",false))).total==0,"Incorrect T9 no match");
        SmartDialSearch.Matches last=SmartDialSearch.search("",entries);
        check(last.isRecent,"Empty input requests latest call");
        check(last.total==2,"Empty input shows only distinct recents");
        check(last.items.get(0).entry==recent2,"Latest recent is first");
        check(SmartDialSearch.search("*",entries).total==0,"Star service code does not search");
        check(SmartDialSearch.search("#",entries).total==0,"Hash service code does not search");
        check(SmartDialSearch.search("+",entries).total==0,"Just plus cannot find contacts");
        check(SmartDialSearch.search("2",entries).total>=1,"Single keypad digit searches number prefix");
        check(SmartDialSearch.search("100000000",entries).total==0,"No invented results");
        check(SmartDialSearch.search("",Collections.emptyList()).total==0,"No records handled");
        check(SmartDialSearch.search(null,entries).isRecent,"Null search is safe");
        check(SmartDialSearch.search("++7",entries).total==0,"Invalid input cannot escape numeric search");
        check(SmartDialSearch.search("Петр",entries).total==0,"Only keypad queries accepted");
        System.out.println("PASS: "+checks+" iPhone-style T9/dialer search assertions");
    }
}
