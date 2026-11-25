package ru.autonatoke.evdial;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Sizes the dialpad from the available window, before laying out its fixed bottom pane. */
final class DialerViewport extends LinearLayout {
    private View header, numberCard, actions, navigation;
    private ScrollView upper;
    private LinearLayout dock, keypad;

    DialerViewport(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBaselineAligned(false);
    }

    void bind(View header, ScrollView upper, LinearLayout dock, LinearLayout keypad,
              View numberCard, View actions, View navigation) {
        this.header = header;
        this.upper = upper;
        this.dock = dock;
        this.keypad = keypad;
        this.numberCard = numberCard;
        this.actions = actions;
        this.navigation = navigation;
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (dock != null && keypad != null && dock.getVisibility() != GONE
                && keypad.getVisibility() != GONE) {
            fitKeys(widthMeasureSpec, heightMeasureSpec);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void fitKeys(int widthSpec, int heightSpec) {
        boolean inCall = numberCard.getVisibility() == GONE && actions.getVisibility() == GONE;
        int diameter = dp(inCall ? 72 : 80);
        int rows = 0;
        int gridExtra = keypad.getPaddingTop() + keypad.getPaddingBottom() + verticalMargins(keypad);
        boolean boundedWidth = MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED;
        int gridWidth = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight()
                - horizontalMargins(dock) - dock.getPaddingLeft() - dock.getPaddingRight()
                - horizontalMargins(keypad) - keypad.getPaddingLeft() - keypad.getPaddingRight();

        for (int r = 0; r < keypad.getChildCount(); r++) {
            View rowView = keypad.getChildAt(r);
            if (rowView.getVisibility() == GONE || !(rowView instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) rowView;
            int columns = 0, columnMargins = 0, rowExtra = 0;
            for (int c = 0; c < row.getChildCount(); c++) {
                View slot = row.getChildAt(c);
                DialpadKey key = findKey(slot);
                if (slot.getVisibility() == GONE || key == null) continue;
                columns++;
                columnMargins += horizontalMargins(slot);
                rowExtra = Math.max(rowExtra, verticalMargins(slot)
                        + slot.getPaddingTop() + slot.getPaddingBottom() + verticalMargins(key));
            }
            if (columns == 0) continue;
            rows++;
            gridExtra += verticalMargins(row) + row.getPaddingTop() + row.getPaddingBottom() + rowExtra;
            if (boundedWidth) {
                int columnWidth = (gridWidth - horizontalMargins(row)
                        - row.getPaddingLeft() - row.getPaddingRight() - columnMargins) / columns;
                for (int c = 0; c < row.getChildCount(); c++) {
                    View slot = row.getChildAt(c);
                    DialpadKey key = findKey(slot);
                    if (slot.getVisibility() == GONE || key == null) continue;
                    diameter = Math.min(diameter, columnWidth - slot.getPaddingLeft()
                            - slot.getPaddingRight() - horizontalMargins(key));
                }
            }
        }
        if (rows == 0) return;

        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) {
            int fixed = getPaddingTop() + getPaddingBottom() + occupiedHeight(header, widthSpec)
                    + occupiedHeight(navigation, widthSpec)
                    + (upper.getVisibility() == GONE ? 0 : verticalMargins(upper))
                    + verticalMargins(dock) + dock.getPaddingTop() + dock.getPaddingBottom() + gridExtra;
            for (int i = 0; i < dock.getChildCount(); i++) {
                View child = dock.getChildAt(i);
                if (child != keypad) fixed += occupiedHeight(child, widthSpec);
            }
            int upperReserve = upper.getVisibility() == GONE ? 0
                    : dp(inCall ? 240 : 72) + upper.getPaddingTop() + upper.getPaddingBottom();
            int available = MeasureSpec.getSize(heightSpec) - fixed - upperReserve;
            diameter = Math.min(diameter, Math.max(0, available / rows));
        }
        // The upper region yields space before any dial key becomes smaller than a touch target.
        diameter = Math.max(dp(48), diameter);
        boolean changed = false;
        for (int r = 0; r < keypad.getChildCount(); r++) {
            View rowView = keypad.getChildAt(r);
            if (!(rowView instanceof ViewGroup)) continue;
            ViewGroup row = (ViewGroup) rowView;
            for (int c = 0; c < row.getChildCount(); c++) {
                View slot = row.getChildAt(c);
                DialpadKey key = findKey(slot);
                if (key == null) continue;
                ViewGroup.LayoutParams size = key.getLayoutParams();
                if (size.width == diameter && size.height == diameter) continue;
                size.width = diameter;
                size.height = diameter;
                // Invalidate this measure chain without scheduling another layout from onMeasure.
                key.forceLayout();
                slot.forceLayout();
                row.forceLayout();
                changed = true;
            }
        }
        if (changed) {
            keypad.forceLayout();
            dock.forceLayout();
        }
    }

    private int occupiedHeight(View view, int widthSpec) {
        if (view == null || view.getVisibility() == GONE) return 0;
        ViewGroup.LayoutParams size = view.getLayoutParams();
        int height = size == null ? -1 : size.height;
        if (height < 0) {
            int width = getChildMeasureSpec(widthSpec, getPaddingLeft() + getPaddingRight()
                    + horizontalMargins(view), size == null ? LayoutParams.MATCH_PARENT : size.width);
            view.measure(width, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            height = view.getMeasuredHeight();
        }
        return height + verticalMargins(view);
    }

    private static DialpadKey findKey(View slot) {
        if (slot instanceof DialpadKey) return (DialpadKey) slot;
        if (!(slot instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) slot;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof DialpadKey) return (DialpadKey) group.getChildAt(i);
        }
        return null;
    }

    private static int verticalMargins(View view) {
        if (view == null || !(view.getLayoutParams() instanceof MarginLayoutParams)) return 0;
        MarginLayoutParams p = (MarginLayoutParams) view.getLayoutParams();
        return p.topMargin + p.bottomMargin;
    }

    private static int horizontalMargins(View view) {
        if (view == null || !(view.getLayoutParams() instanceof MarginLayoutParams)) return 0;
        MarginLayoutParams p = (MarginLayoutParams) view.getLayoutParams();
        return p.leftMargin + p.rightMargin;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
