package com.optifit.service;

import com.optifit.model.FaceProfile;
import com.optifit.model.Preferences;

public interface FaceAnalyzer {

    FaceProfile analyze(byte[] jpeg);

    default FaceProfile analyze(byte[] jpeg, Preferences preferences) {
        return analyze(jpeg);
    }
}
