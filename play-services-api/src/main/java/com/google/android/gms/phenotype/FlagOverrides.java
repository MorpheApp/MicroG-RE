/*
 * SPDX-FileCopyrightText: 2020, microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package com.google.android.gms.phenotype;

// RE changes start
import java.util.Collections;
import java.util.List;
// RE changes end

import org.microg.safeparcel.AutoSafeParcelable;

public class FlagOverrides extends AutoSafeParcelable {
    // RE changes start
    @Field(2)
    public List<FlagOverride> overrides;

    public FlagOverrides() {
        this(Collections.emptyList());
    }

    public FlagOverrides(List<FlagOverride> overrides) {
        this.overrides = overrides;
    }
    // RE changes end

    public static final Creator<FlagOverrides> CREATOR = new AutoCreator<>(FlagOverrides.class);
}
