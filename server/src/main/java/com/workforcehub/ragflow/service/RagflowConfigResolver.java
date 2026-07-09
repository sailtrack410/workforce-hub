package com.workforcehub.ragflow.service;

import com.workforcehub.ragflow.repository.RagflowGlobalSettingRow;
import com.workforcehub.ragflow.repository.RagflowSpaceRow;
import org.springframework.stereotype.Component;

@Component
public class RagflowConfigResolver {

    public RagflowEffectiveConfig resolve(RagflowSpaceRow space, RagflowGlobalSettingRow setting) {
        return new RagflowEffectiveConfig(
            space.embeddingModelId() == null ? setting.embeddingModelId() : space.embeddingModelId(),
            space.embeddingDimension() == null ? setting.embeddingDimension() : space.embeddingDimension(),
            space.chunkSize() == null ? setting.chunkSize() : space.chunkSize(),
            space.chunkOverlap() == null ? setting.chunkOverlap() : space.chunkOverlap(),
            space.retrievalTopK() == null ? setting.retrievalTopK() : space.retrievalTopK(),
            space.scoreThreshold() == null ? setting.scoreThreshold() : space.scoreThreshold()
        );
    }
}
