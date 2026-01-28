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

        // Foundation must be fully filled at dy=0 across footprint
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                if (!blockSet.contains(key(x, 0, z))) {
                    return "Invalid foundation gap at " + x + ",0," + z;
                }
            }
        }

        // No floating blocks: any dy>0 must have a block directly below
        for (var b : plan.blocks) {
            if (b.dy > 0) {
                if (!blockSet.contains(key(b.dx, b.dy - 1, b.dz))) {
                    return "Floating block at " + b.dx + "," + b.dy + "," + b.dz;
                }
            }
        }

        return null;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
