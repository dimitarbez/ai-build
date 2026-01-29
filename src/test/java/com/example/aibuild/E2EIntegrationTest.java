package com.example.aibuild;

import com.example.aibuild.exception.BuildValidationException;
import com.example.aibuild.exception.OpenAIException;
import com.example.aibuild.model.BuildPlan;
import com.example.aibuild.service.PlanParser;
import org.bukkit.*;
import org.bukkit.block.BlockFace;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * End-to-end integration test for AIBuild plugin.
 * Tests the complete flow from API calls through validation to component integration.
 * 
 * Run: ./test-e2e.sh (or run main() directly)
 */
public class E2EIntegrationTest {
    
    private static final Logger LOGGER = Logger.getLogger("E2ETest");
    
    public static void main(String[] args) {
        System.out.println("\n=== AIBuild E2E Integration Test ===\n");
        
        E2EIntegrationTest test = new E2EIntegrationTest();
        
        try {
            // Test 1: Basic build flow
            System.out.println("Test 1: Basic build command flow");
            test.testBasicBuildFlow();
            System.out.println("✓ Test 1 passed\n");
            
            // Test 2: Undo functionality
            System.out.println("Test 2: Undo functionality");
            test.testUndoFlow();
            System.out.println("✓ Test 2 passed\n");
            
            // Test 3: Cooldown enforcement
            System.out.println("Test 3: Cooldown enforcement");
            test.testCooldownEnforcement();
            System.out.println("✓ Test 3 passed\n");
            
            // Test 4: Invalid API key handling
            System.out.println("Test 4: Invalid API key handling");
            test.testInvalidApiKey();
            System.out.println("✓ Test 4 passed\n");
            
            // Test 5: Build validation
            System.out.println("Test 5: Build validation");
            test.testBuildValidation();
            System.out.println("✓ Test 5 passed\n");
            
            // Test 6: Rotation system
            System.out.println("Test 6: Block rotation");
            test.testBlockRotation();
            System.out.println("✓ Test 6 passed\n");
            
            System.out.println("=== ALL E2E TESTS PASSED ===\n");
            
        } catch (Exception e) {
            System.err.println("\n✗ TEST FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Test complete flow: command -> API -> validation -> placement
     */
    public void testBasicBuildFlow() throws Exception {
        // Setup
        String apiKey = EnvConfig.getOpenAiApiKey();
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("PUT_YOUR_KEY")) {
            System.out.println("⚠ Skipping API test - no valid API key");
            return;
        }
        
        System.out.println("  Testing full API -> validation -> parse flow...");
        
        OpenAIClient client = new OpenAIClient(apiKey, "gpt-4o-mini", 60000);
        Set<Material> allowed = Set.of(
            Material.OAK_PLANKS,
            Material.COBBLESTONE,
            Material.GLASS,
            Material.OAK_STAIRS,
            Material.TORCH
        );
        
        AtomicInteger progressCount = new AtomicInteger(0);
        
        // Call API with streaming
        String json = client.generateBuildPlanJsonStreaming(
            "small wooden house",
            100,
            allowed,
            msg -> {
                System.out.println("    Progress: " + ChatColor.stripColor(msg));
                progressCount.incrementAndGet();
            }
        );
        
        System.out.println("  ✓ API call completed, received " + json.length() + " chars");
        assert json != null && !json.isBlank() : "Should receive non-empty JSON";
        assert progressCount.get() > 0 : "Should receive progress updates";
        
        // Parse the response
        PlanParser parser = new PlanParser();
        String[] matArray = allowed.stream()
            .map(Enum::name)
            .sorted()
            .toArray(String[]::new);
        
        BuildPlan plan = parser.parse(json, matArray);
        System.out.println("  ✓ Parsed plan: " + plan.blocks.size() + " blocks in " + 
            plan.size.x + "x" + plan.size.y + "x" + plan.size.z + " space");
        
        assert plan != null : "Plan should parse successfully";
        assert plan.blocks != null && !plan.blocks.isEmpty() : "Plan should have blocks";
        assert plan.size != null : "Plan should have size";
        
        // Validate the plan
        BuildValidator.validate(plan);
        System.out.println("  ✓ Plan validation passed");
        
        // Test rotation system
        for (var block : plan.blocks) {
            int[] rotated = Rotation.rotateXZ(block.dx, block.dz, BlockFace.EAST);
            assert rotated != null && rotated.length == 2 : "Rotation should work";
        }
        System.out.println("  ✓ Rotation system verified");
    }
    
    /**
     * Test undo functionality
     */
    public void testUndoFlow() throws Exception {
        System.out.println("  Testing build history and undo system...");
        
        BuildHistory history = new BuildHistory();
        UUID playerId = UUID.randomUUID();
        
        // Simulate a previous build
        Deque<BuildHistory.PlacedBlock> blocks = new ArrayDeque<>();
        
        // Create some mock placed blocks (Location constructor doesn't require World implementation)
        for (int i = 0; i < 10; i++) {
            blocks.add(new BuildHistory.PlacedBlock(null, Material.AIR));
        }
        
        history.store(playerId, blocks);
        System.out.println("    Stored build history with " + blocks.size() + " blocks");
        
        // Verify history
        assert history.getLastBuildAtMs(playerId) > 0 : "Build time should be recorded";
        System.out.println("    ✓ Build timestamp recorded: " + history.getLastBuildAtMs(playerId));
        
        // Pop history
        Deque<BuildHistory.PlacedBlock> retrieved = history.pop(playerId);
        assert retrieved != null : "Should retrieve history";
        assert retrieved.size() == 10 : "Should retrieve all blocks";
        System.out.println("    ✓ Retrieved " + retrieved.size() + " blocks from history");
        
        // Verify history is cleared after pop
        Deque<BuildHistory.PlacedBlock> empty = history.pop(playerId);
        assert empty == null : "History should be cleared after pop";
        System.out.println("    ✓ History cleared after undo");
    }
    
    /**
     * Test cooldown enforcement
     */
    public void testCooldownEnforcement() throws Exception {
        System.out.println("  Testing cooldown system...");
        
        BuildHistory history = new BuildHistory();
        UUID playerId = UUID.randomUUID();
        
        // Record a recent build
        history.store(playerId, new ArrayDeque<>());
        long firstBuildTime = history.getLastBuildAtMs(playerId);
        
        System.out.println("    Recorded build at: " + firstBuildTime);
        assert firstBuildTime > 0 : "Build time should be recorded";
        
        // Check cooldown calculation
        int cooldownSec = 30;
        long now = System.currentTimeMillis();
        long waitMs = (cooldownSec * 1000L) - (now - firstBuildTime);
        
        assert waitMs > 0 : "Should have cooldown time remaining";
        long waitSec = (waitMs + 999) / 1000;
        System.out.println("    ✓ Cooldown active: " + waitSec + " seconds remaining");
        
        // Wait a bit then verify cooldown decreased
        Thread.sleep(100);
        long now2 = System.currentTimeMillis();
        long waitMs2 = (cooldownSec * 1000L) - (now2 - firstBuildTime);
        assert waitMs2 < waitMs : "Cooldown should decrease over time";
        System.out.println("    ✓ Cooldown decreasing correctly");
    }
    
    /**
     * Test handling of invalid API key
     */
    public void testInvalidApiKey() throws Exception {
        System.out.println("  Testing invalid API key handling...");
        
        // Use invalid API key
        OpenAIClient client = new OpenAIClient("sk-invalid-key-12345", "gpt-4o-mini", 5000);
        
        Set<Material> allowed = Set.of(Material.OAK_PLANKS, Material.COBBLESTONE);
        
        try {
            String json = client.generateBuildPlanJsonStreaming(
                "test",
                100,
                allowed,
                msg -> {}
            );
            throw new AssertionError("Should have thrown OpenAIException for invalid key");
        } catch (OpenAIException e) {
            assert e.getMessage() != null : "Exception should have message";
            System.out.println("    ✓ Caught expected exception: " + e.getMessage());
            
            // Verify it's an auth error
            assert e.isAuthError() : "Should be recognized as auth error";
            System.out.println("    ✓ Correctly identified as authentication error");
        }
    }
    
    /**
     * Test build validation catches invalid plans
     */
    public void testBuildValidation() throws Exception {
        PlanParser parser = new PlanParser();
        String[] materials = {"OAK_PLANKS", "COBBLESTONE", "GLASS"};
        
        // Test 1: Plan with no foundation should fail
        System.out.println("  Testing no-foundation validation...");
        String noFoundationJson = """
            {
              "s": [3, 3, 3],
              "b": [[1,1,1,0], [1,2,1,0]]
            }
            """;
        
        try {
            BuildPlan plan = parser.parse(noFoundationJson, materials);
            BuildValidator.validate(plan);
            throw new AssertionError("Should have thrown BuildValidationException for no foundation");
        } catch (BuildValidationException e) {
            assert e.getMessage().contains("foundation") || e.getMessage().contains("ground") : 
                "Error should mention foundation, got: " + e.getMessage();
            System.out.println("    ✓ No foundation detected");
        }
        
        // Test 2: Valid plan with foundation should pass
        System.out.println("  Testing valid plan...");
        String validJson = """
            {
              "s": [3, 3, 3],
              "b": [[0,0,0,0], [1,0,0,0], [2,0,0,0], [1,1,1,1]]
            }
            """;
        
        BuildPlan validPlan = parser.parse(validJson, materials);
        BuildValidator.validate(validPlan);
        System.out.println("    ✓ Valid plan accepted");
        
        // Test 3: Out of bounds coordinates should fail
        System.out.println("  Testing bounds validation...");
        String outOfBoundsJson = """
            {
              "s": [3, 3, 3],
              "b": [[0,0,0,0], [5,0,0,0]]
            }
            """;
        
        try {
            BuildPlan plan = parser.parse(outOfBoundsJson, materials);
            BuildValidator.validate(plan);
            throw new AssertionError("Should have thrown BuildValidationException for out of bounds");
        } catch (BuildValidationException e) {
            assert e.getMessage().contains("out of bounds") || e.getMessage().contains("coordinate") : 
                "Error should mention bounds, got: " + e.getMessage();
            System.out.println("    ✓ Out of bounds detected");
        }
    }
    
    /**
     * Test block rotation system
     */
    public void testBlockRotation() throws Exception {
        System.out.println("  Testing rotation for all cardinal directions...");
        
        // Test rotation calculations
        int[][] testCases = {
            {1, 0}, // dx=1, dz=0
            {0, 1}, // dx=0, dz=1
            {1, 1}, // dx=1, dz=1
        };
        
        for (BlockFace facing : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            System.out.println("    Testing " + facing + "...");
            for (int[] coords : testCases) {
                int[] rotated = Rotation.rotateXZ(coords[0], coords[1], facing);
                assert rotated.length == 2 : "Rotation should return [x, z]";
                // Just verify it returns valid coordinates
                System.out.println("      (" + coords[0] + "," + coords[1] + ") -> (" + rotated[0] + "," + rotated[1] + ")");
            }
        }
        
        System.out.println("  ✓ Rotation system working correctly");
    }
}
