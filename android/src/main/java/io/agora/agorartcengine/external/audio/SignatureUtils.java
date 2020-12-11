package io.agora.agorartcengine.external.audio;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import androidx.annotation.NonNull;

import com.google.common.io.BaseEncoding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SignatureUtils {
    /**
     * Gets the SHA1 signature, hex encoded for inclusion with Google Cloud Platform API requests
     *
     * @param packageName Identifies the APK whose signature should be extracted.
     * @return a lowercase, hex-encoded
     */
    public static String getSignature(@NonNull PackageManager pm, @NonNull String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (packageInfo == null
                        || packageInfo.signingInfo == null) {
                    return null;
                }
                if (packageInfo.signingInfo.hasMultipleSigners()) {
                    return signatureDigest(packageInfo.signingInfo.getApkContentsSigners()[0]);
                } else {
                    return signatureDigest(packageInfo.signingInfo.getSigningCertificateHistory()[0]);
                }
            } else {
                @SuppressLint("PackageManagerGetSignatures")
                PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                if (packageInfo == null
                        || packageInfo.signatures == null
                        || packageInfo.signatures.length == 0
                        || packageInfo.signatures[0] == null) {
                    return null;
                }
                return signatureDigest(packageInfo.signatures[0]);
            }
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static String signatureDigest(Signature sig) {
        byte[] signature = sig.toByteArray();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] digest = md.digest(signature);
            return BaseEncoding.base16().lowerCase().encode(digest);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
