package com.gymmanagement.service;

import com.gymmanagement.service.ToolChatModel.ToolSpec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AdminAiTools — the complete, closed list of things the Admin AI is
 * allowed to do. This class IS the security boundary between the model
 * and the system.
 *
 * What the model can do is exactly what is written in specs() and
 * execute(): five named, read-only operations. It cannot name a table,
 * write a query, pick a method by reflection, or reach a repository —
 * a function name it makes up simply is not in the switch below and
 * gets back an error. The model supplies only a function name plus a
 * couple of validated scalar arguments (two ISO dates, one small
 * integer); everything else is fixed code on our side.
 *
 * Each tool is a thin adapter to AdminOperationsService. No business
 * logic lives here: parse and validate arguments, call the service,
 * turn the result record into the JSON-style Map Gemini expects.
 *
 * Failure policy: execute() never throws. A bad argument, an unknown
 * tool, or an unexpected data-layer failure comes back as
 * {"error": "..."} so the model can correct itself or tell the admin
 * plainly that the data was unavailable — instead of the whole request
 * dying, or worse, the model papering over a gap with a made-up number.
 * Raw exception text is logged server-side but never handed to the
 * model, for the same reason raw Gemini errors never reach the user.
 */
@Component
public class AdminAiTools {

    public static final String GET_REVENUE_SUMMARY = "getRevenueSummary";
    public static final String GET_OVERDUE_MEMBERS = "getOverdueMembers";
    public static final String GET_MEMBERSHIP_STATS = "getMembershipStats";
    public static final String GET_CLASS_OCCUPANCY = "getClassOccupancy";
    public static final String GET_RECENT_PAYMENTS = "getRecentPayments";

    /** Dates become ISO strings, nulls are dropped — the compact shape the model should see. */
    private static final ObjectMapper RESULT_MAPPER = JsonMapper.builder()
        .findAndAddModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        .build();

    private final AdminOperationsService operations;

    public AdminAiTools(AdminOperationsService operations) {
        this.operations = operations;
    }

    // ── declarations the model sees ───────────────────────

    public List<ToolSpec> specs() {
        return List.of(
            new ToolSpec(GET_REVENUE_SUMMARY,
                "Returns real revenue: the total of completed payments recorded in MyFitness within " +
                "an optional date range, plus the payment count, average payment and a per-month " +
                "breakdown. Use for any question about money taken, income or revenue (for example " +
                "'this month', 'last quarter', 'this year', 'all time'). Convert relative periods to " +
                "explicit dates first. Omit both dates for all-time revenue. Does not know about " +
                "expenses or profit.",
                objectSchema(Map.of(
                    "startDate", stringProperty("Inclusive start date, YYYY-MM-DD. Omit for no lower bound."),
                    "endDate",   stringProperty("Inclusive end date, YYYY-MM-DD. Omit for no upper bound.")))),

            new ToolSpec(GET_OVERDUE_MEMBERS,
                "Returns the members whose membership payment is currently overdue, longest overdue " +
                "first, with their membership type, due date, days overdue and monthly fee. Use for " +
                "questions about late payers, unpaid memberships or who needs chasing.",
                objectSchema(Map.of())),

            new ToolSpec(GET_MEMBERSHIP_STATS,
                "Returns membership head-counts: total, active and inactive members, how many have " +
                "or lack a membership, frozen memberships, the count per membership type, and the " +
                "payment-status breakdown (paid, due soon, overdue, no recurring due date). Use for " +
                "'how many members', membership mix, or overall payment health.",
                objectSchema(Map.of())),

            new ToolSpec(GET_CLASS_OCCUPANCY,
                "Returns every bootcamp class with its schedule, instructor, capacity, number " +
                "enrolled, free spots, occupancy percentage and whether it is full or cancelled, plus " +
                "overall totals. Use for questions about class capacity, fullness, availability or " +
                "attendance.",
                objectSchema(Map.of())),

            new ToolSpec(GET_RECENT_PAYMENTS,
                "Returns the most recent completed payments, newest first, with member name, " +
                "description, amount and date. Use for 'latest payments' or 'what came in recently'.",
                objectSchema(Map.of(
                    "limit", Map.of("type", "integer",
                        "description", "How many payments to return, 1 to " +
                                       AdminOperationsService.MAX_RECENT_PAYMENTS + ". Defaults to " +
                                       AdminOperationsService.DEFAULT_RECENT_PAYMENTS + ".")))));
    }

    // ── execution ─────────────────────────────────────────

    /**
     * Runs one tool by name. Anything not in the switch is rejected —
     * this is the allow-list.
     *
     * @param args the arguments the model supplied (may be null/empty)
     * @return a JSON-style object for the model: the result, or {"error": "..."}
     */
    public Map<String, Object> execute(String name, Map<String, Object> args) {
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        try {
            if (name == null) return error("No tool name was given.");

            Object result = switch (name) {
                case GET_REVENUE_SUMMARY -> operations.getRevenueSummary(
                    optionalDate(safeArgs, "startDate"), optionalDate(safeArgs, "endDate"));
                case GET_OVERDUE_MEMBERS  -> operations.getOverdueMembers();
                case GET_MEMBERSHIP_STATS -> operations.getMembershipStats();
                case GET_CLASS_OCCUPANCY  -> operations.getClassOccupancy();
                case GET_RECENT_PAYMENTS  -> operations.getRecentPayments(optionalInt(safeArgs, "limit"));
                default -> null;
            };

            if (result == null) {
                return error("Unknown tool '" + name + "'. Available tools: " + toolNames() + ".");
            }
            return RESULT_MAPPER.convertValue(result, new TypeReference<LinkedHashMap<String, Object>>() {});

        } catch (IllegalArgumentException e) {
            // Bad or inconsistent arguments from the model — safe, specific, and fixable on retry.
            return error(e.getMessage());
        } catch (Exception e) {
            System.err.println("[AI] Admin tool '" + name + "' failed: " + e);
            return error("The data for this request could not be retrieved right now.");
        }
    }

    // ── argument parsing ──────────────────────────────────

    private static LocalDate optionalDate(Map<String, Object> args, String key) {
        Object raw = args.get(key);
        if (raw == null) return null;
        if (!(raw instanceof String text)) {
            throw new IllegalArgumentException(key + " must be a string in YYYY-MM-DD format.");
        }
        if (text.isBlank()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(key + " '" + text + "' is not a valid date. Use YYYY-MM-DD.");
        }
    }

    private static Integer optionalInt(Map<String, Object> args, String key) {
        Object raw = args.get(key);
        if (raw == null) return null;
        if (raw instanceof Number number) return number.intValue();
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                // fall through to the shared message below
            }
        }
        throw new IllegalArgumentException(key + " must be a whole number.");
    }

    // ── small builders ────────────────────────────────────

    private static Map<String, Object> objectSchema(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties);
    }

    private static Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> error(String message) {
        return Map.of("error", message);
    }

    private String toolNames() {
        return String.join(", ", specs().stream().map(ToolSpec::name).toList());
    }
}
