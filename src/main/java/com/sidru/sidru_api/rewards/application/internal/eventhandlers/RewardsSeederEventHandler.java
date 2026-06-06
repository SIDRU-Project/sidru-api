package com.sidru.sidru_api.rewards.application.internal.eventhandlers;

import com.sidru.sidru_api.rewards.domain.model.aggregates.Reward;
import com.sidru.sidru_api.rewards.infrastructure.persistence.jpa.repositories.RewardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RewardsSeederEventHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RewardsSeederEventHandler.class);

    private final RewardRepository rewardRepository;

    public RewardsSeederEventHandler(RewardRepository rewardRepository) {
        this.rewardRepository = rewardRepository;
    }

    @EventListener
    @Order(30)
    public void on(ApplicationReadyEvent event) {
        var catalog = rewardsCatalog();
        deactivateRewardsOutsideCatalog(catalog);
        catalog.forEach(this::upsertReward);
        LOGGER.info("SIDRU rewards catalog synchronized ({} active rewards)", catalog.size());
    }

    private List<RewardSeed> rewardsCatalog() {
        return List.of(
                new RewardSeed(
                        "Bolsa reutilizable simple",
                        "Bolsa reutilizable para compras diarias.",
                        500, 100),
                new RewardSeed(
                        "Descuento ecologico pequeno",
                        "Cupon de descuento de S/ 5 en comercio aliado.",
                        500, 80),
                new RewardSeed(
                        "Tomatodo basico reutilizable",
                        "Tomatodo basico para uso diario.",
                        1200, 50),
                new RewardSeed(
                        "Cupon ecologico mediano",
                        "Cupon de descuento de S/ 15 en productos sostenibles.",
                        1500, 40),
                new RewardSeed(
                        "Botella reutilizable basica",
                        "Botella reutilizable basica para agua.",
                        2000, 30),
                new RewardSeed(
                        "Kit reciclador SIDRU",
                        "Kit con bolsa reutilizable, sticker SIDRU y guia de reciclaje.",
                        2500, 25),
                new RewardSeed(
                        "Botella reutilizable premium",
                        "Botella reutilizable de acero inoxidable de 500 ml.",
                        4500, 15),
                new RewardSeed(
                        "Botella termica ecologica",
                        "Botella termica reutilizable de mayor durabilidad.",
                        6000, 10)
        );
    }

    private void deactivateRewardsOutsideCatalog(List<RewardSeed> catalog) {
        Set<String> catalogNames = catalog.stream()
                .map(RewardSeed::name)
                .collect(Collectors.toSet());

        rewardRepository.findAll().stream()
                .filter(reward -> !catalogNames.contains(reward.getName()))
                .filter(Reward::isActive)
                .forEach(reward -> {
                    reward.setActive(false);
                    rewardRepository.save(reward);
                });
    }

    private void upsertReward(RewardSeed seed) {
        var reward = rewardRepository.findByName(seed.name())
                .orElseGet(Reward::new);

        reward.setName(seed.name());
        reward.setDescription(seed.description());
        reward.setPointsCost(seed.pointsCost());
        reward.setStock(seed.stock());
        reward.setActive(true);
        reward.setImageUrl(null);

        rewardRepository.save(reward);
    }

    private record RewardSeed(String name, String description, int pointsCost, int stock) {
    }
}
