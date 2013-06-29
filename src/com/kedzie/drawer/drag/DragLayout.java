package com.kedzie.drawer.drag;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewGroupCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RelativeLayout;

import com.kedzie.drawer.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Layout which handles sliding drawers in all directions.
 */
public class DragLayout extends RelativeLayout {

    private static final String TAG = "DragLayout";

    /**
     * Listener for monitoring events about drawers.
     */
    public interface DrawerListener {
        /**
         * Called when a drawer's position changes.
         * @param drawerView The child view that was moved
         * @param slideOffset The new offset of this drawer within its range, from 0-1
         */
        public void onDrawerSlide(View drawerView, float slideOffset);

        /**
         * Called when a drawer has settled in a completely open state.
         * The drawer is interactive at this point.
         *
         * @param drawerView Drawer view that is now open
         */
        public void onDrawerOpened(View drawerView);

        /**
         * Called when a drawer has settled in a completely closed state.
         *
         * @param drawerView Drawer view that is now closed
         */
        public void onDrawerClosed(View drawerView);

        /**
         * Called when the drawer motion state changes. The new state will
         * be one of {@link #STATE_IDLE}, {@link #STATE_DRAGGING} or {@link #STATE_SETTLING}.
         *
         * @param newState The new drawer motion state
         */
        public void onDrawerStateChanged(int newState);
    }

    /** Indicates that any drawers are in an idle, settled state. No animation is in progress. */
    public static final int STATE_IDLE = ViewDragHelper.STATE_IDLE;
    /** Indicates that a drawer is currently being dragged by the user. */
    public static final int STATE_DRAGGING = ViewDragHelper.STATE_DRAGGING;
    /** Indicates that a drawer is in the process of settling to a final position. */
    public static final int STATE_SETTLING = ViewDragHelper.STATE_SETTLING;

    private static final int DEFAULT_SCRIM_COLOR = 0x99000000;
    private int mScrimColor = DEFAULT_SCRIM_COLOR;
    private float mScrimOpacity;
    private DrawerListener mListener;
    private boolean mInLayout=false;
    private boolean mFirstLayout=true;
    private int mDrawerState;

    private float minFlingVelocity;

    /** Each drawer has its own ViewDragHelper and DragCallback */
    private Map<DraggedViewGroup, DrawerHolder> mDrawers = new HashMap<DraggedViewGroup, DrawerHolder>();

    public DragLayout(Context context) {
        this(context, null);
    }

    public DragLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DragLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Drawer, 0, 0);
        try {
            mScrimColor = a.getColor(R.styleable.DrawerLayout_scrim_color, DEFAULT_SCRIM_COLOR);
        } finally {
            a.recycle();
        }

        minFlingVelocity = getResources().getInteger(R.integer.min_fling_velocity) * getResources().getDisplayMetrics().density;

        // So that we can catch the back button
        setFocusableInTouchMode(true);
        ViewGroupCompat.setMotionEventSplittingEnabled(this, false);
    }

    @Override
    protected void onAttachedToWindow() {
        closeAllDrawers();
    }

//    @Override
//    protected void onFinishInflate() {
//        closeAllDrawers();
//    }

    @Override
    public void addView(View child) {
        super.addView(child);
        processAddView(child);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        super.addView(child, params);
        processAddView(child);
    }

    private void processAddView(View child) {
        if(child instanceof DraggedViewGroup) {
            Log.d(TAG, "Registering Drawer");
            DraggedViewGroup dragView = (DraggedViewGroup)child;
            DragCallback callback = new DragCallback();
            ViewDragHelper helper = ViewDragHelper.create(this, 0.5f, callback);
            helper.setMinVelocity(minFlingVelocity);
            callback.setDragHelper(helper);
            callback.setDragView(dragView);
            mDrawers.put(dragView, new DrawerHolder(helper, callback));
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        boolean interceptForDrag = false;
        for(DrawerHolder holder : mDrawers.values()) {
            interceptForDrag |= holder.helper.shouldInterceptTouchEvent(ev);
        }
        return interceptForDrag;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        for(DrawerHolder holder : mDrawers.values()) {
            holder.helper.processTouchEvent(event);
        }
        return true;
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams
                ? new LayoutParams((LayoutParams) p)
                : p instanceof ViewGroup.MarginLayoutParams
                ? new LayoutParams((MarginLayoutParams) p)
                : new LayoutParams(p);
    }

    /**
     * Set a listener to be notified of drawer events.
     *
     * @param listener Listener to notify when drawer events occur
     * @see DrawerListener
     */
    public void setDrawerListener(DrawerListener listener) {
        mListener = listener;
    }

    void setDrawerViewOffset(View drawerView, float slideOffset) {
        final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if (slideOffset == lp.onScreen) {
            return;
        }
        lp.onScreen = slideOffset;
        Log.d(TAG, "Slide offset: " + slideOffset);
    }

    float getDrawerViewOffset(View drawerView) {
        return ((LayoutParams) drawerView.getLayoutParams()).onScreen;
    }

    @Override
    public void requestLayout() {
        if (!mInLayout)
            super.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mInLayout=true;
        super.onLayout(changed, l, t, r, b);
        mInLayout=false;
        mFirstLayout=false;
    }

    @Override
    public void computeScroll() {
        final int childCount = getChildCount();
        float scrimOpacity = 0;
        for (int i = 0; i < childCount; i++) {
            final float onscreen = ((LayoutParams) getChildAt(i).getLayoutParams()).onScreen;
            scrimOpacity = Math.max(scrimOpacity, onscreen);
        }
        mScrimOpacity = scrimOpacity;

        final int baseAlpha = (mScrimColor & 0xff000000) >>> 24;
        final int imag = (int) (baseAlpha * mScrimOpacity);
        final int color = imag << 24 | (mScrimColor & 0xffffff);
        setBackgroundColor(color);

        // "|" used on purpose; both need to run.
        boolean invalidate=false;
        for(DrawerHolder holder : mDrawers.values())
            invalidate |= holder.helper.continueSettling(true);
        if (invalidate)
            ViewCompat.postInvalidateOnAnimation(this);
    }

    /**
     * Open a drawer with animation
     * @param drawerView the drawer to open
     */
    public void openDrawer(DraggedViewGroup drawerView) {
        Log.d(TAG, "Opening drawer");
        final ViewDragHelper mDragHelper = mDrawers.get(drawerView).helper;

        switch(drawerView.getDrawerType()) {
            case DraggedViewGroup.DRAWER_LEFT:
                mDragHelper.smoothSlideViewTo(drawerView, 0, drawerView.getTop());
                break;
            case DraggedViewGroup.DRAWER_RIGHT:
                mDragHelper.smoothSlideViewTo(drawerView, getWidth() - drawerView.getWidth(),
                        drawerView.getTop());
                break;
            case DraggedViewGroup.DRAWER_TOP:
                mDragHelper.smoothSlideViewTo(drawerView, drawerView.getLeft(), getHeight() - drawerView.getHeight());
                break;
            case DraggedViewGroup.DRAWER_BOTTOM:
                mDragHelper.smoothSlideViewTo(drawerView, drawerView.getLeft(), getHeight() - drawerView.getHeight());
                break;
        }
        invalidate();
    }

    /**
     * Close a drawer with animation
     * @param drawerView the drawer to close
     */
    public void closeDrawer(DraggedViewGroup drawerView) {
        Log.d(TAG, "Closing drawer");
        final ViewDragHelper mDragHelper = mDrawers.get(drawerView).helper;

        switch(drawerView.getDrawerType()) {
            case DraggedViewGroup.DRAWER_LEFT:
                mDragHelper.smoothSlideViewTo(drawerView, drawerView.getHandleSize()-drawerView.getWidth(), drawerView.getTop());
                break;
            case DraggedViewGroup.DRAWER_RIGHT:
                mDragHelper.smoothSlideViewTo(drawerView, getWidth()-drawerView.getHandleSize(), drawerView.getTop());
                break;
            case DraggedViewGroup.DRAWER_TOP:
                mDragHelper.smoothSlideViewTo(drawerView, drawerView.getLeft(), drawerView.getHandleSize()-drawerView.getHeight());
                break;
            case DraggedViewGroup.DRAWER_BOTTOM:
                mDragHelper.smoothSlideViewTo(drawerView, drawerView.getLeft(), getHeight() - drawerView.getHandleSize());
                break;
        }
        invalidate();
    }

    /**
     * Close all the drawers
     */
    public void closeAllDrawers() {
        Log.d(TAG, "Closing all drawers");
        for(int i=0; i<getChildCount(); i++) {
            View child = getChildAt(i);
            if(child instanceof DraggedViewGroup)
                closeDrawer((DraggedViewGroup)child);
        }
    }

    /**
     * Resolve the shared state of all drawers from the component ViewDragHelpers.
     * Should be called whenever a ViewDragHelper's state changes to notify listeners.
     */
    void updateDrawerState(int activeState, View activeDrawer) {
        int state = -1;

        for(DrawerHolder holder : mDrawers.values()) {
            if(holder.helper.getViewDragState() == STATE_DRAGGING) {
                state = STATE_DRAGGING;
                break;
            }
        }
        if(state==-1) {
            for(DrawerHolder holder : mDrawers.values()) {
                if(holder.helper.getViewDragState() == STATE_SETTLING) {
                    state = STATE_SETTLING;
                    break;
                }
            }
        }
        if(state==-1)
            state = STATE_IDLE;

        if (activeDrawer != null && activeState == STATE_IDLE) {
            final LayoutParams lp = (LayoutParams) activeDrawer.getLayoutParams();
            if (lp.onScreen == 0)
                dispatchOnDrawerClosed(activeDrawer);
            else if (lp.onScreen == 1)
                dispatchOnDrawerOpened(activeDrawer);
        }

        if (state != mDrawerState) {
            mDrawerState = state;
            if (mListener != null)
                mListener.onDrawerStateChanged(state);
        }
    }

    /**
     * Dispatch drawer close event to registered listener
     * @param drawerView    The drawer relevant to the event
     */
    private void dispatchOnDrawerClosed(View drawerView) {
        final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if (lp.knownOpen) {
            lp.knownOpen = false;
            if (mListener != null)
                mListener.onDrawerClosed(drawerView);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
    }

    /**
     * Dispatch drawer open event to registered listener
     * @param drawerView    The drawer relevant to the event
     */
    private void dispatchOnDrawerOpened(View drawerView) {
        final LayoutParams lp = (LayoutParams) drawerView.getLayoutParams();
        if (!lp.knownOpen) {
            lp.knownOpen = true;
            if (mListener != null)
                mListener.onDrawerOpened(drawerView);
            drawerView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
    }

    /**
     * Dispatch drawer slide event to registered listener
     * @param drawerView    The drawer relevant to the event
     */
    private void dispatchOnDrawerSlide(View drawerView, float slideOffset) {
        if (mListener != null)
            mListener.onDrawerSlide(drawerView, slideOffset);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {

        if(child instanceof DraggedViewGroup) {
            final DraggedViewGroup dragView = (DraggedViewGroup)child;
            if(dragView.getShadowDrawable() != null) {
                Drawable shadow = dragView.getShadowDrawable();
                final int shadowWidth = shadow.getIntrinsicWidth();
                final int shadowHeight = shadow.getIntrinsicHeight();

                switch(dragView.getDrawerType()) {
                    case DraggedViewGroup.DRAWER_LEFT:
                        final int childRight = child.getRight()-dragView.getHandleSize();
                        shadow.setBounds(childRight, child.getTop(),
                                childRight + shadowWidth, child.getBottom());
                        break;
                    case DraggedViewGroup.DRAWER_RIGHT:
                        final int childLeft = child.getLeft()+dragView.getHandleSize();
                        shadow.setBounds(childLeft-shadowWidth, child.getTop(),
                                childLeft, child.getBottom());
                        break;
                    case DraggedViewGroup.DRAWER_TOP:
                        final int childBottom = child.getBottom()-dragView.getHandleSize();
                        shadow.setBounds(child.getLeft(), childBottom,
                                child.getRight(), childBottom+shadowHeight);
                        break;
                    case DraggedViewGroup.DRAWER_BOTTOM:
                        final int childTop = child.getTop()+dragView.getHandleSize();
                        shadow.setBounds(child.getLeft(), childTop-shadowHeight,
                                child.getRight(), childTop);
                        break;
                }
                shadow.draw(canvas);
            }
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    /**
     * Respond to drag events for a particular drawer
     */
    private class DragCallback extends ViewDragHelper.Callback {

        private ViewDragHelper mHelper;
        private DraggedViewGroup mDragView;

        public void setDragHelper(ViewDragHelper helper) {
            mHelper = helper;
        }

        public void setDragView(DraggedViewGroup view) {
            mDragView = view;
        }

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return child == mDragView;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            updateDrawerState(state, mHelper.getCapturedView());
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            float offset=0;
            final DraggedViewGroup dragView = (DraggedViewGroup)changedView;
            final int childWidth = changedView.getWidth()-dragView.getHandleSize();
            final int childHeight = changedView.getHeight()-dragView.getHandleSize();

            switch(dragView.getDrawerType()) {
                case DraggedViewGroup.DRAWER_LEFT:
                    offset = (float) (childWidth + left) / childWidth;
                    break;
                case DraggedViewGroup.DRAWER_RIGHT:
                    offset = (float) (getWidth() - left) / childWidth;
                    break;
                case DraggedViewGroup.DRAWER_TOP:
                    offset = (float) (childHeight + top) / childHeight;
                    break;
                case DraggedViewGroup.DRAWER_BOTTOM:
                    offset = (float) (getHeight() - top) / childHeight;
                    break;

            }
            offset = Math.min(offset, 1f);
            setDrawerViewOffset(changedView, offset);
            invalidate();
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
            final LayoutParams lp = (LayoutParams) capturedChild.getLayoutParams();
            lp.isPeeking = false;
            closeOtherDrawers((DraggedViewGroup)capturedChild);
        }

        private void closeOtherDrawers(DraggedViewGroup dragView) {
            for(int i=0; i<getChildCount(); i++) {
                View child = getChildAt(i);
                if(child instanceof DraggedViewGroup && child != dragView) {
                    closeDrawer((DraggedViewGroup)child);
                }
            }
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            final float offset = getDrawerViewOffset(releasedChild);
            final int childWidth = releasedChild.getWidth();
            final int childHeight = releasedChild.getHeight();
            final DraggedViewGroup dragView = (DraggedViewGroup)releasedChild;

            int left=releasedChild.getLeft();
            int top=releasedChild.getTop();
            switch(dragView.getDrawerType()) {
                case DraggedViewGroup.DRAWER_LEFT:
                    left = xvel > 0 || xvel == 0 && offset > 0.5f ? 0 : dragView.getHandleSize()-childWidth;
                    break;
                case DraggedViewGroup.DRAWER_RIGHT:
                    final int width = getWidth();
                    left = xvel < 0 || xvel == 0 && offset > 0.5f ? width-childWidth : width-dragView.getHandleSize();
                    break;
                case DraggedViewGroup.DRAWER_TOP:
                    top = yvel > 0 || yvel == 0 && offset > 0.5f ? 0 : dragView.getHandleSize()-childHeight;
                    break;
                default:
                    final int height = getHeight();
                    top = yvel < 0 || yvel == 0 && offset > 0.5f ? height-childHeight : height-dragView.getHandleSize();
                    break;

            }
            mHelper.settleCapturedViewAt(left, top);
            invalidate();
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            final DraggedViewGroup dragView = (DraggedViewGroup)child;

            switch(dragView.getDrawerType()) {
                case DraggedViewGroup.DRAWER_LEFT:
                case DraggedViewGroup.DRAWER_RIGHT:
                    return child.getWidth()-dragView.getHandleSize();
                default:
                    return 0;

            }
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            final DraggedViewGroup dragView = (DraggedViewGroup)child;
            switch(dragView.getDrawerType()) {
                case DraggedViewGroup.DRAWER_BOTTOM:
                case DraggedViewGroup.DRAWER_TOP:
                    return child.getHeight()-dragView.getHandleSize();
                default:
                    return 0;
            }
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            final DraggedViewGroup dragView = (DraggedViewGroup)child;

            switch(dragView.getDrawerType()) {
                case DraggedViewGroup.DRAWER_TOP:
                    return Math.max(dragView.getHandleSize()-child.getHeight(), Math.min(top, 0));
                case DraggedViewGroup.DRAWER_BOTTOM:
                    final int height = getHeight();
                    return Math.max(height - child.getHeight(), Math.min(top, height-dragView.getHandleSize()));
                default:
                    return child.getTop();

            }
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            final DraggedViewGroup dragView = (DraggedViewGroup)child;

            switch(dragView.getDrawerType()) {
                case DraggedViewGroup.DRAWER_LEFT:
                    return Math.max(dragView.getHandleSize()-child.getWidth(), Math.min(left, 0));
                case DraggedViewGroup.DRAWER_RIGHT:
                    final int width = getWidth();
                    return Math.max(width - child.getWidth(), Math.min(left, width-dragView.getHandleSize()));
                default:
                    return child.getLeft();
            }

        }
    }

    /**
     * Drawer related LayoutParams
     */
    public static class LayoutParams extends RelativeLayout.LayoutParams{

        float onScreen;
        boolean isPeeking;
        boolean knownOpen;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }
    }

    /**
     * Reference to a drawer and helper functionality
     */
    public static class DrawerHolder {
        public ViewDragHelper helper;
        public DragCallback callback;

        public DrawerHolder() {}

        public DrawerHolder(ViewDragHelper helper, DragCallback callback) {
            this.helper=helper;
            this.callback=callback;
        }
    }
}
