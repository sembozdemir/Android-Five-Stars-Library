package angtrim.com.fivestarslibrary;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RatingBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.play.core.tasks.OnCompleteListener;
import com.google.android.play.core.tasks.OnFailureListener;
import com.google.android.play.core.tasks.Task;


/**
 * Created by angtrim on 12/09/15.
 */
public class FiveStarsDialog implements DialogInterface.OnClickListener {

    private final static String DEFAULT_TITLE = "Rate this app";
    private final static String DEFAULT_TEXT = "How much do you love our app?";
    private final static String DEFAULT_POSITIVE = "Ok";
    private final static String DEFAULT_NEGATIVE = "Not Now";
    private final static String DEFAULT_NEVER = "Never";
    private final static String SP_NUM_OF_ACCESS = "numOfAccess";
    private static final String SP_DISABLED = "disabled";
    private static final String TAG = FiveStarsDialog.class.getSimpleName();
    private final Context context;
    private boolean isForceMode = false;
    private final SharedPreferences sharedPrefs;
    private String supportEmail;
    private TextView contentTextView;
    private RatingBar ratingBar;
    private String title = null;
    private String rateText = null;
    private String positiveLabel = "";
    private String notNowLabel = "";
    private String neverLabel = "";
    private AlertDialog alertDialog;
    private View dialogView;
    private int upperBound = 4;
    private NegativeReviewListener negativeReviewListener;
    private PositiveReviewListener positiveReviewListener;
    private ReviewListener reviewListener;
    private InAppReviewListener inAppReviewListener;
    private int starColor;
    private boolean isTesting = false;
    private boolean inAppReviewMode = false;

    public FiveStarsDialog(Context context, String supportEmail) {
        this.context = context;
        sharedPrefs = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        this.supportEmail = supportEmail;
    }

    private void build() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        dialogView = inflater.inflate(R.layout.stars, null);
        String titleToAdd = (title == null) ? DEFAULT_TITLE : title;
        String textToAdd = (rateText == null) ? DEFAULT_TEXT : rateText;
        contentTextView = dialogView.findViewById(R.id.text_content);
        contentTextView.setText(textToAdd);
        ratingBar = dialogView.findViewById(R.id.ratingBar);
        ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
            @Override
            public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
                Log.d(TAG, "Rating changed : " + v);
                if (isForceMode && v >= upperBound) {
                    if (inAppReviewMode) {
                        launchInAppReview();
                    } else {
                        openMarket();
                    }
                    if (reviewListener != null)
                        reviewListener.onReview((int) ratingBar.getRating());
                }
            }
        });

        if (starColor != -1) {
            LayerDrawable stars = (LayerDrawable) ratingBar.getProgressDrawable();
            stars.getDrawable(1).setColorFilter(starColor, PorterDuff.Mode.SRC_ATOP);
            stars.getDrawable(2).setColorFilter(starColor, PorterDuff.Mode.SRC_ATOP);
        }

        builder.setTitle(titleToAdd)
                .setView(dialogView)
                .setPositiveButton(positiveLabel, this);

        if (!TextUtils.isEmpty(notNowLabel)) {
            builder.setNegativeButton(notNowLabel, this);
        }

        if (!TextUtils.isEmpty(neverLabel)) {
            builder.setNeutralButton(neverLabel, this);
        }
        alertDialog = builder.create();
    }

    private void launchInAppReview() {
        final ReviewManager manager = ReviewManagerFactory.create(context);
        Task<ReviewInfo> request = manager.requestReviewFlow();
        request.addOnCompleteListener(new OnCompleteListener<ReviewInfo>() {
            @Override
            public void onComplete(@NonNull Task<ReviewInfo> task) {
                if (task.isSuccessful()) {
                    if (context instanceof Activity) {
                        Task<Void> flow = manager.launchReviewFlow(((Activity) context), task.getResult());
                        flow.addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> task) {
                                if (inAppReviewListener != null) {
                                    inAppReviewListener.onInAppReview();
                                }
                            }
                        });
                        flow.addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                openMarket();
                            }
                        });
                    } else {
                        openMarket();
                    }
                } else {
                    openMarket();
                }
            }
        });
    }

    private void disable() {
        if (isTesting) {
            return;
        }
        SharedPreferences shared = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = shared.edit();
        editor.putBoolean(SP_DISABLED, true);
        editor.apply();
    }

    private void openMarket() {
        final String appPackageName = context.getPackageName();
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }

    private void sendEmail() {
        final Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("text/email");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{supportEmail});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "App Report (" + context.getPackageName() + ")");
        emailIntent.putExtra(Intent.EXTRA_TEXT, "");
        context.startActivity(Intent.createChooser(emailIntent, "Send mail..."));
    }

    private void show() {
        boolean disabled = sharedPrefs.getBoolean(SP_DISABLED, false);
        if (!disabled) {
            build();
            alertDialog.show();
        }
    }

    public void showAfter(int numberOfAccess) {
        build();
        SharedPreferences.Editor editor = sharedPrefs.edit();
        int numOfAccess = sharedPrefs.getInt(SP_NUM_OF_ACCESS, 0);
        editor.putInt(SP_NUM_OF_ACCESS, numOfAccess + 1);
        editor.apply();
        if (numOfAccess + 1 >= numberOfAccess) {
            show();
        }
    }


    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
        if (i == DialogInterface.BUTTON_POSITIVE) {
            if (ratingBar.getRating() < upperBound) {
                if (negativeReviewListener == null) {
                    sendEmail();
                } else {
                    negativeReviewListener.onNegativeReview((int) ratingBar.getRating());
                }

            } else if (!isForceMode) {
                if (positiveReviewListener != null) {
                    positiveReviewListener.onPositiveReview((int) ratingBar.getRating());
                } else if (inAppReviewMode) {
                    launchInAppReview();
                } else {
                    openMarket();
                }
            }
            disable();
            if (reviewListener != null)
                reviewListener.onReview((int) ratingBar.getRating());
        }
        if (i == DialogInterface.BUTTON_NEUTRAL) {
            disable();
        }
        if (i == DialogInterface.BUTTON_NEGATIVE) {
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putInt(SP_NUM_OF_ACCESS, 0);
            editor.apply();
        }
        alertDialog.hide();
    }

    public FiveStarsDialog setTitle(String title) {
        this.title = title;
        return this;

    }

    public FiveStarsDialog setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
        return this;
    }

    public FiveStarsDialog setRateText(String rateText) {
        this.rateText = rateText;
        return this;
    }

    public FiveStarsDialog setStarColor(int color) {
        starColor = color;
        return this;
    }

    /**
     * Set to true if you want to send the user directly to the market
     *
     * @param isForceMode
     * @return
     */
    public FiveStarsDialog setForceMode(boolean isForceMode) {
        this.isForceMode = isForceMode;
        return this;
    }

    /**
     * Set the upper bound for the rating.
     * If the rating is >= of the bound, the market is opened.
     *
     * @param bound the upper bound
     * @return the dialog
     */
    public FiveStarsDialog setUpperBound(int bound) {
        this.upperBound = bound;
        return this;
    }

    /**
     * Set a custom listener if you want to OVERRIDE the default "send email" action when the user gives a negative review
     *
     * @param listener
     * @return
     */
    public FiveStarsDialog setNegativeReviewListener(NegativeReviewListener listener) {
        this.negativeReviewListener = listener;
        return this;
    }

    /**
     * Set a custom listener if you want to OVERRIDE the default "open market" action when the user gives a positive review
     *
     * @param listener
     * @return
     */
    public FiveStarsDialog setPositiveReviewListener(PositiveReviewListener listener) {
        this.positiveReviewListener = listener;
        return this;
    }

    /**
     * Set a listener to get notified when a review (positive or negative) is issued, for example for tracking purposes
     *
     * @param listener
     * @return
     */
    public FiveStarsDialog setReviewListener(ReviewListener listener) {
        this.reviewListener = listener;
        return this;
    }

    /**
     * Set to true if you do not want to disable dialogs when clicking OK or Never
     * Default false
     *
     * @param isTesting
     * @return
     */
    public FiveStarsDialog setTesting(boolean isTesting) {
        this.isTesting = isTesting;
        return this;
    }

    /**
     * Set custom labels to support localization
     *
     * @param positive positive label
     * @param notNow not now label
     * @param never never label
     * @return
     */
    public FiveStarsDialog setLabels(String positive, String notNow, String never) {
        positiveLabel = positive;
        notNowLabel = notNow;
        neverLabel = never;
        return this;
    }

    /**
     * Enable in-app review popup
     *
     * @param inAppReviewMode
     * @return
     */
    public FiveStarsDialog setInAppReviewMode(boolean inAppReviewMode) {
        this.inAppReviewMode = inAppReviewMode;
        return this;
    }

    /**
     * Set a listener to get notified when in app review flow completed, for example for tracking purposes
     *
     * Note that, The API does not indicate whether the user reviewed or not, or even whether the review dialog was shown
     *
     * @param inAppReviewListener
     * @return
     */
    public FiveStarsDialog setInAppReviewListener(InAppReviewListener inAppReviewListener) {
        this.inAppReviewListener = inAppReviewListener;
        return this;
    }
}
