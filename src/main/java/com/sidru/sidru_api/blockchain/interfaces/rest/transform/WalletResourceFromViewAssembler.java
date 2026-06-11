package com.sidru.sidru_api.blockchain.interfaces.rest.transform;

import com.sidru.sidru_api.blockchain.application.internal.queryservices.WalletView;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WalletResource;

public class WalletResourceFromViewAssembler {

    private WalletResourceFromViewAssembler() {}

    public static WalletResource toResourceFromView(WalletView view) {
        return new WalletResource(
                view.address(),
                view.network(),
                view.balanceCtc(),
                view.balanceWei(),
                view.solesRef(),
                view.linkedWallet());
    }
}
