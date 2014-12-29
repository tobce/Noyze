package me.barrasso.android.volume.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Base64DataException;

import com.android.vending.billing.IInAppBillingService;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.barrasso.android.volume.LogUtils;

// Because not one enjoys Accounting.
public class Accountant {

    private static String r(String s) {
        return new StringBuilder(s).reverse().toString();
    }

    // TODO: this should NEVER be set to true for production!
    public static final boolean FREE_FOR_ALL = true;

    private IInAppBillingService mService;
    private String mSignatureBase64;
    private Context context;

    protected boolean mConnected;
    protected boolean mSetupDone;
    protected boolean mSubscriptionsSupported;

    private static Accountant mAccountant;
    public static synchronized Accountant getInstance(Context context) {
        LogUtils.LOGI("Accountant", "getInstance(" + context + "), mAccountant = " + mAccountant);
        if (null == mAccountant)
            mAccountant = new Accountant(context);
        return mAccountant;
    }

    public static void destroyInstance() {
        LogUtils.LOGI("Accountant", "destroyInstance()");
        if (null != mAccountant) {
            mAccountant.destroy();
            mAccountant = null;
        }
    }

    public Accountant(Context context) {
        LogUtils.LOGI("Accountant", "new Accountant(" + context + ')');
        this.context = context.getApplicationContext();

        synchronized (this) {
            // Build our Base64 Public Key... but in an annoying way!
            // This makes static reverse engineering a pain in the butt.
            StringBuilder b = new StringBuilder();
            b.append('M');
            b.append((char) 73);
            b.append((char) 73);
            b.append('B');
            b.append((char) 73);
            b.append(r("MkJnsAEQACKgCBIIMA8QACOAAFEQAB0w9GikhqkgBNAj"));
            b.append('/');
            b.append(r("IB9PgyKqXQ5RdjDO"));
            b.append('/');
            b.append(new String(new char[] { 76, 100, 97, 71, 70, 51 })); // LdaGF3
            b.append("rvHk4tSabdTfA9PJr7FnlhO2H7rJaMzTSLriY");
            b.append('/');
            b.append("EXTvTI9xOhyKTHJyV1cmgCS6RA5VUsc3P3gPCIme");
            b.append(r("bJBkSeWhLLis38ZBoiCl9BPZaLg34iTdiv3t1nf8m5WAm94kuWMhFE"));
            b.append((char) 43);
            b.append("FOiQxkVXd6ZHIrntBViIrry");
            b.append('/');
            b.append("0r6xg6qY8KLaworK" + 'O');
            b.append('/');
            b.append("zn85viDBGJWqKkbUOQCbhg1dmOj1xcPuCyJDSjH0jWx2gT3Vojwn9djcVOcoq+4puFGhOyFhLYNLPAX/");
            b.append("j5ZG39FzcWq3xtn5POLUewvN581Zdl");
            b.append(r("AQADIwnkAmAmlYN3RF3QHe60lAV84xL3Q"));
            b.append('B');
            mSignatureBase64 = b.toString();
        }

        connect();
    }

    public void destroy() {
        LogUtils.LOGI("Accountant", "destroy()");
        mSetupDone = false;
        if (mService != null) {
            if (context != null) {
                try {
                    context.unbindService(mServiceConn);
                } catch (Exception iae) {
                    LogUtils.LOGE("Accountant", "Error unbinding from the Google Play service.", iae);
                }
            }
        }
        mService = null;
        mAccountant = null;
    }

    public IInAppBillingService getService() {
        return mService;
    }

    /** @return All purchased SKUs */
    public List<String> getPurchases() {
        if (FREE_FOR_ALL) {
            // TODO: include ALL in-app purchase SKUs here.
            List<String> list = new ArrayList<String>(1);
            list.add("theme_unlock");
            return list;
        }
        if (null == mService) return new ArrayList<String>(0);
        LogUtils.LOGI("Accountant", "getPurchases()");
        try {
            Bundle bundle = mService.getPurchases(3, context.getPackageName(), ITEM_TYPE_INAPP, null);
            if (bundle.getInt(RESPONSE_CODE) == BILLING_RESPONSE_RESULT_OK) {
                return bundle.getStringArrayList(RESPONSE_INAPP_ITEM_LIST);
            }
        } catch (RemoteException re) {
            LogUtils.LOGE("Accountant", "Error obtaining in-app purchases.", re);
        }
        return new ArrayList<String>(0);
    }

    /** @return True if the user has purchased the given SKU. */
    public Boolean has(String sku) {
        if (FREE_FOR_ALL) return true;
        if (null == mService || null == sku) return null;
        LogUtils.LOGI("Accountant", "has(" + sku + ')');
        List<String> skus = getPurchases();
        return (null != skus && skus.contains(sku));
    }

    /** Launches the purchase flow (Google Play UI to complete in-app purchase. */
    public boolean buy(Activity activity, String sku) {
        if (null == mService || activity == null) return false;
        LogUtils.LOGI("Accountant", "buy(" + sku + ')');
        try {
            Bundle bundle = mService.getBuyIntent(3, activity.getPackageName(),
                    sku, ITEM_TYPE_INAPP, null);
            LogUtils.LOGD("Accountant", Utils.bundle2string(bundle));

            PendingIntent pendingIntent = bundle.getParcelable(RESPONSE_BUY_INTENT);
            if (bundle.getInt(RESPONSE_CODE) == BILLING_RESPONSE_RESULT_OK) {
                // Start purchase flow (this brings up the Google Play UI).
                // Result will be delivered through onActivityResult().
                activity.startIntentSenderForResult(pendingIntent.getIntentSender(),
                        RESULT_CODE_BUY, new Intent(), 0, 0, 0);
                return true;
            }
        } catch (Throwable t) {
            LogUtils.LOGE("Accountant", "Error launching in-app purchase Intent.", t);
        }
        return false;
    }

    public boolean connect() {
        LogUtils.LOGI("Accountant", "connect()");
        if (null != mService) return true;
        Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        if (!context.getPackageManager().queryIntentServices(serviceIntent, 0).isEmpty()) {
            Intent nServiceIntent = Utils.createExplicitFromImplicitIntent(context, serviceIntent);
            return context.bindService(((null != nServiceIntent) ? nServiceIntent : serviceIntent),
                    mServiceConn, Context.BIND_AUTO_CREATE);
        } else {
            // LISTENER: no service available to handle that Intent
            return false;
        }
    }

    public String getSignatureBase64() { return mSignatureBase64; }
    public boolean inAppPurchasesSupported() {
        return mSetupDone;
    }

    ServiceConnection mServiceConn = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LogUtils.LOGI("Accountant", "onServiceDisconnected(" + name.toString() + ')');
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LogUtils.LOGI("Accountant", "onServiceConnected(" + name.toString() + ')');
            mService = IInAppBillingService.Stub.asInterface(service);
            String packageName = context.getPackageName();
            try {
                // check for in-app billing v3 support
                int response = mService.isBillingSupported(3, packageName, ITEM_TYPE_INAPP);
                if (response != BILLING_RESPONSE_RESULT_OK) {
                    // LISTENER: No in-app billing
                    // if in-app purchases aren't supported, neither are subscriptions.
                    mSubscriptionsSupported = false;
                    return;
                }

                // check for v3 subscriptions support
                response = mService.isBillingSupported(3, packageName, ITEM_TYPE_SUBS);
                if (response == BILLING_RESPONSE_RESULT_OK) {
                    mSubscriptionsSupported = true;
                } else {
                    // LISTENER: Doesn't support version 3.
                }

                mSetupDone = true;
            } catch (RemoteException e) {
                // LISTENER: RemoteException
                e.printStackTrace();
            }

            // LISTENER: SUCCESS!
        }
    };

    // Billing response codes
    public static final int BILLING_RESPONSE_RESULT_OK = 0;
    public static final int BILLING_RESPONSE_RESULT_USER_CANCELED = 1;
    public static final int BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3;
    public static final int BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4;
    public static final int BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5;
    public static final int BILLING_RESPONSE_RESULT_ERROR = 6;
    public static final int BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7;
    public static final int BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8;

    public static final int RESULT_CODE_BUY = 1984;

    // IAB Helper error codes
    public static final int RESPONSE_ERROR_BASE = -1000;
    public static final int RESPONSE_REMOTE_EXCEPTION = -1001;
    public static final int RESPONSE_BAD_RESPONSE = -1002;
    public static final int RESPONSE_VERIFICATION_FAILED = -1003;
    public static final int RESPONSE_SEND_INTENT_FAILED = -1004;
    public static final int RESPONSE_USER_CANCELLED = -1005;
    public static final int RESPONSE_UNKNOWN_PURCHASE_RESPONSE = -1006;
    public static final int RESPONSE_MISSING_TOKEN = -1007;
    public static final int RESPONSE_UNKNOWN_ERROR = -1008;
    public static final int RESPONSE_SUBSCRIPTIONS_NOT_AVAILABLE = -1009;
    public static final int RESPONSE_INVALID_CONSUMPTION = -1010;

    // Keys for the responses from InAppBillingService
    public static final String RESPONSE_CODE = "RESPONSE_CODE";
    public static final String RESPONSE_GET_SKU_DETAILS_LIST = "DETAILS_LIST";
    public static final String RESPONSE_BUY_INTENT = "BUY_INTENT";
    public static final String RESPONSE_INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";
    public static final String RESPONSE_INAPP_SIGNATURE = "INAPP_DATA_SIGNATURE";
    public static final String RESPONSE_INAPP_ITEM_LIST = "INAPP_PURCHASE_ITEM_LIST";
    public static final String RESPONSE_INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
    public static final String RESPONSE_INAPP_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
    public static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";

    // Item types
    public static final String ITEM_TYPE_INAPP = "inapp";
    public static final String ITEM_TYPE_SUBS = "subs";

    // some fields on the getSkuDetails response bundle
    public static final String GET_SKU_DETAILS_ITEM_LIST = "ITEM_ID_LIST";
    public static final String GET_SKU_DETAILS_ITEM_TYPE_LIST = "ITEM_TYPE_LIST";

    private static final String KEY_FACTORY_ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    /**
     * Verifies that the data was signed with the given signature, and returns
     * the verified purchase. The data is in JSON format and signed
     * with a private key.
     * @param base64PublicKey the base64-encoded public key to use for verifying.
     * @param signedData the signed JSON string (signed, not encrypted)
     * @param signature the signature for the data, signed with the private key
     */
    public static boolean verifyPurchase(String base64PublicKey, String signedData, String signature) {
        if (signedData == null) {
            return false;
        }

        boolean verified;
        if (!TextUtils.isEmpty(signature)) {
            PublicKey key = generatePublicKey(base64PublicKey);
            verified = verify(key, signedData, signature);
            if (!verified) {
                return false;
            }
        }
        return true;
    }

    /**
     * Generates a PublicKey instance from a string containing the
     * Base64-encoded public key.
     *
     * @param encodedPublicKey Base64-encoded public key
     * @throws IllegalArgumentException if encodedPublicKey is invalid
     */
    public static PublicKey generatePublicKey(String encodedPublicKey) {
        try {
            byte[] decodedKey = Base64.decode(encodedPublicKey, 0);
            KeyFactory keyFactory = KeyFactory.getInstance(KEY_FACTORY_ALGORITHM);
            return keyFactory.generatePublic(new X509EncodedKeySpec(decodedKey));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            LogUtils.LOGE("Accountant", "Invalid key specification.", e);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Verifies that the signature from the server matches the computed
     * signature on the data.  Returns true if the data is correctly signed.
     *
     * @param publicKey public key associated with the developer account
     * @param signedData signed data from server
     * @param signature server signature
     * @return true if the data and signature match
     */
    public static boolean verify(PublicKey publicKey, String signedData, String signature) {
        Signature sig;
        try {
            sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(signedData.getBytes());
            return sig.verify(Base64.decode(signature, 0));
        } catch (NoSuchAlgorithmException e) {
            LogUtils.LOGE("Accountant", "NoSuchAlgorithmException.", e);
        } catch (InvalidKeyException e) {
            LogUtils.LOGE("Accountant", "Invalid key specification.", e);
        } catch (SignatureException e) {
            LogUtils.LOGE("Accountant", "Signature exception.", e);
        }
        return false;
    }

}