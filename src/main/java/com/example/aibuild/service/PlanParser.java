package com.example.aibuild.service;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.example.aibuild.exception.PlanParseException;
import com.example.aibuild.model.BuildPlan;

import java.io.StringReader;

/**
 * Service for parsing build plans from JSON
 */
public class PlanParser {
    private final Gson gson = new Gson();
    
    /**
     * Parse a build plan from JSON string
     * @param json JSON string containing the build plan
     * @param materialNames Array of material names for compact format expansion
     * @return Parsed and expanded build plan
     * @throws PlanParseException if parsing fails
     */
    public BuildPlan parse(String json, String[] materialNames) throws PlanParseException {
        if (json == null || json.isBlank()) {
            throw new PlanParseException("Empty JSON input", "");
        }
        
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(true);
            BuildPlan plan = gson.fromJson(reader, BuildPlan.class);
            
            if (plan == null) {
                throw new PlanParseException("Failed to parse JSON - result is null", getSnippet(json));
            }
            
            // Expand compact format if present
            plan.expandCompact(materialNames);
            
            return plan;
        } catch (Exception e) {
            throw new PlanParseException(
                "Failed to parse build plan: " + e.getMessage(), 
                e, 
                getSnippet(json)
            );
        }
    }
    
    /**
     * Get a snippet of the JSON for error reporting (first 500 chars)
     */
    private String getSnippet(String json) {
        if (json == null) return "";
        return json.substring(0, Math.min(500, json.length()));
    }
}
