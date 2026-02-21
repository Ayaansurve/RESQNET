package com.example.myapplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * TriageCalculator — Prioritizes survivors based on standard triage protocols.
 *
 * Standard triage categories (START protocol adapted):
 * 1. IMMEDIATE (Red) — Critical injuries, life-threatening conditions
 * 2. URGENT (Yellow) — Serious injuries but stable
 * 3. DELAYED (Green) — Minor injuries, mobile
 * 4. MINOR (White) — No injuries or very minor
 * 5. DECEASED (Black) — Not applicable in this context
 *
 * The prioritization considers:
 * - Injury severity (primary factor)
 * - Age (secondary factor — both children and elderly are higher priority)
 * - Location proximity (if distance data available)
 */
public class TriageCalculator {

    /**
     * Sort survivors by triage priority (highest priority first).
     * Returns a new list sorted by priority score.
     */
    public static List<SurvivorInfo> calculateTriage(List<SurvivorInfo> survivors) {
        List<SurvivorInfo> sorted = new ArrayList<>(survivors);
        Collections.sort(sorted, new Comparator<SurvivorInfo>() {
            @Override
            public int compare(SurvivorInfo a, SurvivorInfo b) {
                return Integer.compare(a.getTriagePriority(), b.getTriagePriority());
            }
        });
        return sorted;
    }

    /**
     * Group survivors by triage category.
     */
    public static List<List<SurvivorInfo>> groupByTriageCategory(List<SurvivorInfo> survivors) {
        List<List<SurvivorInfo>> groups = new ArrayList<>();

        // IMMEDIATE (Red) — Critical injuries
        List<SurvivorInfo> immediate = new ArrayList<>();
        // URGENT (Yellow) — Serious injuries
        List<SurvivorInfo> urgent = new ArrayList<>();
        // DELAYED (Green) — Minor injuries
        List<SurvivorInfo> delayed = new ArrayList<>();
        // MINOR (White) — No injuries
        List<SurvivorInfo> minor = new ArrayList<>();

        for (SurvivorInfo survivor : survivors) {
            switch (survivor.injuryLevel) {
                case CRITICAL:
                    immediate.add(survivor);
                    break;
                case SERIOUS:
                    urgent.add(survivor);
                    break;
                case MINOR:
                    delayed.add(survivor);
                    break;
                case NONE:
                    minor.add(survivor);
                    break;
            }
        }

        // Sort within each category by age
        Collections.sort(immediate, new Comparator<SurvivorInfo>() {
            @Override
            public int compare(SurvivorInfo a, SurvivorInfo b) {
                return Integer.compare(a.ageGroup.triagePriority, b.ageGroup.triagePriority);
            }
        });
        Collections.sort(urgent, new Comparator<SurvivorInfo>() {
            @Override
            public int compare(SurvivorInfo a, SurvivorInfo b) {
                return Integer.compare(a.ageGroup.triagePriority, b.ageGroup.triagePriority);
            }
        });
        Collections.sort(delayed, new Comparator<SurvivorInfo>() {
            @Override
            public int compare(SurvivorInfo a, SurvivorInfo b) {
                return Integer.compare(a.ageGroup.triagePriority, b.ageGroup.triagePriority);
            }
        });
        Collections.sort(minor, new Comparator<SurvivorInfo>() {
            @Override
            public int compare(SurvivorInfo a, SurvivorInfo b) {
                return Integer.compare(a.ageGroup.triagePriority, b.ageGroup.triagePriority);
            }
        });

        groups.add(immediate);
        groups.add(urgent);
        groups.add(delayed);
        groups.add(minor);

        return groups;
    }

    /**
     * Get the triage category color for UI display.
     */
    public static int getTriageCategoryColor(SurvivorInfo.InjuryLevel level) {
        return level.color;
    }

    /**
     * Get the triage category label.
     */
    public static String getTriageCategoryLabel(SurvivorInfo.InjuryLevel level) {
        return level.label;
    }

    /**
     * Get triage recommendation based on injury and age.
     */
    public static String getTriageRecommendation(SurvivorInfo survivor) {
        String recommendation;
        if (survivor.injuryLevel == SurvivorInfo.InjuryLevel.CRITICAL) {
            recommendation = "⚠️ CRITICAL: Immediate evacuation and advanced life support needed. " +
                    "Age: " + survivor.ageGroup.label + ", Location: " + survivor.location;
        } else if (survivor.injuryLevel == SurvivorInfo.InjuryLevel.SERIOUS) {
            recommendation = "🔴 URGENT: Requires immediate attention. " +
                    "Age: " + survivor.ageGroup.label + ", Location: " + survivor.location;
        } else if (survivor.injuryLevel == SurvivorInfo.InjuryLevel.MINOR) {
            recommendation = "🟡 DELAYED: Monitor closely, stabilise when possible. " +
                    "Age: " + survivor.ageGroup.label + ", Location: " + survivor.location;
        } else {
            recommendation = "🟢 MINOR: Ambulatory, can assist others. " +
                    "Age: " + survivor.ageGroup.label + ", Location: " + survivor.location;
        }
        return recommendation;
    }

    /**
     * Estimate rescue sequence based on current triage list.
     * Returns a formatted string with rescue order.
     */
    public static String getRescueSequence(List<SurvivorInfo> survivors) {
        List<SurvivorInfo> triaged = calculateTriage(survivors);
        StringBuilder sb = new StringBuilder();
        sb.append("Rescue Priority Order:\n\n");

        int count = 1;
        for (SurvivorInfo s : triaged) {
            sb.append(count).append(". ").append(s.name)
                    .append(" (").append(s.injuryLevel.label).append(")")
                    .append(" @ ").append(s.location)
                    .append("\n");
            count++;
        }

        return sb.toString();
    }
}





