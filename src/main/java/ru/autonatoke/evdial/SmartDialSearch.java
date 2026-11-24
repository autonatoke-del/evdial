package ru.autonatoke.evdial;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure, offline T9 and phone-number search. Never contacts a network service. */
final class SmartDialSearch {
    static final class Entry {
        final String name, number, contactUri;
        final boolean recent, starred;
        final long date;

        Entry(String name, String number, String contactUri, boolean recent, boolean starred, long date) {
            this.name = name == null ? "" : name;
            this.number = number == null ? "" : number;
            this.contactUri = contactUri == null ? "" : contactUri;
            this.recent = recent;
            this.starred = starred;
            this.date = date;
        }
    }

    static final class Hit {
        final Entry entry;
        final int relevance;
        Hit(Entry entry, int relevance) { this.entry = entry; this.relevance = relevance; }
    }

    static final class Matches {
        final List<Hit> items;
        final int total;
        final boolean isRecent;
        Matches(List<Hit> items, int total, boolean isRecent) {
            this.items = Collections.unmodifiableList(items);
            this.total = total;
            this.isRecent = isRecent;
        }
    }

    private SmartDialSearch() { }

    static String digits(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= '0' && ch <= '9') out.append(ch);
        }
        return out.toString();
    }

    /** Russian 8/7 dialing prefixes are equivalent, even during partial input: 89 -> 79. */
    static String nationalDigits(String value) {
        String d = digits(value);
        if (d.length() >= 2 && d.charAt(0) == '8' && d.charAt(1) == '9') return "7" + d.substring(1);
        if (d.length() == 11 && d.charAt(0) == '8') return "7" + d.substring(1);
        return d;
    }

    /** Mirrors the Cyrillic / Latin alphabet groups printed on the dialpad. */
    static String t9(String name) {
        if (name == null) return "";
        String upper = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .toUpperCase(Locale.ROOT).replace('Ё', 'Е');
        StringBuilder result = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK) continue;
            if (ch >= '0' && ch <= '9') result.append(ch);
            else if ("ABCАБВГ".indexOf(ch) >= 0) result.append('2');
            else if ("DEFДЕЖЗ".indexOf(ch) >= 0) result.append('3');
            else if ("GHIИЙКЛ".indexOf(ch) >= 0) result.append('4');
            else if ("JKLМНОП".indexOf(ch) >= 0) result.append('5');
            else if ("MNOРСТУ".indexOf(ch) >= 0) result.append('6');
            else if ("PQRSФХЦЧ".indexOf(ch) >= 0) result.append('7');
            else if ("TUVШЩЪЫ".indexOf(ch) >= 0) result.append('8');
            else if ("WXYZЬЭЮЯ".indexOf(ch) >= 0) result.append('9');
            else result.append(' ');
        }
        return result.toString();
    }

    private static int score(Entry entry, String query, String nameQuery) {
        String canonical = nationalDigits(entry.number);
        if (canonical.isEmpty()) return Integer.MAX_VALUE;
        if (canonical.equals(query)) return entry.recent ? -2 : -6;
        if (canonical.startsWith(query)) return entry.recent ? 6 : 0;
        if (canonical.contains(query)) return entry.recent ? 29 : 24;

        // Two or more keypad digits can represent the beginning of any word in a name.
        if (nameQuery.length() >= 2 && !entry.name.isEmpty()) {
            String mapped = t9(entry.name);
            if (mapped.startsWith(nameQuery)) return 10;
            for (String token : mapped.split(" +")) {
                if (token.startsWith(nameQuery)) return 13;
            }
            if (nameQuery.length() >= 3 && mapped.contains(nameQuery)) return 35;
        }
        return Integer.MAX_VALUE;
    }

    private static final Comparator<Hit> ORDER = (a, b) -> {
        int result = Integer.compare(a.relevance, b.relevance);
        if (result != 0) return result;
        result = Boolean.compare(b.entry.starred, a.entry.starred);
        if (result != 0) return result;
        result = Boolean.compare(a.entry.recent, b.entry.recent);
        if (result != 0) return result;
        result = Long.compare(b.entry.date, a.entry.date);
        if (result != 0) return result;
        result = a.entry.name.compareToIgnoreCase(b.entry.name);
        return result != 0 ? result : a.entry.number.compareTo(b.entry.number);
    };

    static Matches search(String rawInput, List<Entry> entries) {
        if (entries == null || entries.isEmpty()) return new Matches(new ArrayList<>(), 0, rawInput == null || rawInput.isEmpty());
        String input = rawInput == null ? "" : rawInput.trim();
        if (!input.isEmpty() && !input.matches("\\+?[0-9() .-]+"))
            return new Matches(new ArrayList<>(), 0, false);
        String query = nationalDigits(input);
        // A keypad name sequence is not a telephone prefix. Never normalize its 8 to 7.
        String nameQuery = input.matches("[0-9]+") ? input : "";
        if (query.isEmpty() && !input.isEmpty()) return new Matches(new ArrayList<>(), 0, false);
        // Group the exact same dialable number across SIM/SIP call history and contacts.
        Map<String, Hit> unique = new LinkedHashMap<>();
        for (Entry item : entries) {
            if (item == null || item.number.isEmpty()) continue;
            if (query.isEmpty() && !item.recent) continue;
            int rating = query.isEmpty() ? 0 : score(item, query, nameQuery);
            if (rating == Integer.MAX_VALUE) continue;
            String key = nationalDigits(item.number);
            if (key.isEmpty()) continue;
            Hit current = unique.get(key);
            Hit next = new Hit(item, rating);
            if (current == null || ORDER.compare(next, current) < 0) unique.put(key, next);
        }
        ArrayList<Hit> found = new ArrayList<>(unique.values());
        if (query.isEmpty()) found.sort((a, b) -> Long.compare(b.entry.date, a.entry.date));
        else found.sort(ORDER);
        return new Matches(found, found.size(), query.isEmpty());
    }
}
