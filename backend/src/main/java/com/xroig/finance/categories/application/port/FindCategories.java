package com.xroig.finance.categories.application.port;

import com.xroig.finance.categories.application.CategoryView;

import java.util.List;

/** Inbound port: list every category as a read model. */
public interface FindCategories {

    List<CategoryView> all();
}
