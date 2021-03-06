/*
 *   Copyright 2015 Eric Liu
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package permissionhelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import permissionhelper.annotations.PermissionDenied;
import permissionhelper.annotations.PermissionGranted;

/**
 * A helper for checking and requesting permissions for app that targeting Android M (API >= 23)
 * <p/>
 * Created by Dixit on 15/12/28.
 */
public class PermissionHelper {

    private static final String TAG = "PermissionHelper";

    private static PermissionHelper instance = new PermissionHelper();

    public static PermissionHelper getInstance() {
        return instance;
    }

    /**
     * Return if the context has the permission.
     *
     * @param context
     * @param permission
     * @return
     */
    public boolean hasPermission(Context context, String permission) {
        return (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED);
    }

    /**
     * Request the permission.
     *
     * @param context
     * @param permission
     */
    public void requestPermission(Activity context, String permission) {
        requestPermission(context, permission, null);
    }

    /**
     * Request the permission.
     *
     * @param context
     * @param permission
     * @param rationale
     */
    public void requestPermission(Activity context, String permission, String rationale) {
        if (hasPermission(context, permission)) {
            invokeGrantedMethod(context, permission);
        } else if (!TextUtils.isEmpty(rationale) && !ActivityCompat.shouldShowRequestPermissionRationale(context, permission)) {
            showRequestPermissionRationale(context, permission, rationale);
        } else {
            ActivityCompat.requestPermissions(context, new String[]{permission}, 0);
        }
    }

    /**
     * Show UI with rationale for requesting a permission.
     *
     * @param activity
     * @param permission
     * @param rationale
     */
    private void showRequestPermissionRationale(final Activity activity, final String permission, String rationale) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setMessage(rationale)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(activity, new String[]{permission}, 0);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).create();
        dialog.show();
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermission(Activity, String, String)}.
     *
     * @param obj
     * @param permissions
     * @param grantResults
     */
    public void onRequestPermissionsResult(Object obj, String[] permissions, int[] grantResults) {
         /*if (isGranted(grantResults)) {
            if(permissions[0] !=null)
            invokeGrantedMethod(obj, permissions[0]);
        } else {
            if(permissions[0] !=null)
            invokeDeniedMethod(obj, permissions[0]);
        }*/
        if(permissions != null && verifyPermissions(grantResults))
        {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                    invokeGrantedMethod(obj, permissions[i]);
                else if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                    invokeDeniedMethod(obj, permissions[i]);
            }
        }
    }

    /**
     * internal usage.
     */
    private boolean verifyPermissions(int[] grantResults) {
        if (grantResults==null) {
            return false;
        }
        if (grantResults.length < 1) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Invoke a method with annotation PermissionGranted.
     *
     * @param obj
     * @param permission
     */
    private void invokeGrantedMethod(Object obj, String permission) {
        Class clazz = obj.getClass();
        PermissionGranted permissionGranted;

        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(PermissionGranted.class)) {
                permissionGranted = method.getAnnotation(PermissionGranted.class);
                if (permissionGranted.permission().equals(permission)) {
                    if (method.getModifiers() != Modifier.PUBLIC) {
                        throw new IllegalArgumentException(String.format("Annotation method %s must be public.", method));
                    }

                    if (method.getParameterTypes().length > 0) {
                        throw new RuntimeException(String.format("Cannot execute non-void method %s.", method));
                    }

                    try {
                        method.invoke(obj);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Invoke a method with annotation PermissionDenied.
     *
     * @param obj
     * @param permission
     */
    private void invokeDeniedMethod(Object obj, String permission) {
        Class clazz = obj.getClass();
        PermissionDenied permissionDenied;

        for (Method method : clazz.getMethods()) {
            if (method.isAnnotationPresent(PermissionDenied.class)) {
                permissionDenied = method.getAnnotation(PermissionDenied.class);
                if (permissionDenied.permission().equals(permission)) {
                    if (method.getModifiers() != Modifier.PUBLIC) {
                        throw new IllegalArgumentException(String.format("Annotation method %s must be public.", method));
                    }

                    if (method.getParameterTypes().length > 0) {
                        throw new RuntimeException(String.format("Cannot execute non-void method %s.", method));
                    }

                    try {
                        method.invoke(obj);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

}
