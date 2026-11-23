package ru.autonatoke.evdial;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Read-only, non-exported provider for explicitly shared bundled wallpapers. */
public final class WallpaperShareProvider extends ContentProvider {
    static Uri prepare(Context context) throws IOException {
        String id=PhoneThemes.selectedId(context);
        String name=id.isEmpty()?"nebenzin-wallpaper.webp":"nebenzin-wallpaper-"+id+".png";
        File target=new File(context.getCacheDir(),name);
        synchronized(WallpaperShareProvider.class) {
                File temporary=new File(context.getCacheDir(),name+".tmp");
                try(InputStream source=context.getResources().openRawResource(PhoneThemes.resource(context,PhoneThemes.find(id)));
                    FileOutputStream output=new FileOutputStream(temporary)) {
                    byte[] buffer=new byte[16384];int count;
                    while((count=source.read(buffer))!=-1)output.write(buffer,0,count);
                }
                if(!temporary.renameTo(target))throw new IOException("Wallpaper cache unavailable");
        }
        return new Uri.Builder().scheme("content").authority(context.getPackageName()+".wallpaper")
                .appendPath(name).build();
    }
    @Override public boolean onCreate(){return true;}
    private String check(Uri uri) {
        String name=uri.getLastPathSegment();
        boolean known="nebenzin-wallpaper.webp".equals(name);
        for(PhoneThemes.Theme theme:PhoneThemes.values())
            if(("nebenzin-wallpaper-"+theme.id+".png").equals(name))known=true;
        if(!known||uri.getPathSegments().size()!=1||!"content".equals(uri.getScheme())
                ||!(getContext().getPackageName()+".wallpaper").equals(uri.getAuthority()))
            throw new IllegalArgumentException("Unknown wallpaper");
        return name;
    }
    @Override public String getType(Uri uri){return check(uri).endsWith(".png")?"image/png":"image/webp";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        String name=check(uri);if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(),name),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order) {
        String name=check(uri);
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];
        for(int i=0;i<columns.length;i++) {
            if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=name;
            else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=new File(getContext().getCacheDir(),name).length();
        }
        cursor.addRow(row);return cursor;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException("Read only");}
}
