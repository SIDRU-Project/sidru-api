package com.sidru.sidru_api.rewards.domain.model.aggregates;

import com.sidru.sidru_api.shared.domain.model.aggregates.AuditableAbstractAggregateRoot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rewards")
public class Reward extends AuditableAbstractAggregateRoot<Reward> {

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false)
    private String name;

    @Size(max = 500)
    private String description;

    @Min(1)
    @Column(nullable = false)
    private int pointsCost;

    @PositiveOrZero
    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean active;

    @Size(max = 300)
    private String imageUrl;

    public Reward() {
        this.active = true;
    }

    public Reward(String name, String description, int pointsCost, int stock, String imageUrl) {
        this();
        this.name = name;
        this.description = description;
        this.pointsCost = pointsCost;
        this.stock = stock;
        this.imageUrl = imageUrl;
    }

    public boolean hasStock() {
        return stock > 0;
    }

    public void decrementStock() {
        if (stock <= 0) throw new IllegalStateException("Reward out of stock");
        stock--;
    }
}
