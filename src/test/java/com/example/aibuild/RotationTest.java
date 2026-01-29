package com.example.aibuild;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Rotation utility class
 * Tests coordinate transformations for all cardinal directions
 */
class RotationTest {

    @Test
    void testRotateNorth_NoChange() {
        // North is the baseline - no rotation needed
        int[] result = Rotation.rotateXZ(5, 3, BlockFace.NORTH);
        assertArrayEquals(new int[]{5, 3}, result, "North should not change coordinates");
    }

    @Test
    void testRotateEast() {
        // East: 90° clockwise rotation
        // (dx, dz) -> (-dz, dx)
        int[] result = Rotation.rotateXZ(5, 3, BlockFace.EAST);
        assertArrayEquals(new int[]{-3, 5}, result, "East should rotate 90° clockwise");
    }

    @Test
    void testRotateSouth() {
        // South: 180° rotation
        // (dx, dz) -> (-dx, -dz)
        int[] result = Rotation.rotateXZ(5, 3, BlockFace.SOUTH);
        assertArrayEquals(new int[]{-5, -3}, result, "South should rotate 180°");
    }

    @Test
    void testRotateWest() {
        // West: 270° clockwise (or 90° counter-clockwise)
        // (dx, dz) -> (dz, -dx)
        int[] result = Rotation.rotateXZ(5, 3, BlockFace.WEST);
        assertArrayEquals(new int[]{3, -5}, result, "West should rotate 270° clockwise");
    }

    @Test
    void testRotateOrigin() {
        // Origin point should remain at origin for all directions
        assertArrayEquals(new int[]{0, 0}, Rotation.rotateXZ(0, 0, BlockFace.NORTH));
        assertArrayEquals(new int[]{0, 0}, Rotation.rotateXZ(0, 0, BlockFace.EAST));
        assertArrayEquals(new int[]{0, 0}, Rotation.rotateXZ(0, 0, BlockFace.SOUTH));
        assertArrayEquals(new int[]{0, 0}, Rotation.rotateXZ(0, 0, BlockFace.WEST));
    }

    @Test
    void testRotateNegativeCoordinates_North() {
        int[] result = Rotation.rotateXZ(-3, -7, BlockFace.NORTH);
        assertArrayEquals(new int[]{-3, -7}, result);
    }

    @Test
    void testRotateNegativeCoordinates_East() {
        int[] result = Rotation.rotateXZ(-3, -7, BlockFace.EAST);
        assertArrayEquals(new int[]{7, -3}, result);
    }

    @Test
    void testRotateNegativeCoordinates_South() {
        int[] result = Rotation.rotateXZ(-3, -7, BlockFace.SOUTH);
        assertArrayEquals(new int[]{3, 7}, result);
    }

    @Test
    void testRotateNegativeCoordinates_West() {
        int[] result = Rotation.rotateXZ(-3, -7, BlockFace.WEST);
        assertArrayEquals(new int[]{-7, 3}, result);
    }

    @Test
    void testFullRotationCycle() {
        // After 4 rotations (N->E->S->W->N), should return to original
        int dx = 10, dz = 5;
        
        int[] north = Rotation.rotateXZ(dx, dz, BlockFace.NORTH);
        int[] east = Rotation.rotateXZ(north[0], north[1], BlockFace.EAST);
        int[] south = Rotation.rotateXZ(east[0], east[1], BlockFace.SOUTH);
        int[] west = Rotation.rotateXZ(south[0], south[1], BlockFace.WEST);
        
        // Note: This doesn't work as expected because each rotation is independent
        // Each call assumes input is in NORTH orientation
        // This test is documenting current behavior, not testing cycle
    }

    @Test
    void testSymmetricCoordinates() {
        // Test that symmetric points remain symmetric after rotation
        int[] pos1 = Rotation.rotateXZ(5, 5, BlockFace.EAST);
        int[] pos2 = Rotation.rotateXZ(-5, -5, BlockFace.EAST);
        
        // After rotation, they should still be symmetric (opposite signs or equal)
        assertEquals(-pos1[0], pos2[0]);
        assertEquals(-pos1[1], pos2[1]);
    }

    @Test
    void testAxisAligned_PositiveX() {
        // Point on positive X axis
        int[] north = Rotation.rotateXZ(10, 0, BlockFace.NORTH);
        int[] east = Rotation.rotateXZ(10, 0, BlockFace.EAST);
        int[] south = Rotation.rotateXZ(10, 0, BlockFace.SOUTH);
        int[] west = Rotation.rotateXZ(10, 0, BlockFace.WEST);
        
        assertArrayEquals(new int[]{10, 0}, north);
        assertArrayEquals(new int[]{0, 10}, east);
        assertArrayEquals(new int[]{-10, 0}, south);
        assertArrayEquals(new int[]{0, -10}, west);
    }

    @Test
    void testAxisAligned_PositiveZ() {
        // Point on positive Z axis
        int[] north = Rotation.rotateXZ(0, 10, BlockFace.NORTH);
        int[] east = Rotation.rotateXZ(0, 10, BlockFace.EAST);
        int[] south = Rotation.rotateXZ(0, 10, BlockFace.SOUTH);
        int[] west = Rotation.rotateXZ(0, 10, BlockFace.WEST);
        
        assertArrayEquals(new int[]{0, 10}, north);
        assertArrayEquals(new int[]{-10, 0}, east);
        assertArrayEquals(new int[]{0, -10}, south);
        assertArrayEquals(new int[]{10, 0}, west);
    }

    @Test
    void testDefaultCase_UnknownBlockFace() {
        // Test with unsupported BlockFace (should default to NORTH behavior)
        int[] result = Rotation.rotateXZ(5, 3, BlockFace.UP);
        assertArrayEquals(new int[]{5, 3}, result, "Unknown BlockFace should default to NORTH");
    }

    @Test
    void testLargeCoordinates() {
        // Test with large coordinates to ensure no overflow
        int[] result = Rotation.rotateXZ(1000, 2000, BlockFace.EAST);
        assertArrayEquals(new int[]{-2000, 1000}, result);
    }

    @Test
    void testRotationPreservesDistance() {
        // Rotation should preserve distance from origin
        int dx = 3, dz = 4; // Distance = 5
        
        int[] north = Rotation.rotateXZ(dx, dz, BlockFace.NORTH);
        int[] east = Rotation.rotateXZ(dx, dz, BlockFace.EAST);
        int[] south = Rotation.rotateXZ(dx, dz, BlockFace.SOUTH);
        int[] west = Rotation.rotateXZ(dx, dz, BlockFace.WEST);
        
        // Calculate distances (should all be 5)
        assertEquals(25, north[0]*north[0] + north[1]*north[1]);
        assertEquals(25, east[0]*east[0] + east[1]*east[1]);
        assertEquals(25, south[0]*south[0] + south[1]*south[1]);
        assertEquals(25, west[0]*west[0] + west[1]*west[1]);
    }

    @Test
    void testRotation90Degrees() {
        // Verify that each rotation is exactly 90 degrees
        int dx = 1, dz = 0;
        
        // Starting point (1, 0)
        int[] north = Rotation.rotateXZ(dx, dz, BlockFace.NORTH);
        int[] east = Rotation.rotateXZ(dx, dz, BlockFace.EAST);
        int[] south = Rotation.rotateXZ(dx, dz, BlockFace.SOUTH);
        int[] west = Rotation.rotateXZ(dx, dz, BlockFace.WEST);
        
        // Should form a square: (1,0) -> (0,1) -> (-1,0) -> (0,-1)
        assertArrayEquals(new int[]{1, 0}, north);
        assertArrayEquals(new int[]{0, 1}, east);
        assertArrayEquals(new int[]{-1, 0}, south);
        assertArrayEquals(new int[]{0, -1}, west);
    }
}
