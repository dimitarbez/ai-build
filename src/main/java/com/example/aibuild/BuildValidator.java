package com.example.aibuild;

import com.example.aibuild.exception.BuildValidationException;
import com.example.aibuild.model.BuildPlan;

import java.util.HashSet;
import java.util.Set;

/**
 * Validates building plans for safety and correctness
 */
public class BuildValidator {

    /**
     * Validate a build plan
     * @param plan The build plan to validate
     * @throws BuildValidationException if validation fails
     */
    public static void validate(BuildPlan plan) throws BuildValidationException {
        if (plan.size == null) {
            throw new BuildValidationException("Missing size");
        }
        if (plan.blocks == null) {
            throw new BuildValidationException("Missing blocks");
        }

        int sx = plan.size.x;
        int sy = plan.size.y;
        int sz = plan.size.z;

        if (sx <= 0 || sy <= 0 || sz <= 0) {
            throw new BuildValidationException("Invalid size: dimensions must be positive");
        }
        if (sx > 80 || sy > 80 || sz > 80) {
            throw new BuildValidationException("Size too large: maximum 80 blocks per dimension");
        }

        Set<String> blockSet = new HashSet<>(plan.blocks.size() * 2);

        for (var b : plan.blocks) {
            if (b == null) {
                throw new BuildValidationException("Null block entry found");
            }
            if (b.dx < 0 || b.dx >= sx) {
                throw new BuildValidationException(String.format(
                    "Block X coordinate %d out of bounds [0, %d)", b.dx, sx));
            }
            if (b.dy < 0 || b.dy >= sy) {
                throw new BuildValidationException(String.format(
                    "Block Y coordinate %d out of bounds [0, %d)", b.dy, sy));
            }
            if (b.dz < 0 || b.dz >= sz) {
                throw new BuildValidationException(String.format(
                    "Block Z coordinate %d out of bounds [0, %d)", b.dz, sz));
            }
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
            throw new BuildValidationException("No foundation: structure must have at least one block at ground level (dy=0)");
        }

        // Allow architectural features like overhangs, balconies, archways
        // Don't validate floating blocks - AI can create complex structures
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }
}
