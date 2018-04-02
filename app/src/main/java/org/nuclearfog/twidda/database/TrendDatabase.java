package org.nuclearfog.twidda.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import org.nuclearfog.twidda.backend.listitems.Trend;

public class TrendDatabase {

    private AppDatabase dataHelper;

    public TrendDatabase(Context c) {
        dataHelper = AppDatabase.getInstance(c);
    }

    /**
     * Load trend List
     * @return list of trends
     */
    public List<Trend> load() {
        SQLiteDatabase db = dataHelper.getReadableDatabase();
        List<Trend> trends = new ArrayList<>();
        String SQL_TREND = "SELECT * FROM trend ORDER BY trendpos ASC";
        Cursor cursor = db.rawQuery(SQL_TREND,null);
        if(cursor.moveToFirst()) {
            do {
                int index = cursor.getColumnIndex("trendpos");
                int position = cursor.getInt(index);
                index = cursor.getColumnIndex("trendname");
                String name = cursor.getString(index);
                index = cursor.getColumnIndex("trendlink");
                String link = cursor.getString(index);
                trends.add(new Trend(position, name, link));
            } while(cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return trends;
    }

    /**
     * Store Trend List
     * @param trends List of Trends
     */
    public void store(final List<Trend> trends) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase db = dataHelper.getWritableDatabase();
                ContentValues trendcolumn = new ContentValues();
                for(int pos = 0; pos < trends.size(); pos++) {
                    Trend trend = trends.get(pos);
                    trendcolumn.put("trendpos", trend.position);
                    trendcolumn.put("trendname", trend.trend);
                    trendcolumn.put("trendlink", trend.link);
                    db.insertWithOnConflict("trend",null, trendcolumn,SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
        }).start();
    }
}