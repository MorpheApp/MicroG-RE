package com.google.android.gms.auth.api.signin.internal;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.Status;

interface ISignInCallbacks {
    // RE changes start
    void onSignIn(in GoogleSignInAccount account, in Status status) = 101;
    void onSignOut(in Status status) = 102;
    void onRevokeAccess(in Status status) = 103;
    // RE changes end
}
