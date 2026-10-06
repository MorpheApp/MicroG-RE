package com.google.android.gms.auth.api.signin.internal;

import com.google.android.gms.auth.api.signin.internal.ISignInCallbacks;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

interface ISignInService {
    // RE changes start
    void silentSignIn(ISignInCallbacks callbacks, in GoogleSignInOptions options) = 101;
    void signOut(ISignInCallbacks callbacks, in GoogleSignInOptions options) = 102;
    void revokeAccess(ISignInCallbacks callbacks, in GoogleSignInOptions options) = 103;
    // RE changes end
}
