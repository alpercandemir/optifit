package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.optifit.exception.ApiException;
import com.optifit.model.FaceProfile;
import com.optifit.model.Shape;
import com.optifit.service.GeminiFaceAnalyzer;

class GeminiFaceAnalyzerTest {

    @Test
    void acceptsAUsableSingleFaceProfile() {
        var profile = new FaceProfile(true, 1, "Visual style suggestion only.", List.of(Shape.ROUND));
        assertThat(GeminiFaceAnalyzer.validate(profile)).isSameAs(profile);
    }

    @Test
    void rejectsInvalidProviderOutputBeforeProductSearch() {
        assertThatThrownBy(() -> GeminiFaceAnalyzer.validate(null)).isInstanceOf(ApiException.class)
                .hasMessage("The visual assessment could not be completed. Please try again.");
        assertThatThrownBy(() -> GeminiFaceAnalyzer.validate(new FaceProfile(true, 1, "", List.of(Shape.UNKNOWN))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void doesNotExposeArbitraryProviderGuidanceInApiErrors() {
        var profile = new FaceProfile(false, 2, "Untrusted provider-generated text", List.of());
        assertThatThrownBy(() -> GeminiFaceAnalyzer.validate(profile)).isInstanceOf(ApiException.class)
                .hasMessage("Upload a clear, front-facing photo containing exactly one face.");
    }
}
