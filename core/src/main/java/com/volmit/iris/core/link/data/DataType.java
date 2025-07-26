package com.volmit.iris.core.link.data;

import com.volmit.iris.core.link.ExternalDataProvider;
import com.volmit.iris.core.link.Identifier;

import java.util.MissingResourceException;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public enum DataType implements BiPredicate<ExternalDataProvider, Identifier> {
    ITEM,
    BLOCK,
    ENTITY;

    @Override
    public boolean test(ExternalDataProvider dataProvider, Identifier identifier) {
        if (!dataProvider.isValidProvider(identifier, this)) return false;
        try {
            switch (this) {
                case ITEM -> dataProvider.getItemStack(identifier);
                case BLOCK -> dataProvider.getBlockData(identifier);
                case ENTITY -> {}
            }
            return true;
        } catch (MissingResourceException e) {
            return false;
        }
    }

    public Predicate<Identifier> asPredicate(ExternalDataProvider dataProvider) {
        return i -> test(dataProvider, i);
    }
}
