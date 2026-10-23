package com.ohgiraffers.handlermethod.cost;

public class CostLimitException extends RuntimeException {
    private final AiCostControlService.CostLimitBlock block;

    public CostLimitException(AiCostControlService.CostLimitBlock block) {
        super(block.detailCode());
        this.block = block;
    }

    public AiCostControlService.CostLimitBlock block() { return block; }
}
