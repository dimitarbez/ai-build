package com.example.aibuild;

import java.util.HashSet;
import java.util.Set;

public class BuildValidator {

    public static String validate(AIBuildCommand.BuildPlan plan) {
        if (plan.size == null) return "Missing size";
        if (plan.blocks == null) return "Missing blocks";

        int sx = plan.size.x;
        int sy = plan.size.y;
        int sz = plan.size.z;

        if (sx <= 0 || sy <= 0 || sz <= 0) return "Invalid size";
        if (sx > 80 || sy > 80 || sz > 80) return "Size too large (cap 80)";

        Set<String> blockSet = new HashSet<>(plan.blocks.size() * 2);

        for (var b : plan.blocks) {
            if (b == null) return "Null block entry";
            if (b.dx < 0 || b.dx >= sx) return "Block out of X bounds";
            if (b.dy < 0 || b.dy >= sy) return "Block out of Y bounds";
            if (b.dz < 0 || b.dz >= sz) return "Block out of Z bounds";
            blockSet.add(key(b.dx, b.dy, b.dz));
        }

        // Check: at least one foundation block must exist
        boolean hasFoundation = false;
        for (var b : plan.blocks) {
            if (b.dy == 0) {
                hasFoundation = true;
                break;
            }
        }
        if (!hasFoundation) {
            return "No foundation blocks (at least one block at dy=0 required)";
        }

        // Allow architectural features like overhangs, balconies, archways
        // Don't validate floating blocks - AI can create complex structures

        return null;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
