package org.ganimede.launcher;

import org.ganimede.launcher.AppList.Extra;
import org.ganimede.launcher.core.Search;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Contacts for the search: read one query at a time off the main thread, cached, with the phone-number index. */
final class ContactSearch {
    /** How many contacts match a query, and the rows read so far: the first few, or all of them after "Show all". */
    static final class Hits {
        final int count;
        final List<Extra> people;
        Hits(int count, List<Extra> people) { this.count = count; this.people = people; }
    }

    private record NumberRow(long id, String digits) {}

    /** Contacts read one query at a time, off the main thread. */
    private static final ExecutorService CONTACTS = Executors.newSingleThreadExecutor();

    private final Ui ui;
    private final Handler handler;
    /** Contacts found per query (the last 20): typing back and forth costs nothing. */
    private final Map<String, Hits> hits = new LinkedHashMap<String, Hits>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Hits> e) { return size() > 20; }
    };
    /** The read asked last ("\n" at the end: all of them); a queued read that is no longer it is skipped. */
    private volatile String asked = "";
    /** (contact id, digits of a number) of every phone number, read on the first number search and dropped when the search closes. */
    private volatile List<NumberRow> numberIndex;
    /** Bumped each time the search closes: a scan that began before it must not store its index. */
    private volatile int numberEpoch;

    ContactSearch(Ui ui, Handler handler) { this.ui = ui; this.handler = handler; }

    /** What was read for the query, or null. */
    Hits get(String q) { return hits.get(q); }

    /** The search closed: contacts may change before the next search. */
    void closed() { numberIndex = null; numberEpoch++; hits.clear(); }

    /** Reads the query's contacts in the background; done runs on the main thread once they are cached. */
    void read(String q, boolean all, Runnable done) {
        String ask = all ? q + "\n" : q;
        if (ask.equals(asked)) return; // already being read
        asked = ask;
        CONTACTS.execute(() -> {
            if (!ask.equals(asked)) return; // the text changed while this waited its turn
            Hits found = query(q, all);
            handler.post(() -> {
                // A failed read isn't cached as "nobody", nor asked again at once (that would loop while the text
                // stays): asked keeps it until the text changes.
                if (found == null) return;
                hits.put(q, found);
                if (ask.equals(asked)) asked = "";
                done.run();
            });
        });
    }

    /**
     * Only people I can call or write to (messenger accounts like WhatsApp or Telegram add contacts with a name and
     * neither). How many contacts match, starred first then by name, with the details (number, email) of only the rows shown:
     * the first few, or all of them after "Show all". Only contacts the search rule matches (name, email, company or nickname) are counted and shown.
     */
    private Hits query(String q, boolean all) {
        List<Long> found = new ArrayList<>();
        Set<Long> phoneless = new HashSet<>(); // reachable only if they have an email
        List<Uri> foundLooks = new ArrayList<>();
        List<String> foundNames = new ArrayList<>();
        boolean byNumber = Search.isNumberQuery(q); // digits: the contacts whose number holds them, not the name filter
        Uri u = byNumber ? ContactsContract.Contacts.CONTENT_URI
                : Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q));
        try (Cursor c = ui.a.getContentResolver().query(u, new String[]{ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER},
                byNumber ? ContactsContract.Contacts._ID + " IN (" + numberContactIds(q) + ")" : null, null,
                ContactsContract.Contacts.STARRED + " DESC, " + ContactsContract.Contacts.SORT_KEY_PRIMARY)) {
            while (c != null && c.moveToNext()) {
                if (c.getString(2) == null) continue;
                if (!byNumber && c.getInt(3) == 0) phoneless.add(c.getLong(0));
                found.add(c.getLong(0));
                foundLooks.add(ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1)));
                foundNames.add(c.getString(2));
            }
        } catch (Exception unavailable) { // provider busy or gone
            return null;
        }
        // the provider also matches mid-word and on other entries: each contact passes the search rule on its name or
        // on its email, company or nickname
        Set<Long> mailed = new HashSet<>();
        Map<Long, List<String>> others = byNumber ? Map.of() : otherEntries(found, mailed);
        if (others == null) return null; // not read: the contacts with no number may have an email, so no answer, not a wrong one
        List<Long> ids = new ArrayList<>();
        List<Uri> looks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int count = 0;
        for (int i = 0; i < found.size(); i++) {
            if (phoneless.contains(found.get(i)) && !mailed.contains(found.get(i))) continue;
            if (!byNumber && !Search.contactMatches(foundNames.get(i), others.getOrDefault(found.get(i), List.of()), q)) continue;
            if (ids.size() >= Search.shownContacts(++count, all)) continue;
            ids.add(found.get(i));
            looks.add(foundLooks.get(i));
            names.add(foundNames.get(i));
        }
        Map<String, String> data = contactData(ids);
        List<Extra> people = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Uri look = looks.get(i);
            String name = names.get(i);
            Extra e = new Extra("☺", name, () -> ui.open(new Intent(Intent.ACTION_VIEW, look)));
            // Contact actions: call, message, email in one tap (the dialer opens; no calling permission).
            String phone = data.get(ids.get(i) + "|" + Phone.CONTENT_ITEM_TYPE), mail = data.get(ids.get(i) + "|" + Email.CONTENT_ITEM_TYPE);
            if (phone != null) {
                e.button("✆", ui.f("Call %s", name), () -> ui.open(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone)))));
                e.button("✉", ui.f("Message %s", name), () -> ui.open(new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)))));
            }
            if (mail != null) e.button("@", ui.f("Email %s", name), () -> ui.open(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(mail, "@")))));
            people.add(e);
        }
        return new Hits(count, people);
    }

    /** The email addresses, companies and nicknames of each contact, by contact id (null when the read failed); the ids of those with an email go into mailed. */
    private Map<Long, List<String>> otherEntries(List<Long> all, Set<Long> mailed) {
        Map<Long, List<String>> out = new HashMap<>();
        for (int from = 0; from < all.size(); from += 500) {
            List<Long> ids = all.subList(from, Math.min(all.size(), from + 500));
            String[] args = new String[ids.size() + 3];
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) { in.append(i == 0 ? "?" : ",?"); args[i] = String.valueOf(ids.get(i)); }
            args[ids.size()] = Email.CONTENT_ITEM_TYPE;
            args[ids.size() + 1] = ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE;
            args[ids.size() + 2] = ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE;
            try (Cursor d = ui.a.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.Data.CONTACT_ID, ContactsContract.Data.DATA1, ContactsContract.Data.MIMETYPE},
                    ContactsContract.Data.CONTACT_ID + " IN (" + in + ") AND " + ContactsContract.Data.MIMETYPE + " IN (?,?,?)",
                    args, null)) {
                while (d != null && d.moveToNext()) {
                    out.computeIfAbsent(d.getLong(0), k -> new ArrayList<>()).add(d.getString(1));
                    if (Email.CONTENT_ITEM_TYPE.equals(d.getString(2))) mailed.add(d.getLong(0));
                }
            } catch (Exception e) {
                return null;
            }
        }
        return out;
    }

    /** Ids (comma separated) of the contacts with a phone number holding the typed digits; the rule is Search.numberMatches. */
    private String numberContactIds(String q) {
        List<NumberRow> index = numberIndex;
        if (index == null) { // one scan of all the numbers while the search is open, not one per key
            int epoch = numberEpoch;
            index = new ArrayList<>();
            try (Cursor p = ui.a.getContentResolver().query(Phone.CONTENT_URI, new String[]{Phone.CONTACT_ID, Phone.NUMBER}, null, null, null)) {
                while (p != null && p.moveToNext()) if (p.getString(1) != null) index.add(new NumberRow(p.getLong(0), Search.digits(p.getString(1))));
            }
            if (epoch == numberEpoch) numberIndex = index; // not if the search closed meanwhile
        }
        StringBuilder ids = new StringBuilder();
        Search.NumberQuery typed = Search.numberQuery(q); // read once, not per number
        for (NumberRow n : index) if (typed.matches(n.digits)) ids.append(ids.length() == 0 ? "" : ",").append(n.id);
        return ids.toString();
    }

    /**
     * The first phone number and email of each contact (data1, primary first), keyed "id|mimetype": one query for up to
     * 500 of them instead of two per contact (SQLite takes at most 999 parameters before Android 11).
     */
    private Map<String, String> contactData(List<Long> all) {
        Map<String, String> first = new HashMap<>();
        for (int from = 0; from < all.size(); from += 500) {
            List<Long> ids = all.subList(from, Math.min(all.size(), from + 500));
            String[] args = new String[ids.size() + 2];
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) { in.append(i == 0 ? "?" : ",?"); args[i] = String.valueOf(ids.get(i)); }
            args[ids.size()] = Phone.CONTENT_ITEM_TYPE;
            args[ids.size() + 1] = Email.CONTENT_ITEM_TYPE;
            try (Cursor d = ui.a.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.Data.CONTACT_ID, ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1},
                    ContactsContract.Data.CONTACT_ID + " IN (" + in + ") AND " + ContactsContract.Data.MIMETYPE + " IN (?,?)",
                    args, ContactsContract.Data.IS_PRIMARY + " DESC")) {
                while (d != null && d.moveToNext()) {
                    String k = d.getLong(0) + "|" + d.getString(1);
                    if (!first.containsKey(k)) first.put(k, d.getString(2));
                }
            } catch (Exception e) {
                // no buttons
            }
        }
        return first;
    }
}
