package com.tinyvpn;

import android.app.Activity;
import android.os.Message;
import android.util.Log;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.util.ArrayList;
import java.util.List;

public class MyPurchasesUpdatedListener implements PurchasesUpdatedListener {
    public Activity activity;
    private BillingClient billingClient;
    SkuDetails skuDetailsYear;


    void start(){
        billingClient = BillingClient.newBuilder(MainActivity.mContext).enablePendingPurchases().setListener(this).build();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() ==  BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    Log.d("SUB", "billing connect ok.");
                    //MainActivity.txtLog.append("billing connect ok.");
                }
            }
            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                Log.d("SUB", "billing connect fail.");
                //MainActivity.txtLog.append("billing connect fail.");
            }
        });

    }
    void query() {
        List<String> skuList = new ArrayList<>();
        skuList.add("sub.premium.2");
        //skuList.add("android.test.purchased");
        SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
        params.setSkusList(skuList).setType(BillingClient.SkuType.SUBS);
        Log.d("SUB", "set query ok.");

        if(billingClient==null)
            return;
        billingClient.querySkuDetailsAsync(params.build(),
                new SkuDetailsResponseListener() {
                    @Override
                    public void onSkuDetailsResponse(BillingResult billingResult,
                                                     List<SkuDetails> skuDetailsList) {
                        // Process the result.
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                            for (SkuDetails skuDetails : skuDetailsList) {
                                String sku = skuDetails.getSku();
                                String price = skuDetails.getPrice();
                                Log.d("SUB", "query,"+sku+","+price);
                                if ("sub.premium.2".equals(sku)) {
                                    Log.d("SUB", "sub.premium.2:"+price);
                                    skuDetailsYear = skuDetails;
                                    Message msg = new Message();
                                    msg.what = 4;
                                    MainActivity.handler.sendMessage(msg);

                                }
                            }
                        }
                    }
                });


    }
    void launch(){
        // Retrieve a value for "skuDetails" by calling querySkuDetailsAsync().
        if(billingClient==null)
            return;
        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetailsYear)
                .build();
        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
    }
    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                && purchases != null) {
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle an error caused by a user cancelling the purchase flow.
        } else {
            // Handle any other error codes.
        }
    }
    void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            // Grant entitlement to the user.

            Log.d("SUB", "send premium user info to server.");
            new Thread(MainActivity.charge_runnable).start();
            // Acknowledge the purchase if it hasn't already been acknowledged.
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                AcknowledgePurchaseResponseListener acknowledgePurchaseResponseListener = new AcknowledgePurchaseResponseListener() {
                    @Override
                    public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
                        Log.d("SUB","Purchase acknowledged");
                    }
                };
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, acknowledgePurchaseResponseListener);
            }
        }
    }
}
