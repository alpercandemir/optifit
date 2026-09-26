package com.optifit;

import com.optifit.Models.FaceProfile;
import com.optifit.Models.Preferences;

interface FaceAnalyzer {

    FaceProfile analyze(byte[] jpeg);

    default FaceProfile analyze(byte[] jpeg, Preferences preferences) {
        return analyze(jpeg);
    }
}
