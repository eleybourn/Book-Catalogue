package com.eleybourn.bookcatalogue;

import android.app.Activity;

import androidx.annotation.NonNull;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * Manages Google Play Billing for subscription services and one-time purchases.
 */
public class BillingManager implements PurchasesUpdatedListener {
    // The SKU/Product ID for the subscription
    public static final String SUBSCRIPTION_ID = "online_backup_subscription";
    // The SKU/Product ID for the lifetime one-time purchase
    public static final String LIFETIME_ID = "online_backup_lifetime";

    public interface BillingListener {
        void onSubscriptionStatusChanged(boolean isSubscribed);
        void onPricesReceived(String subscriptionPrice, String lifetimePrice);
    }

    private final BillingClient mBillingClient;
    private final Activity mActivity;
    private final BookCataloguePreferences mPrefs;
    private BillingListener mListener;
    private String mLastSubsPrice = null;
    private String mLastLifetimePrice = null;

    // Internal state for combined query
    private boolean mFoundSubscribed = false;
    private boolean mFoundLifetime = false;
    private boolean mFoundAutoRenewing = false;

    public BillingManager(Activity activity) {
        mActivity = activity;
        mPrefs = new BookCataloguePreferences();
        mBillingClient = BillingClient.newBuilder(activity)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
    }

    public void setListener(BillingListener listener) {
        mListener = listener;
    }

    public void startConnection() {
        if (mBillingClient.getConnectionState() == BillingClient.ConnectionState.CONNECTING) {
            return;
        }
        mBillingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryPurchases();
                    queryProductDetails();
                } else {
                    mActivity.runOnUiThread(() ->
                            android.widget.Toast.makeText(mActivity, "Billing connection failed: " + billingResult.getDebugMessage(), android.widget.Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Connection lost, should handle retry if needed.
            }
        });
    }

    public void queryPurchases() {
        if (!mBillingClient.isReady()) {
            startConnection();
            return;
        }

        mFoundSubscribed = false;
        mFoundAutoRenewing = false;

        // Query subscriptions first
        mBillingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                (billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (Purchase purchase : purchases) {
                            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED && !purchase.isSuspended()) {
                                for (String productId : purchase.getProducts()) {
                                    if (productId.equals(SUBSCRIPTION_ID)) {
                                        mFoundSubscribed = true;
                                        mFoundAutoRenewing = purchase.isAutoRenewing();
                                        handlePurchase(purchase);
                                    }
                                }
                            }
                        }
                    }

                    // Then query one-time products
                    mBillingClient.queryPurchasesAsync(
                            QueryPurchasesParams.newBuilder()
                                    .setProductType(BillingClient.ProductType.INAPP)
                                    .build(),
                            (billingResult2, purchases2) -> {
                                if (billingResult2.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                                    for (Purchase purchase : purchases2) {
                                        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                                            for (String productId : purchase.getProducts()) {
                                                if (productId.equals(LIFETIME_ID)) {
                                                    mFoundSubscribed = true;
                                                    mFoundLifetime = true;
                                                    // Lifetime never auto-renews
                                                    handlePurchase(purchase);
                                                }
                                            }
                                        }
                                    }
                                }

                                mPrefs.setSubscribed(mFoundSubscribed);
                                mPrefs.setLifetime(mFoundLifetime);
                                mPrefs.setAutoRenewing(mFoundAutoRenewing);
                                if (mListener != null) {
                                    final boolean finalIsSubscribed = mFoundSubscribed;
                                    mActivity.runOnUiThread(() -> mListener.onSubscriptionStatusChanged(finalIsSubscribed));
                                }
                            }
                    );
                }
        );
    }

    public void queryProductDetails() {
        if (!mBillingClient.isReady()) {
            return;
        }
        querySubsDetails();
    }

    private void querySubsDetails() {
        QueryProductDetailsParams subsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(SUBSCRIPTION_ID)
                                .setProductType(BillingClient.ProductType.SUBS)
                                .build()))
                .build();

        mBillingClient.queryProductDetailsAsync(subsParams, (billingResult, productDetailsResult) -> {
            List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                ProductDetails productDetails = productDetailsList.get(0);
                List<ProductDetails.SubscriptionOfferDetails> offerDetails = productDetails.getSubscriptionOfferDetails();
                if (offerDetails != null && !offerDetails.isEmpty()) {
                    mLastSubsPrice = offerDetails.get(0).getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice();
                }
            }
            queryInAppDetails();
        });
    }

    private void queryInAppDetails() {
        QueryProductDetailsParams inappParams = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(LIFETIME_ID)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()))
                .build();

        mBillingClient.queryProductDetailsAsync(inappParams, (billingResult, productDetailsResult) -> {
            List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null && !productDetailsList.isEmpty()) {
                ProductDetails productDetails = productDetailsList.get(0);
                ProductDetails.OneTimePurchaseOfferDetails oneTimeDetails = productDetails.getOneTimePurchaseOfferDetails();
                if (oneTimeDetails != null) {
                    mLastLifetimePrice = oneTimeDetails.getFormattedPrice();
                }
            }

            if (mListener != null) {
                mActivity.runOnUiThread(() -> mListener.onPricesReceived(mLastSubsPrice, mLastLifetimePrice));
            }
        });
    }

    public String getLastSubsPrice() {
        return mLastSubsPrice;
    }

    public String getLastLifetimePrice() {
        return mLastLifetimePrice;
    }

    public void launchPurchaseFlow(String productId) {
        if (!mBillingClient.isReady()) {
            android.widget.Toast.makeText(mActivity, "Billing service not ready. Connecting...", android.widget.Toast.LENGTH_SHORT).show();
            startConnection();
            return;
        }

        String productType = productId.equals(SUBSCRIPTION_ID) ? BillingClient.ProductType.SUBS : BillingClient.ProductType.INAPP;

        QueryProductDetailsParams queryProductDetailsParams =
                QueryProductDetailsParams.newBuilder()
                        .setProductList(
                                Collections.singletonList(
                                        QueryProductDetailsParams.Product.newBuilder()
                                                .setProductId(productId)
                                                .setProductType(productType)
                                                .build()))
                        .build();

        mBillingClient.queryProductDetailsAsync(
                queryProductDetailsParams,
                (billingResult, productDetailsResult) -> {
                    int responseCode = billingResult.getResponseCode();
                    if (responseCode == BillingClient.BillingResponseCode.OK) {
                        List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                        if (!productDetailsList.isEmpty()) {
                            ProductDetails productDetails = productDetailsList.get(0);

                            BillingFlowParams.ProductDetailsParams.Builder paramsBuilder =
                                    BillingFlowParams.ProductDetailsParams.newBuilder()
                                            .setProductDetails(productDetails);

                            if (productType.equals(BillingClient.ProductType.SUBS)) {
                                List<ProductDetails.SubscriptionOfferDetails> offerDetails = productDetails.getSubscriptionOfferDetails();
                                if (offerDetails != null && !offerDetails.isEmpty()) {
                                    paramsBuilder.setOfferToken(offerDetails.get(0).getOfferToken());
                                } else {
                                    mActivity.runOnUiThread(() ->
                                            android.widget.Toast.makeText(mActivity, "No active offers found for subscription.", android.widget.Toast.LENGTH_SHORT).show()
                                    );
                                    return;
                                }
                            }

                            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                    .setProductDetailsParamsList(Collections.singletonList(paramsBuilder.build()))
                                    .build();

                            mBillingClient.launchBillingFlow(mActivity, billingFlowParams);
                        } else {
                            mActivity.runOnUiThread(() ->
                                    android.widget.Toast.makeText(mActivity, "Product ID '" + productId + "' not found.", android.widget.Toast.LENGTH_LONG).show()
                            );
                        }
                    } else {
                        final String debugMessage = billingResult.getDebugMessage();
                        mActivity.runOnUiThread(() ->
                                android.widget.Toast.makeText(mActivity, "Error " + responseCode + ": " + debugMessage, android.widget.Toast.LENGTH_SHORT).show()
                        );
                    }
                }
        );
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
            queryPurchases(); // Refresh state
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            // Check if it's the subscription or lifetime
            boolean isSubs = false;
            boolean isLifetime = false;
            for (String pid : purchase.getProducts()) {
                if (pid.equals(SUBSCRIPTION_ID)) {
                    isSubs = true;
                } else if (pid.equals(LIFETIME_ID)) {
                    isLifetime = true;
                }
            }

            if (isSubs) {
                mPrefs.setAutoRenewing(purchase.isAutoRenewing());
            } else {
                mPrefs.setAutoRenewing(false);
            }
            
            if (isLifetime) {
                mPrefs.setLifetime(true);
            }

            if (!mPrefs.isSubscribed()) {
                mPrefs.setOnlineSyncEnabled(true);
            }

            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                mBillingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        mPrefs.setSubscribed(true);
                        if (mListener != null) {
                            mActivity.runOnUiThread(() -> mListener.onSubscriptionStatusChanged(true));
                        }
                    }
                });
            } else {
                mPrefs.setSubscribed(true);
                if (mListener != null) {
                    mActivity.runOnUiThread(() -> mListener.onSubscriptionStatusChanged(true));
                }
            }
        }
    }

    public void endConnection() {
        mBillingClient.endConnection();
    }
}
