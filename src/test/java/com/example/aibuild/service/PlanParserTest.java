package com.example.aibuild.service;

import com.example.aibuild.exception.PlanParseException;
import com.example.aibuild.model.BuildPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlanParser service
 */
class PlanParserTest {

    private PlanParser parser;
    private String[] materialNames;

    @BeforeEach
    void setUp() {
        parser = new PlanParser();
        materialNames = new String[]{"STONE", "OAK_PLANKS", "GLASS"};
    }

    @Test
    void testParseCompactFormat() throws PlanParseException {
        String json = "{\"s\": [5, 3, 5], \"b\": [[0, 0, 0, 0], [1, 0, 0, 0], [0, 1, 0, 1]]}";

        BuildPlan plan = parser.parse(json, materialNames);

        assertNotNull(plan);
        assertEquals(5, plan.size.x);
        assertEquals(3, plan.size.y);
        assertEquals(5, plan.size.z);
        assertEquals(3, plan.blocks.size());
        assertEquals("STONE", plan.blocks.get(0).material);
        assertEquals("STONE", plan.blocks.get(1).material);
        assertEquals("OAK_PLANKS", plan.blocks.get(2).material);
    }

    @Test
    void testParseEmptyJson() {
        String json = "";

        PlanParseException ex = assertThrows(
            PlanParseException.class,
            () -> parser.parse(json, materialNames)
        );
        assertTrue(ex.getMessage().contains("Empty JSON"));
    }

    @Test
    void testParseNullJson() {
        PlanParseException ex = assertThrows(
            PlanParseException.class,
            () -> parser.parse(null, materialNames)
        );
        assertTrue(ex.getMessage().contains("Empty JSON"));
    }

    @Test
    void testParseInvalidJson() {
        String json = "{ this is not valid json }";

        PlanParseException ex = assertThrows(
            PlanParseException.class,
            () -> parser.parse(json, materialNames)
        );
        assertTrue(ex.getMessage().contains("Failed to parse"));
    }

    @Test
    void testParseCompactFormat_MaterialIdOutOfBounds() throws PlanParseException {
        String json = "{\"s\": [5, 3, 5], \"b\": [[0, 0, 0, 999]]}";

        BuildPlan plan = parser.parse(json, materialNames);

        assertEquals("STONE", plan.blocks.get(0).material);
    }

    @Test
    void testParseNullResult() {
        String json = "null";

        PlanParseException ex = assertThrows(
            PlanParseException.class,
            () -> parser.parse(json, materialNames)
        );
        assertTrue(ex.getMessage().contains("null"));
    }
}
