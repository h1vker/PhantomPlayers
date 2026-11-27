package me.sparklee.phantomplayers.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class TemplateSelector {

    private TemplateSelector() {
    }

    public static ChatTemplate select(List<ChatTemplate> templates, Random random, Set<String> alreadyUsed) {
        if (templates.isEmpty()) {
            return null;
        }
        List<ChatTemplate> pool = new ArrayList<>();
        for (ChatTemplate template : templates) {
            if (!alreadyUsed.contains(template.name())) {
                pool.add(template);
            }
        }
        // Start a fresh cycle only after every available template has been used.
        if (pool.isEmpty()) {
            alreadyUsed.clear();
            pool.addAll(templates);
        }
        int totalWeight = 0;
        for (ChatTemplate template : pool) {
            totalWeight += Math.max(1, template.weight());
        }
        int pick = random.nextInt(totalWeight);
        for (ChatTemplate template : pool) {
            pick -= Math.max(1, template.weight());
            if (pick < 0) {
                return template;
            }
        }
        return pool.get(pool.size() - 1);
    }
}
