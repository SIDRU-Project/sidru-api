package com.sidru.sidru_api.blockchain.interfaces.rest.transform;

import com.sidru.sidru_api.blockchain.application.internal.queryservices.WalletSummaryView;
import com.sidru.sidru_api.blockchain.interfaces.rest.resources.WalletResource;

public class WalletResourceFromViewAssembler {

    private WalletResourceFromViewAssembler() {}

    public static WalletResource toResourceFromView(WalletSummaryView view) {
        return new WalletResource(
                view.pointsBalance(),
                view.ctcEquivalent(),
                view.solesEquivalent(),
                view.network(),
                view.explorerBaseUrl(),
                view.linkedWallet(),
                view.minWithdrawalPoints(),
                view.withdrawalsEnabled(),
                view.hasWithdrawalInProgress());
    }
}
