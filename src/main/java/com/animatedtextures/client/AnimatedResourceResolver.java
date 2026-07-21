package com.animatedtextures.client;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AnimatedResourceResolver {

    enum Format {
        GIF(".gif"),
        APNG(".png3");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        String extension() {
            return extension;
        }
    }

    record SelectedResource(Identifier sourceId, Identifier fallbackId, Format format, Resource resource) {
    }

    private AnimatedResourceResolver() {
    }

    static List<SelectedResource> resolve(ResourceManager manager) {
        Map<String, Integer> priorities = resourcePriorities(manager);
        List<SelectedResource> selections = new ArrayList<>();
        for (Identifier pngId : manager.findResources("textures", id -> id.getPath().endsWith(".png")).keySet()) {
            Identifier baseId = Identifier.of(pngId.getNamespace(), pngId.getPath().substring(0, pngId.getPath().length() - 4));
            List<Candidate> candidates = new ArrayList<>();
            addCandidates(manager, priorities, baseId, pngId, Format.GIF, candidates);
            addCandidates(manager, priorities, baseId, pngId, Format.APNG, candidates);
            choose(candidates).ifPresent(selection -> {
                if (candidates.size() > 1) {
                    AnimatedTexturesClient.LOGGER.info(
                            "[AnimatedTextures] repair category=resource action=selected target={} format={} pack={} candidates={}",
                            selection.fallbackId(), selection.format(), selection.resource().getPackId(), candidates.size());
                }
                selections.add(selection);
            });
        }
        selections.sort(Comparator.comparing(selection -> selection.sourceId().toString()));
        return List.copyOf(selections);
    }

    private static void addCandidates(ResourceManager manager, Map<String, Integer> priorities,
                                      Identifier baseId, Identifier fallbackId, Format format,
                                      List<Candidate> candidates) {
        Identifier sourceId = Identifier.of(baseId.getNamespace(), baseId.getPath() + format.extension());
        for (Resource resource : manager.getAllResources(sourceId)) {
            candidates.add(new Candidate(sourceId, fallbackId, format, resource,
                    priorities.getOrDefault(resource.getPackId(), -1)));
        }
    }

    private static java.util.Optional<SelectedResource> choose(List<Candidate> candidates) {
        return candidates.stream()
                .max(Comparator.comparingInt(Candidate::priority)
                        .thenComparing(candidate -> candidate.format() == Format.APNG))
                .map(candidate -> new SelectedResource(candidate.sourceId(), candidate.fallbackId(),
                        candidate.format(), candidate.resource()));
    }

    private static Map<String, Integer> resourcePriorities(ResourceManager manager) {
        Map<String, Integer> priorities = new HashMap<>();
        int index = 0;
        for (ResourcePack pack : (Iterable<ResourcePack>) manager.streamResourcePacks()::iterator) {
            priorities.put(pack.getId(), index++);
        }
        return priorities;
    }

    private record Candidate(Identifier sourceId, Identifier fallbackId, Format format,
                             Resource resource, int priority) {
    }
}
