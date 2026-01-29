package com.example.aibuild.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Minecraft building plan with size and block specifications
 */
public class BuildPlan {
    public String name;
    public Size size;
    public List<BlockSpec> blocks;
    
    // Compact format parsing
    public List<Double> s;  // [x, y, z]
    public List<List<Number>> b;  // [[x,y,z,m], ...]
    
    /**
     * Convert compact format to expanded format
     * Compact format: s=[x,y,z], b=[[x,y,z,m],...]
     * Expanded format: size={x,y,z}, blocks=[{dx,dy,dz,material},...]
     */
    public void expandCompact(String[] materialNames) {
        if (s != null && b != null) {
            size = new Size();
            size.x = s.get(0).intValue();
            size.y = s.get(1).intValue();
            size.z = s.get(2).intValue();
            
            blocks = new ArrayList<>(b.size());
            for (List<Number> block : b) {
                BlockSpec spec = new BlockSpec();
                spec.dx = block.get(0).intValue();
                spec.dy = block.get(1).intValue();
                spec.dz = block.get(2).intValue();
                int matId = block.get(3).intValue();
                spec.material = (matId >= 0 && matId < materialNames.length) 
                    ? materialNames[matId] : materialNames[0];
                blocks.add(spec);
            }
        }
    }
}
