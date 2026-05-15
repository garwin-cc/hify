package com.hify.model.domain;

public interface ProviderHealthAlertNotifier {

    void notify(ProviderHealthAlertEvent event);
}
