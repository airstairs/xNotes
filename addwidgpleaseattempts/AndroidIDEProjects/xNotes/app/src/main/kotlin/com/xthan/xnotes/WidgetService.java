package com.xthan.xnotes;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new WidgetDataProvider(getApplicationContext(), intent);
    }
}

class WidgetDataProvider implements RemoteViewsService.RemoteViewsFactory {
    private Context context;
    private List<String> notebookList = new ArrayList<>();

    public WidgetDataProvider(Context context, Intent intent) {
        this.context = context;
    }

    @Override
    public void onCreate() {
        loadNotes();
    }

    @Override
    public void onDataSetChanged() {
        loadNotes();
    }

    private void loadNotes() {
        notebookList.clear();
        SharedPreferences prefs = context.getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE);
        Set<String> savedList = prefs.getStringSet("notes_list", new HashSet<>());
        if (savedList != null) {
            notebookList.addAll(savedList);
        }
    }

    @Override
    public int getCount() {
        return notebookList.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        String noteName = notebookList.get(position);
        SharedPreferences prefs = context.getSharedPreferences("xnotes_prefs", Context.MODE_PRIVATE);
        int noteColor = prefs.getInt("note_color_" + noteName, Color.GRAY);

        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_item_notebook);
        
        // Set notebook title text on the button container
        rv.setTextViewText(R.id.widget_item_open, noteName);

        // Generate the custom icon bitmap matching MainActivity's icon logic
        Bitmap iconBitmap = createNotebookIcon(noteColor);
        rv.setImageViewBitmap(R.id.widget_item_icon, iconBitmap);

        // Setup fill-in intent for opening the notebook when the row is clicked
        Intent openIntent = new Intent();
        openIntent.putExtra("NOTEBOOK_ID", noteName);
        openIntent.putExtra("NOTEBOOK_TITLE", noteName);
        rv.setOnClickFillInIntent(R.id.widget_item_layout, openIntent);

        // Setup fill-in intent for deleting an individual note item
        Intent deleteIntent = new Intent();
        deleteIntent.setAction("com.xthan.xnotes.ACTION_DELETE_NOTE");
        deleteIntent.putExtra("DELETE_NOTE_NAME", noteName);
        rv.setOnClickFillInIntent(R.id.widget_item_delete, deleteIntent);

        return rv;
    }

    private Bitmap createNotebookIcon(int color) {
        int size = 96;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint bgPaint = new Paint();
        bgPaint.setColor(color);
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRect(0f, 0f, (float) size, (float) size, bgPaint);

        Paint circlePaint = new Paint();
        circlePaint.setColor(Color.WHITE);
        circlePaint.setStyle(Paint.Style.FILL);
        circlePaint.setAntiAlias(true);
        
        float centerX = size / 2f;
        float centerY = size / 2f;
        float radius = size * 0.3f;
        canvas.drawCircle(centerX, centerY, radius, circlePaint);

        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(radius * 1.3f);
        textPaint.setAntiAlias(true);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        float textY = centerY - ((textPaint.descent() + textPaint.ascent()) / 2f);
        canvas.drawText("x", centerX, textY, textPaint);

        return bitmap;
    }

    @Override
    public RemoteViews getLoadingView() { return null; }
    @Override
    public int getViewTypeCount() { return 1; }
    @Override
    public long getItemId(int position) { return position; }
    @Override
    public boolean hasStableIds() { return true; }
    @Override
    public void onDestroy() { notebookList.clear(); }
}