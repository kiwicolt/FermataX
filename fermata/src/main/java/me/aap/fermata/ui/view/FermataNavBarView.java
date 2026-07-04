package me.aap.fermata.ui.view;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.view.GestureDetectorCompat;

import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.ui.view.GestureListener;
import me.aap.utils.ui.view.NavBarView;
import me.aap.utils.ui.view.NavButtonView;

/**
 * @author Andrey Pavlenko
 */
public class FermataNavBarView extends NavBarView implements GestureListener {
	private final GestureDetectorCompat gestureDetector;

	public FermataNavBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		gestureDetector = new GestureDetectorCompat(context, this);
	}

	public FermataNavBarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		gestureDetector = new GestureDetectorCompat(context, this);
	}

	protected boolean interceptTouchEvent(MotionEvent e) {
		gestureDetector.onTouchEvent(e);
		return super.onTouchEvent(e);
	}

	@Override
	protected MainActivityDelegate getActivity() {
		return MainActivityDelegate.get(getContext());
	}

	@Override
	public void addView(View child) {
		super.addView(child);
		sizeVerticalNavButton(child, getVerticalButtonExtent());
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		sizeVerticalNavButtons(w);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		sizeVerticalNavButtons(MeasureSpec.getSize(widthMeasureSpec));
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		sizeVerticalNavButtons(r - l);
		super.onLayout(changed, l, t, r, b);
	}

	@Override
	public boolean onSwipeLeft(MotionEvent e1, MotionEvent e2) {
		return getMainActivity().getControlPanel().onSwipeLeft(e1, e2);
	}

	@Override
	public boolean onSwipeRight(MotionEvent e1, MotionEvent e2) {
		return getMainActivity().getControlPanel().onSwipeRight(e1, e2);
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		if (scrollNavBar(distanceX, distanceY)) return true;
		return getMainActivity().getControlPanel().onScroll(e1, e2, distanceX, distanceY);
	}

	private boolean scrollNavBar(float distanceX, float distanceY) {
		int max;
		int next;

		if (isBottom()) {
			max = Math.max(0, getContentWidth() - getWidth());
			if (max == 0) return false;
			next = clamp(getScrollX() + (int) distanceX, 0, max);
			if (next == getScrollX()) return true;
			scrollTo(next, 0);
		} else {
			max = Math.max(0, getContentHeight() - getHeight());
			if (max == 0) return false;
			next = clamp(getScrollY() + (int) distanceY, 0, max);
			if (next == getScrollY()) return true;
			scrollTo(0, next);
		}

		return true;
	}

	private int getContentWidth() {
		int count = getChildCount();
		if (count == 0) return 0;
		View child = getChildAt(count - 1);
		return child.getRight() + getPaddingRight();
	}

	private int getContentHeight() {
		int count = getChildCount();
		if (count == 0) return 0;
		View child = getChildAt(count - 1);
		return child.getBottom() + getPaddingBottom();
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private int getVerticalButtonExtent() {
		int extent = getWidth();
		if (extent <= 0) {
			var lp = getLayoutParams();
			if (lp != null) extent = lp.width;
		}
		return (extent <= 0) ? extent : extent + (extent / 4);
	}

	private void sizeVerticalNavButtons(int extent) {
		if (isBottom()) return;
		extent = (extent <= 0) ? getVerticalButtonExtent() : extent + (extent / 4);
		if (extent <= 0) return;
		for (int i = 0, n = getChildCount(); i < n; i++) {
			sizeVerticalNavButton(getChildAt(i), extent);
		}
	}

	private void sizeVerticalNavButton(View child, int extent) {
		if (isBottom() || !(child instanceof NavButtonView) || (extent <= 0)) return;

		var lp = child.getLayoutParams();
		if (lp == null) return;
		boolean changed = (lp.width != MATCH_PARENT) || (lp.height != extent);
		lp.width = MATCH_PARENT;
		lp.height = extent;

		if ((lp instanceof LinearLayoutCompat.LayoutParams llp) && (llp.weight != 0F)) {
			llp.weight = 0F;
			changed = true;
		} else if ((lp instanceof LinearLayout.LayoutParams llp) && (llp.weight != 0F)) {
			llp.weight = 0F;
			changed = true;
		}

		if (changed) child.setLayoutParams(lp);
	}

	private MainActivityDelegate getMainActivity() {
		return MainActivityDelegate.get(getContext());
	}
}
