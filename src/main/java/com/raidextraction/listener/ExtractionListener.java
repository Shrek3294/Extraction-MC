package com.raidextraction.listener;

import com.raidextraction.extraction.ExtractionService;
import org.bukkit.event.Listener;

import java.util.Objects;

public final class ExtractionListener implements Listener {
    private final ExtractionService extractionService;

    public ExtractionListener(ExtractionService extractionService) {
        this.extractionService = Objects.requireNonNull(extractionService, "extractionService");
    }
}
