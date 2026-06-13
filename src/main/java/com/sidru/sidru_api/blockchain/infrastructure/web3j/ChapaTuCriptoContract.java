package com.sidru.sidru_api.blockchain.infrastructure.web3j;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.tx.response.TransactionReceiptProcessor;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Hand-written Web3j gateway for the ChapaTuCripto (CTC) ERC-20 + AccessControl
 * contract on Polygon Amoy. No web3j CLI wrapper is used; calls are encoded with
 * {@link FunctionEncoder} and sent through a {@link RawTransactionManager}.
 *
 * <p>The backend wallet (BACKEND_ROLE) signs and pays gas. Custodial user addresses
 * never sign. The signing key is loaded lazily and never logged.
 */
@Component
public class ChapaTuCriptoContract {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChapaTuCriptoContract.class);

    /** Polygon Amoy chainId (EIP-155 replay protection + EIP-1559 envelope). */
    private static final long CHAIN_ID = 80002L;

    /**
     * Fallback priority fee for Amoy when {@code eth_maxPriorityFeePerGas} is unavailable.
     * Amoy commonly requires ~25-30 gwei tips to be relayed; 30 gwei is a safe default.
     */
    private static final BigInteger FALLBACK_PRIORITY_FEE_WEI =
            BigInteger.valueOf(30L).multiply(BigInteger.TEN.pow(9)); // 30 gwei

    /**
     * Static gas limit. recordAndReward measured ~111.7k gas and withdrawTo/redeemFrom are lower,
     * so 150k keeps a comfortable (~34%) margin while reducing the up-front balance the node
     * reserves for a tx (balance >= gasLimit * maxFeePerGas), which matters on a low-POL wallet.
     */
    private static final BigInteger TX_GAS_LIMIT = BigInteger.valueOf(150_000L);

    private final BlockchainProperties properties;

    private volatile Web3j web3j;
    private volatile RawTransactionManager txManager;
    private volatile TransactionReceiptProcessor receiptProcessor;
    private volatile String fromAddress;

    public ChapaTuCriptoContract(BlockchainProperties properties) {
        this.properties = properties;
    }

    private synchronized void ensureConnected() {
        if (web3j != null) {
            return;
        }
        Web3j client = Web3j.build(new HttpService(properties.getNodeUrl()));
        // Credentials never logged; only the resulting public address is exposed.
        Credentials credentials = Credentials.create(properties.getPrivateKey());
        TransactionReceiptProcessor processor =
                new PollingTransactionReceiptProcessor(client, 3000, 40);
        // chainId 80002 = Polygon Amoy (EIP-155 replay protection).
        this.txManager = new RawTransactionManager(client, credentials, 80002L, processor);
        this.receiptProcessor = processor;
        this.fromAddress = credentials.getAddress();
        this.web3j = client;
        LOGGER.info("ChapaTuCripto gateway connected. Backend signer address: {}", fromAddress);
    }

    /**
     * recordAndReward(address user, uint256 sessionId, bytes32 qrHash, uint256 amount).
     * Mints {@code amount} CTC to the custodial address; reverts if the session was
     * already recorded (anti double-spend).
     */
    public TransactionReceipt recordAndReward(String user, BigInteger sessionId,
                                              byte[] qrHash, BigInteger amount) throws Exception {
        ensureConnected();
        Function function = new Function(
                "recordAndReward",
                Arrays.asList(
                        new Address(user),
                        new Uint256(sessionId),
                        new Bytes32(qrHash),
                        new Uint256(amount)),
                Collections.emptyList());
        return sendTransaction(function);
    }

    /**
     * withdrawTo(address from, address to, uint256 amount). Privileged custodial
     * transfer: the backend pays gas, the custodial address never signs.
     */
    public TransactionReceipt withdrawTo(String from, String to, BigInteger amount) throws Exception {
        ensureConnected();
        Function function = new Function(
                "withdrawTo",
                Arrays.asList(new Address(from), new Address(to), new Uint256(amount)),
                Collections.emptyList());
        return sendTransaction(function);
    }

    /**
     * redeemFrom(address from, uint256 amount, uint256 rewardTxId). Burns CTC from
     * the citizen's custody when a reward is redeemed, keeping on-chain CTC in sync
     * with the off-chain points deduction. Backend pays gas; custodial never signs.
     */
    public TransactionReceipt redeemFrom(String from, BigInteger amount, BigInteger rewardTxId)
            throws Exception {
        ensureConnected();
        Function function = new Function(
                "redeemFrom",
                Arrays.asList(new Address(from), new Uint256(amount), new Uint256(rewardTxId)),
                Collections.emptyList());
        return sendTransaction(function);
    }

    /** balanceOf(address) view call. Returns the on-chain CTC balance in wei. */
    public BigInteger balanceOf(String account) throws Exception {
        ensureConnected();
        Function function = new Function(
                "balanceOf",
                Collections.singletonList(new Address(account)),
                Collections.singletonList(new TypeReference<Uint256>() {}));
        String encoded = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
                        Transaction.createEthCallTransaction(fromAddress, properties.getContractAddress(), encoded),
                        DefaultBlockParameterName.LATEST)
                .send();
        if (response.isReverted()) {
            throw new IllegalStateException("balanceOf reverted: " + response.getRevertReason());
        }
        List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        if (decoded.isEmpty()) {
            return BigInteger.ZERO;
        }
        return (BigInteger) decoded.get(0).getValue();
    }

    /**
     * rewardRedeemed(uint256) view call. Returns whether a {@code rewardTxId} was already
     * burned on-chain (anti double-redeem). Lets the backend check idempotency before a
     * (re)burn without spending gas. No gas: it is an eth_call.
     */
    public boolean rewardRedeemed(BigInteger rewardTxId) throws Exception {
        ensureConnected();
        Function function = new Function(
                "rewardRedeemed",
                Collections.singletonList(new Uint256(rewardTxId)),
                Collections.singletonList(new TypeReference<Bool>() {}));
        String encoded = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
                        Transaction.createEthCallTransaction(fromAddress, properties.getContractAddress(), encoded),
                        DefaultBlockParameterName.LATEST)
                .send();
        if (response.isReverted()) {
            throw new IllegalStateException("rewardRedeemed reverted: " + response.getRevertReason());
        }
        List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        return !decoded.isEmpty() && (Boolean) decoded.get(0).getValue();
    }

    /**
     * Lazily connects (if needed) and returns the shared {@link Web3j} client. Exposed so the
     * event listener can build an {@code EthFilter}/flowable over the contract logs without
     * duplicating connection/credentials handling. The signing key is never exposed.
     */
    public Web3j web3jClient() {
        ensureConnected();
        return web3j;
    }

    /** Contract address (lowercase 0x...) of the deployed CTC contract. */
    public String contractAddress() {
        return properties.getContractAddress();
    }

    /**
     * Sends a state-changing call as an EIP-1559 (type-2) transaction.
     *
     * <p>Polygon Amoy is an EIP-1559 chain: legacy static gas pricing
     * ({@code DefaultGasProvider.GAS_PRICE}) underprices or stalls transactions, which can lead to
     * replaced/rejected txs or wrong fees on real mint/withdraw. We therefore price dynamically:
     * <ul>
     *   <li>{@code maxPriorityFeePerGas} comes from {@code eth_maxPriorityFeePerGas}; if the node
     *       does not support it or returns null, we fall back to a safe Amoy default (30 gwei).</li>
     *   <li>{@code baseFee} is read from the latest block; {@code maxFeePerGas = baseFee*2 + tip}
     *       to absorb base-fee growth across a couple of blocks.</li>
     *   <li>If the latest block has no {@code baseFeePerGas} (non-1559 response), we degrade to a
     *       legacy {@code sendTransaction} so the call still goes through.</li>
     * </ul>
     * The {@link RawTransactionManager} is already built with chainId 80002, so it can sign the
     * type-2 envelope. balanceOf is an eth_call (view) and never reaches this path: it spends no gas.
     */
    private TransactionReceipt sendTransaction(Function function) throws Exception {
        String encoded = FunctionEncoder.encode(function);
        String contractAddress = properties.getContractAddress();

        BigInteger maxPriorityFeePerGas = resolveMaxPriorityFeePerGas();
        BigInteger baseFee = resolveBaseFeePerGas();

        org.web3j.protocol.core.methods.response.EthSendTransaction sent;
        if (baseFee == null) {
            // No baseFeePerGas on the latest block: not a 1559-shaped response. Fall back to legacy
            // pricing so the transaction is still relayed (documented degradation).
            LOGGER.warn("Latest block has no baseFeePerGas; falling back to legacy gas pricing.");
            sent = txManager.sendTransaction(
                    DefaultGasProvider.GAS_PRICE,
                    TX_GAS_LIMIT,
                    contractAddress,
                    encoded,
                    BigInteger.ZERO);
        } else {
            // maxFee covers base-fee growth over ~2 blocks plus the priority tip.
            BigInteger maxFeePerGas = baseFee.multiply(BigInteger.TWO).add(maxPriorityFeePerGas);
            sent = txManager.sendEIP1559Transaction(
                    CHAIN_ID,
                    maxPriorityFeePerGas,
                    maxFeePerGas,
                    TX_GAS_LIMIT,
                    contractAddress,
                    encoded,
                    BigInteger.ZERO);
        }

        if (sent.hasError()) {
            throw new IllegalStateException("RPC error sending tx: " + sent.getError().getMessage());
        }
        String txHash = sent.getTransactionHash();
        return receiptProcessor.waitForTransactionReceipt(txHash);
    }

    /** Queries the node's suggested priority fee; falls back to the Amoy default on null/failure. */
    private BigInteger resolveMaxPriorityFeePerGas() {
        try {
            EthMaxPriorityFeePerGas response = web3j.ethMaxPriorityFeePerGas().send();
            if (response != null && !response.hasError() && response.getMaxPriorityFeePerGas() != null) {
                return response.getMaxPriorityFeePerGas();
            }
        } catch (Exception ex) {
            // Some RPCs do not implement eth_maxPriorityFeePerGas; degrade quietly to the default.
            LOGGER.debug("eth_maxPriorityFeePerGas unavailable, using fallback tip: {}", ex.getMessage());
        }
        return FALLBACK_PRIORITY_FEE_WEI;
    }

    /** Reads baseFeePerGas from the latest block, or {@code null} if the chain/response is not 1559. */
    private BigInteger resolveBaseFeePerGas() {
        try {
            EthBlock block = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send();
            if (block != null && block.getBlock() != null) {
                return block.getBlock().getBaseFeePerGas();
            }
        } catch (Exception ex) {
            LOGGER.debug("Could not read latest block baseFeePerGas: {}", ex.getMessage());
        }
        return null;
    }
}
