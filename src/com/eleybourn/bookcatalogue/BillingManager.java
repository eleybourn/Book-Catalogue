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
 * Manages Google Play Billing for subscription services.
 */
public class BillingManager implements PurchasesUpdatedListener {
    // The SKU/Product ID for the subscription
    public static final String SUBSCRIPTION_ID = "online_backup_subscription";

    public interface BillingListener {
        void onSubscriptionStatusChanged(boolean isSubscribed);
        void onPriceReceived(String price);
    }

    private final BillingClient mBillingClient;
    private final Activity mActivity;
    private final BookCataloguePreferences mPrefs;
    private BillingListener mListener;

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
        mBillingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                (billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        boolean isSubscribed = false;
                        boolean isAutoRenewing = false;
                        for (Purchase purchase : purchases) {
                            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED && !purchase.isSuspended()) {
                                for (String productId : purchase.getProducts()) {
                                    if (productId.equals(SUBSCRIPTION_ID)) {
                                        isSubscribed = true;
                                        isAutoRenewing = purchase.isAutoRenewing();
                                        // Ensure it stays acknowledged
                                        handlePurchase(purchase);
                                        break;
                                    }
                                }
                            }
                        }
                        mPrefs.setSubscribed(isSubscribed);
                        mPrefs.setAutoRenewing(isAutoRenewing);
                        if (mListener != null) {
                            final boolean finalIsSubscribed = isSubscribed;
                            mActivity.runOnUiThread(() -> mListener.onSubscriptionStatusChanged(finalIsSubscribed));
                        }
                    }
                }
        );
    }

    public void queryProductDetails() {
        if (!mBillingClient.isReady()) {
            return;
        }

        QueryProductDetailsParams queryProductDetailsParams =
                QueryProductDetailsParams.newBuilder()
                        .setProductList(
                                Collections.singletonList(
                                        QueryProductDetailsParams.Product.newBuilder()
                                                .setProductId(SUBSCRIPTION_ID)
                                                .setProductType(BillingClient.ProductType.SUBS)
                                                .build()))
                        .build();

        mBillingClient.queryProductDetailsAsync(
                queryProductDetailsParams,
                (billingResult, productDetailsResult) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                        if (!productDetailsList.isEmpty()) {
                            ProductDetails productDetails = productDetailsList.get(0);
                            List<ProductDetails.SubscriptionOfferDetails> offerDetails = productDetails.getSubscriptionOfferDetails();
                            if (offerDetails != null && !offerDetails.isEmpty()) {
                                // Just get the first pricing phase of the first offer
                                String price = offerDetails.get(0).getPricingPhases().getPricingPhaseList().get(0).getFormattedPrice();
                                if (mListener != null) {
                                    mActivity.runOnUiThread(() -> mListener.onPriceReceived(price));
                                }
                            }
                        }
                    }
                }
        );
    }

    public void launchPurchaseFlow() {
        if (!mBillingClient.isReady()) {
            android.widget.Toast.makeText(mActivity, "Billing service not ready. Connecting...", android.widget.Toast.LENGTH_SHORT).show();
            startConnection();
            return;
        }

        QueryProductDetailsParams queryProductDetailsParams =
                QueryProductDetailsParams.newBuilder()
                        .setProductList(
                                Collections.singletonList(
                                        QueryProductDetailsParams.Product.newBuilder()
                                                .setProductId(SUBSCRIPTION_ID)
                                                .setProductType(BillingClient.ProductType.SUBS)
                                                .build()))
                        .build();

        mBillingClient.queryProductDetailsAsync(
                queryProductDetailsParams,
                (billingResult, productDetailsResult) -> {
                    int responseCode = billingResult.getResponseCode();
                    if (responseCode == BillingClient.BillingResponseCode.OK) {
                        List<ProductDetails> productDetailsList = productDetailsResult.getProductDetailsList();
                        if (!productDetailsList.isEmpty()) {
                            // Product found!
                            ProductDetails productDetails = productDetailsList.get(0);
                            
                            List<ProductDetails.SubscriptionOfferDetails> offerDetails = productDetails.getSubscriptionOfferDetails();
                            if (offerDetails != null && !offerDetails.isEmpty()) {
                                String offerToken = offerDetails.get(0).getOfferToken();
                                
                                List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList =
                                        Collections.singletonList(
                                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                                        .setProductDetails(productDetails)
                                                        .setOfferToken(offerToken)
                                                        .build());

                                BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                        .setProductDetailsParamsList(productDetailsParamsList)
                                        .build();

                                mBillingClient.launchBillingFlow(mActivity, billingFlowParams);
                            } else {
                                mActivity.runOnUiThread(() ->
                                        android.widget.Toast.makeText(mActivity, "No active offers found for subscription.", android.widget.Toast.LENGTH_SHORT).show()
                                );
                            }
                        } else {
                            // If not found as SUBS
                            mActivity.runOnUiThread(() ->
                                    android.widget.Toast.makeText(mActivity, "Product ID '" + SUBSCRIPTION_ID + "' not found in Play Console. Check if it is Active and has an Active Base Plan.", android.widget.Toast.LENGTH_LONG).show()
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
        }
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            mPrefs.setAutoRenewing(purchase.isAutoRenewing());

            // If they are becoming subscribed for the first time (or re-subscribing),
            // enable sync by default.
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
