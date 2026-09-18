package com.sidru.sidru_api.blockchain.infrastructure.web3j;

import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthMaxPriorityFeePerGas;
import org.web3j.protocol.core.methods.response.Log;
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
 * The backend wallet (BACKEND_ROLE) signs and pays gas. Custodial user addresses never
 * sign. The signing key is loaded lazily and never logged.
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

    // ---------------------------------------------------------------------
    // Retiro (spec sidru-mainnet): CTC solo existe al retirar puntos.
    // ---------------------------------------------------------------------

    private static final Event RESERVE_PAID_OUT_EVENT = new Event(
            "ReservePaidOut",
            Arrays.asList(
                    new TypeReference<Address>(true) {},
                    new TypeReference<Uint256>(true) {},
                    new TypeReference<Uint256>(false) {},
                    new TypeReference<Uint256>(false) {}));

    private static final String WITHDRAWAL_ALREADY_PROCESSED_SELECTOR =
            selector("WithdrawalAlreadyProcessed(uint256)");
    private static final String INSUFFICIENT_RESERVE_SELECTOR =
            selector("InsufficientReserve(uint256,uint256)");

    private static String selector(String errorSignature) {
        return Hash.sha3String(errorSignature).substring(0, 10); // "0x" + 8 hex chars (4 bytes)
    }

    /**
     * mintWithdrawal(address to, uint256 withdrawalId, uint256 amount). Mints CTC straight to
     * the citizen's own wallet when they withdraw app points. Idempotent per withdrawalId.
     */
    public TransactionReceipt mintWithdrawal(String to, BigInteger withdrawalId, BigInteger amountWei)
            throws Exception {
        ensureConnected();
        Function function = new Function(
                "mintWithdrawal",
                Arrays.asList(new Address(to), new Uint256(withdrawalId), new Uint256(amountWei)),
                Collections.emptyList());
        return sendTransaction(function);
    }

    /**
     * payoutReserve(address to, uint256 withdrawalId, uint256 ctcAmount). Pays a citizen in
     * reserve asset (USDC) at par instead of minting CTC. Idempotent per withdrawalId. The
     * reserve amount actually transferred is emitted in {@code ReservePaidOut}; decode it from
     * the returned receipt with {@link #decodeReserveOut(TransactionReceipt)}.
     */
    public TransactionReceipt payoutReserve(String to, BigInteger withdrawalId, BigInteger ctcAmountWei)
            throws Exception {
        ensureConnected();
        Function function = new Function(
                "payoutReserve",
                Arrays.asList(new Address(to), new Uint256(withdrawalId), new Uint256(ctcAmountWei)),
                Collections.emptyList());
        return sendTransaction(function);
    }

    /**
     * Decodes the reserve amount (6 decimals, raw units) transferred by a {@link #payoutReserve}
     * call from the {@code ReservePaidOut} event in the receipt's logs. Returns null if the
     * event is not present (should not happen for a successful payoutReserve receipt).
     */
    public BigInteger decodeReserveOut(TransactionReceipt receipt) {
        String eventSignature = EventEncoder.encode(RESERVE_PAID_OUT_EVENT);
        for (Log log : receipt.getLogs()) {
            if (log.getTopics().isEmpty() || !log.getTopics().get(0).equals(eventSignature)) {
                continue;
            }
            List<Type> nonIndexed = FunctionReturnDecoder.decode(
                    log.getData(),
                    org.web3j.abi.Utils.convert(
                            Arrays.asList(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {})));
            if (nonIndexed.size() == 2) {
                return (BigInteger) nonIndexed.get(1).getValue();
            }
        }
        return null;
    }

    /** withdrawalProcessed(uint256) view call. True once a withdrawalId was settled (mint or payout). */
    public boolean withdrawalProcessed(BigInteger withdrawalId) throws Exception {
        ensureConnected();
        Function function = new Function(
                "withdrawalProcessed",
                Collections.singletonList(new Uint256(withdrawalId)),
                Collections.singletonList(new TypeReference<Bool>() {}));
        return (Boolean) ethCallSingle(function);
    }

    /** collateralizationBps() view call. 10000 = exactly 100% backed. */
    public BigInteger collateralizationBps() throws Exception {
        ensureConnected();
        Function function = new Function(
                "collateralizationBps",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Uint256>() {}));
        return (BigInteger) ethCallSingle(function);
    }

    /** reserveBalance() view call. Reserve (e.g. USDC) currently held by the contract. */
    public BigInteger reserveBalance() throws Exception {
        ensureConnected();
        Function function = new Function(
                "reserveBalance",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Uint256>() {}));
        return (BigInteger) ethCallSingle(function);
    }

    /** requiredReserve() view call. Reserve needed to fully back the circulating CTC supply. */
    public BigInteger requiredReserve() throws Exception {
        ensureConnected();
        Function function = new Function(
                "requiredReserve",
                Collections.emptyList(),
                Collections.singletonList(new TypeReference<Uint256>() {}));
        return (BigInteger) ethCallSingle(function);
    }

    /**
     * True if {@code ex} is (or wraps) a {@link ContractRevertException} whose revert data
     * carries the {@code WithdrawalAlreadyProcessed(uint256)} selector.
     */
    public boolean isWithdrawalAlreadyProcessed(Exception ex) {
        return matchesSelector(ex, WITHDRAWAL_ALREADY_PROCESSED_SELECTOR);
    }

    /**
     * True if {@code ex} is (or wraps) a {@link ContractRevertException} whose revert data
     * carries the {@code InsufficientReserve(uint256,uint256)} selector.
     */
    public boolean isInsufficientReserve(Exception ex) {
        return matchesSelector(ex, INSUFFICIENT_RESERVE_SELECTOR);
    }

    private boolean matchesSelector(Exception ex, String selectorHex) {
        if (!(ex instanceof ContractRevertException revert)) {
            return false;
        }
        String reason = revert.getRevertReason();
        return reason != null && reason.toLowerCase().contains(selectorHex.toLowerCase());
    }

    /** Runs a view function and returns its single decoded return value. */
    private Object ethCallSingle(Function function) throws Exception {
        String encoded = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
                        Transaction.createEthCallTransaction(fromAddress, properties.getContractAddress(), encoded),
                        DefaultBlockParameterName.LATEST)
                .send();
        if (response.isReverted()) {
            throw new IllegalStateException(function.getName() + " reverted: " + response.getRevertReason());
        }
        List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        return decoded.isEmpty() ? null : decoded.get(0).getValue();
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
     * sessionRecorded(uint256) view call. Returns whether a {@code sessionId} was already
     * recorded/minted on-chain (anti double-spend). Lets the backend check idempotency BEFORE
     * a (re)mint without spending gas: if it returns true, sending {@code recordAndReward}
     * would only revert with "session already recorded" and burn POL for nothing. No gas: it
     * is an eth_call. Mirrors {@link #rewardRedeemed(BigInteger)} on the mint side.
     */
    public boolean sessionRecorded(BigInteger sessionId) throws Exception {
        ensureConnected();
        Function function = new Function(
                "sessionRecorded",
                Collections.singletonList(new Uint256(sessionId)),
                Collections.singletonList(new TypeReference<Bool>() {}));
        String encoded = FunctionEncoder.encode(function);
        EthCall response = web3j.ethCall(
                        Transaction.createEthCallTransaction(fromAddress, properties.getContractAddress(), encoded),
                        DefaultBlockParameterName.LATEST)
                .send();
        if (response.isReverted()) {
            throw new IllegalStateException("sessionRecorded reverted: " + response.getRevertReason());
        }
        List<Type> decoded = FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        return !decoded.isEmpty() && (Boolean) decoded.get(0).getValue();
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
     * Sends a state-changing call as an EIP-1559 (type-2) transaction. Amoy is EIP-1559, so legacy
     * static pricing ({@code DefaultGasProvider.GAS_PRICE}) underprices/stalls txs; we price
     * dynamically instead: {@code maxPriorityFeePerGas} from {@code eth_maxPriorityFeePerGas} (or a
     * 30 gwei Amoy fallback), {@code maxFeePerGas = baseFee*2 + tip} to absorb base-fee growth. If
     * the latest block has no {@code baseFeePerGas} (non-1559 response) we degrade to a legacy
     * {@code sendTransaction}. The {@link RawTransactionManager} is built with chainId 80002 to sign
     * the type-2 envelope. balanceOf is an eth_call and never reaches this path (no gas).
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
        TransactionReceipt receipt = receiptProcessor.waitForTransactionReceipt(txHash);
        if (!receipt.isStatusOK()) {
            throw new ContractRevertException(fetchRevertReason(function, receipt.getBlockNumber()));
        }
        return receipt;
    }

    /**
     * Re-simulates {@code function} as an eth_call at the block where it reverted, to recover
     * the revert reason (custom error selector + args, or a classic Error(string)). Best-effort:
     * returns null if the node does not support/return revert data.
     */
    private String fetchRevertReason(Function function, BigInteger blockNumber) {
        try {
            String encoded = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                            Transaction.createEthCallTransaction(
                                    fromAddress, properties.getContractAddress(), encoded),
                            new DefaultBlockParameterNumber(blockNumber))
                    .send();
            if (response.getError() != null) {
                Object data = response.getError().getData();
                if (data != null) {
                    return data.toString();
                }
            }
            return response.getRevertReason();
        } catch (Exception ex) {
            LOGGER.debug("No se pudo recuperar el revert reason: {}", ex.getMessage());
            return null;
        }
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
