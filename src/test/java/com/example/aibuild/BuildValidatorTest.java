package com.example.aibuild;

import com.example.aibuild.exception.BuildValidationException;
import com.example.aibuild.model.BlockSpec;
import com.example.aibuild.model.BuildPlan;
import com.example.aibuild.model.Size;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BuildValidator
 */
class BuildValidatorTest {

    @Test
    void testValidPlan() {
        BuildPlan plan = createValidPlan();
        assertDoesNotThrow(() -> BuildValidator.validate(plan));
    }

    @Test
    void testMissingSize() {
        BuildPlan plan = new BuildPlan();
        plan.size = null;
        plan.blocks = new ArrayList<>();
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("Missing size"));
    }

    @Test
    void testMissingBlocks() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(10, 10, 10);
        plan.blocks = null;
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("Missing blocks"));
    }

    @Test
    void testInvalidSize_Negative() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(-1, 10, 10);
        plan.blocks = new ArrayList<>();
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("Invalid size"));
    }

    @Test
    void testInvalidSize_Zero() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(0, 10, 10);
        plan.blocks = new ArrayList<>();
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("Invalid size"));
    }

    @Test
    void testSizeTooLarge() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(81, 10, 10);
        plan.blocks = new ArrayList<>();
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("Size too large"));
    }

    @Test
    void testSizeAtLimit() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(80, 80, 80);
        plan.blocks = new ArrayList<>();
        plan.blocks.add(new BlockSpec(0, 0, 0, "STONE"));
        
        assertDoesNotThrow(() -> BuildValidator.validate(plan));
    }

    @Test
    void testBlockOutOfXBounds() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(10, 10, 10);
        plan.blocks = new ArrayList<>();
        plan.blocks.add(new BlockSpec(10, 0, 0, "STONE")); // x=10 but max is 9
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("X coordinate"));
        assertTrue(ex.getMessage().contains("out of bounds"));
    }

    @Test
    void testBlockOutOfYBounds() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(10, 10, 10);
        plan.blocks = new ArrayList<>();
        plan.blocks.add(new BlockSpec(0, -1, 0, "STONE"));
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("Y coordinate"));
        assertTrue(ex.getMessage().contains("out of bounds"));
    }

    @Test
    void testBlockOutOfZBounds() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(10, 10, 10);
        plan.blocks = new ArrayList<>();
        plan.blocks.add(new BlockSpec(0, 0, 15, "STONE"));
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("Z coordinate"));
        assertTrue(ex.getMessage().contains("out of bounds"));
    }

    @Test
    void testNullBlockEntry() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(10, 10, 10);
        plan.blocks = new ArrayList<>();
        plan.blocks.add(null);
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("Null block entry"));
    }

    @Test
    void testNoFoundation() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(10, 10, 10);
        plan.blocks = new ArrayList<>();
        // All blocks above ground
        plan.blocks.add(new BlockSpec(0, 1, 0, "STONE"));
        plan.blocks.add(new BlockSpec(1, 2, 1, "STONE"));
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("No foundation"));
    }

    @Test
    void testWithFoundation() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(10, 10, 10);
        plan.blocks = new ArrayList<>();
        // At least one block at ground level
        plan.blocks.add(new BlockSpec(0, 0, 0, "STONE"));
        plan.blocks.add(new BlockSpec(0, 1, 0, "STONE"));
        
        assertDoesNotThrow(() -> BuildValidator.validate(plan));
    }

    @Test
    void testDuplicateBlocks_Allowed() {
        // Duplicate blocks at same position should be allowed
        // (validator doesn't check for duplicates currently)
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(10, 10, 10);
        plan.blocks = new ArrayList<>();
        plan.blocks.add(new BlockSpec(0, 0, 0, "STONE"));
        plan.blocks.add(new BlockSpec(0, 0, 0, "STONE")); // duplicate
        
        assertDoesNotThrow(() -> BuildValidator.validate(plan));
    }

    @Test
    void testFloatingBlocks_Allowed() {
        // Floating blocks should be allowed for architectural features
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(10, 10, 10);
        plan.blocks = new ArrayList<>();
        plan.blocks.add(new BlockSpec(0, 0, 0, "STONE")); // foundation
        plan.blocks.add(new BlockSpec(5, 5, 5, "STONE")); // floating
        
        assertDoesNotThrow(() -> BuildValidator.validate(plan));
    }

    @Test
    void testEmptyBlocksList_WithFoundation() {
        // Empty blocks list should fail (no foundation)
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(10, 10, 10);
        plan.blocks = new ArrayList<>();
        
        BuildValidationException ex = assertThrows(
            BuildValidationException.class,
            () -> BuildValidator.validate(plan)
        );
        assertTrue(ex.getMessage().contains("No foundation"));
    }

    @Test
    void testLargeValidPlan() {
        BuildPlan plan = new BuildPlan();
        plan.size = new Size(50, 50, 50);
        plan.blocks = new ArrayList<>();
        
        // Add foundation layer
        for (int x = 0; x < 50; x++) {
            for (int z = 0; z < 50; z++) {
                plan.blocks.add(new BlockSpec(x, 0, z, "STONE"));
            }
        }
        
        assertDoesNotThrow(() -> BuildValidator.validate(plan));
    }

    // Helper method
    private BuildPlan createValidPlan() {
        BuildPlan plan = new BuildPlan();
        plan.name = "Test Structure";
        plan.size = new Size(5, 5, 5);
        plan.blocks = new ArrayList<>();
        
        // Simple 3x3 foundation
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                plan.blocks.add(new BlockSpec(x, 0, z, "STONE"));
            }
        }
        
        // Add some walls
        for (int y = 1; y < 4; y++) {
            plan.blocks.add(new BlockSpec(0, y, 0, "STONE"));
            plan.blocks.add(new BlockSpec(2, y, 0, "STONE"));
            plan.blocks.add(new BlockSpec(0, y, 2, "STONE"));
            plan.blocks.add(new BlockSpec(2, y, 2, "STONE"));
        }
        
        return plan;
    }
}
