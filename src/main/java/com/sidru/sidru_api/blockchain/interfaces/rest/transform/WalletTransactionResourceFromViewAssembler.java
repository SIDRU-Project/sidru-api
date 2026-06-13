package com.sidru.sidru_api.blockchain.interfaces.rest.transform;

import com.sidru.sidru_api.blockchain.application.internal.queryservices.WalletTransactionView;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WalletTransactionResource;

public class WalletTransactionResourceFromViewAssembler {

    private WalletTransactionResourceFromViewAssembler() {}

    public static WalletTransactionResource toResourceFromView(WalletTransactionView view) {
        return new WalletTransactionResource(
                view.type(),
                view.txHash(),
                view.status(),
                view.explorerUrl());
    }
}
