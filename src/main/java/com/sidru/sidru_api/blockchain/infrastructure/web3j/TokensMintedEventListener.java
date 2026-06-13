package com.sidru.sidru_api.blockchain.infrastructure.web3j;

import com.sidru.sidru_api.blockchain.application.internal.eventhandlers.TokensMintedHandler;
import com.sidru.sidru_api.blockchain.infrastructure.web3j.config.BlockchainProperties;
import io.reactivex.disposables.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;

import jakarta.annotation.PreDestroy;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Subscribes to the on-chain {@code TokensMinted} event and forwards each occurrence to
 * {@link TokensMintedHandler} (US-BC-07 / US-39, RNF-BC-06).
 *
 * <p>Event signature: {@code TokensMinted(address indexed user, uint256 amount, uint256 indexed sessionId)}.
 * <ul>
 *   <li>topics[0] = keccak256 of the event signature (filter topic0)</li>
 *   <li>topics[1] = user (indexed address)</li>
 *   <li>topics[2] = sessionId (indexed uint256)</li>
 *   <li>data      = amount (non-indexed uint256)</li>
 * </ul>
 *
 * <p>Resilience: subscription starts at {@code LATEST} (no historical replay) on app start.
 * It runs on its own scheduler thread, never blocking startup. On RxJava {@code onError} or a
 * failure to connect, it re-subscribes with a bounded exponential backoff. With
 * {@code BLOCKCHAIN_ENABLED=false} it does not subscribe nor touch the network. No secrets logged.
 */
@Component
public class TokensMintedEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TokensMintedEventListener.class);

    /** TokensMinted(address indexed user, uint256 amount, uint256 indexed sessionId). */
    private static final Event TOKENS_MINTED_EVENT = new Event(
            "TokensMinted",
            Arrays.asList(
                    new TypeReference<Address>(true) {},      // user (indexed)
                    new TypeReference<Uint256>(false) {},     // amount (data)
                    new TypeReference<Uint256>(true) {}       // sessionId (indexed)
            ));

    private static final long BACKOFF_BASE_SECONDS = 5L;
    private static final long BACKOFF_MAX_SECONDS = 60L;

    private final ChapaTuCriptoContract contract;
    private final BlockchainProperties properties;
    private final TokensMintedHandler handler;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile ScheduledExecutorService scheduler;
    private volatile Disposable subscription;
    private int attempt = 0;

    public TokensMintedEventListener(ChapaTuCriptoContract contract,
                                     BlockchainProperties properties,
                                     TokensMintedHandler handler) {
        this.contract = contract;
        this.properties = properties;
        this.handler = handler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isEnabled()) {
            LOGGER.info("Blockchain disabled — TokensMinted listener not started");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tokens-minted-listener");
            t.setDaemon(true);
            return t;
        });
        LOGGER.info("Starting TokensMinted event listener on network {} (topic0={})",
                properties.getNodeUrl(), EventEncoder.encode(TOKENS_MINTED_EVENT));
        // Subscribe off the main thread so a down RPC never blocks startup.
        scheduler.execute(this::subscribe);
    }

    private void subscribe() {
        if (!running.get()) {
            return;
        }
        try {
            EthFilter filter = new EthFilter(
                    DefaultBlockParameterName.LATEST,
                    DefaultBlockParameterName.LATEST,
                    contract.contractAddress());
            filter.addSingleTopic(EventEncoder.encode(TOKENS_MINTED_EVENT));

            subscription = contract.web3jClient().ethLogFlowable(filter).subscribe(
                    this::handleLog,
                    this::onStreamError);

            attempt = 0; // connected successfully -> reset backoff
            LOGGER.info("TokensMinted listener subscribed (from LATEST)");
        } catch (Exception ex) {
            LOGGER.warn("Failed to subscribe TokensMinted listener: {}", ex.getMessage());
            scheduleResubscribe();
        }
    }

    private void handleLog(Log log) {
        // Per-event try/catch: a single bad log must not kill the subscription.
        try {
            List<String> topics = log.getTopics();
            if (topics == null || topics.size() < 3) {
                LOGGER.warn("TokensMinted log with unexpected topics size — skipped");
                return;
            }

            // Indexed params come from topics; non-indexed from data.
            String user = decodeIndexedAddress(topics.get(1));
            BigInteger sessionId = decodeIndexedUint(topics.get(2));
            BigInteger amount = decodeAmountFromData(log.getData());

            LOGGER.info("TokensMinted received: sessionId={} amount(wei)={}", sessionId, amount);
            handler.onTokensMinted(sessionId.longValueExact(), user, amount);
        } catch (Exception ex) {
            LOGGER.error("Error processing TokensMinted log: {}", ex.getMessage(), ex);
        }
    }

    private void onStreamError(Throwable error) {
        if (!running.get()) {
            return;
        }
        LOGGER.warn("TokensMinted stream error: {} — scheduling re-subscription", error.getMessage());
        disposeSubscription();
        scheduleResubscribe();
    }

    private void scheduleResubscribe() {
        if (!running.get() || scheduler == null) {
            return;
        }
        attempt++;
        long delay = Math.min(BACKOFF_BASE_SECONDS * (1L << Math.min(attempt - 1, 4)), BACKOFF_MAX_SECONDS);
        LOGGER.info("Re-subscribing TokensMinted listener in {}s (attempt {})", delay, attempt);
        scheduler.schedule(this::subscribe, delay, TimeUnit.SECONDS);
    }

    private String decodeIndexedAddress(String topic) {
        Type<?> value = FunctionReturnDecoder.decodeIndexedValue(topic, new TypeReference<Address>() {});
        return value.getValue().toString();
    }

    private BigInteger decodeIndexedUint(String topic) {
        Type<?> value = FunctionReturnDecoder.decodeIndexedValue(topic, new TypeReference<Uint256>() {});
        return (BigInteger) value.getValue();
    }

    private BigInteger decodeAmountFromData(String data) {
        List<Type> decoded = FunctionReturnDecoder.decode(data, TOKENS_MINTED_EVENT.getNonIndexedParameters());
        if (decoded.isEmpty()) {
            return BigInteger.ZERO;
        }
        return (BigInteger) decoded.get(0).getValue();
    }

    private void disposeSubscription() {
        Disposable current = subscription;
        if (current != null && !current.isDisposed()) {
            current.dispose();
        }
        subscription = null;
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        disposeSubscription();
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
