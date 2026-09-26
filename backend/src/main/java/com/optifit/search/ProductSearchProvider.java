package com.optifit.search;

import java.util.List;

import com.optifit.model.FaceProfile;
import com.optifit.model.Preferences;
import com.optifit.model.Product;

public interface ProductSearchProvider {

    List<Product> search(Preferences preferences, FaceProfile profile);
}
