package com.optifit;

import java.util.List;

import com.optifit.Models.FaceProfile;
import com.optifit.Models.Preferences;
import com.optifit.Models.Product;

interface ProductSearchProvider {

    List<Product> search(Preferences preferences, FaceProfile profile);
}
