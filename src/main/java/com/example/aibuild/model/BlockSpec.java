package com.example.aibuild.model;

/**
 * Represents a single block in a building plan with relative coordinates and material
 */
public class BlockSpec {
    public int dx, dy, dz;
    public String material;
    
    public BlockSpec() {}
    
    public BlockSpec(int dx, int dy, int dz, String material) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.material = material;
    }
}
