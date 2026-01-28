package com.example.aibuild;

import org.bukkit.block.BlockFace;

public class Rotation {
    // treat plan as NORTH-oriented; rotate to player facing
    public static int[] rotateXZ(int dx, int dz, BlockFace facing) {
        return switch (facing) {
            case NORTH -> new int[]{dx, dz};
            case EAST  -> new int[]{-dz, dx};
            case SOUTH -> new int[]{-dx, -dz};
            case WEST  -> new int[]{dz, -dx};
            default    -> new int[]{dx, dz};
        };
    }
}
